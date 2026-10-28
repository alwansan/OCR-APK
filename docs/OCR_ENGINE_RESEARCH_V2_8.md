# OCR engine research — why swapping models alone will not solve the screenshot bug

2026-10-28. Feature branch only. This is an implementation plan, NOT a claim that
PaddleOCR has already been embedded into this APK.

## Screenshot diagnosis

The user screenshot shows visible `android.app.ActivityThread` while an
OCR-derived blue hitbox begins *inside* that word, leaving the first letters
unselectable. This can be TWO independent problems:
1. OCR misses part of the characters in its recognised text.
2. The detector's element or line rectangle does not cover the visible glyphs
   (even when the text is correctly recognised).

A more powerful recognition model cannot alone repair defective selection
geometry. We must measure **character error rate (CER)** and **visible text
region recall (VTR)** as separate metrics. The provided image is itself a
screenshot of the OCR viewer, not the original lossless scan.

## Candidate on-device engines

| Backend | Arabic | English / JVM traces | Android feasibility | Planned use |
|---|---|---|---|---|
| Existing ML Kit Text Recognition v2 | No Arabic recognizer | Supported, fast | Already integrated | Fast baseline |
| Existing Tesseract4Android + tessdata_fast | Yes | Yes | Already integrated | Independent retry / offline fallback |
| PaddleOCR PP-OCRv6 small det + rec | Arabic not in unified v6 model | English and Latin scripts; newer small tier | ONNX Runtime; verify exported model operators | **Preferred first English A/B benchmark** |\n| PaddleOCR PP-OCRv5 mobile det + en_mobile_rec | English only | Proven specialized English option | ONNX Runtime or Paddle-Lite | Fallback English candidate |
| PaddleOCR PP-OCRv5 mobile det + arabic_mobile_rec | Yes; also English supported | Arabic + English | ONNX Runtime; separate recognizer & dictionary | Primary Arabic candidate |
| RapidOCR | Depends on configured models | PP-OCRv5/6 variants | ONNX Runtime, MNN, or other runtimes | Alternate model deployment/toolchain |

References (official where possible):
- https://github.com/PaddlePaddle/PaddleOCR
- https://paddlepaddle.github.io/PaddleOCR/latest/en/version3.x/algorithm/PP-OCRv5/PP-OCRv5_multi_languages.html
- https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/lite/readme.md
- https://github.com/RapidAI/RapidOCR
- https://github.com/egdels/makeacopy — **real Android app**, working
  PaddleOCR V5 via ONNX Runtime with Arabic and English; separate paddle
  and Tesseract product flavors, OCR review UI, and on-device tests.
- https://github.com/egdels/makeacopy/tree/main/app/src/paddle/java/de/schliweb/makeacopy/utils/ocr/paddle
  — useful Apache-2.0 reference for detection, quad crops, recognition and
  model management. Integration requires adapters and dependency validation.

## How to implement the PaddleOCR proof of concept

**Do not** replace ML Kit in the shipping path until tested. PP-OCRv6 (June 2026) has small/tiny/medium tiers for English and Latin scripts. Arabic still requires the PP-OCRv5 Arabic recognizer. See https://github.com/PaddlePaddle/PaddleOCR/releases/tag/v3.7.0 and official v6 documentation.
1. Introduce a separate `PaddleOnnxEngine` behind a backend interface.
   Models are optional, downloaded on explicit user action, not embedded in the
   ~42 MB baseline APK. Checksums must be pinned to a specific model revision.
2. Benchmark PP-OCRv6_small_det + small_rec as the newer English baseline, and also PP-OCRv5_mobile_det + `en_PP-OCRv5_mobile_rec` ONNX/ORT
   with matching character dictionary and preprocessing, or use a verified
   MakeACopy-compatible asset bundle. For Arabic import
   `arabic_PP-OCRv5_mobile_rec` and its dictionary separately.
3. Use Android ONNX Runtime AAR/Java API on background worker, CPU-first,
   limit sessions to 1–2 threads, and verify actual minimum Android API (our
   app minSdk 23 differs from MakeACopy's minSdk 29).
4. Return **actual text polygons** (detector), recognised string, confidence,
   source-image page, orientation and coordinate transform. Keep a source-ID
   for each result; never derive word geometry from text length.
5. Adapt Paddle's line/polygon boxes to the existing `OcrWord` geometry.
   If Paddle only returns line polygons, offer line selection; do not claim
   per-word boxes without proper text segmentation.
6. Use the existing image tiler with overlap and deduplication by geometry,
   process bounded regions and return to the UI progressively. Cap memory.
7. Create a benchmark of at least 20 ORIGINAL screenshots and 10 Arabic
   samples: dense stacktrace, tiny captions, wrapped code, long screenshots,
   rotated images, black and light backgrounds, punctuation, RTL/LTR mixes.
8. Benchmark ML Kit, Tesseract, Paddle English and Paddle Arabic on the exact
   SAME originals. Metrics: CER, WER, number of skipped lines, bounding-box
   recall against annotated visible text, P50/P95 processing duration, peak
   memory, cold-start time, APK/model download size, and no-network operation.
9. Only prefer Paddle by default if accuracy gains are repeatable on JVM
   screenshots without unacceptable on-device latency; retain old engines as
   user-selectable fallbacks.

## Separate immediate geometry fix in this branch

`ImageLineGeometry` estimates the left boundary of visible bright glyphs
on a dark screenshot **independently of ML Kit boxes**. Full-line and leading-word
selection rectangles are extended; actual OCR text is NOT fabricated or
silently repaired. This is deliberately a bounded fallback, not a substitute
for the real Paddle detection model.

## License and release policy

PaddleOCR and MakeACopy's Paddle code are Apache-2.0 in the examined paths.
Preserve their copyright notices and include their required NOTICE files when
adapting code. Do not copy unverified model weights or remove required
attribution. Keep the existing shared community debug signing key and
auto-published feature-branch prereleases. Never merge into main without
passing device evaluation.
