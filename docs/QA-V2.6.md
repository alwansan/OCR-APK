# v2.6 — OCR accuracy improvements

## Real limitations of supplied samples
- The provided very long original screenshot was compressed to just 243 px wide in chat. Upscaling may improve segmentation but cannot recreate characters lost to downsampling/JPEG artifacts.
- Prefer selecting the ORIGINAL screenshot from Android Gallery, not a screenshot of a zoomed-out preview or an image compressed by messaging apps.

## Improvements
- Keep original input pixels for screenshots up to 3200 px wide; tiny inputs get 2x to 3x scaling with bounded ~4 MP image tiles.
- On dark or small images, ML Kit runs an additional reading of a contrast-enhanced (often inverted) tile; source input remains untouched.
- Fuse text hypotheses by overlap, shared tokens and content similarity, not by length alone.
- Save full ML Kit line rectangles separately from word rectangles. In image viewer choose **Whole lines / الأسطر كاملة** to select full recognized Java lines even when element boxes miss pieces.
- Full line selection is only offered where true line bounding boxes exist. Arabic/bilingual Tesseract keeps word selection until equivalent accurate line geometry is available.
- Low-memory, all on-device. Existing OCR text is preserved as RAW for comparison.

## Manual QA
1. Import original large screenshot and the compressed 243px wide version as separate inputs.
2. Compare recognized text, punctuation and missing leading `android` fragments in both.
3. Open image overlay; switch Words / Whole lines and copy a full Java frame.
4. Confirm thumbnails, multi-page navigation, Arabic OCR, and long-image scroll still work.
5. Check that double recognition does not insert duplicate lines or change correct stacktrace text.
6. Check memory/performance on long screenshots and low-end devices.

## Build
- Branch feature/v2-6-ocr-line-accuracy only; do not merge to main until device QA.
- GitHub Actions runs unit tests, builds debug APK, then publishes a direct v2.6 prerelease after successful checks.
- Existing shared test signing key is retained.