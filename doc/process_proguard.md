# R8 and ProGuard Optimization Process

This document details the configuration, strategy, changes made, and verification procedures for R8 minification, bytecode optimization, resource shrinking, and ProGuard rules in Nova Video Player.

---

## 1. Overview & Strategy

Google Play Console flags 4 optimization areas:
1. **Optimization** (DEX bytecode optimization and dead code elimination)
2. **Minification** (Tree-shrinking unused Java/Kotlin code)
3. **Resource Shrinking** (Stripping unused XML, drawables, and strings)
4. **Obfuscation** (Renaming classes and methods to short symbols like `a.b.c()`)

### Strategy Chosen
* **Minification, Optimization, & Resource Shrinking**: Fully **ENABLED**.
* **Obfuscation**: Intentionally **DISABLED** (`-dontobfuscate`).
  * *Rationale*: Keeping class and method names un-obfuscated preserves 100% human-readable stack traces in Sentry crash reporting and `logcat` without requiring mapping symbolication. Obfuscation provides negligible execution performance gain on modern Android ART compared to tree shrinking and bytecode optimization.

---

## 2. Summary of Changes Made

### A. Project Build Files
* **[`Video/build.gradle`](file:///Users/marc/Documents/git/nova-publish/Video/build.gradle)**:
  * Set `shrinkResources = true` under `buildTypes.release`.
* **[`Video/gradle.properties`](file:///Users/marc/Documents/git/nova-publish/Video/gradle.properties)**:
  * Added `android.r8.optimizedResourceShrinking=false` to ensure compatibility between resource shrinking and Java `switch` statements over constant `R.id.*` expressions.
* **[`FileCoreLibrary/build.gradle`](file:///Users/marc/Documents/git/nova-publish/FileCoreLibrary/build.gradle) & [`MediaLib/build.gradle`](file:///Users/marc/Documents/git/nova-publish/MediaLib/build.gradle)**:
  * Added `consumerProguardFiles 'proguard-project.txt'` to `defaultConfig` so module-specific keep rules are automatically exported to consumer builds.

### B. Dynamic Resource Preservation
* **[`Video/res/raw/keep.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/raw/keep.xml)**:
  * Created raw keep configuration (`tools:keep="@string/codepage_extra_*,@string/iso639_*"`) to prevent dynamic string resources fetched via `Resources.getIdentifier` (subtitle codepages and language codes) from being stripped by R8 resource shrinker.

### C. ProGuard / R8 Rule Configurations

#### 1. [`FileCoreLibrary/proguard-project.txt`](file:///Users/marc/Documents/git/nova-publish/FileCoreLibrary/proguard-project.txt)
```proguard
#### Project Internal Classes ####
-keep class com.archos.filecorelibrary.** { *; }
-keep class com.archos.environment.** { *; }

#### Network & Protocol Libraries ####
-keep class com.hierynomus.smbj.** { *; }
-keep class net.schmizz.sshj.** { *; }
-keep class com.jcraft.jsch.** { *; }
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-keep class org.simpleframework.xml.** { *; }
-keep class jcifs.** { *; }
-dontwarn jcifs.**

#### Security & Logging ####
-keep class org.bouncycastle.** { *; }
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }
```

#### 2. [`MediaLib/proguard-project.txt`](file:///Users/marc/Documents/git/nova-publish/MediaLib/proguard-project.txt)
```proguard
#### JNI-bound & project internal classes ####
-keep class com.archos.medialib.** { *; }
-keep class com.archos.mediaprovider.** { *; }
-keep class com.archos.mediascraper.** { *; }

#### TMDb / Trakt JSON entities (Gson/Retrofit reflection targets) ####
-keep class com.uwetrottmann.trakt5.entities.** { *; }
-keep class com.uwetrottmann.trakt5.enums.** { *; }
-keep class com.uwetrottmann.tmdb2.entities.** { *; }
-keep class com.uwetrottmann.tmdb2.enumerations.** { *; }

#### Retrofit ####
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

#### Jetty / UPnP ####
-keep class org.eclipse.jetty.** { *; }
-keep class org.jupnp.** { *; }
```

#### 3. [`Video/proguard-project.txt`](file:///Users/marc/Documents/git/nova-publish/Video/proguard-project.txt)
```proguard
# R8 is configured with tree-shrinking (minification) and optimization enabled,
# while obfuscation remains disabled (-dontobfuscate) to preserve readable stack traces.

-dontobfuscate

-keepattributes SourceFile,LineNumberTable,Exceptions,InnerClasses,Signature,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

#### Application & project internal classes ####
-keep class com.archos.mediacenter.** { *; }
-keep class org.courville.nova.** { *; }

#### Serializable ####
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

#### Suppress warnings from optional/platform-specific third-party libraries ####
-dontwarn javax.**
-dontwarn com.sun.**
-dontwarn org.apache.**
-dontwarn org.seamless.**
-dontwarn org.eclipse.jetty.**
-dontnote org.eclipse.jetty.**
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.ietf.**
-dontwarn org.newsclub.net.unix.AFUNIXServerSocketChannel
-dontwarn org.newsclub.net.unix.AFUNIXSocketAddress
-dontwarn org.newsclub.net.unix.AFUNIXSocketChannel
-dontwarn sun.security.x509.X509Key
-dontwarn org.bouncycastle.crypto.DefaultBufferedBlockCipher
-dontwarn org.bouncycastle.crypto.modes.CBCModeCipher
-dontwarn org.bouncycastle.crypto.modes.CTRModeCipher
-dontwarn org.osgi.framework.**
-dontwarn org.osgi.service.**
```

---

## 3. Explanation of Rules & Architectural Rationale

### A. JNI Native Code Interop (Critical)
* **The Issue**: Native C/C++ shared libraries (`libavos.so`, `libfilecoreutils.so`, `libsfdec.so`, `libcputest.so`) dynamically register JNI methods at runtime via string-based lookups (e.g. `FindClass("com/archos/filecorelibrary/ArchosFileChannel")`).
* **The Safeguard**: Keeping `-keep class com.archos.** { *; }` and `-keep class org.courville.nova.** { *; }` ensures native `FindClass` calls always find their target Java classes, preventing startup crashes (`JNI FatalError called: Native registration unable to find class...`).

### B. Network & Storage Protocol Reflection
* **SMB (`smbj`, `jcifs-ng`)**: Requires keeping `com.hierynomus.smbj.**` and `jcifs.**` to allow dynamic negotiation of SMB2/SMB3 dialects and credentials.
* **SFTP (`sshj`, `jsch`)**: Requires keeping `net.schmizz.sshj.**`, `com.jcraft.jsch.**`, and `org.bouncycastle.**` for reflective cipher and key-exchange algorithm instantiation.
* **WebDAV (`sardine-android`)**: Requires keeping `com.thegrizzlylabs.sardineandroid.**` and `org.simpleframework.xml.**` for XML deserialization of WebDAV directory listings.
* **UPnP (`jupnp`, `jetty`)**: Requires keeping `org.jupnp.**` and `org.eclipse.jetty.**` for local DLNA/UPnP HTTP server endpoints.

### C. Media Scraping & DTOs
* **TMDb & Trakt**: Keeping `com.uwetrottmann.tmdb2.entities.**` and `com.uwetrottmann.trakt5.entities.**` ensures Gson/Retrofit JSON deserialization maps fields correctly.

---

## 4. Verification & Audit Checklist (What is to be Checked)

### 1. Build Verification
Run a clean release build to confirm R8 compilation:
```bash
cd Video
./gradlew clean assembleNoamazonRelease
```
* **Check**: `:minifyNoamazonReleaseWithR8` and `:convertShrunkResourcesToBinaryNoamazonRelease` complete successfully with `BUILD SUCCESSFUL`.

### 2. AAPT2 Resource Verification
Verify that dynamic resources are preserved:
```bash
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump resources \
  build/outputs/apk/noamazon/release/org.courville.nova-*-arm64-v8a-release.apk | grep -E "codepage_extra|iso639"
```
* **Check**: Output lists `@string/codepage_extra_*` and `@string/iso639_*` resources.

### 3. APK Inspection (Android Studio)
Open the release APK in **Build > Analyze APK...**:
* **Check**: Open `classes.dex` and confirm `com.archos` class names remain original (un-obfuscated) while third-party library classes are shrunk.

### 4. Runtime Logcat Audit
Install the release APK onto a test device or Android TV over ADB:
```bash
adb install -r build/outputs/apk/noamazon/release/org.courville.nova-*-arm64-v8a-release.apk
adb logcat -c
adb logcat "*:E"
```

**Perform the following 5 functional checks:**
1. **Cold Launch**: Verify instant main screen load without `JNI FatalError` or `ClassNotFoundException`.
2. **Samba / SMB**: Browse a local Windows/Samba share.
3. **SFTP**: Browse an SFTP server.
4. **WebDAV**: Browse a WebDAV folder.
5. **Video Playback & Subtitles**: Play a video and change subtitle encoding in settings to verify codepage string resolution.
