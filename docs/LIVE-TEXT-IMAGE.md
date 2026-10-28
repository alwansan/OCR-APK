# v2.3 Live Text on Image QA

The image view uses the OCR engine's **actual word geometry**, not generated
rectangles. ML Kit uses `Text.Element.boundingBox` and Arabic/bilingual Tesseract
uses hOCR `ocrx_word` word rectangles.

## Manual Android QA

- Import the original crash screenshot; open the **source image** card on the result page.
- Tap one word: light-blue box becomes highlighted. Tap it again: deselect.
- Drag from a selected word to another across a stacktrace line. Press **Copy selected**
  and compare to the image. Use **Select all**, **Clear**, **Share**.
- Switch to an Arabic screenshot and Arabic/bilingual OCR. Verify RTL text
  is read in native order; no keyboard should appear anywhere in image view.
- Pinch zoom and pan from the black background without losing selection.
- Test a 1080×25,000 screenshot, and compare boxes at the top, middle, and bottom.
  Viewer should use a region decoder (not one giant in-memory bitmap).
- Open the result from **Recent** after app restart. Image and word boxes must persist.
- Remove the record / clear history. Its image must be deleted from app-private storage.
- Test 2+ input images; Previous/Next pages must show each image's own detected words.
- Older history records created before v2.3 will be text-only and do not acquire images retroactively.
- Copy/share only after selecting text. Ensure system keyboard never appears.

## Caveats
- Source images are copied into app-private storage only while history exists.
  Up to 12 images; individual image size is limited to 30 MiB, combined 120 MiB.
  Oversized/corrupt image copies fail safely while preserving text results.
- Word bounding boxes can be inaccurate when OCR fails or when source EXIF
  rotation is nontrivial. Review the actual screenshot before relying on
  package names or punctuation for bug reports.
- Debug signer is public/test-only and does not secure APK update identity.

## CI
`gradle :app:testDebugUnitTest :app:assembleDebug`
and inspect `.github/workflows/build-apk.yml` artifacts for new branch.
