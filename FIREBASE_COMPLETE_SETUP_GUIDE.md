# FIREBASE COMPLETE SETUP GUIDE — Smart Plant Care (V3.1 I2C Edition)

This guide provides the end-to-end Firebase Console, Realtime Database, Authentication, Cloud Functions, Open-Meteo Weather Sync, and FCM Push Notification setup for your **Smart Plant Care** project (`smartplantcare-7c3e5`).

---

## 1. Existing Firebase Project Assessment (`app/google-services.json`)

From the read-only inspection of `app/google-services.json`:
- **Project ID:** `smartplantcare-7c3e5`
- **Project Number:** `209604742842`
- **Android Package Name:** `com.example.smartplantcare`
- **Storage Bucket:** `smartplantcare-7c3e5.firebasestorage.app`
- **Current Gap in `google-services.json`:** `project_info` does **not** yet contain a `firebase_url` property because **Firebase Realtime Database** had not been provisioned when `google-services.json` was originally downloaded.

---

## 2. Step-by-Step Firebase Console Setup

### Step 2.1 — Enable Firebase Authentication Providers
1. Open the [Firebase Console](https://console.firebase.google.com/) and select **`smartplantcare-7c3e5`**.
2. Go to **Build → Authentication → Sign-in method**.
3. Ensure the following providers are **Enabled**:
   - **Email/Password** (used by `AuthViewModel.kt` for standard sign-up/sign-in).
   - **Google** (used by `AuthViewModel.kt` for Google Sign-In).
   - *(Note on Guest Mode)*: Your Android app currently implements Guest Mode locally via `SharedPreferences` (`is_guest = true`). Our Realtime Database security rules and `claimDevice` Cloud Function explicitly require a real authenticated non-guest Firebase user (`auth != null && auth.token.firebase.sign_in_provider !== 'anonymous'`) to claim or control an ESP32 hardware unit.

### Step 2.2 — Create Firebase Realtime Database (RTDB)
1. Go to **Build → Realtime Database → Create Database**.
2. Choose your closest region (recommended: **`asia-southeast1` (Singapore)** or **`us-central1`**).
3. Start in **Locked mode** (you will deploy `firebase_backend/database.rules.json` in Step 4).
4. Copy your Realtime Database URL (e.g., `https://smartplantcare-7c3e5-default-rtdb.asia-southeast1.firebasedatabase.app`).
5. **Re-download `google-services.json`**:
   - Go to **Project Settings (gear icon) → General → Your apps → Android app (`com.example.smartplantcare`)**.
   - Download the updated `google-services.json` (which now contains `"firebase_url": "https://..."`) so the Android app automatically resolves the default RTDB instance when you later integrate the Android IoT screens.

### Step 2.3 — Confirm Cloud Messaging (FCM)
1. Go to **Project Settings → Cloud Messaging**.
2. Verify **Firebase Cloud Messaging API (V1)** is **Enabled**.
3. **Never** copy legacy server keys or service-account private keys into the ESP32 firmware or the Android app. All FCM messages are sent by the `onDeviceEventCreated` Cloud Function using the Admin SDK (`admin.messaging().sendEachForMulticast`).

---

## 3. Complete Realtime Database Schema

The complete seed JSON structure is provided in [`firebase_backend/sample_rtdb_seed.json`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/firebase_backend/sample_rtdb_seed.json).

### Top-Level Paths
| Path | Purpose | Who Reads | Who Writes |
|---|---|---|---|
| `/users/{uid}` | User profile, `/fcmTokens/{tokenId}`, `/devices/{deviceId}`, `/notificationPreferences` | Authenticated user (`auth.uid === uid`) | User (`profile`, `fcmTokens`, `notificationPreferences`); Admin SDK (`devices`) |
| `/deletingAccounts/{uid}` | Short-lived lock created during `deleteMyAccountAndReleaseDevices` | User (`uid`) | Admin SDK only |
| `/deviceSecrets/{deviceId}` | Stores `claimCodeSha256` and `deviceAuthSecretSha256` | Nobody (Admin SDK only) | Admin SDK / Console only |
| `/devices/{deviceId}/meta` | `ownershipState` (`UNCLAIMED`, `CLAIM_PENDING`, `OWNED`, `RESET_PENDING`), `ownerUid`, `ownershipEpoch`, `resetRequired`, `location` | Authorized members + scoped ESP32 device token | Admin SDK only |
| `/devices/{deviceId}/acl` | `ownerUid`, `members/{uid}/role` (`owner`, `operator`, `viewer`), `invitations/{inviteCode}` | Authorized members | Admin SDK only |
| `/devices/{deviceId}/status` | Live connectivity, `mode`, `rainSuspensionActive`, `weatherAdvisoryActive`, `activeValve`, `pumpState`, `irrigationState`, `ownershipEpoch` | Authorized members + scoped ESP32 | Scoped ESP32 device token (`auth.token.deviceId === deviceId` & matching `ownershipEpoch`) |
| `/devices/{deviceId}/telemetry` | Coalesced 15s sensor snapshot (`temperatureC`, `humidityPct`, `lightBrightnessPct`, `reservoirAirGapInches`, `reservoirPct`, `soil/tomato|chili|eggplant`) | Authorized members + scoped ESP32 | Scoped ESP32 device token |
| `/devices/{deviceId}/weather` | Cached Open-Meteo 3-hour max `rainProbabilityPct`, `valid`, `fetchedAt`, `validUntil` | Authorized members + scoped ESP32 | Admin SDK (`refreshWeatherScheduled`) |
| `/devices/{deviceId}/config` | `mode`, `rainThresholdPct` (`60`), `wetConfirmMs` (`30000`), `dryRecoverMs` (`120000`), `reservoirEmptyGapIn` (`7.0`), `reservoirFullGapIn`, `crops/*` | Authorized members + scoped ESP32 | `owner` and `operator` |
| `/devices/{deviceId}/commandRequests/{cmdId}` | Manual watering (`WATER_CROP`), `STOP_WATERING`, `CLEAR_EMERGENCY`, `MUTE_BUZZER` | Authorized members + scoped ESP32 | `owner` and `operator` (create); scoped ESP32 (`state = "PROCESSED"`) |
| `/devices/{deviceId}/commandResults/{cmdId}` | Execution result (`ACCEPTED`, `REJECTED`, `DONE`, `ABORTED`) + `reason` | Authorized members | Scoped ESP32 device token |
| `/devices/{deviceId}/events/{eventId}` | Deduplicated state-transition alert records (`<deviceId>_<bootId>_<seq>`) | Authorized members | Scoped ESP32 device token |
| `/notificationOutbox/{deviceId}/{dedupeKey}` | Per-event cooldown tracking for FCM push deduplication | Nobody (Admin SDK only) | Admin SDK (`onDeviceEventCreated`) |

---

## 4. Deploying RTDB Security Rules & Cloud Functions

All backend files are ready in [`firebase_backend/`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/firebase_backend/firebase.json).

### Step 4.1 — Install Firebase CLI & Authenticate
```powershell
npm install -g firebase-tools
firebase login
```

### Step 4.2 — Install Function Dependencies & Build TypeScript
```powershell
cd C:\Users\Admin\AndroidStudioProjects\SmartPlantCare\firebase_backend\functions
npm install
npm run build
```

### Step 4.3 — Configure `.env` for Cloud Functions
Copy `firebase_backend/functions/.env.example` to `firebase_backend/functions/.env` and set your garden's default coordinates (used by Open-Meteo when a device does not yet have custom coordinates in `/meta/location`):
```ini
DEFAULT_LATITUDE=14.5995
DEFAULT_LONGITUDE=120.9842
WEATHER_VALIDITY_TTL_MS=3600000
NOTIFICATION_DEDUPE_COOLDOWN_MS=900000
DEVICE_OFFLINE_THRESHOLD_MS=180000
```

### Step 4.4 — Deploy Rules and Cloud Functions
```powershell
cd C:\Users\Admin\AndroidStudioProjects\SmartPlantCare\firebase_backend
firebase use smartplantcare-7c3e5
firebase deploy --only database,functions
```

---

## 5. Registering Your First Physical ESP32 Unit in RTDB

Before your ESP32 board (`spc-unit-0001`) can authenticate to `/mintDeviceCustomToken` or be claimed via `claimDevice`, compute the SHA-256 hashes of its `claimCode` and `deviceAuthSecret`:

```powershell
node -e "const c=require('crypto'); console.log('claimCodeSha256:', c.createHash('sha256').update('SPC-CLAIM-0001').digest('hex')); console.log('deviceAuthSecretSha256:', c.createHash('sha256').update('SPC-DEVSECRET-0001').digest('hex'));"
```

Import or set `/deviceSecrets/spc-unit-0001` and `/devices/spc-unit-0001/meta` in Firebase Realtime Database (using `firebase_backend/sample_rtdb_seed.json` as a template, with `ownershipState: "UNCLAIMED"` and `ownershipEpoch: 0` for a fresh factory board).
