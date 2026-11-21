package com.example.smartplantcare.iot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class WifiWeatherSnapshot(
    val locationName: String,
    val lat: Double,
    val lon: Double,
    val rainProbabilityPct: Int,
    val provider: String = "open-meteo-gps",
    val locationSource: String = "owner_gps"
)

/**
 * Resolves the Plant Box's accurate installation location from the OWNER's Android phone GPS
 * (using built-in Android LocationManager + Geocoder, with zero Play Services dependency),
 * and fetches the live 3-hour rain probability % from Open-Meteo.
 *
 * Shared users (OPERATOR / VIEWER) NEVER call [fetchOwnerMobileGpsAndRainForecast]; they use
 * [fetchRainForecastForCoordinates] with the Owner's already-saved box coordinates in Firebase.
 */
class WifiWeatherLocationClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * OWNER-ONLY: Captures the phone's real GPS/Network location, reverse-geocodes it to a clean
     * "City, Province" label, and fetches Open-Meteo 3-hour max precipitation probability.
     */
    suspend fun fetchOwnerMobileGpsAndRainForecast(context: Context): Result<WifiWeatherSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!hasLocationPermission(context)) {
                    throw SecurityException("Location permission is required to set the box GPS location.")
                }

                val location = resolveCurrentOrLastLocation(context)
                    ?: throw IllegalStateException("Could not get GPS fix. Turn on phone Location/GPS and try again.")

                val lat = location.latitude
                val lon = location.longitude
                val placeName = reverseGeocodeName(context, lat, lon)
                val rainPct = fetchOpenMeteo3hMaxRainPct(lat, lon)
                    ?: throw IllegalStateException("Open-Meteo weather API unavailable right now.")

                WifiWeatherSnapshot(
                    locationName = placeName,
                    lat = lat,
                    lon = lon,
                    rainProbabilityPct = rainPct,
                    provider = "open-meteo-gps",
                    locationSource = "owner_gps"
                )
            }
        }

    /**
     * Fetches the live 3-hour rain forecast from Open-Meteo for the box's existing saved coordinates
     * WITHOUT accessing phone GPS or requesting location permission. Safe for shared users.
     */
    suspend fun fetchRainForecastForCoordinates(
        lat: Double,
        lon: Double,
        locationName: String,
        locationSource: String = "owner_gps"
    ): Result<WifiWeatherSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(lat != 0.0 || lon != 0.0) { "Box coordinates have not been set by the Owner yet." }
                val rainPct = fetchOpenMeteo3hMaxRainPct(lat, lon)
                    ?: throw IllegalStateException("Open-Meteo weather API unavailable right now.")
                val cleanName = locationName.trim().ifBlank {
                    String.format(Locale.US, "%.3f, %.3f", lat, lon)
                }
                WifiWeatherSnapshot(
                    locationName = cleanName,
                    lat = lat,
                    lon = lon,
                    rainProbabilityPct = rainPct,
                    provider = "open-meteo-gps",
                    locationSource = locationSource
                )
            }
        }

    @SuppressLint("MissingPermission")
    private suspend fun resolveCurrentOrLastLocation(context: Context): Location? {
        val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = buildList {
            if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
            ) {
                add(0, LocationManager.FUSED_PROVIDER)
            }
        }

        // 1. Try fresh current location with bounded timeout (prefer FUSED / GPS / NETWORK)
        for (provider in providers) {
            val fresh = requestFreshLocation(context, lm, provider, timeoutMs = 5_500L)
            if (fresh != null) return fresh
        }

        // 2. Fallback to freshest valid lastKnownLocation across all enabled/passive providers
        val allCandidates = (providers + listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )).distinct()

        return allCandidates
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(
        context: Context,
        lm: LocationManager,
        provider: String,
        timeoutMs: Long
    ): Location? = withTimeoutOrNull(timeoutMs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(
                        provider,
                        signal,
                        context.mainExecutor
                    ) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { lm.removeUpdates(this) }
                            if (cont.isActive) cont.resume(location)
                        }
                    }
                    cont.invokeOnCancellation {
                        runCatching { lm.removeUpdates(listener) }
                    }
                    runCatching {
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
    }

    private suspend fun reverseGeocodeName(context: Context, lat: Double, lon: Double): String {
        val fromAndroid = runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(context, Locale.getDefault())
            val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(4_000L) {
                    suspendCancellableCoroutine { cont ->
                        runCatching {
                            geocoder.getFromLocation(lat, lon, 1) { list ->
                                if (cont.isActive) cont.resume(list.firstOrNull())
                            }
                        }.onFailure {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
            if (address != null) {
                formatLocationName(
                    city = address.locality ?: address.subAdminArea ?: "",
                    region = address.adminArea ?: "",
                    countryCode = address.countryCode ?: "",
                    lat = lat,
                    lon = lon
                )
            } else {
                null
            }
        }.getOrNull()

        if (!fromAndroid.isNullOrBlank()) return fromAndroid

        // Lightweight Nominatim fallback if device Geocoder backend is unavailable
        val fromNominatim = runCatching {
            val url = String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.5f&lon=%.5f&zoom=10",
                lat,
                lon
            )
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SmartPlantCare-Android/3.1")
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val json = JSONObject(resp.body?.string().orEmpty())
                val addr = json.optJSONObject("address") ?: return@runCatching null
                val city = addr.optString("city", "")
                    .ifBlank { addr.optString("town", "") }
                    .ifBlank { addr.optString("municipality", "") }
                    .ifBlank { addr.optString("village", "") }
                val region = addr.optString("state", "")
                    .ifBlank { addr.optString("province", "") }
                val cc = addr.optString("country_code", "").uppercase(Locale.US)
                formatLocationName(city, region, cc, lat, lon)
            }
        }.getOrNull()

        return fromNominatim ?: String.format(Locale.US, "%.3f, %.3f", lat, lon)
    }

    private fun fetchOpenMeteo3hMaxRainPct(lat: Double, lon: Double): Int? {
        return runCatching {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=precipitation_probability&forecast_hours=3&timezone=auto",
                lat,
                lon
            )
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val json = JSONObject(resp.body?.string().orEmpty())
                val hourly = json.optJSONObject("hourly") ?: return@runCatching null
                val arr = hourly.optJSONArray("precipitation_probability") ?: return@runCatching null
                if (arr.length() == 0) return@runCatching null
                var maxProb = 0
                val count = minOf(3, arr.length())
                for (i in 0 until count) {
                    val p = arr.optInt(i, 0)
                    if (p > maxProb) maxProb = p
                }
                maxProb.coerceIn(0, 100)
            }
        }.getOrNull()
    }

    private fun formatLocationName(
        city: String,
        region: String,
        countryCode: String,
        lat: Double,
        lon: Double
    ): String {
        val c = city.trim()
        val r = region.trim()
        val cc = countryCode.trim()
        return when {
            c.isNotEmpty() && r.isNotEmpty() && !c.equals(r, ignoreCase = true) -> "$c, $r"
            c.isNotEmpty() && cc.isNotEmpty() -> "$c, $cc"
            c.isNotEmpty() -> c
            r.isNotEmpty() -> r
            else -> String.format(Locale.US, "%.3f, %.3f", lat, lon)
        }
    }
}
