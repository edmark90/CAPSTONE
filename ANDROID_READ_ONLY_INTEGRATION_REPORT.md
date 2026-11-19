# ANDROID READ-ONLY INTEGRATION REPORT — Smart Plant Care

> [!IMPORTANT]
> In strict compliance with your instructions, **zero Kotlin, Jetpack Compose, Android XML, or Gradle files were modified during this ESP32 and Firebase implementation**. This report documents the exact state of your existing Android codebase (`C:\Users\Admin\AndroidStudioProjects\SmartPlantCare\app`) and how the newly implemented ESP32 firmware and Firebase backend align with it.

---

## 1. Executive Summary of Read-Only Android Audit

| Subsystem | Current Android Implementation Status | Key Files Inspected |
|---|---|---|
| **Firebase Project Binding** | Configured for `smartplantcare-7c3e5` (`com.example.smartplantcare`), but `google-services.json` was exported before Realtime Database was enabled (`firebase_url` missing). | [`app/google-services.json`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/google-services.json) |
| **Gradle Firebase Libraries** | Includes `firebase-bom = "33.10.0"`, `firebase-auth-ktx`, and `firebase-firestore-ktx`. **Missing:** `firebase-database-ktx` (RTDB) and `firebase-messaging-ktx` (FCM). | [`gradle/libs.versions.toml`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/gradle/libs.versions.toml), [`app/build.gradle.kts`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/build.gradle.kts) |
| **User Authentication** | Fully implemented Email/Password Login & Sign-Up, Google Sign-In, Password Reset, and local SharedPreferences Guest Mode (`is_guest`). User profile saved in Firestore `users/{uid}`. | [`AuthViewModel.kt`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/src/main/java/com/example/smartplantcare/auth/AuthViewModel.kt) |
| **AI Plant & Disease Pipeline** | Fully implemented on-device TFLite Plant Gatekeeper (`plantidentification.tflite`), Disease Classifier (`model_int8.tflite`), Pl@ntNet API lookup, and Firestore daily quota (`users/{uid}/plantIdentificationUsage/daily`). | [`HomeViewModel.kt`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/src/main/java/com/example/smartplantcare/homescreen/HomeViewModel.kt), [`PlantIdentificationUsageRepository.kt`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/src/main/java/com/example/smartplantcare/plantnetnetwork/PlantIdentificationUsageRepository.kt) |
| **Local Treatment & Reminders** | Fully implemented 7-day local Room `TreatmentPlan` and `NotificationHistory` with `AlarmManager` + `WorkManager`. | [`AppDatabase.kt`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/src/main/java/com/example/smartplantcare/data/local/AppDatabase.kt), [`TreatmentReminderScheduler.kt`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/src/main/java/com/example/smartplantcare/notification/TreatmentReminderScheduler.kt) |
| **IoT Device Pairing & Live Telemetry (`MyPlantsScreen`)** | **Placeholder UI only**: Displays a radar animation and a `"Connect Device"` button that increments a local counter. No BLE/SoftAP provisioning, QR/claim-code dialog, or Firebase RTDB listener is wired yet. | [`MyPlantsScreen.kt`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/src/main/java/com/example/smartplantcare/homescreen/MyPlantsScreen.kt) |
| **Device Sharing & Account Deletion (`SettingsScreen`)** | **Not yet implemented in UI**: `SettingsScreen.kt` supports Dark Mode, Reminder Toggle, and Sign Out/Sign In, but does not yet include `"Delete My Account"`, `"Unclaim Device"`, or `"Share Device"` actions. | [`SettingsScreen.kt`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/src/main/java/com/example/smartplantcare/homescreen/SettingsScreen.kt) |
| **FCM Push Notification Receiver** | **Not yet implemented**: `POST_NOTIFICATIONS` permission is declared in `AndroidManifest.xml` (for local treatment reminders), but no `FirebaseMessagingService` subclass is registered yet. | [`AndroidManifest.xml`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/app/src/main/AndroidManifest.xml) |

---

## 2. Classification of Project Features

### A. Already Implemented in Android App
1. **Authentication (`AuthViewModel.kt`)**:
   - Email/Password registration and login.
   - Google Sign-In (`signInWithGoogle`).
   - Firestore user document creation (`users/{uid}` with `fullName`, `email`, `createdAt`).
2. **Home Dashboard & Plant Care Diagnostics (`homescreen/`)**:
   - Camera / Gallery image capture, EXIF rotation normalization, center-crop preprocessing, TFLite inference, Pl@ntNet identification, and 7-day treatment plan tracking.
3. **Connectivity Monitoring (`util/NetworkMonitor.kt`)**:
   - Reactive `Flow<Boolean>` monitoring validated internet capability (`NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_VALIDATED`).

### B. Documented in IoT Architecture & Now Implemented in ESP32 + Firebase Backend (Awaiting Future Android UI Wiring)
1. **3-Zone Soil Moisture + 7-Inch Ultrasonic Reservoir + DHT22 + LDR + Rain/Open-Meteo Telemetry**:
   - Published by the ESP32 every 15 seconds to `/devices/{deviceId}/status` and `/devices/{deviceId}/telemetry`.
2. **Physical Claim & Ownership Epoch Lifecycle**:
   - Implemented in `firebase_backend/functions/src/index.ts` (`claimDevice`, `unclaimDevice`, `acknowledgeDeviceReset`) and `esp32_firmware/SmartPlantCare/ProvisioningPairingManager.cpp`.
3. **Multi-User Device Sharing (`owner`, `operator`, `viewer`)**:
   - Implemented in `firebase_backend/functions/src/index.ts` (`createShareInvite`, `acceptShareInvite`, `revokeMemberAccess`) and enforced by `firebase_backend/database.rules.json`.
4. **Account Deletion with Automatic Owned-Device Release**:
   - Implemented in `deleteMyAccountAndReleaseDevices` in `firebase_backend/functions/src/index.ts` (cleans up both RTDB `/users/{uid}`, owned `/devices/{deviceId}`, and the existing Firestore `users/{uid}` + `plantIdentificationUsage/daily` documents created by your Android app!).
5. **Deduplicated Data-Only FCM Push Notifications**:
   - Implemented in `onDeviceEventCreated` and `checkDeviceOfflineScheduled` in `firebase_backend/functions/src/index.ts`.

---

## 3. Inconsistencies & Security Risks Identified & Resolved

1. **Firestore vs. Realtime Database Coexistence**:
   - Your Android app already uses **Cloud Firestore** for `users/{uid}` and `users/{uid}/plantIdentificationUsage/daily`, whereas high-frequency IoT telemetry (`15s` sensor updates, live valve/pump state, and commands) belongs in **Firebase Realtime Database**.
   - **Resolution**: Our `deleteMyAccountAndReleaseDevices` Cloud Function cleans up **both** the Realtime Database nodes (`/users/{uid}`, `/devices/{deviceId}`) and the existing Cloud Firestore documents (`users/{uid}` and `users/{uid}/plantIdentificationUsage/daily`) so no orphaned data remains in either database.
2. **Local SharedPreferences Guest Mode vs. Firebase Auth**:
   - In `AuthViewModel.kt`, Guest Mode sets `prefs.edit().putBoolean("is_guest", true)` without signing into Firebase Auth (`auth.currentUser == null`).
   - **Resolution**: `database.rules.json` and `claimDevice` require a real authenticated non-anonymous Firebase user (`auth != null && auth.token.firebase.sign_in_provider !== 'anonymous'`). Guest users can continue using local features in the app, while claiming or controlling an ESP32 requires signing in with Email/Password or Google.
3. **Missing `firebase_url` in `app/google-services.json`**:
   - Once you enable Realtime Database in the Firebase Console (`smartplantcare-7c3e5`), re-download `google-services.json` into `app/google-services.json` when you are ready to connect the Android UI.

---

## 4. Exact Future Android Integration Blueprint (When You Choose to Unlock Android Editing)

When you later decide to wire `MyPlantsScreen.kt` and `SettingsScreen.kt` to the live ESP32 + Firebase backend:
1. **Add Gradle Dependencies (`gradle/libs.versions.toml` & `app/build.gradle.kts`)**:
   - `com.google.firebase:firebase-database-ktx`
   - `com.google.firebase:firebase-messaging-ktx`
   - `com.google.firebase:firebase-functions-ktx`
2. **Register FCM Token on Login**:
   - Write the device's FCM token to `/users/{uid}/fcmTokens/{installationId}` with `{ token, platform: "android", updatedAt }`.
3. **Bind `MyPlantsScreen.kt` to `/users/{uid}/devices` and `/devices/{deviceId}`**:
   - Observe `/devices/{deviceId}/status`, `/telemetry`, `/weather`, and `/config` using `ValueEventListener` wrapped in Kotlin `callbackFlow`.
   - Send manual watering commands by pushing a child to `/devices/{deviceId}/commandRequests/{cmdId}` with `type = "WATER_CROP"`, `crop = "tomato" | "chili" | "eggplant"`, `durationMs = 4000`, and `ownershipEpoch`.
4. **Wire Account Deletion in `SettingsScreen.kt`**:
   - Call the `deleteMyAccountAndReleaseDevices` callable Cloud Function instead of calling `FirebaseAuth.getInstance().currentUser?.delete()` directly from the client, guaranteeing that all owned ESP32 boards are transitioned to `RESET_PENDING` and wiped safely.
