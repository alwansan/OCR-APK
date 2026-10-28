# CrashLogOCR — v3 engineering roadmap (research-driven)

Status: proposal only, except the separate main APK release workflow change in this PR.
This is **not** a claim that PaddleOCR or any other new model has shipped.
Current stable baseline: v2.8 (merged into main via PR #10).

## Guiding principle

The user-provided Android stacktrace screenshots exposed two **different** failure modes:
1. **Recognition**: the recognizer returned `d.app.ActivityThread` instead of `android.app.ActivityThread`.
2. **Detection/selection geometry**: the pixels for `android` were visibly present but the blue hitbox began partway through the word.

Never measure only how much text is highlighted. Evaluate transcription and
geometry separately. Avoid guessing unknown identifiers in Java stacktraces.

## Priority 0 — reproducible evaluation before another model swap

Build a local benchmark (no screenshot uploads):
- At least 30 original English Android crash screenshots, 20 Arabic images,
  10 genuinely bilingual screenshots, 10 extra-long scrolling screenshots.
- Include untouched originals and clearly marked compressed/downscaled copies.
- Annotate exact text, all visible text rows, original-resolution coordinates,
  script per row and line reading order.
- Run existing ML Kit Latin, Tesseract Arabic/English and any new engine against
  the *same* data. Include latency and memory on Android devices (CPU only).
- Metrics: CER and WER (case-sensitive for stacktraces), percentage of detected
  visible lines, bbox recall at IoU >=0.5, P50/P95 latency, peak RSS, total
  model bytes, cold/warm loads, battery and fully-offline execution.
- Acceptance: no loss of existing English/Arabic coverage; significant
  *measured* reduction in missed lines and partial initial word boxes.
- Add golden tests for proper RTL/LTR order, multi-line JVM frames, EXIF
  rotation and page-tile overlap (including `Caused by:` chains).

**Why:** Previous iterations added more retries without objective per-engine
measurements. A model with higher benchmark OCR accuracy may still draw
incomplete hitboxes.

## Priority 1 — optional PaddleOCR proof of concept (v3.0 experiment)

Separate engine interface:
`OcrBackend.recognize(image, language, options): List<DetectedLine>`
where each line carries `text`, `polygon`, `confidence`, `sourceTransform`,
`modelVersion`, `pageIndex`, and reading order.

- Prototype PaddleOCR PP-OCRv6 **small** detector+recognizer for English code
  screenshots (Paddle's `tiny` tier is an alternate speed benchmark).
- For Arabic/English mixed screenshots, compare the dedicated
  `arabic_PP-OCRv5_mobile_rec` model with the existing Tesseract Arabic
  model; v6's 50-language unified model **does not include Arabic**.
- Use the Android ONNX Runtime CPU path as a starting point; check minSdk 23
  compatibility and ABI before shipping.
- Reference, but do not blindly copy, MakeACopy Android's Apache-2.0
  PaddleOCR pipeline: DB text detection -> polygon crop -> recognition ->
  dictionary decode -> editable word text layer.
- Do not replace ML Kit or bundle large OCR models by default. Introduce
  optional downloadable model packs, with SHA-256 verification, storage
  budget, explicit consent, and rollback. Offline after installation.
- Limit inference threads (start with 1–2), process bounded tiles and release
  bitmaps/native sessions. Avoid RAM/thermal spikes on Android phones.
- Compare real APK/model sizes and install/update continuity.
- Go/no-go: based on the P0 local benchmark, not vendor headline percentages.

## Priority 1 — correct text-on-image interaction (v3.0)

- Preserve **polygons**/quadrilaterals from detector; never synthesize
  selectable glyphs simply because line text contains missing words.
- Offer separate word/line selection with reliable reading order.
- Use geometry-aware nearest-ink hit testing, adjustable selection handles
  and fast vertical scroll; ensure all gestures remain responsive with
  20,000+ px screenshots and without summoning the keyboard.
- Explicit confidence highlighting; tap a low-confidence word to show OCR
  alternatives. Allow manual correction as a separate action.
- Remember corrections per history entry; never alter raw OCR silently.
- Show direct image/text comparison for debugging misalignment.

## Priority 2 — smarter practical workflows

1. **Crash-log mode**: detect Android Java/Kotlin stacktraces, render
   exception/cause/frame hierarchy, select or copy the full `Caused by:`
   chain, join wrapped lines with trace-specific rules while keeping raw.
   Show source line metadata and export a shareable plain-text report.
2. **Model manager** in Settings: per-language size, installed/version status,
   progress, checksums, update/rollback/removal, Wi-Fi-only option, storage
   limits; optional Tesseract `tessdata_best` vs `fast` comparison.
3. **Batch & history**: multi-select from Android Photo Picker without
   broad storage permissions; progress, cancel, resume, search, pin results,
   duplicates, tags and configurable local retention.
4. **Document capture**: optionally integrate ML Kit Document Scanner
   (Google Play services) for edge detection, crop, deskew, shadow removal,
   multipage scanning; maintain a gallery/file-picker fallback when Google
   Play services is unavailable.
5. **Export**: TXT, UTF-8 JSON with per-word coordinates, searchable PDF;
   preserve Arabic right-to-left structure and keep exported files local
   unless the user explicitly shares them.
6. **Quality diagnostics**: a user-opt-in local overlay diagnostic with
   original OCR line/word polygons and saved algorithm version. Redaction
   controls for crash-log IDs/package paths before sharing.

## Priority 3 — advanced optional features

- Multiple page side-by-side compare, smart crops with independent re-OCR,
  reusable per-app crash presets, and accessibility labels.
- Smart scan actions: copy error name, class/method, root cause, or full
  log; one-tap search for user-selected error text.
- Automated model health: check pinned source URLs for new versions and
  offer an explicit download, with release notes and rollback.
- **Do not** automatically upload private screenshots or stacktraces to a
  cloud OCR/VLM provider. An opt-in cloud mode, if ever introduced, needs
  clear disclosure and explicit image-level approval.

## Release and CI (implemented separately in this PR)

- Preserve the shared public community debug signer for update continuity.
- After successful tests/build **on main**, automatically publish an
  explicitly labelled **test prerelease APK** for easy direct install.
- Do not claim the public debug signature authenticates the publisher.
- Future: main/feature release channels, per-ABI artifacts, reproducible
  build checks, hardware test matrix (Android 6+, Arabic/English RTL).

## Source links (retrieved October 2026)

- PaddleOCR v6 (small/tiny/mobile): https://www.paddleocr.ai/latest/en/version3.x/algorithm/PP-OCRv6/PP-OCRv6.html
- PaddleOCR v5 multilingual Arabic models: https://www.paddleocr.ai/latest/en/version3.x/algorithm/PP-OCRv5/PP-OCRv5_multi_languages.html
- PaddleOCR module documentation: https://www.paddleocr.ai/latest/en/version3.x/module_usage/text_recognition.html
- Real Android PaddleOCR app + OCR review: https://github.com/egdels/makeacopy
- Tesseract traineddata fast versus best: https://tesseract-ocr.github.io/tessdoc/Data-Files
- ML Kit on-device document scanner: https://developers.google.com/ml-kit/vision/doc-scanner
- Google ML Kit text-recognition script coverage: https://developers.google.com/ml-kit/vision/text-recognition/v2/languages

## خلاصة بالعربية

الأولوية مو إضافة محركات أكثر بدون قياس. لازم نسوي مجموعة صور
حقيقية ونحسب دقة الحروف والكلمات، واكتشاف كل الأسطر، وحدود التحديد،
والسرعة والذاكرة. بعدها نجرب PaddleOCR v6 Small للإنجليزية ونموذج
PaddleOCR v5 العربي، كخيار إضافي مع ONNX Runtime، ونقارن النتائج
على الهاتف نفسه. من بعدها نطور تصحيح الكلمات المشكوك بها، وتصدير
PDF قابل للبحث، وفحص الصور جماعياً، والتحديثات المنفصلة للنماذج.
