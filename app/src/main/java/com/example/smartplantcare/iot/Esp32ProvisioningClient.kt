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
     * custom Plant Box Name to the ESP32 (`POST http://192.168.4.1/api/provision`).
     */
    suspend fun sendProvisioningPayload(
        wifiSsid: String,
        wifiPassword: String,
        claimCode: String,
        displayName: String,
        ownerUid: String,
        nextOwnershipEpoch: Long
    ): ProvisionResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("ssid", wifiSsid.trim())
                put("password", wifiPassword)
                put("claimCode", claimCode.trim())
                put("displayName", displayName.trim().ifEmpty { "SmartPlantCare Box" })
                put("ownerUid", ownerUid)
                put("ownershipEpoch", nextOwnershipEpoch)
            }.toString()

            val req = Request.Builder()
                .url("$ESP_BASE_URL/api/provision")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            boundHttpClient.newCall(req).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(respStr) }.getOrNull()
                if (!resp.isSuccessful) {
                    val err = json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Provisioning rejected (HTTP ${resp.code})"
                    return@use ProvisionResult(success = false, errorMessage = err)
                }
                ProvisionResult(
                    success = json?.optBoolean("success", true) ?: true,
                    deviceId = json?.optString("deviceId", "spc-unit-0001") ?: "spc-unit-0001",
                    displayName = json?.optString("displayName", displayName.trim()) ?: displayName.trim(),
                    ownershipEpoch = json?.optLong("ownershipEpoch", nextOwnershipEpoch) ?: nextOwnershipEpoch
                )
            }
        }.getOrElse { ex ->
            ProvisionResult(
                success = false,
                errorMessage = ex.localizedMessage ?: "Failed to send Wi-Fi credentials to ESP32."
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
