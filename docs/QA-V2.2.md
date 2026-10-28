# CrashLogOCR 2.2 — Android manual validation

Automated Gradle tests cover trace formatting. These steps cover Android IME/selection
and Tesseract's actual on-device Arabic OCR, which require an Android device or emulator.

## 1. Result editor / scrolling
1. Install **v2.2** and open a long Java stacktrace screenshot.
2. Default mode is editable (open eye). Tap in the code: keyboard appears.
3. Scroll the page all the way down while the keyboard remains visible. The last code line,
   Copy and Share actions must remain reachable. Code wraps instead of being cut off horizontally.
4. Close and reopen the result: editing preference should remain.
5. Tap the eye to disable edits: keyboard disappears, tapping the text no longer opens it.
   Long-press a word and select/copy text; editing/pasting must be blocked.
6. Restart the app: the eye remains in read-only state.
7. Enable edits again; compact view should remain generated/read-only.

## 2. Language correctness
1. Menu -> OCR languages & models -> English: extract a Java stacktrace.
   Check package/class punctuation against the source image.
2. Choose Arabic: extract a clear Arabic paragraph (not an error screenshot).
   Arabic lines and character direction must be preserved; no Java formatting is applied.
3. Choose Arabic + English: extract an image with two paragraphs (one per script).
   Confirm both scripts are recognized. Check for word order/RTL and missing lines.
4. Test a very tall screenshot in all modes; make sure no out-of-memory crash occurs.
   Duplicate lines at tile boundaries should be checked manually.

## 3. Offline operation and explicit model update
1. With airplane mode enabled, read an Arabic screenshot immediately after installation.
   The bundled ara and eng traineddata files should be available.
2. Go online, open OCR languages & models, choose Refresh Arabic model.
   A download progress message appears and a success/failure result follows.
3. Disconnect again: Arabic should still work.
4. Repeat with the English model used by bilingual Tesseract; the ML Kit Latin engine
   is embedded in the APK and **does not** update this way.

## 4. Signing certificate caveat
The public community-debug key is **not a trusted release key**. Anyone can use it.
For successive v2.2+ debug builds check the certificate fingerprint stays identical:
`apksigner verify --print-certs app-debug.apk`. This signature is different from
the original v2.1 ephemeral debug key; Android won't permit an in-place upgrade
across the signature change. Export logs before a required uninstall.

Do NOT assert these manual cases have passed merely because the CI Gradle build is green.
