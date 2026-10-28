# CrashLogOCR 📋

**EN** · [العربية](#العربية)

CrashLogOCR is a tiny Android utility that extracts **Java / Android crash logs and stacktraces from screenshots**, removes common crash-dialog UI noise, repairs some OCR line wrapping, and copies the result.

## Why this app?

Screenshots of crash dialogs (including Arabic/RTL Android skins) often wrap English package names, stack frames and file paths across multiple lines. Standard image-to-text apps copy all the UI clutter too. CrashLogOCR focuses on developer logs such as:

```text
java.lang.NoClassDefFoundError: Failed resolution of: Lcom/shabakaty/downloader/j32;
    at com.shabakaty.cinemana.CinemanaApplication.attachBaseContext(CinemanaApplication.kt:2)
Caused by: java.lang.ClassNotFoundException: com.shabakaty.downloader.j32
    ... 15 more
```

## Features

- **Share → copy**: Share a screenshot from Gallery or the screenshot share menu to CrashLogOCR. A transparent activity runs local OCR, cleans the trace, copies it and closes.
- **Review mode**: Launch the app normally, choose an image or paste text, compare **original OCR** with **cleaned output**, edit, copy or share.
- **Offline recognition**: Google ML Kit Latin text-recognition model is bundled with the APK. No server/API key, no network permission.
- **Privacy**: The app does not upload screenshots or keep a history. Android's clipboard is controlled by the operating system and may be readable by other apps depending on device/version.
- **No media permission**: Uses Android's system document picker and temporary share URI access.
- **English and Arabic UI** selected automatically from your phone language.

## Build / install

1. Go to **[GitHub Actions](https://github.com/alwansan/OCR-APK/actions/workflows/build-apk.yml)**.
2. Open the most recent **Build Android APK** run, wait for a green checkmark.
3. Download **CrashLogOCR-debug-apk** under **Artifacts**, unzip, and install `app-debug.apk`.
4. Or open the repository in Android Studio (AGP 8.9.2 / Gradle 8.11.1 / JDK 17 / SDK 35).

CI uses a pinned Gradle distribution through `gradle/actions/setup-gradle`; a Gradle wrapper JAR is not included.

## Limitations and safety

OCR is probabilistic: dots, case, slash/semicolon and class names may be wrong. Auto-repair is conservative; **always verify technical identifiers in the original screenshot** when diagnosing bugs. Input must be an image, not another app's protected runtime data. The scanner is designed mainly for Latin-script stacktraces, not Arabic prose recognition. Very long screenshots or damaged images can fail due to device memory; try a crop or clearer source. Android may show a system clipboard notification after copying.

## Project layout

- `app/src/main/java/.../OcrEngine.kt` — bundled ML Kit recognizer
- `TraceCleaner.kt` — deterministic stacktrace heuristics (no generative guesses)
- `ShareActivity.kt` — transparent Android share target
- `MainActivity.kt` — select / paste / review / edit / copy
- `TraceCleanerTest.kt` — regression samples
- `.github/workflows/build-apk.yml` — tests and APK artifact

## العربية

**CrashLogOCR** تطبيق أندرويد مجاني ومفتوح المصدر لاستخراج سجلات انهيار التطبيقات من صور الشاشة، خصوصاً عندما تكون واجهة الجهاز عربية ويتقطع النص الإنجليزي بين الأسطر.

**طريقتان للاستخدام:**
1. التقط صورة للخطأ ← مشاركة ← CrashLogOCR ← يُنسخ السجل المنظّف تلقائياً ويُغلق التطبيق.
2. افتح التطبيق ← اختر الصورة أو الصق النص ← راجع النص الأصلي والمنظّف ← عدّل وانسخ أو شارك.

يعمل التعرف على النص محلياً حتى دون إنترنت، ولا يحتاج صلاحية الصور أو الإنترنت. لا توجد مزامنة أو سجل محفوظ. **تحقق من أسماء الكلاسات والأسطر دائماً** لأن أخطاء OCR قد تغيّر حرفاً مؤثراً في التشخيص.

**تنزيل APK:** افتح [Actions](https://github.com/alwansan/OCR-APK/actions/workflows/build-apk.yml)، اختر تشغيل البناء الناجح، ثم نزّل الملف `CrashLogOCR-debug-apk` من Artifacts وفك الضغط وثبّته.

## Credits / sources

- [Google ML Kit text recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [Android secure image-sharing patterns](https://developer.android.com/training/sharing/receive)
- [GitHub Actions](https://docs.github.com/en/actions)
