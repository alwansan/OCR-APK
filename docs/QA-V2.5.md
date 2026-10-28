# CrashLogOCR v2.5 — smooth navigation and OCR quality regression QA

## Long screenshot navigation
- Open a saved 1080×20,000 crash screenshot in the on-image word selector.
- Default Move image mode: swipe vertically or diagonally **even starting on a word** to pan, flick to continue scrolling.
- Drag blue scrollbar thumb on the right edge to jump to top, middle and bottom.
- Two-finger pinch zoom, tapping toggles a word, long-press then drag starts range selection.
- Optional Select range mode changes one-finger drag to selection. Switch back to Move image to pan.
- Adjust blue selection handles and copy/share. No keyboard should appear.
- Crop/OCR mode remains independent of scroll/selection mode.

## Recovered missing word fragments
- ML Kit line text can include fragments not returned as separate Element boxes.
- LineBoxRecovery estimates an overlay ONLY for text already in that line, without inventing characters.
- Test the observed andro + id.app.ActivityThread example. Geometry is approximate; inspect before copying.
- For pixels not recognized by OCR at all, select Crop/OCR for a new pass.
- Tesseract hOCR x_wconf regex corrected; inspect low-confidence highlighting on Arabic/bilingual words.

## Spurious trailing words in OCR
- Reprocess a saved Java trace ending ZygoteInit.java:944)July! / ...Jluy! with Clean again.
- Cleaned text should end with ) but Raw must remain unchanged.
- Real Arabic prose with isolated ع and literal July in prose are not removed.

## Build and release
- CI tests and builds feature/v2-5-smooth-pan-fast-scroll-ocr.
- If successful, a downloadable v2.5.0-dev.<run_number> APK is published automatically as GitHub prerelease.
- Shared public debug signing key unchanged, main untouched.
- Device gesture and OCR QA still required even when CI passes.