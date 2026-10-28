# OCR validation cases

These are **reference patterns**, not claims that pixel-perfect OCR is guaranteed.

## Missing class

```text
java.lang.NoClassDefFoundError: Failed resolution of: Lcom/shabakaty/downloader/j32;
at com.shabakaty.cinemana.CinemanaApplication.attachBaseContext(CinemanaApplication.kt:2)
Caused by: java.lang.ClassNotFoundException: com.shabakaty.downloader.j32
... 15 more
```

Expected: keep the exception heading, original class descriptors, stack frames, cause and "... more". Remove Arabic crash dialog buttons.

## Verifier rejected a class

```text
java.lang.VerifyError: Verifier rejected class com.shabakaty.cinemana.ca1.RecoveryCapsule
at com.shabakaty.cinemana.ca1.Queue110.scanBody(Queue110.java:63)
at com.shabakaty.cinemana.ca1.Restart108.tick(Restart108.java:143)
```

Expected: do not change identifiers in `VerifyError` automatically. Multi-line descriptions should be preserved as well as possible. A human must inspect any uncertain OCR characters.

## Standalone "at" due to RTL wrapping

```text
java.lang.NoClassDefFoundError: missing
at
com.shabakaty.cinemana.CinemanaApplication.attachBas
eContext(CinemanaApplication.kt:2)
```

Expected cleaned frame on a **new line**, with `at ` and the method name joined. The automated JUnit regression test covers this pattern.

## Manual verification on Android

1. Install the APK produced by Actions.
2. Open the app, select each screenshot and compare *Original OCR* and *Cleaned stacktrace*.
3. Copy results and check they include the expected package name and exception type.
4. Share a screenshot from Gallery to CrashLogOCR and confirm the app copies then dismisses.
5. Disable mobile data and Wi-Fi, repeat: the bundled model should work offline.
6. Confirm no storage or Internet runtime permission is requested.

**Reminder:** automated unit tests validate the formatter, not ML Kit's recognition quality on every image/device.
