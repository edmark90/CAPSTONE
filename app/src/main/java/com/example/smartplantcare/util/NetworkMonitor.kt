package com.example.smartplantcare.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.example.smartplantcare.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Detects whether the device has internet and whether that connection is
 * "unstable" (present but slow). Shows a "Unstable connection" toast ONLY
 * when the device is online but the connection is slow — never when there
 * is no internet at all (each screen handles the no-internet case itself).
 */
object NetworkMonitor {

    /** A latency probe that takes >= this long is considered unstable/slow. */
    private const val SLOW_THRESHOLD_MS = 3000L

    /** Max time we wait for the latency probe. */
    private const val PROBE_TIMEOUT_MS  = 6000L

    /** Reuse a probe result for this long to avoid pinging on every action. */
    private const val CACHE_TTL_MS      = 15000L

    /** Minimum gap between toasts to avoid spamming. */
    private const val TOAST_COOLDOWN_MS = 10000L

    private var appContext: Context? = null
    private var lastProbeAt = 0L
    private var lastSlow    = false
    private var lastToastAt = 0L

    private val _isOnlineFlow = MutableStateFlow(true)
    val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    /**
     * Timestamp (epoch ms) when the phone last transitioned from online -> offline.
     * Null while the phone has internet. Used to freeze ESP32 freshness evaluation so
     * an online ESP32 is NOT falsely marked "ESP32 Offline" while the phone has no internet.
     */
    private val _disconnectedAtMsFlow = MutableStateFlow<Long?>(null)
    val disconnectedAtMsFlow: StateFlow<Long?> = _disconnectedAtMsFlow.asStateFlow()

    private var isCallbackRegistered = false

    fun init(context: Context) {
        appContext = context.applicationContext
        val onlineNow = isOnline()
        _isOnlineFlow.value = onlineNow
        _disconnectedAtMsFlow.value = if (onlineNow) null else System.currentTimeMillis()
        registerNetworkCallback()
    }

    private fun setOnlineState(online: Boolean) {
        val wasOnline = _isOnlineFlow.value
        if (online) {
            _disconnectedAtMsFlow.value = null
        } else if (wasOnline || _disconnectedAtMsFlow.value == null) {
            _disconnectedAtMsFlow.value = System.currentTimeMillis()
        }
        _isOnlineFlow.value = online
    }

    /** Re-checks active network capabilities and updates [isOnlineFlow] immediately. */
    fun refreshOnlineState(): Boolean {
        val onlineNow = isOnline()
        setOnlineState(onlineNow)
        return onlineNow
    }

    private fun registerNetworkCallback() {
        if (isCallbackRegistered) return
        val ctx = appContext ?: return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    setOnlineState(isOnline())
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    setOnlineState(hasInternet)
                }

                override fun onLost(network: Network) {
                    // On Android, cm.activeNetwork can still briefly return the lost network inside onLost().
                    val active = try { cm.activeNetwork } catch (_: Throwable) { null }
                    if (active == null || active == network) {
                        setOnlineState(false)
                    } else {
                        setOnlineState(isOnline())
                    }
                }

                override fun onUnavailable() {
                    setOnlineState(false)
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            isCallbackRegistered = true
        } catch (_: Throwable) {
            setOnlineState(isOnline())
        }
    }

    /** True only when the device has a validated internet connection. */
    fun isOnline(): Boolean {
        return try {
            val ctx = appContext ?: return false
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * If the device IS online but the connection is slow/unstable, shows a
     * "Unstable connection" toast. Does nothing when there is no internet.
     */
    suspend fun notifyIfUnstable() {
        val ctx = appContext ?: return
        if (!isOnline()) return

        val now = SystemClock.elapsedRealtime()
        val useCache = lastProbeAt != 0L && (now - lastProbeAt) < CACHE_TTL_MS
        val slow = if (useCache) {
            lastSlow
        } else {
            val result = withContext(Dispatchers.IO) { probeLatency() }
            lastSlow = result
            lastProbeAt = SystemClock.elapsedRealtime()
            result
        }

        if (slow) {
            showUnstableToast(ctx)
        }
    }

    /** Latency probe against a lightweight connectivity-check endpoint. */
    private suspend fun probeLatency(): Boolean {
        return try {
            val start = SystemClock.elapsedRealtime()
            val succeeded = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                val url = URL("https://connectivitycheck.gstatic.com/generate_204")
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.instanceFollowRedirects = false
                    conn.requestMethod = "GET"
                    conn.responseCode in 200..299
                } finally {
                    conn.disconnect()
                }
            } ?: false
            val elapsed = SystemClock.elapsedRealtime() - start
            succeeded && elapsed >= SLOW_THRESHOLD_MS
        } catch (e: Exception) {
            true // probe itself failed quickly -> likely unstable
        }
    }

    private fun showUnstableToast(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToastAt < TOAST_COOLDOWN_MS) return
        lastToastAt = now

        val message = try {
            context.getString(R.string.unstable_connection)
        } catch (e: Exception) {
            "Unstable connection"
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
