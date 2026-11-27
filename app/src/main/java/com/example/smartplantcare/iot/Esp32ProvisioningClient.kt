package com.example.smartplantcare.iot

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class EspScanBlocker {
    NONE,
    WIFI_DISABLED,
    LOCATION_PERMISSION_MISSING,
    LOCATION_SERVICE_DISABLED
}

data class EspScanSnapshot(
    val devices: List<NearbyEspDevice>,
    val blocker: EspScanBlocker = EspScanBlocker.NONE,
    val totalVisibleNetworks: Int = 0,
    val activeScanTriggered: Boolean = false
)

/**
 * Handles nearby ESP32 discovery (filtering SPC-SETUP-*), Android 10+ WifiNetworkSpecifier
 * binding to the ESP32 SoftAP (192.168.4.1), 2.4 GHz Wi-Fi scanning (/api/wifi-scan),
 * and secure JSON provisioning (/api/provision) bound strictly to the ESP32 Network socket.
 */
class Esp32ProvisioningClient(private val appContext: Context) {

    companion object {
        private const val TAG = "Esp32ProvClient"
        private const val ESP_BASE_URL = "http://192.168.4.1"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null
    private var boundHttpClient: OkHttpClient = buildHttpClient(null)

    private fun buildHttpClient(
        network: Network?,
        connectTimeoutMs: Long = 8000L,
        readTimeoutMs: Long = 15000L
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
        if (network != null) {
            builder.socketFactory(network.socketFactory)
        }
        return builder.build()
    }

    /**
     * Returns true ONLY if [network] has an IPv4 interface on the ESP32 SoftAP subnet (`192.168.4.x`),
     * preventing `http://192.168.4.1` polls from ever leaking onto a `192.168.1.x` home Wi-Fi router
     * after the SoftAP switches channels or closes.
     */
    private fun isEspSoftApNetwork(network: Network?): Boolean {
        if (network == null) return false
        val lp = runCatching { connectivityManager.getLinkProperties(network) }.getOrNull() ?: return false
        return lp.linkAddresses.any { la ->
            val host = la.address?.hostAddress.orEmpty()
            host.startsWith("192.168.4.")
        }
    }

    /**
     * Checks all Android OS prerequisites required for [WifiManager.getScanResults] to return
     * nearby Wi-Fi networks (Wi-Fi ON, Precise Location granted, System Location/GPS ON).
     */
    fun checkScanBlocker(): EspScanBlocker {
        val wifiEnabled = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
        if (!wifiEnabled) {
            return EspScanBlocker.WIFI_DISABLED
        }
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            return EspScanBlocker.LOCATION_PERMISSION_MISSING
        }
        val locationEnabled = locationManager != null &&
            runCatching { LocationManagerCompat.isLocationEnabled(locationManager) }.getOrDefault(false)
        if (!locationEnabled) {
            return EspScanBlocker.LOCATION_SERVICE_DISABLED
        }
        return EspScanBlocker.NONE
    }

    private fun extractSsid(sr: ScanResult): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val wifiSsid = sr.wifiSsid
            if (wifiSsid != null) {
                val utf8 = runCatching { String(wifiSsid.bytes, Charsets.UTF_8) }.getOrNull()
                if (!utf8.isNullOrBlank()) return utf8
                return wifiSsid.toString().removeSurrounding("\"")
            }
        }
        @Suppress("DEPRECATION")
        return sr.SSID?.removeSurrounding("\"")
    }

    @SuppressLint("MissingPermission")
    private fun readCachedScanResults(): List<Pair<String, Int>> {
        return try {
            wifiManager.scanResults.mapNotNull { sr ->
                val ssid = extractSsid(sr)
                if (ssid.isNullOrBlank()) null else ssid to sr.level
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException reading WifiManager.scanResults (ACCESS_FINE_LOCATION missing?)", se)
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading WifiManager.scanResults", e)
            emptyList()
        }
    }

    /**
     * Scans nearby Wi-Fi signals and returns ONLY unclaimed ESP32 setup hotspots (`SPC-SETUP-*`),
     * along with prerequisite diagnostics so permission/GPS/Wi-Fi blockers are never hidden.
     *
     * Set [triggerActiveScan] to true at most once every ~30s to stay within Android 9+'s
     * 4-scans-per-2-minutes foreground Wi-Fi scan throttle limit.
     */
    @SuppressLint("MissingPermission")
    suspend fun scanNearbyEspDevicesDetailed(
        triggerActiveScan: Boolean = true
    ): EspScanSnapshot = withContext(Dispatchers.IO) {
        val blocker = checkScanBlocker()
        if (blocker != EspScanBlocker.NONE) {
            Log.w(TAG, "ESP32 Wi-Fi scan blocked: $blocker")
            return@withContext EspScanSnapshot(devices = emptyList(), blocker = blocker)
        }

        val cached = readCachedScanResults()
        val cachedEsp = IotDomainLogic.parseNearbyEspDevices(cached)
        if (!triggerActiveScan || cachedEsp.isNotEmpty()) {
            Log.d(
                TAG,
                "Cached scan read: total=${cached.size}, matchedEsp=${cachedEsp.size}, triggerActiveScan=$triggerActiveScan"
            )
            return@withContext EspScanSnapshot(
                devices = cachedEsp,
                blocker = EspScanBlocker.NONE,
                totalVisibleNetworks = cached.size,
                activeScanTriggered = false
            )
        }

        var activeStarted = false
        val fresh = withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine<List<Pair<String, Int>>> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        runCatching { appContext.unregisterReceiver(this) }
                        val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true) ?: true
                        val results = readCachedScanResults()
                        Log.d(TAG, "SCAN_RESULTS_AVAILABLE updated=$updated, total=${results.size}")
                        if (cont.isActive) cont.resume(results)
                    }
                }
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    appContext.registerReceiver(receiver, filter)
                }
                @Suppress("DEPRECATION")
                activeStarted = runCatching { wifiManager.startScan() }.getOrDefault(false)
                if (!activeStarted) {
                    Log.w(TAG, "wifiManager.startScan() returned false (throttled by Android OS); using cached results")
                    runCatching { appContext.unregisterReceiver(receiver) }
                    if (cont.isActive) cont.resume(readCachedScanResults())
                }
                cont.invokeOnCancellation {
                    runCatching { appContext.unregisterReceiver(receiver) }
                }
            }
        } ?: readCachedScanResults()

        val combined = fresh.ifEmpty { cached }
        val matched = IotDomainLogic.parseNearbyEspDevices(combined)
        Log.d(TAG, "ESP32 scan complete: activeStarted=$activeStarted, total=${combined.size}, matched=${matched.size}")
        EspScanSnapshot(
            devices = matched,
            blocker = EspScanBlocker.NONE,
            totalVisibleNetworks = combined.size,
            activeScanTriggered = activeStarted
        )
    }

    /**
     * Scans nearby Wi-Fi signals and returns ONLY unclaimed ESP32 setup hotspots (`SPC-SETUP-*`).
     */
    suspend fun scanNearbyEspDevices(): List<NearbyEspDevice> =
        scanNearbyEspDevicesDetailed(triggerActiveScan = true).devices

    /**
     * Scans nearby 2.4 GHz Wi-Fi networks using the Android radio as a fallback if ESP32 scan returns empty.
     */
    @SuppressLint("MissingPermission")
    fun getLocalPhone24GhzNetworks(): List<ScannedWifiNetwork> {
        val list = runCatching {
            wifiManager.scanResults.mapNotNull { sr ->
                if (sr.frequency !in 2400..2500) return@mapNotNull null
                val ssid = extractSsid(sr)
                if (ssid.isNullOrBlank()) return@mapNotNull null
                val secure = sr.capabilities.contains("WEP", true) ||
                    sr.capabilities.contains("PSK", true) ||
                    sr.capabilities.contains("EAP", true) ||
                    sr.capabilities.contains("SAE", true)
                ScannedWifiNetwork(ssid = ssid, rssi = sr.level, secure = secure)
            }
        }.getOrDefault(emptyList())
        return IotDomainLogic.normalizeScannedWifi(list)
    }

    /**
     * Connects the phone to the ESP32 Setup Hotspot (`SPC-SETUP-<deviceId>`) and binds OkHttp
     * directly to the resulting Network so `192.168.4.1` never leaks to cellular data.
     */
    suspend fun connectToEspHotspot(
        ssid: String,
        passphrase: String = IotDomainLogic.DEFAULT_ESP_POP
    ): Result<EspDeviceInfo> = withContext(Dispatchers.IO) {
        disconnectFromEspHotspot()

        // Fast pre-check (1.2s timeout) in case user already joined 192.168.4.1 in Phone Wi-Fi Settings
        val activeWifiNet = connectivityManager.allNetworks.firstOrNull { net ->
            connectivityManager.getNetworkCapabilities(net)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (activeWifiNet != null) {
            val quickClient = buildHttpClient(activeWifiNet, connectTimeoutMs = 1200L, readTimeoutMs = 1500L)
            val directInfo = fetchDeviceInfoInternal(quickClient)
            if (directInfo.isSuccess) {
                boundNetwork = activeWifiNet
                boundHttpClient = buildHttpClient(activeWifiNet)
                return@withContext directInfo
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = withTimeoutOrNull(25000L) {
                suspendCancellableCoroutine<Network?> { cont ->
                    val specifier = WifiNetworkSpecifier.Builder()
                        .setSsid(ssid)
                        .setWpa2Passphrase(passphrase)
                        .build()

                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifier)
                        .build()

                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            Log.d(TAG, "Connected to ESP32 SoftAP network: $ssid")
                            boundNetwork = network
                            boundHttpClient = buildHttpClient(network)
                            if (cont.isActive) cont.resume(network)
                        }

                        override fun onLost(network: Network) {
                            Log.w(TAG, "ESP32 SoftAP network temporarily lost: $ssid")
                            if (boundNetwork == network) {
                                boundNetwork = null
                            }
                        }

                        override fun onUnavailable() {
                            Log.w(TAG, "User cancelled or ESP32 SoftAP unavailable: $ssid")
                            if (cont.isActive) cont.resume(null)
                        }
                    }

                    activeNetworkCallback = callback
                    try {
                        connectivityManager.requestNetwork(request, callback)
                    } catch (e: Exception) {
                        Log.e(TAG, "requestNetwork failed", e)
                        if (cont.isActive) cont.resume(null)
                    }

                    cont.invokeOnCancellation {
                        disconnectFromEspHotspot()
                    }
                }
            }

            if (network == null) {
                disconnectFromEspHotspot()
                return@withContext Result.failure(
                    IllegalStateException("Could not connect to $ssid. Tap Connect on the Android Wi-Fi prompt or join $ssid manually in Phone Wi-Fi Settings.")
                )
            }
        }

        // Verify device identity via GET http://192.168.4.1/api/info (retry once while DHCP settles)
        val firstAttempt = fetchDeviceInfoInternal(boundHttpClient)
        if (firstAttempt.isSuccess) return@withContext firstAttempt
        delay(600L)
        fetchDeviceInfoInternal(boundHttpClient)
    }

    /**
     * Checks whether the phone is already connected directly to the ESP32 SoftAP (manual fallback).
     */
    suspend fun verifyAlreadyConnectedToEsp(): Result<EspDeviceInfo> = withContext(Dispatchers.IO) {
        val activeWifiNet = connectivityManager.allNetworks.firstOrNull { net ->
            val caps = connectivityManager.getNetworkCapabilities(net)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val client = buildHttpClient(activeWifiNet)
        val result = fetchDeviceInfoInternal(client)
        if (result.isSuccess) {
            boundNetwork = activeWifiNet
            boundHttpClient = client
        }
        result
    }

    private fun fetchDeviceInfoInternal(client: OkHttpClient): Result<EspDeviceInfo> {
        return runCatching {
            val req = Request.Builder().url("$ESP_BASE_URL/api/info").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("ESP32 /api/info returned HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                EspDeviceInfo(
                    deviceId = json.optString("deviceId", "spc-unit-0001"),
                    displayName = json.optString("displayName", "SmartPlantCare Box"),
                    setupApSsid = json.optString("setupApSsid", "SPC-SETUP-spc-unit-0001"),
                    model = json.optString("model", "SmartPlantCare-3Zone"),
                    firmwareVersion = json.optString("firmwareVersion", "3.1.0-i2c"),
                    hardwareRevision = json.optString("hardwareRevision", "ESP32-WROOM32-V3.1-I2C"),
                    ownershipState = json.optString("ownershipState", "UNCLAIMED"),
                    ownershipEpoch = json.optLong("ownershipEpoch", 0L)
                )
            }
        }
    }

    /**
     * Calls GET http://192.168.4.1/api/wifi-scan on the ESP32 so the user gets an authentic
     * clickable list of nearby 2.4 GHz Wi-Fi networks that the ESP32 can actually join.
     */
    suspend fun scanEsp24GhzWifi(): Result<List<ScannedWifiNetwork>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$ESP_BASE_URL/api/wifi-scan").get().build()
            boundHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("ESP32 /api/wifi-scan returned HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val arr = json.optJSONArray("networks")
                val items = mutableListOf<ScannedWifiNetwork>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        items.add(
                            ScannedWifiNetwork(
                                ssid = obj.optString("ssid", ""),
                                rssi = obj.optInt("rssi", -80),
                                secure = obj.optBoolean("secure", true)
                            )
                        )
                    }
                }
                IotDomainLogic.normalizeScannedWifi(items)
            }
        }
    }

    /**
     * Sends the Owner's selected 2.4 GHz Wi-Fi SSID, password, physical Claim Code, and
     * custom Plant Box Name to the ESP32 (`POST http://192.168.4.1/api/provision`), then
     * POLLS `GET http://192.168.4.1/api/provision-status` until the ESP32 confirms it ACTUALLY
     * connected to the router (`status == "CONNECTED"`) or rejects a wrong password (`status == "FAILED"`).
     */
    suspend fun sendProvisioningPayload(
        wifiSsid: String,
        wifiPassword: String,
        claimCode: String,
        displayName: String,
        ownerUid: String,
        nextOwnershipEpoch: Long,
        onStatusUpdate: (String) -> Unit = {},
        onSoftApInterruptedCheckCloud: (suspend () -> Boolean)? = null
    ): ProvisionResult = withContext(Dispatchers.IO) {
        runCatching {
            val cleanSsid = IotDomainLogic.sanitizeWifiSsid(wifiSsid)
            val cleanPassword = IotDomainLogic.sanitizeWifiPassword(wifiPassword)
            val payload = JSONObject().apply {
                put("ssid", cleanSsid)
                put("password", cleanPassword)
                put("claimCode", claimCode.trim())
                put("displayName", displayName.trim().ifEmpty { "SmartPlantCare Box" })
                put("ownerUid", ownerUid)
                put("ownershipEpoch", nextOwnershipEpoch)
            }.toString()

            val req = Request.Builder()
                .url("$ESP_BASE_URL/api/provision")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            var initialStatus = ""
            var initialDeviceId = "spc-unit-0001"
            var initialDisplayName = displayName.trim()
            var initialEpoch = nextOwnershipEpoch
            var initialSuccess = false

            boundHttpClient.newCall(req).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(respStr) }.getOrNull()
                if (!resp.isSuccessful) {
                    val err = json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Provisioning rejected (HTTP ${resp.code})"
                    return@withContext ProvisionResult(success = false, errorMessage = err)
                }
                initialStatus = json?.optString("status", "") ?: ""
                initialSuccess = json?.optBoolean("success", false) ?: false
                initialDeviceId = json?.optString("deviceId", initialDeviceId) ?: initialDeviceId
                initialDisplayName = json?.optString("displayName", initialDisplayName) ?: initialDisplayName
                initialEpoch = json?.optLong("ownershipEpoch", nextOwnershipEpoch) ?: nextOwnershipEpoch
            }

            // Backward compatibility if an older firmware immediately returned CONNECTED/success=true
            if (initialStatus.equals("CONNECTED", ignoreCase = true) || (initialStatus.isEmpty() && initialSuccess)) {
                return@withContext ProvisionResult(
                    success = true,
                    deviceId = initialDeviceId,
                    displayName = initialDisplayName,
                    ownershipEpoch = initialEpoch
                )
            }

            // Give ESP32 single 2.4 GHz radio 2.2s of uninterrupted airtime to start router handshake
            delay(2200L)

            // Poll GET /api/provision-status up to 38 seconds (covers AP+STA profiles + pure-STA fallback)
            val pollDeadlineMs = System.currentTimeMillis() + 38_000L
            var consecutiveNetErrors = 0
            var activePollNetwork: Network? = boundNetwork
            var pollClient = buildHttpClient(
                network = activePollNetwork,
                connectTimeoutMs = 2500L,
                readTimeoutMs = 3000L
            )

            while (System.currentTimeMillis() < pollDeadlineMs) {
                val secLeft = ((pollDeadlineMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)

                // Only use boundNetwork or a verified 192.168.4.x ESP32 SoftAP network — NEVER route
                // 192.168.4.1 onto the phone's home Wi-Fi router when SoftAP hops channels!
                val currentNet = boundNetwork?.takeIf { isEspSoftApNetwork(it) }
                    ?: connectivityManager.allNetworks.firstOrNull { net ->
                        connectivityManager.getNetworkCapabilities(net)
                            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                            isEspSoftApNetwork(net)
                    }

                if (currentNet == null) {
                    consecutiveNetErrors++
                    Log.w(TAG, "ESP32 SoftAP link dropped during router test (#$consecutiveNetErrors) — probing cloud fallback")
                    onStatusUpdate("ESP32 switched to router channel — checking cloud link... (${secLeft}s)")
                    if (onSoftApInterruptedCheckCloud != null && runCatching { onSoftApInterruptedCheckCloud() }.getOrDefault(false)) {
                        return@withContext ProvisionResult(
                            success = true,
                            deviceId = initialDeviceId,
                            displayName = initialDisplayName,
                            ownershipEpoch = initialEpoch
                        )
                    }
                    // Release WifiNetworkSpecifier quickly after 3 misses (~4.5s) so Android restores
                    // full home Wi-Fi / mobile data internet for Stage 2/3 cloud verification!
                    if (consecutiveNetErrors >= 3) {
                        return@withContext ProvisionResult(
                            success = false,
                            deviceId = initialDeviceId,
                            displayName = initialDisplayName,
                            ownershipEpoch = initialEpoch,
                            contactLostAfterAccept = true,
                            errorMessage = "Could not verify Wi-Fi connection on ESP32. Please check your Wi-Fi password (2.4 GHz) and reconnect to SPC-SETUP."
                        )
                    }
                    delay(1500L)
                    continue
                }

                if (currentNet != activePollNetwork) {
                    activePollNetwork = currentNet
                    pollClient = buildHttpClient(
                        network = activePollNetwork,
                        connectTimeoutMs = 2200L,
                        readTimeoutMs = 2800L
                    )
                }

                val statusReq = Request.Builder().url("$ESP_BASE_URL/api/provision-status").get().build()
                var httpCode = -1
                val pollOutcome = runCatching {
                    pollClient.newCall(statusReq).execute().use { resp ->
                        httpCode = resp.code
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) null else JSONObject(body)
                    }
                }.getOrNull()

                if (httpCode == 404) {
                    return@withContext ProvisionResult(
                        success = false,
                        errorMessage = "ESP32 firmware is outdated (missing /api/provision-status). Please re-upload the updated SmartPlantCare sketch to your ESP32."
                    )
                }

                if (pollOutcome == null) {
                    consecutiveNetErrors++
                    Log.w(TAG, "Poll /api/provision-status transient miss #$consecutiveNetErrors (http=$httpCode)")
                    onStatusUpdate("ESP32 is testing router connection... (${secLeft}s)")
                    if (consecutiveNetErrors >= 3 && onSoftApInterruptedCheckCloud != null) {
                        if (runCatching { onSoftApInterruptedCheckCloud() }.getOrDefault(false)) {
                            return@withContext ProvisionResult(
                                success = true,
                                deviceId = initialDeviceId,
                                displayName = initialDisplayName,
                                ownershipEpoch = initialEpoch
                            )
                        }
                    }
                    // Hand off to Stage 2/3 cloud check after 6 consecutive HTTP misses so phone regains internet promptly
                    if (consecutiveNetErrors >= 6) {
                        return@withContext ProvisionResult(
                            success = false,
                            deviceId = initialDeviceId,
                            displayName = initialDisplayName,
                            ownershipEpoch = initialEpoch,
                            contactLostAfterAccept = true,
                            errorMessage = "Could not verify Wi-Fi connection on ESP32. Please check your Wi-Fi password (2.4 GHz) and reconnect to SPC-SETUP."
                        )
                    }
                    delay(1600L)
                    continue
                }

                consecutiveNetErrors = 0
                val status = pollOutcome.optString("status", "TESTING")
                val reasonCode = pollOutcome.optInt("reasonCode", 0)
                val reasonName = pollOutcome.optString("reasonName", "")
                val staConnected = pollOutcome.optBoolean("staConnected", false)
                when (status.uppercase()) {
                    "CONNECTED" -> {
                        return@withContext ProvisionResult(
                            success = true,
                            deviceId = pollOutcome.optString("deviceId", initialDeviceId),
                            displayName = pollOutcome.optString("displayName", initialDisplayName),
                            ownershipEpoch = pollOutcome.optLong("ownershipEpoch", nextOwnershipEpoch)
                        )
                    }
                    "FAILED" -> {
                        val errMsg = pollOutcome.optString("error").takeIf { it.isNotBlank() }
                            ?: if (reasonCode > 0) {
                                val detail = if (reasonName.isNotBlank()) " ($reasonCode: $reasonName)" else " (code $reasonCode)"
                                "Invalid Wi-Fi password or router rejected connection$detail. Please check your password and try again."
                            } else {
                                "Invalid Wi-Fi password. Please check your password and try again."
                            }
                        return@withContext ProvisionResult(
                            success = false,
                            errorMessage = errMsg
                        )
                    }
                    "IDLE" -> {
                        return@withContext ProvisionResult(
                            success = false,
                            errorMessage = "ESP32 Wi-Fi verification did not start. Please try again."
                        )
                    }
                    else -> {
                        if (staConnected) {
                            onStatusUpdate("Password accepted by router! Finalizing... (${secLeft}s)")
                        } else {
                            onStatusUpdate("Verifying Wi-Fi password on ESP32... (${secLeft}s)")
                        }
                    }
                }
                delay(1800L)
            }

            ProvisionResult(
                success = false,
                deviceId = initialDeviceId,
                displayName = initialDisplayName,
                ownershipEpoch = initialEpoch,
                contactLostAfterAccept = consecutiveNetErrors >= 3,
                errorMessage = "Timed out verifying Wi-Fi on ESP32. Please check your Wi-Fi password and ensure your router broadcasts 2.4 GHz WPA2."
            )
        }.getOrElse { ex ->
            ProvisionResult(
                success = false,
                errorMessage = ex.localizedMessage ?: "Failed to verify Wi-Fi credentials on ESP32."
            )
        }
    }

    /**
     * Releases the temporary ESP32 SoftAP network request so the phone immediately returns
     * to normal Wi-Fi / Mobile Data to sync with Firebase Realtime Database.
     */
    fun disconnectFromEspHotspot() {
        activeNetworkCallback?.let { cb ->
            runCatching { connectivityManager.unregisterNetworkCallback(cb) }
        }
        activeNetworkCallback = null
        boundNetwork = null
        boundHttpClient = buildHttpClient(null)
    }
}
