"use strict";
/**
 * @file index.ts
 * @brief Complete Trusted Backend (Firebase Cloud Functions v2) for Smart Plant Care V3.1.
 *
 * Implements:
 * 1. mintDeviceCustomToken            — Scoped custom-token auth for ESP32 (no Admin keys on device)
 * 2. claimDevice                      — Physical claim-code verification & ownershipEpoch increment
 * 3. createShareInvite                — Owner creates single-use 24h operator/viewer invite
 * 4. acceptShareInvite                — Authenticated user redeems invite into /acl/members
 * 5. revokeMemberAccess               — Owner revokes member (or member leaves device)
 * 6. unclaimDevice                    — Transitions device to RESET_PENDING & preserves calibration
 * 7. deleteMyAccountAndReleaseDevices — Atomic account deletion + owned-device RESET_PENDING release
 * 8. acknowledgeDeviceReset           — ESP32 confirms local NVS wipe -> transitions to UNCLAIMED
 * 9. refreshWeatherScheduled          — Every 15m Open-Meteo 3-hour max rain probability sync
 * 10. onDeviceEventCreated            — Deduplicated data-only FCM push dispatcher
 * 11. checkDeviceOfflineScheduled     — Every 5m heartbeat watchdog & offline notification
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.checkDeviceOfflineScheduled = exports.onDeviceEventCreated = exports.refreshWeatherScheduled = exports.acknowledgeDeviceReset = exports.deleteMyAccountAndReleaseDevices = exports.unclaimDevice = exports.revokeMemberAccess = exports.acceptShareInvite = exports.createShareInvite = exports.claimDevice = exports.mintDeviceCustomToken = void 0;
const crypto = __importStar(require("crypto"));
const admin = __importStar(require("firebase-admin"));
const https_1 = require("firebase-functions/v2/https");
const database_1 = require("firebase-functions/v2/database");
const scheduler_1 = require("firebase-functions/v2/scheduler");
admin.initializeApp();
const db = admin.database();
const firestore = admin.firestore();
function sha256Hex(input) {
    return crypto.createHash("sha256").update(input, "utf8").digest("hex");
}
function timingSafeHexEquals(aHex, bHex) {
    if (!aHex || !bHex || aHex.length !== bHex.length) {
        return false;
    }
    return crypto.timingSafeEqual(Buffer.from(aHex, "utf8"), Buffer.from(bHex, "utf8"));
}
/**
 * Formats a UTC epoch millisecond timestamp into a Philippine Local Time (Asia/Manila, UTC+8)
 * YYYY-MM-DD calendar date key.
 */
function formatPhDateKey(epochMs) {
    const phMs = epochMs + 8 * 60 * 60 * 1000;
    const d = new Date(phMs);
    const yyyy = d.getUTCFullYear();
    const mm = String(d.getUTCMonth() + 1).padStart(2, "0");
    const dd = String(d.getUTCDate()).padStart(2, "0");
    return `${yyyy}-${mm}-${dd}`;
}
// ============================================================================
// 1. ESP32 DEVICE AUTHENTICATION: mintDeviceCustomToken
// ============================================================================
exports.mintDeviceCustomToken = (0, https_1.onRequest)({ cors: false }, async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method Not Allowed" });
        return;
    }
    const { deviceId, deviceAuthSecret, bootId, firmwareVersion } = req.body || {};
    if (typeof deviceId !== "string" || typeof deviceAuthSecret !== "string" || !deviceId) {
        res.status(400).json({ error: "Missing deviceId or deviceAuthSecret" });
        return;
    }
    const secretSnap = await db.ref(`/deviceSecrets/${deviceId}`).get();
    if (!secretSnap.exists()) {
        res.status(403).json({ error: "Unregistered deviceId" });
        return;
    }
    const expectedHash = String(secretSnap.child("deviceAuthSecretSha256").val() || "");
    const providedHash = sha256Hex(deviceAuthSecret);
    if (!timingSafeHexEquals(expectedHash, providedHash)) {
        res.status(403).json({ error: "Invalid device authentication secret" });
        return;
    }
    const metaSnap = await db.ref(`/devices/${deviceId}/meta`).get();
    const ownershipState = String(metaSnap.child("ownershipState").val() || "UNCLAIMED");
    const ownershipEpoch = Number(metaSnap.child("ownershipEpoch").val() || 0);
    if (bootId || firmwareVersion) {
        await db.ref(`/devices/${deviceId}/meta`).update({
            lastBootId: bootId || null,
            firmwareVersion: firmwareVersion || "3.1.0-i2c",
            lastTokenMintedAt: Date.now(),
        });
    }
    const customToken = await admin.auth().createCustomToken(`device:${deviceId}`, {
        role: "device",
        deviceId,
        ownershipEpoch,
    });
    res.status(200).json({
        customToken,
        ownershipState,
        ownershipEpoch,
    });
});
// ============================================================================
// 2. PHYSICAL DEVICE PAIRING: claimDevice
// ============================================================================
exports.claimDevice = (0, https_1.onCall)(async (request) => {
    const uid = request.auth?.uid;
    const isAnonymous = request.auth?.token?.firebase?.sign_in_provider === "anonymous";
    if (!uid || isAnonymous) {
        throw new https_1.HttpsError("unauthenticated", "A signed-in non-guest account is required to claim a device.");
    }
    const deletingSnap = await db.ref(`/deletingAccounts/${uid}`).get();
    if (deletingSnap.exists()) {
        throw new https_1.HttpsError("failed-precondition", "Account deletion is in progress.");
    }
    const { deviceId, claimCode, displayName, lat, lon, timezone } = request.data || {};
    if (typeof deviceId !== "string" || typeof claimCode !== "string" || !deviceId || !claimCode) {
        throw new https_1.HttpsError("invalid-argument", "deviceId and physical claimCode are required.");
    }
    const secretSnap = await db.ref(`/deviceSecrets/${deviceId}`).get();
    if (!secretSnap.exists()) {
        throw new https_1.HttpsError("not-found", "Device ID not found.");
    }
    const expectedClaimHash = String(secretSnap.child("claimCodeSha256").val() || "");
    const providedClaimHash = sha256Hex(claimCode.trim());
    if (!timingSafeHexEquals(expectedClaimHash, providedClaimHash)) {
        throw new https_1.HttpsError("permission-denied", "Invalid physical claim code.");
    }
    const metaRef = db.ref(`/devices/${deviceId}/meta`);
    const metaSnap = await metaRef.get();
    const currentState = String(metaSnap.child("ownershipState").val() || "UNCLAIMED");
    const currentOwner = String(metaSnap.child("ownerUid").val() || "");
    const currentEpoch = Number(metaSnap.child("ownershipEpoch").val() || 0);
    if (currentState === "RESET_PENDING") {
        throw new https_1.HttpsError("failed-precondition", "Device is completing a factory reset. Wait until it returns to UNCLAIMED setup mode.");
    }
    if (currentState === "OWNED" && currentOwner && currentOwner !== uid) {
        throw new https_1.HttpsError("already-exists", "Device is already claimed by another owner.");
    }
    const newEpoch = currentState === "OWNED" && currentOwner === uid ? currentEpoch : currentEpoch + 1;
    const now = Date.now();
    const updates = {};
    updates[`/devices/${deviceId}/meta/deviceId`] = deviceId;
    updates[`/devices/${deviceId}/meta/displayName`] = displayName || "Smart Plant Care Unit";
    updates[`/devices/${deviceId}/meta/ownershipState`] = "OWNED";
    updates[`/devices/${deviceId}/meta/ownerUid`] = uid;
    updates[`/devices/${deviceId}/meta/ownershipEpoch`] = newEpoch;
    updates[`/devices/${deviceId}/meta/resetRequired`] = false;
    updates[`/devices/${deviceId}/meta/claimedAt`] = now;
    if (typeof lat === "number" && typeof lon === "number") {
        updates[`/devices/${deviceId}/meta/location`] = {
            lat,
            lon,
            timezone: timezone || "Asia/Manila",
        };
    }
    updates[`/devices/${deviceId}/acl/ownerUid`] = uid;
    updates[`/devices/${deviceId}/acl/members/${uid}`] = {
        role: "owner",
        addedAt: now,
        addedBy: "physical_claim",
    };
    updates[`/users/${uid}/devices/${deviceId}`] = {
        role: "owner",
        ownershipEpoch: newEpoch,
        addedAt: now,
    };
    await db.ref().update(updates);
    return {
        success: true,
        deviceId,
        ownershipState: "OWNED",
        ownershipEpoch: newEpoch,
    };
});
// ============================================================================
// 3. DEVICE SHARING & ACL MANAGEMENT (PHASE 9)
// ============================================================================
exports.createShareInvite = (0, https_1.onCall)(async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
        throw new https_1.HttpsError("unauthenticated", "Authentication required.");
    }
    const { deviceId, role } = request.data || {};
    if (typeof deviceId !== "string" || (role !== "operator" && role !== "viewer")) {
        throw new https_1.HttpsError("invalid-argument", "deviceId and role ('operator' or 'viewer') are required.");
    }
    const metaSnap = await db.ref(`/devices/${deviceId}/meta`).get();
    if (metaSnap.child("ownershipState").val() !== "OWNED" ||
        metaSnap.child("ownerUid").val() !== uid) {
        throw new https_1.HttpsError("permission-denied", "Only the device owner can create share invitations.");
    }
    const inviteCode = crypto.randomBytes(6).toString("hex").toUpperCase();
    const now = Date.now();
    const expiresAt = now + 24 * 60 * 60 * 1000; // 24 hours
    const epoch = Number(metaSnap.child("ownershipEpoch").val() || 1);
    await db.ref(`/devices/${deviceId}/acl/invitations/${inviteCode}`).set({
        inviteCode,
        deviceId,
        role,
        createdByUid: uid,
        ownershipEpoch: epoch,
        createdAt: now,
        expiresAt,
        status: "PENDING",
    });
    return { inviteCode, deviceId, role, expiresAt };
});
exports.acceptShareInvite = (0, https_1.onCall)(async (request) => {
    const uid = request.auth?.uid;
    const isAnonymous = request.auth?.token?.firebase?.sign_in_provider === "anonymous";
    if (!uid || isAnonymous) {
        throw new https_1.HttpsError("unauthenticated", "Sign in with a full account to accept a device share invite.");
    }
    const { deviceId, inviteCode } = request.data || {};
    if (typeof deviceId !== "string" || typeof inviteCode !== "string") {
        throw new https_1.HttpsError("invalid-argument", "deviceId and inviteCode are required.");
    }
    const metaSnap = await db.ref(`/devices/${deviceId}/meta`).get();
    if (metaSnap.child("ownershipState").val() !== "OWNED") {
        throw new https_1.HttpsError("failed-precondition", "Device is not currently active.");
    }
    const currentEpoch = Number(metaSnap.child("ownershipEpoch").val() || 0);
    const inviteRef = db.ref(`/devices/${deviceId}/acl/invitations/${inviteCode}`);
    const inviteSnap = await inviteRef.get();
    if (!inviteSnap.exists()) {
        throw new https_1.HttpsError("not-found", "Invitation not found.");
    }
    const invite = inviteSnap.val();
    const now = Date.now();
    if (invite.status !== "PENDING" ||
        invite.ownershipEpoch !== currentEpoch ||
        now > Number(invite.expiresAt || 0)) {
        throw new https_1.HttpsError("failed-precondition", "Invitation has expired or is no longer valid.");
    }
    const role = invite.role === "operator" ? "operator" : "viewer";
    const updates = {};
    updates[`/devices/${deviceId}/acl/invitations/${inviteCode}/status`] = "ACCEPTED";
    updates[`/devices/${deviceId}/acl/invitations/${inviteCode}/acceptedByUid`] = uid;
    updates[`/devices/${deviceId}/acl/invitations/${inviteCode}/acceptedAt`] = now;
    updates[`/devices/${deviceId}/acl/members/${uid}`] = {
        role,
        addedAt: now,
        addedBy: invite.createdByUid,
    };
    updates[`/users/${uid}/devices/${deviceId}`] = {
        role,
        ownershipEpoch: currentEpoch,
        addedAt: now,
    };
    await db.ref().update(updates);
    return { success: true, deviceId, role };
});
exports.revokeMemberAccess = (0, https_1.onCall)(async (request) => {
    const callerUid = request.auth?.uid;
    if (!callerUid) {
        throw new https_1.HttpsError("unauthenticated", "Authentication required.");
    }
    const { deviceId, targetUid } = request.data || {};
    if (typeof deviceId !== "string" || typeof targetUid !== "string") {
        throw new https_1.HttpsError("invalid-argument", "deviceId and targetUid are required.");
    }
    const metaSnap = await db.ref(`/devices/${deviceId}/meta`).get();
    const ownerUid = String(metaSnap.child("ownerUid").val() || "");
    if (targetUid === ownerUid) {
        throw new https_1.HttpsError("failed-precondition", "Owner cannot be revoked via member removal; use unclaimDevice.");
    }
    if (callerUid !== ownerUid && callerUid !== targetUid) {
        throw new https_1.HttpsError("permission-denied", "Only the owner or the member themselves can remove access.");
    }
    const updates = {};
    updates[`/devices/${deviceId}/acl/members/${targetUid}`] = null;
    updates[`/users/${targetUid}/devices/${deviceId}`] = null;
    await db.ref().update(updates);
    return { success: true, deviceId, removedUid: targetUid };
});
// ============================================================================
// 4. UNCLAIM, ACCOUNT DELETION & BOARD RESET LIFECYCLE (PHASE 10)
// ============================================================================
async function buildDeviceResetUpdates(deviceId, updates) {
    const devSnap = await db.ref(`/devices/${deviceId}`).get();
    const currentEpoch = Number(devSnap.child("meta/ownershipEpoch").val() || 0);
    const nextEpoch = currentEpoch + 1;
    // Remove device reference from all members' /users/{uid}/devices/{deviceId}
    const membersObj = devSnap.child("acl/members").val() || {};
    for (const memberUid of Object.keys(membersObj)) {
        updates[`/users/${memberUid}/devices/${deviceId}`] = null;
    }
    // Transition meta to RESET_PENDING with incremented ownershipEpoch
    updates[`/devices/${deviceId}/meta/ownershipState`] = "RESET_PENDING";
    updates[`/devices/${deviceId}/meta/ownerUid`] = null;
    updates[`/devices/${deviceId}/meta/ownershipEpoch`] = nextEpoch;
    updates[`/devices/${deviceId}/meta/resetRequired`] = true;
    updates[`/devices/${deviceId}/meta/unclaimedAt`] = Date.now();
    // Wipe ACL, commands, historical events, 7-day history, and notification outbox
    updates[`/devices/${deviceId}/acl`] = null;
    updates[`/devices/${deviceId}/commandRequests`] = null;
    updates[`/devices/${deviceId}/commandResults`] = null;
    updates[`/devices/${deviceId}/events`] = null;
    updates[`/deviceHistory/${deviceId}`] = null;
    updates[`/notificationOutbox/${deviceId}`] = null;
    // Force safe status representation while preserving physical sensor calibration in /config
    updates[`/devices/${deviceId}/status/pumpState`] = "OFF";
    updates[`/devices/${deviceId}/status/activeValve`] = "none";
    updates[`/devices/${deviceId}/status/irrigationState`] = "BOOT_SAFE";
    return nextEpoch;
}
exports.unclaimDevice = (0, https_1.onCall)(async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
        throw new https_1.HttpsError("unauthenticated", "Authentication required.");
    }
    const { deviceId } = request.data || {};
    if (typeof deviceId !== "string" || !deviceId) {
        throw new https_1.HttpsError("invalid-argument", "deviceId is required.");
    }
    const metaSnap = await db.ref(`/devices/${deviceId}/meta`).get();
    if (metaSnap.child("ownerUid").val() !== uid) {
        throw new https_1.HttpsError("permission-denied", "Only the primary owner can unclaim this device.");
    }
    const updates = {};
    const nextEpoch = await buildDeviceResetUpdates(deviceId, updates);
    await db.ref().update(updates);
    return {
        success: true,
        deviceId,
        ownershipState: "RESET_PENDING",
        ownershipEpoch: nextEpoch,
    };
});
exports.deleteMyAccountAndReleaseDevices = (0, https_1.onCall)(async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
        throw new https_1.HttpsError("unauthenticated", "Authentication required.");
    }
    // Step 1: Mark account as deleting to immediately lock out new writes/commands
    await db.ref(`/deletingAccounts/${uid}`).set({ startedAt: Date.now() });
    const userDevicesSnap = await db.ref(`/users/${uid}/devices`).get();
    const userDevices = userDevicesSnap.val() || {};
    const updates = {};
    for (const [deviceId, info] of Object.entries(userDevices)) {
        const metaSnap = await db.ref(`/devices/${deviceId}/meta`).get();
        const ownerUid = String(metaSnap.child("ownerUid").val() || "");
        if (ownerUid === uid || info?.role === "owner") {
            // Owned device: release and transition to RESET_PENDING, revoke all shared users
            await buildDeviceResetUpdates(deviceId, updates);
        }
        else {
            // Shared member only: remove membership without disturbing the owner's device
            updates[`/devices/${deviceId}/acl/members/${uid}`] = null;
        }
    }
    // Wipe user's RTDB tree and deleting lock
    updates[`/users/${uid}`] = null;
    updates[`/deletingAccounts/${uid}`] = null;
    await db.ref().update(updates);
    // Wipe Firestore profile and plantIdentificationUsage subcollection
    try {
        const usageDoc = firestore
            .collection("users")
            .doc(uid)
            .collection("plantIdentificationUsage")
            .doc("daily");
        await usageDoc.delete();
        await firestore.collection("users").doc(uid).delete();
    }
    catch (err) {
        console.warn("Firestore cleanup warning during account deletion:", err);
    }
    // Delete Firebase Auth user record
    await admin.auth().deleteUser(uid);
    return { success: true };
});
exports.acknowledgeDeviceReset = (0, https_1.onRequest)({ cors: false }, async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ error: "Method Not Allowed" });
        return;
    }
    const { deviceId, deviceAuthSecret, acknowledgedEpoch } = req.body || {};
    if (typeof deviceId !== "string" || typeof deviceAuthSecret !== "string") {
        res.status(400).json({ error: "Missing deviceId or deviceAuthSecret" });
        return;
    }
    const secretSnap = await db.ref(`/deviceSecrets/${deviceId}`).get();
    if (!secretSnap.exists()) {
        res.status(403).json({ error: "Unregistered deviceId" });
        return;
    }
    const expectedHash = String(secretSnap.child("deviceAuthSecretSha256").val() || "");
    if (!timingSafeHexEquals(expectedHash, sha256Hex(deviceAuthSecret))) {
        res.status(403).json({ error: "Invalid device secret" });
        return;
    }
    await db.ref(`/devices/${deviceId}/meta`).update({
        ownershipState: "UNCLAIMED",
        ownerUid: null,
        resetRequired: false,
        lastResetAckEpoch: Number(acknowledgedEpoch || 0),
        lastResetAckAt: Date.now(),
    });
    res.status(200).json({
        success: true,
        ownershipState: "UNCLAIMED",
    });
});
// ============================================================================
// 5. OPEN-METEO 3-HOUR PRECIPITATION PROBABILITY SYNC (PHASE 3D)
// ============================================================================
exports.refreshWeatherScheduled = (0, scheduler_1.onSchedule)("every 15 minutes", async () => {
    const devicesSnap = await db.ref("/devices").get();
    if (!devicesSnap.exists()) {
        return;
    }
    const defaultLat = Number(process.env.DEFAULT_LATITUDE || 14.5995);
    const defaultLon = Number(process.env.DEFAULT_LONGITUDE || 120.9842);
    const validityTtlMs = Number(process.env.WEATHER_VALIDITY_TTL_MS || 3600000);
    const tasks = [];
    devicesSnap.forEach((devChild) => {
        const deviceId = devChild.key;
        const ownershipState = devChild.child("meta/ownershipState").val();
        if (ownershipState !== "OWNED") {
            return;
        }
        const lat = Number(devChild.child("meta/location/lat").val() ?? defaultLat);
        const lon = Number(devChild.child("meta/location/lon").val() ?? defaultLon);
        tasks.push((async () => {
            const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}` +
                `&hourly=precipitation_probability&forecast_hours=3&timezone=auto`;
            try {
                const response = await fetch(url);
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                const json = await response.json();
                const probs = json?.hourly?.precipitation_probability || [];
                if (!Array.isArray(probs) || probs.length === 0) {
                    throw new Error("Missing hourly.precipitation_probability");
                }
                // Authoritative rule: maximum precipitation_probability across the next 3 hours
                const maxProb = Math.round(Math.max(...probs.slice(0, 3).map((n) => Number(n) || 0)));
                const now = Date.now();
                await db.ref(`/devices/${deviceId}/weather`).set({
                    provider: "open-meteo",
                    valid: true,
                    rainProbabilityPct: Math.min(100, Math.max(0, maxProb)),
                    windowHours: 3,
                    windowLabel: "NEXT 3 H",
                    fetchedAt: now,
                    validUntil: now + validityTtlMs,
                });
            }
            catch (err) {
                console.error(`[Weather] Open-Meteo fetch failed for ${deviceId}:`, err);
                await db.ref(`/devices/${deviceId}/weather/valid`).set(false);
            }
        })());
    });
    await Promise.allSettled(tasks);
});
// ============================================================================
// 6. DEDUPLICATED FCM PUSH DISPATCHER (PHASE 11)
// ============================================================================
exports.onDeviceEventCreated = (0, database_1.onValueCreated)("/devices/{deviceId}/events/{eventId}", async (event) => {
    const { deviceId, eventId } = event.params;
    const evData = event.data.val();
    if (!evData) {
        return;
    }
    const metaSnap = await db.ref(`/devices/${deviceId}/meta`).get();
    if (metaSnap.child("ownershipState").val() !== "OWNED") {
        return;
    }
    const currentEpoch = Number(metaSnap.child("ownershipEpoch").val() || 0);
    if (Number(evData.ownershipEpoch || 0) !== currentEpoch) {
        return;
    }
    const dedupeKey = String(evData.dedupeKey || `${evData.type}:global`).replace(/[.#$/[\]]/g, "_");
    const cooldownMs = Number(process.env.NOTIFICATION_DEDUPE_COOLDOWN_MS || 900000);
    const outboxRef = db.ref(`/notificationOutbox/${deviceId}/${dedupeKey}`);
    const outboxSnap = await outboxRef.get();
    const now = Date.now();
    if (outboxSnap.exists() &&
        evData.severity !== "critical" &&
        now - Number(outboxSnap.child("lastSentAt").val() || 0) < cooldownMs) {
        return;
    }
    await outboxRef.set({
        lastEventId: eventId,
        lastSentAt: now,
        type: evData.type,
    });
    const membersSnap = await db.ref(`/devices/${deviceId}/acl/members`).get();
    if (!membersSnap.exists()) {
        return;
    }
    const memberUids = Object.keys(membersSnap.val() || {});
    const tokenEntries = [];
    for (const uid of memberUids) {
        const tokensSnap = await db.ref(`/users/${uid}/fcmTokens`).get();
        if (!tokensSnap.exists())
            continue;
        tokensSnap.forEach((tokChild) => {
            const tok = tokChild.child("token").val();
            if (typeof tok === "string" && tok.length >= 20) {
                tokenEntries.push({ uid, tokenId: tokChild.key, token: tok });
            }
        });
    }
    if (tokenEntries.length === 0) {
        return;
    }
    const multicastResponse = await admin.messaging().sendEachForMulticast({
        tokens: tokenEntries.map((t) => t.token),
        data: {
            eventId: String(eventId),
            deviceId: String(deviceId),
            ownershipEpoch: String(currentEpoch),
            type: String(evData.type || "INFO"),
            severity: String(evData.severity || "info"),
            crop: String(evData.crop || "none"),
            title: String(evData.title || "Smart Plant Care Alert"),
            body: String(evData.message || ""),
            createdAt: String(evData.createdAt || now),
            dedupeKey: String(evData.dedupeKey || ""),
        },
        android: {
            priority: evData.severity === "critical" ? "high" : "normal",
        },
    });
    // Prune invalid / unregistered FCM tokens
    const pruneUpdates = {};
    multicastResponse.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error) {
            const code = resp.error.code;
            if (code === "messaging/registration-token-not-registered" ||
                code === "messaging/invalid-registration-token") {
                const entry = tokenEntries[idx];
                pruneUpdates[`/users/${entry.uid}/fcmTokens/${entry.tokenId}`] = null;
            }
        }
    });
    if (Object.keys(pruneUpdates).length > 0) {
        await db.ref().update(pruneUpdates);
    }
});
// ============================================================================
// 7. SCHEDULED HEARTBEAT MONITOR: checkDeviceOfflineScheduled
// ============================================================================
exports.checkDeviceOfflineScheduled = (0, scheduler_1.onSchedule)("every 5 minutes", async () => {
    const devicesSnap = await db.ref("/devices").get();
    if (!devicesSnap.exists()) {
        return;
    }
    const now = Date.now();
    const offlineThresholdMs = Number(process.env.DEVICE_OFFLINE_THRESHOLD_MS || 180000);
    const updates = {};
    devicesSnap.forEach((devChild) => {
        const deviceId = devChild.key;
        const ownershipState = devChild.child("meta/ownershipState").val();
        const epoch = Number(devChild.child("meta/ownershipEpoch").val() || 0);
        const online = Boolean(devChild.child("status/online").val());
        const lastSeenAt = Number(devChild.child("status/lastSeenAt").val() || 0);
        if (ownershipState === "OWNED" && online && lastSeenAt > 0 && now - lastSeenAt > offlineThresholdMs) {
            updates[`/devices/${deviceId}/status/online`] = false;
            const evId = `${deviceId}_offline_${Math.floor(now / 60000)}`;
            updates[`/devices/${deviceId}/events/${evId}`] = {
                eventId: evId,
                type: "DEVICE_OFFLINE",
                severity: "warning",
                title: "Device Offline",
                message: "Smart Plant Care unit has not reported heartbeat for over 3 minutes.",
                crop: null,
                dedupeKey: "DEVICE_OFFLINE:global",
                createdAt: now,
                ownershipEpoch: epoch,
            };
        }
    });
    if (Object.keys(updates).length > 0) {
        await db.ref().update(updates);
    }
    // Enforce strict 7-day retention (Asia/Manila PHT) on /deviceHistory/{deviceId}/days & minuteDays
    const cutoffDateKey = formatPhDateKey(now - 6 * 24 * 60 * 60 * 1000);
    const historySnap = await db.ref("/deviceHistory").get();
    if (historySnap.exists()) {
        const pruneUpdates = {};
        historySnap.forEach((devHistChild) => {
            const devId = devHistChild.key;
            const daysChild = devHistChild.child("days");
            if (daysChild.exists()) {
                daysChild.forEach((dayChild) => {
                    const dayKey = dayChild.key;
                    if (dayKey && dayKey < cutoffDateKey) {
                        pruneUpdates[`/deviceHistory/${devId}/days/${dayKey}`] = null;
                    }
                });
            }
            const minuteDaysChild = devHistChild.child("minuteDays");
            if (minuteDaysChild.exists()) {
                minuteDaysChild.forEach((minDayChild) => {
                    const dayKey = minDayChild.key;
                    if (dayKey && dayKey < cutoffDateKey) {
                        pruneUpdates[`/deviceHistory/${devId}/minuteDays/${dayKey}`] = null;
                    }
                });
            }
        });
        if (Object.keys(pruneUpdates).length > 0) {
            await db.ref().update(pruneUpdates);
        }
    }
});
//# sourceMappingURL=index.js.map