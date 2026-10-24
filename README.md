# PhotoRevive
A separate offline Android app for batch photo enhancement and scratch / narrow-tear repair. Kotlin and bundled OpenCV. Android 10+. No account, backend, Internet permission, image uploads or downloaded models.

## Use
Select local photos or a folder (for example Pictures/MemoryBox/Family). Folder import reads images directly in that folder, not nested folders. Choose an output album. Tap a row to repair or preview one image, or process the pending queue in a batch.

Enhancement applies conservative grey-world colour balancing, local luminance contrast, light denoising and gentle sharpening. In the editor, pinch to zoom, choose Move to pan, and paint over scratches or narrow tears. Erase removes unwanted marks; Undo removes the latest brush stroke. Detect scratches proposes thin bright/dark structures as red marks. Review them and erase false positives before applying repair. Before/after preview has a draggable divider. Save always writes a new JPEG, quality 97, to Pictures/PhotoRevive/<Album>/.

Batch automatic scratch repair is OFF by default because detection can select intentional lines and fine details. Enable it only after reviewing a representative photo. Batch work runs one image at a time while the app is open. Leaving the app pauses after the current photo; progress is saved after every file. Resume pending files on return or after reopening. Failed images are retained in the queue with an error and can be retried. A process kill at the exact moment a JPEG is saved can leave an extra output copy; originals are never overwritten.

## Limits and safety
OpenCV Telea inpainting interpolates surrounding pixels. It can repair small scratches and narrow tears; it cannot reliably reconstruct missing faces, text, large tears or lost historical detail. Areas covering 20% or more of the photo are rejected. Repair wide damage in smaller sections and inspect results. Automatic candidates are heuristic and need review. This is not a generative restoration model.

ImageDecoder applies source orientation, converts to sRGB, and limits the longest decoded side to 3200 pixels to bound memory. Smaller images retain their resolution. Originals are opened read-only and never modified. Output JPEGs have baked-in orientation; source EXIF metadata is not copied. Review saved photos in your Gallery app. Selected URI access can expire if a provider does not support persistent permissions; reselect those photos.

## Build and install
Use JDK 17, Gradle 8.11.1 and Android SDK 35:
```sh
gradle wrapper --gradle-version 8.11.1
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
# With an emulator/device attached:
./gradlew :app:connectedDebugAndroidTest
```
GitHub Actions generates the pinned Gradle wrapper, builds both APKs, runs unit tests and lint, runs instrumented tests on Android 15, verifies the APK signature and absence of Internet permission, and uploads PhotoRevive-debug (APK + SHA-256, retained 30 days).

On Android, download the artifact ZIP, extract it in Files, tap PhotoRevive-debug.apk, and allow installation from Files when prompted. It installs alongside MemoryBox with a separate application ID (com.photorevive.android).

## Tests
Unit tests cover safe destination names. Emulator tests cover scratch detection, masked inpainting, overly large mask rejection, enhancement and empty masks, MediaStore JPEG export, and a two-photo batch that verifies original file hashes remain unchanged. Real old photos and a range of phones still require manual quality testing.
