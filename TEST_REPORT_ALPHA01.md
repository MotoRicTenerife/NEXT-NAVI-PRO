# NEXT NAVI PRO 0.2.0-alpha01 — test report

Date: 2026-10-12

## Passed in preparation environment

- 17 static source regression checks (`python3 roadtools/foundation_smoke_test.py`).
- XML parsing of AndroidManifest and resource XML files.
- Balanced Java delimiters for all six Java source files.
- ZIP archive integrity will be checked on packaging.

## Not executed / not certified

- Android Gradle build and APK assembly: **not executed** because this environment has no Gradle installation, Android SDK, and no network access to install them.
- JUnit tests: no dedicated Android/JUnit test suite has been run in this environment.
- Emulator/device startup, permission flow, MapLibre rendering, routing API availability, voice timing, GPS accuracy, thermal/battery behavior and road tests: **not tested**.

The checks above are not equivalent to a successful Android build. GitHub Actions is configured to run a real build and upload `app-debug.apk` as an artifact. Only after that build passes should the APK be installed, and then validated in controlled conditions before relying on it for navigation.
