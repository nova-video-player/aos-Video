# Google Play Console Bitmap Performance & Sub-Sampling Analysis

## 1. Overview & Google Play Console Feedback

Google Play Console flagged a performance and memory warning regarding direct usage of `BitmapFactory` without `BitmapFactory.Options.inSampleSize` sub-sampling.

### Original Feedback (French)
> **Améliorez les performances de votre appli avec le sous-échantillonnage de bitmap**
> Votre appli utilise `BitmapFactory` sans sous-échantillonnage aux endroits suivants :
>
> 1. `com.archos.mediacenter.utils.imageview.ScraperImageProcessor.loadBitmap`  
>    *Type de problème : paramètre BitmapFactory.Options manquant*
> 2. `com.archos.mediacenter.utils.imageview.SimpleFileProcessor.loadBitmap`  
>    *Type de problème : paramètre BitmapFactory.Options manquant*
> 3. `com.archos.mediacenter.video.browser.MainActivity$GlobalResumeTask.lambda$execute$1`  
>    *Type de problème : paramètre BitmapFactory.Options manquant*
> 4. `com.archos.mediacenter.video.browser.UpdateRecommendationsService.update`  
>    *Type de problème : paramètre BitmapFactory.Options manquant*
> 5. `com.archos.mediacenter.video.picasso.SmbRequestHandler.load`  
>    *Type de problème : paramètre BitmapFactory.Options manquant*
> 6. `com.archos.mediacenter.video.player.PlayerActivity$62.run`  
>    *Type de problème : paramètre BitmapFactory.Options manquant*
> 7. `com.archos.mediacenter.video.player.PlayerService.updateNowPlayingMetadata`  
>    *Type de problème : paramètre BitmapFactory.Options manquant*
>
> *Le chargement de bitmaps en haute résolution peut entraîner une utilisation excessive de la mémoire, en particulier si la résolution des images augmente dans les futures mises à jour.*
> *Envisagez d'utiliser une bibliothèque pour le chargement d'images afin de gérer automatiquement la mémoire et le sous-échantillonnage. Si vous devez utiliser BitmapFactory directement, assurez-vous d'utiliser BitmapFactory.Options.inSampleSize pour sous-échantillonner l'image à la taille d'affichage requise.*

---

## 2. Technical Context & Memory Impact

### Why Un-sampled Bitmap Decoding is Problematic
When an image file (e.g., $3000 \times 4000$ pixels or raw camera photo / 4K fanart) is decoded into memory via `BitmapFactory.decodeFile()` without `BitmapFactory.Options`, Android allocates an uncompressed `ARGB_8888` pixel array:
$$\text{Memory Size} = \text{Width} \times \text{Height} \times 4 \text{ bytes}$$
- A single $3000 \times 4000$ image consumes **48 MB of RAM**.
- Loading multiple full-resolution bitmaps in background tasks or list adapters causes heavy Garbage Collection (GC) pauses, UI stutter, and potential Out Of Memory (`OOM`) crashes.

### Sub-Sampling (`inSampleSize`) Mechanism
`inSampleSize` decodes pixels at intervals (power-of-two scale factors, e.g. `2`, `4`, `8`):
- `inSampleSize = 2`: Decodes $\frac{1}{2}$ width and $\frac{1}{2}$ height $\rightarrow$ **75% memory reduction** ($\frac{1}{4}$ RAM).
- `inSampleSize = 4`: Decodes $\frac{1}{4}$ width and $\frac{1}{4}$ height $\rightarrow$ **93.75% memory reduction** ($\frac{1}{16}$ RAM).

---

## 3. Backdrop Analysis (`w1280` & Custom 4K Local Backdrops)

### TMDb Backdrop Resolution (`w1280`)
In `MediaLib` (`ScraperImage.java`), TMDb backdrops are fetched as `BACKDROP_LARGE = "w1280"` ($1280 \times 720$ px widescreen high-definition):
- A single `w1280` backdrop bitmap in RAM consumes:
  $$1280 \times 720 \times 4 \text{ bytes} \approx \mathbf{3.68 \text{ MB RAM}}$$

### Fullscreen Display vs. Grid Selection Thumbnails
Backdrops are used in two distinct UI contexts in Nova:

1. **Fullscreen Backgrounds (`VideoDetailsFragment`, `MainFragment`, Leanback `BackgroundManager`)**:
   - Displayed full-screen on 1080p ($1920 \times 1080$) or 4K ($3840 \times 2160$) displays.
   - For a `w1280` backdrop ($1280 \times 720$), requesting target bounds of $1920 \times 1080$ calculates **`inSampleSize = 1`**.
   - **Result**: Zero downsampling loss; 100% of the $1280 \times 720$ HD backdrop detail is preserved for full-screen display.

2. **Backdrop Selector Grid Tiles (`VideoInfoBackdropChooserFragment`)**:
   - Displays multiple backdrop choices in a small grid view (~$320 \times 180$ px per tile).
   - **Without sub-sampling**: Loading 10 backdrop choices in a grid decodes ten full `w1280` images into RAM $\rightarrow$ **36.8 MB RAM**.
   - **With sub-sampling (`inSampleSize = 4`)**: Decodes tiles at $320 \times 180$ px $\rightarrow$ **0.23 MB RAM per tile** (94% RAM reduction), while maintaining 100% crisp tile display.

3. **Protection Against Custom 4K Local Backdrops**:
   - Users with custom local NFO backdrops or raw fanart ($3840 \times 2160$ px, **33.1 MB RAM**) would previously cause severe OOM risks during grid scrolling.
   - Sub-sampling with target bounds of $1920 \times 1080$ downsamples 4K backdrops to $1920 \times 1080$ (`inSampleSize = 2`, **8.3 MB RAM**), protecting against OOM while matching full HD screen resolution.

---

## 4. Synergy with TMDB `w500` & `FidelityTransformation`

1. **TMDb Poster Resolution (`w500`)**:  
   In `MediaLib`, commit `8a4464738c879e684ac10d6d32360e2c5b328f60` standardized poster downloads to TMDb `w500` (~500 × 750 px, ~1.5 MB RAM).
2. **`FidelityTransformation` Integration**:  
   `Video/src/main/java/com/archos/mediacenter/video/picasso/FidelityTransformation.java` performs multi-step SSAA downscaling and enables GPU mipmapping (`finalBitmap.setHasMipMap(true)`).
3. **Sub-sampling Safety**:  
   - For standard `w500` posters (~500px), sub-sampling to view containers (~250–500px) calculates `inSampleSize = 1` or `inSampleSize = 2`.
   - `inSampleSize = 1` retains 100% of the `w500` image detail.
   - `inSampleSize = 2` downsamples to ~250 × 375 px, which feeds `FidelityTransformation`'s optimized *Path B (Fast Single-pass Path)*.
   - For non-standard high-res local images (3000 × 4000 px), `inSampleSize` prevents out-of-memory crashes before Picasso or `FidelityTransformation` touches the bitmap.

---

## 5. Analysis & Proposed Fixes for the 7 Flagged Locations

### Location 1: `ScraperImageProcessor.loadBitmap`
- **File**: `MediaLib/src/com/archos/mediacenter/utils/imageview/ScraperImageProcessor.java` ([line 41](file:///Users/marc/Documents/git/nova-publish/MediaLib/src/com/archos/mediacenter/utils/imageview/ScraperImageProcessor.java#L41))
- **Current Code**:
  ```java
  taskItem.result.bitmap = BitmapFactory.decodeFile(file);
  ```
- **Proposed Change**: Use `BitmapUtils.decodeSampledBitmapFromFile(file, reqWidth, reqHeight)` based on whether the `ScraperImage` target is a poster or backdrop.

---

### Location 2: `SimpleFileProcessor.loadBitmap`
- **File**: `MediaLib/src/com/archos/mediacenter/utils/imageview/SimpleFileProcessor.java` ([line 45](file:///Users/marc/Documents/git/nova-publish/MediaLib/src/com/archos/mediacenter/utils/imageview/SimpleFileProcessor.java#L45))
- **Current Code**:
  ```java
  Bitmap bm = BitmapFactory.decodeFile(file);
  if (mScale && bm != null) {
      bm = BitmapUtils.scaleThumbnailCenterCrop(bm, mWidth, mHeight);
  }
  ```
- **Proposed Change**: Pass `mWidth` and `mHeight` to `BitmapUtils.decodeSampledBitmapFromFile(file, mWidth, mHeight)` *before* cropping, avoiding loading full-resolution pixels into RAM first.

---

### Location 3: `MainActivity$GlobalResumeTask.execute`
- **File**: `Video/src/main/java/com/archos/mediacenter/video/browser/MainActivity.java` ([line 948](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/browser/MainActivity.java#L948))
- **Current Code**:
  ```java
  thumbnail = BitmapFactory.decodeFile(scraperCursor.getString(index_cover));
  ```
- **Proposed Change**: Use `BitmapUtils.decodeSampledBitmapFromFile(coverPath, 300, 450)` to decode thumbnail cover sized for home screen resume banner.

---

### Location 4: `UpdateRecommendationsService.update`
- **File**: `Video/src/main/java/com/archos/mediacenter/video/browser/UpdateRecommendationsService.java` ([line 155](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/browser/UpdateRecommendationsService.java#L155))
- **Current Code**:
  ```java
  Bitmap bitmap = BitmapFactory.decodeFile(scraperCover);
  ```
- **Proposed Change**: Use `BitmapUtils.decodeSampledBitmapFromFile(scraperCover, 500, 750)` matching recommendation card bounds.

---

### Location 5: `SmbRequestHandler.load`
- **File**: `Video/src/main/java/com/archos/mediacenter/video/picasso/SmbRequestHandler.java` ([line 63](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/picasso/SmbRequestHandler.java#L63))
- **Current Code**:
  ```java
  Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
  ```
- **Proposed Change**: Use `request.targetWidth` and `request.targetHeight` from Picasso's `Request` to compute `inSampleSize` for `decodeStream`.

---

### Location 6: `PlayerActivity` Thumbnail Thread
- **File**: `Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java` ([line 3624](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java#L3624))
- **Current Code**:
  ```java
  Bitmap bm = BitmapFactory.decodeFile(posterPath);
  ```
- **Proposed Change**: Use `BitmapUtils.decodeSampledBitmapFromFile(posterPath, 150, 225)` downsampling before `createScaledBitmap` for 100px control thumb.

---

### Location 7: `PlayerService.updateNowPlayingMetadata`
- **File**: `Video/src/main/java/com/archos/mediacenter/video/player/PlayerService.java` ([line 2477](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/PlayerService.java#L2477))
- **Current Code**:
  ```java
  bitmap = BitmapFactory.decodeFile(mVideoInfo.scraperCover);
  ```
- **Proposed Change**: Use `BitmapUtils.decodeSampledBitmapFromFile(mVideoInfo.scraperCover, 512, 512)` matching lockscreen art metadata bounds.

---

## 6. Helper Utility Additions in `BitmapUtils.java`

```java
public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {
        final int halfHeight = height / 2;
        final int halfWidth = width / 2;
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2;
        }
    }
    return inSampleSize;
}

public static Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
    if (filePath == null) return null;
    final BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(filePath, options);

    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
    options.inJustDecodeBounds = false;
    return BitmapFactory.decodeFile(filePath, options);
}
```

---

## 7. Expected Results & Summary

- **Visual Quality**: 100% HD visual fidelity maintained for full-screen backdrops ($1280 \times 720$) and posters ($500 \times 750$).
- **Grid RAM Savings**: 94% RAM reduction when displaying backdrop selection grids (`VideoInfoBackdropChooserFragment`).
- **4K Safeguard**: Prevents OOM when users load custom 4K local backdrops ($3840 \times 2160$, 33.1 MB RAM downsampled to 8.3 MB RAM).
- **Play Console Compliance**: Fully addresses all 7 flagged `BitmapFactory.Options` performance issues.
