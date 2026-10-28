# CrashLogOCR 2.1 — Android crash screenshot OCR

[العربية أدناه](#العربية) · [CI build](https://github.com/alwansan/OCR-APK/actions)

An offline-first Android screenshot OCR tool for Java/Kotlin stacktraces. Based on the original CrashLogOCR v1 and the supplied Claude Code v2 prototype, with security, gallery access, and build improvements.

## Features

- **Choose from Photos** on the home screen using the Android system photo picker (no storage permission). Multiple images, up to 12.
- **Browse screenshots** through a built-in MediaStore gallery (optional photo permission; supports Android 14 partial media access) and quick access to the latest screenshot.
- **Open files** via Android's document picker (including Downloads and cloud providers).
- **Long screenshots** processed in overlapping tiles using a region decoder to reduce memory use and avoid shrinking small text into unreadability. OCR works entirely on device with the bundled ML Kit Latin model.
- **Stacktrace-specific cleaner**: strip dialog buttons, normalize line breaks and indented frames, handle some RTL effects. Keeps raw OCR alongside cleaned text. Never assume identifiers are correct without manual review.
- **Results review**: syntax highlights, root-cause summary, full/compact/raw tabs, code wrapping, edit, copy, Markdown copy, share.
- **Local history**: up to 30 recent results stored in app-private preferences, with individual delete and clear-all. Logs may contain sensitive information; clear history when no longer needed. Android backup is disabled.
- **Manual update checker**: HTTPS GitHub Releases latest endpoint, only accessed after user taps Check for updates. No screenshot uploads. App version update checks are NOT ML Kit runtime library updates.
- **Dependencies**: GitHub Dependabot PRs for Gradle and Actions dependencies. The bundled OCR engine requires a new APK and rebuild to update.

## Build

GitHub Actions produces **debug APK** on feature branches/PRs and main. Open a successful run's Artifacts. Built with Gradle 8.11.1, JDK 17, Android SDK 35. Debug builds use per-run developer debug signing; installing over older builds with different signatures may require uninstalling the old APK. **Back up / copy logs before uninstalling, because history is app-private.**

The attached Claude v2 prototype contained a JKS signing key plus passwords in source. They are deliberately **excluded**, and this project does not ship any production signing credentials. For seamless upgrades, configure a dedicated private release key as GitHub Actions secrets with a reviewed signing workflow; never commit it to the repository.

## Notes

- OCR is fallible for tiny punctuation and obfuscated class names (e.g. `j32`, `ca1`). Review raw and cleaned text before debugging.
- Gallery permission is only requested for the optional **in-app MediaStore gallery/latest screenshot** feature. The main **Choose from Photos** flow needs no media permission and reaches albums made available by Android's picker.
- Only Latin text recognition is bundled; Arabic UI buttons are filtered by cleanup rules, not OCR of Arabic script.
- Some extreme rotated panoramas may still be limited by Android bitmap memory; screenshots with normal EXIF orientation use efficient region decoding.

## العربية

التطبيق يستخرج سجل انهيار برامج أندرويد من لقطات الشاشة حتى الطويلة، ثم يعرض النص الأصلي والمنظّم للمراجعة.

**اختر من معرض الصور مباشرة** عبر زر «اختيار من معرض الصور» دون طلب صلاحية الوصول لكل صور الهاتف. يوجد أيضاً معرض داخلي اختياري بلقطات الشاشة، يطلب إذن الصور، وقائمة الملفات لتصفح التنزيلات.

**نسخ ومشاركة** النص كاملاً أو مختصراً أو ككتلة Markdown، مع تمييز سطور الخطأ والإطارات وإمكانية التعديل وسجل محلي للنتائج.

**التحديثات:** زر فحص التحديثات يبحث عن إصدار تطبيق جديد على GitHub Releases فقط. مكتبة OCR المدمجة تتحدث عبر Dependabot وإصدار APK جديد، ولا يتم تحميل كود تنفيذي تلقائياً من الإنترنت.

**الخصوصية:** OCR محلي؛ فقط فحص التحديثات يستخدم الإنترنت عند الطلب. السجلات محفوظة على الجهاز ويُفضل حذفها بعد الانتهاء. ملف التوقيع الأصلي الذي وصل داخل ZIP غير مدرج في المستودع لأسباب أمنية.
