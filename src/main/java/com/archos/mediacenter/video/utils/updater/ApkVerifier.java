// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.utils.updater;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * Validate a downloaded APK before handing it to the package installer: it must be
 * the same package and share the installed app's signing certificate, otherwise the
 * OS would reject the update (or, worse, install a different app).
 */
public final class ApkVerifier {

    private static final Logger log = LoggerFactory.getLogger(ApkVerifier.class);

    private ApkVerifier() {
    }

    public static class Result {
        public final boolean parsed;
        public final boolean packageMatches;
        public final boolean signerMatches;
        public final String packageName;
        public final long versionCode;
        public final String error;

        Result(boolean parsed, boolean packageMatches, boolean signerMatches, String packageName, long versionCode, String error) {
            this.parsed = parsed;
            this.packageMatches = packageMatches;
            this.signerMatches = signerMatches;
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.error = error;
        }

        public boolean isInstallable() {
            return parsed && packageMatches && signerMatches;
        }
    }

    @SuppressWarnings("deprecation")
    public static Result verify(Context context, File apk) {
        if (apk == null || !apk.isFile() || apk.length() == 0) {
            return new Result(false, false, false, null, -1L, "empty download");
        }
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive;
        try {
            archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        } catch (Exception e) {
            return new Result(false, false, false, null, -1L, "cannot read apk: " + e.getMessage());
        }
        if (archive == null) {
            return new Result(false, false, false, null, -1L, "cannot parse apk");
        }
        String packageName = archive.packageName;
        long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode()
                : archive.versionCode;
        boolean packageMatches = context.getPackageName().equals(packageName);
        boolean signerMatches = false;
        try {
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            Set<String> installedSigners = signerHashes(installed);
            Set<String> archiveSigners = signerHashes(archive);
            signerMatches = !installedSigners.isEmpty() && installedSigners.equals(archiveSigners);
        } catch (PackageManager.NameNotFoundException e) {
            log.error("verify: cannot read installed package", e);
        }
        if (!packageMatches || !signerMatches) {
            log.warn("verify: rejecting {} packageMatches={} signerMatches={}", apk.getName(), packageMatches, signerMatches);
        }
        return new Result(true, packageMatches, signerMatches, packageName, versionCode, null);
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signerHashes(PackageInfo info) {
        Set<String> hashes = new HashSet<>();
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo != null) {
                signatures = signingInfo.hasMultipleSigners()
                        ? signingInfo.getApkContentsSigners()
                        : signingInfo.getSigningCertificateHistory();
            }
        } else {
            signatures = info.signatures;
        }
        if (signatures == null) return hashes;
        for (Signature signature : signatures) {
            String hash = sha256(signature.toByteArray());
            if (hash != null) hashes.add(hash);
        }
        return hashes;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
