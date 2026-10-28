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

## CrashLogOCR v2.2 — result usability and bilingual OCR

- The result screen now scrolls vertically, including when the keyboard is visible. Wrapped code lines are the default for new installs.
- An eye control switches between **Edit** and **Read only**. Read-only text is selectable/copyable but cannot be changed and does not show the keyboard. The setting persists on the phone.
- The home screen menu includes **OCR languages & models**: English (bundled ML Kit), Arabic (Tesseract), and Arabic + English (Tesseract).
- The app embeds official `tessdata_fast` Arabic (`ara`) and English (`eng`) models during Gradle builds. First recognition works offline.
- You can explicitly refresh the Arabic or English language model from Settings. Refresh downloads a traineddata file from the official repository over HTTPS, checks size and validates it with Tesseract before replacing the old file.
- No screenshot or OCR result is uploaded. Updating native OCR libraries still requires a new APK.

### Reproducible PUBLIC test signing — important security limitation

The project now contains `app/signing/community-debug.jks.base64`, a shared public **test-only** private key. Gradle decodes it and uses the same certificate for all debug builds. Alias: `crashlogocr`; public test password: `ocr-open-debug`.

**Security warning:** Everybody can sign a modified or malicious APK using this published private key. Android will not distinguish that APK from an authentic update based on its signature alone. It is therefore NOT appropriate for trusted release signing; obtain APKs only from the repository's verified CI and consider setting up private signing for a production channel. Switching from earlier Android-generated debug keys requires one uninstall/reinstall because the old signatures differ.

### العربية

تم إصلاح تمرير صفحة النتيجة حتى عند ظهور الكيبورد، وإضافة زر العين للتبديل بين التحرير والقراءة فقط. بوضع القراءة تگدر تحدد النص وتنسخه بدون كيبورد، وينحفظ آخر اختيار تلقائياً.

للتعرف على العربية، افتح القائمة الرئيسية ← **لغات OCR والنماذج** واختر العربية أو العربية والإنجليزية. العربية تعتمد Tesseract ونموذجه `ara`، والإنجليزية البرمجية تستمر على ML Kit. ملفات اللغة موجودة مع التطبيق للعمل دون إنترنت، ويمكن تحديثها يدوياً من الإعدادات.

**تنبيه:** مفتاح Debug المشترك منشور علناً حسب طلب صاحب المشروع. هذا مفيد لاستمرارية توقيع نسخ التجربة، لكنه لا يوفر حماية التحديثات ضد APK مزور بتوقيع نفس المفتاح.

## v2.3 — Live OCR text selection on screenshots

A new **original image** card appears at the top of OCR results for screenshots scanned in v2.3+. Open it to see the actual image with detected words highlighted directly on top, similar to Live Text or Google Lens.

- **Tap a word** to select or deselect it. **Drag between words** to select an entire range.
- **Pinch to zoom**, swipe the empty background to pan, or press **Fit** to reset.
- **Select all**, **Clear**, **Copy selected** and **Share** do not invoke the keyboard.
- Multiple scanned images have **Previous / Next** navigation with separate word geometry.
- English uses ML Kit word-element boxes; Arabic / Arabic+English uses Tesseract hOCR word boxes.
- Large screenshots are loaded using Android's **BitmapRegionDecoder**, not decoded as a single giant bitmap, to reduce the risk of out-of-memory crashes.
- Images are stored only inside the app-private result history and deleted when their record is removed. The app saves up to 12 pages per result, at most 30 MiB each and 120 MiB combined. Prior v2.2 history is still readable as text only.
- **No cloud OCR and no keyboard** in this image-selection screen.

### العربية — عرض الصورة وتحديد الكلمات

من صفحة النتيجة اضغط على مربع **«عرض النص على الصورة الأصلية»**. راح تنفتح الصورة كاملة وتظهر الكلمات المحددة تلقائياً. اضغط على أي كلمة أو اسحب على عدة كلمات حتى تحددها، وتكدر تستخدم **تحديد الكل / إلغاء التحديد / نسخ المحدد / مشاركة**. للتكبير استخدم إصبعين، وللتحريك اسحب على المساحة الخالية، وزر **ملاءمة** يرجع الصورة لحجمها المناسب. ماكو كيبورد نهائياً داخل هذا العارض.

**ملاحظة:** الاستجابة الفعلية للصور الطولية جداً ودقة مواضع الكلمات تحتاج اختبار على أجهزة حقيقية؛ نجاح بناء GitHub Actions وحده لا يثبت أداء اللمس والـ OCR في كل الصور. راجع `docs/LIVE-TEXT-IMAGE.md`.

## v2.6 — Higher OCR accuracy for crash screenshots

- The OCR engine now uses a second contrast-adjusted reading on dark or very narrow screenshots. It merges hypotheses conservatively by position and textual similarity rather than always taking the longer output.
- Android screenshot pixels are preserved without arbitrary downscaling up to 3200 px width. Very small screenshots are magnified up to 3x using region tiles limited to approximately 4 megapixels per pass.
- In auto Arabic+English OCR mode, recognizable Java crash traces use the improved ML Kit Latin pipeline; Arabic and ordinary bilingual documents continue to use Tesseract.
- Image text selector has a **Words / Whole lines** toggle for Java traces. Full-line selectable boxes are sourced from ML Kit's actual line geometry and help when individual word-element boxes are incomplete.
- Old scans retain their saved geometry; rescan an image to apply the new OCR pipeline.
- No network OCR and no text hallucination/correction of unknown class names. Some extremely blurry/compressed text cannot be recovered.

### العربية

نسخة 2.6 تضيف محاولة OCR ثانية للصورة ذات الخلفية الداكنة أو النص الصغير، مع تحسين التباين ودمج النتائج بشكل محافظ. تضيف تحديد **الأسطر كاملة** حتى تكدر تنسخ إطار Java كامل إذا مربعات الكلمات ناقصة. الوضع المختلط يختار ML Kit لسجلات Java الواضحة بدلاً من الاعتماد على Tesseract لكل نص. استخدم الصورة الأصلية بدقة كاملة كلما أمكن؛ تكبير صورة مضغوطة ما يرجع تفاصيل الحروف المفقودة.

See docs/QA-V2.6.md for manual device checks.

## v2.7 — Independent OCR checks for missing characters

- Added *third-pass*, fully-offline Tesseract English `PSM_SINGLE_LINE` verification on Java stack frames that ML Kit has low confidence in or appears to have truncated.
- Every candidate is compared conservatively with the original OCR reading before replacing it; guesses about class names and silently substituting unrelated text are deliberately avoided.
- Home menu → **OCR accuracy / speed**: Fast, Balanced (default), High accuracy. Verification is capped per page to protect CPU/RAM on long screenshots.
- Source image still uses accurate full-line boxes and separate word selection. Whole-line copy now inserts newlines and shows a line count.
- The existing public debug signing key and automatic APK prerelease pipeline remain unchanged. The feature is developed separately from `main`.
- Note: for best quality, scan the uncompressed original screenshot using Gallery.

### العربية

أضفنا فحصاً ثالثاً باستخدام Tesseract الإنجليزي للسطر البرمجي الواحد فقط عندما تظهر مؤشرات أنه ناقص أو غير واضح. من القائمة تگدر تختار **دقة التعرف وسرعته**: سريع أو متوازن أو دقة عالية. المراجعة محدودة لكل صورة حتى ما تثقل على الهاتف، والتطبيق لا يستبدل كلمات Java بشكل عشوائي. كذلك نسخ الأسطر الكاملة صار يفصل كل سطر بسطر مستقل.

See `docs/QA-V2.7.md` for checks.

## v2.8 — visible-text selection and stronger OCR research

A new pixel-based check tries to extend **whole-line and leading-word** selectable rectangles
over visibly bright letters when the ML Kit rectangle starts in the middle
of the word (as in a screenshot where `android.app` is visible but only
`d.app` becomes highlighted). It does **not** guess missing letters in OCR
output. For best coverage, use **Whole lines** when selecting Java frames.

Research and migration plan: [PaddleOCR / RapidOCR / real Android
MakeACopy implementation](docs/OCR_ENGINE_RESEARCH_V2_8.md).
**PaddleOCR has not been integrated into the app yet**: the plan requires
model/runtime selection, on-device benchmark, and Arabic/English regression
testing. The tested ML Kit/Tesseract path remains the working default.

### العربية
تمت إضافة معالجة مستقلة عن OCR لموضع بداية السطر في الصور الداكنة عندما
تظهر أحرف على الصورة لكن مربع تحديد السطر يبدأ من وسط الكلمة. هذه الميزة
تحسّن مربع **الأسطر كاملة** فقط، ولا تخمّن الحروف المفقودة من النص.
كذلك بحثنا مشروع MakeACopy الذي يستخدم PaddleOCR على أندرويد فعلياً
ووضعنا خطة لدمج المحرك بعد قياس الدقة والسرعة.
