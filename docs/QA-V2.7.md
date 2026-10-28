# CrashLogOCR v2.7 — independent OCR verification and accuracy

## Why v2.6 still missed text
- ML Kit sometimes recognizes the Java frame but omits leading characters (`android`), particularly on compressed, zoomed-out screenshots.
- Whole-line selection is a geometry alternative; it cannot independently restore characters the OCR engine did not recognize.
- ML Kit Text.Line.text is built from its Element strings; drawing extra rectangles cannot reconstruct absent letters.

## New pipeline
1. Preserve original source resolution and tile long images; continue Latin ML Kit first pass.
2. On dark/narrow images, perform the bounded contrast-based ML Kit second pass.
3. Use offline Tesseract `eng` with `PSM_SINGLE_LINE` to examine only suspicious Java lines. Crops include context and a white border.
4. Adopt a Tesseract candidate only if it is materially more complete AND strongly overlaps the original candidate. Never blindly replace based solely on length.
5. Keep word boxes from ML Kit, and use full-line boxes for copying corrected lines where word geometry is incomplete. The user can select Words or Whole lines.
6. Accuracy level is selectable from Home menu: Fast (no line verification), Balanced (default, up to 8 checks per image), High (up to 24 checks per image).
7. All processing is local; the Tesseract English language model is already bundled. Unavailable Tesseract models fall back safely to ML Kit.

## Real-device test checklist
- Rescan the ORIGINAL screenshot from Android Gallery, not a compressed chat preview. Old history retains earlier OCR text and boxes.
- Compare `android.app.ActivityThread` in Words and Whole lines modes. Inspect RAW and Cleaned; do not infer OCR accuracy from blue selection outlines alone.
- Rescan in Fast, Balanced, High and compare text content and processing time.
- Verify High does not freeze the phone with a long screenshot or multiple pages.
- Test Arabic, English, and auto bilingual Java trace; all UI-language settings continue to work.
- Confirm no keyboard appears in image selection, scroll/fling/side scrub remains responsive, cropping and re-OCR still work.
- Verify status/header background text is not copied unintentionally when selecting specific frames.

## Limitations
- The screenshot provided in the chat is a screenshot of the app UI; it is not the original uncompressed screenshot that OCR reads.
- Even multiple local OCR engines cannot reliably reconstruct pixels that were destroyed by compression or a very small source image.
- High accuracy can be noticeably slower, because it runs another OCR engine over selected image regions.
- This change remains in a draft PR targeting a feature branch, not main.
