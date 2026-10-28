# CrashLogOCR v3 alpha: offline Engine Lab

## What is actually implemented
- The home menu contains **OCR Engine Lab / مختبر محركات OCR**.
- Select exactly one original screenshot with the system picker.
- The app reads the identical URI with independent **ML Kit Latin** and **Tesseract** backends.
- For ML Kit comparison, the normal Tesseract single-line correction pass is disabled so it is a genuinely independent source.
- The Tesseract lab backend uses the current Arabic, English or bilingual OCR language preference, including English-only Tesseract in English mode.
- Shows both raw outputs, separate elapsed times, exact matching line count, and unmatched line counts.
- You can select text and copy the FULL result from either engine without sending screenshots to a server.
- Displays up to 45,000 characters for each result so huge screenshots don't overload TextView, but Copy copies full text.
- The test results remain in memory; they are not added to OCR history automatically.
- The comparison is **not an accuracy score** because agreement between two models does not establish ground truth.

## Phone QA
1. Open three-dot home menu > OCR Engine Lab.
2. Pick the exact uncompressed screenshot showing the start of `android.app.ActivityThread`.
3. Compare ML Kit vs Tesseract and note which returns the complete prefix, original case and punctuation.
4. Change recognition language to English and run again (to compare with English Tesseract), then select Bilingual to compare Arabic+English.
5. Compare 1080px-wide, 243px-wide compressed, and 20,000px tall screenshots; note elapsed times.
6. Confirm no app keyboard appears in the read-only results, Copy contains untruncated text, and Back exits safely.
7. When one engine fails, confirm the other result and the error remain visible.

## Next integration: PaddleOCR Android
- Official PP-OCRv6 SDK example (`deploy/ppocr-android/ppocr-sdk`) advertises minSdk 26, ONNX Runtime 1.21.1, and OpenCV 4.5.3. The stable app minSdk is 23.
- Do not silently raise the entire app minSdk, or ship megabytes of unverified models.
- Create an optional Android 26+ engine implementation or verified compatible ONNX-based adapter, with model files as explicit downloadable packs and SHA-256 checksums.
- Select from PP-OCRv6 Small English and PP-OCRv5 Arabic, and preserve detected polygon coordinates and reading order.
- Measure CER/WER and box recall using the existing `tools/ocr_benchmark.py`, on the same original images.
- Until integration and benchmarks are complete, PaddleOCR must NOT be presented as installed in this APK.

References:
https://swhl.github.io/PaddleOCR/main/en/version3.x/inference_deployment/cross_platform/android_deployment.html
https://github.com/egdels/makeacopy
https://github.com/PaddlePaddle/PaddleOCR
