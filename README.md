# Clinote

Clinote is a premium, offline-first Android medical record note-taking app. It presents records in an easy-to-scan spreadsheet-style table and keeps them in chronological order based on the actual save timestamp.

## Features

- Records: Bed Number, Patient Name, Primary Consultant, Details, and immutable Date & Time Saved.
- Search, edit, update, delete, print, and create/share a PDF register.
- Continuous voice capture: tap Stop voice recording when finished. Android speech recognition may return short segments, and Clinote automatically starts the next segment until stopped.
- Spoken labelled fields are assigned by label rather than their speaking order. For example, “Patient name Asha, bed 12, consultant Dr Bharat” maps to the right cells.
- After saying Details, following unlabelled speech is appended to Details. A later field label switches the target column.
- A local editable medical dictionary corrects voice spellings. The six requested consultant names are protected and always restored exactly.

## Build

Open this folder in Android Studio (Ladybug or newer is recommended), allow Gradle sync, select a device running Android 8.0/API 26 or later, then run the app configuration. Grant microphone permission to use voice input.

With Gradle 8.11.1 and JDK 17 installed, run gradle :app:assembleDebug. The APK is written to app/build/outputs/apk/debug/app-debug.apk.

## GitHub Actions

.github/workflows/android-apk.yml runs tests, builds a debug APK, and uploads it as the clinote-debug-apk artifact on pushes, pull requests, and manual runs.

## Privacy and safety

- Records are stored in Android private app storage and are not uploaded by Clinote.
- Android’s built-in speech recognizer varies by device and can use a network speech service. Confirm that against your hospital privacy policy before handling protected health information.
- Clinote is a note-taking aid, not a clinical decision-making or electronic health-record system. Apply your institution’s consent, retention, and access-control policies before production use.
