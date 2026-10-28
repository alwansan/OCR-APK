# CrashLogOCR v2.4 — regression and manual verification

## Fixed crash: custom image viewer

Reported stack: `NoSuchMethodException: LiveTextImageView.<init>(Context, AttributeSet)`
when opening `ImageTextActivity` (layout line 38).

Fix: `LiveTextImageView @JvmOverloads constructor(Context, AttributeSet?, defStyleAttr)`.
JUnit `LiveTextImageViewConstructorTest` asserts the exact Java reflection constructor XML
inflation requires. A successful Gradle build alone cannot prove runtime touch interactions,
so verify on Android by opening an actual image result from History.

## Image reader, selection and crop

1. Scan an image and tap the preview in Result. The image picker should open without a crash or keyboard.
2. Tap a word; drag across words. Drag blue selection handles to adjust range.
3. Use Select all, Clear, Copy, Share and Search selected.
4. For Tesseract Arabic or bilingual OCR, yellow indicates an available low-confidence
   word (<55 Tesseract x_wconf); toggle the confidence overlay on/off.
   ML Kit word elements in the selected API do not supply comparable scores.
5. Tap Crop / OCR; draw a rectangle on the original image; tap Recognize crop.
   Check that the new OCR result is recorded in history. The crop uses the visible
   source image region only, with sampled decoding bounded to ~4 megapixels.
6. Pinch zoom, pan, scan tall screenshots, test multiple images and EXIF rotation.
7. Reopen results after process restart. Old pre-v2.3 history remains text-only.

## Language choices

- With phone in Arabic, leave UI language on **Follow phone language**;
  interface should appear in Arabic, OCR defaults to bilingual for new users.
- With phone in English, default UI is English and OCR defaults to English.
- Override UI language manually in menu. Restart and check persistence.
- OCR recognition language remains independently selectable.
- Restore Follow phone language; check OS language changes propagate.
- Test Android 6 API 23 and Android 13+ per-app language settings.

## Trailing OCR artifacts

`OcrTailGuard` only strips the observed isolated `ع` / `July!` after a
confirmed JVM stack frame; never globally filters Arabic documents.
Unit tests assert both cleaning and preservation of ordinary Arabic text.
The Raw tab still shows original OCR for comparison.

## CI and APK distribution

- Push to `feature/v2-4-view-fix-lang-releases`.
- GitHub Actions tests and assembles Debug APK using the public shared test signer.
- Unique `versionCode = 24000 + github.run_number` to allow in-place updates.
- `publish-prerelease` only runs if the build job passes; creates a
  `v2.4.0-dev.<run_number>` GitHub prerelease with an actual APK asset.
- Users download that APK directly from GitHub Releases, no manual artifact ZIP extraction.
- The published **test signing key is public**. It has update continuity, not
  identity authenticity: malicious third-party APKs could use the same key.

Do not merge into main until phone QA and release job permissions are verified.
