package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify


import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.BuildConfig
import com.example.smartplantcare.plantnetnetwork.PlantIdentificationSummary
import com.example.smartplantcare.plantnetnetwork.PlantNetOutcome
import com.example.smartplantcare.plantnetnetwork.PlantNetRepository
import com.example.smartplantcare.util.NetworkMonitor
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * UI state ng buong Identify flow: idle -> scanning -> result / error
 */
sealed class IdentifyUiState {
    data object Idle : IdentifyUiState()
    data object Scanning : IdentifyUiState()
    data class Result(
        val topMatch   : PlantIdentificationSummary,
        val allResults : List<PlantIdentificationSummary>
    ) : IdentifyUiState()
    data class Error(val message: String) : IdentifyUiState()
}


class PlantIdentifyViewModel(
    private val context: Context? = null
) : ViewModel() {

    /** Minimum top-match confidence to treat a result as a real match. */
    private companion object {
        const val MIN_CONFIDENCE = 0.10
        const val NO_PLANT_FOUND_MESSAGE = "No plant found. Please try again."
    }

    private val apiKey     = BuildConfig.PLANTNET_API_KEY
    private val repository = PlantNetRepository(apiKey = apiKey)
    private val usageRepository =
        com.example.smartplantcare.plantnetnetwork.PlantIdentificationUsageRepository()

    var uiState by mutableStateOf<IdentifyUiState>(IdentifyUiState.Idle)
        private set

    /** Live usage data — null until first fetch completes. */
    var usage by mutableStateOf<com.example.smartplantcare.plantnetnetwork.PlantIdentificationUsage?>(null)
        private set

    /** True while usage is loading from Firestore. */
    var isLoadingUsage by mutableStateOf(false)
        private set

    init {
        loadUsage()
    }

    fun loadUsage() {
        viewModelScope.launch {
            isLoadingUsage = true
            try {
                NetworkMonitor.notifyIfUnstable()
                usage = usageRepository.getUsage()
                android.util.Log.d("PlantIdentifyVM", "Usage loaded - Count: ${usage?.identifyCount}, Remaining: ${usage?.remaining}")
            } catch (e: Exception) {
                android.util.Log.e("PlantIdentifyVM", "Failed to load usage", e)
            } finally {
                isLoadingUsage = false
            }
        }
    }

    private fun isNetworkAvailable(): Boolean = NetworkMonitor.isOnline()

    fun identifyPlant(imageFile: File) {
        android.util.Log.d("PlantIdentify", "BuildConfig API Key: '${BuildConfig.PLANTNET_API_KEY}'")
        android.util.Log.d("PlantIdentify", "Using API Key: '$apiKey'")
        android.util.Log.d("PlantIdentify", "Image file: ${imageFile.absolutePath}, size: ${imageFile.length()} bytes, exists: ${imageFile.exists()}")

        // Guard: API key
        if (apiKey.isBlank()) {
            uiState = IdentifyUiState.Error(
                "PlantNet API key is not configured.\n\n" +
                        "To fix this:\n" +
                        "1. Open the file 'local.properties.template' in your project root\n" +
                        "2. Rename it to 'local.properties'\n" +
                        "3. Get a free API key from: https://my.plantnet.org/\n" +
                        "4. Add this line: PLANTNET_API_KEY=your_api_key_here\n" +
                        "5. Rebuild the app"
            )
            return
        }

        // Guard: network
        if (!isNetworkAvailable()) {
            uiState = IdentifyUiState.Error(
                "No internet connection. Please check your network and try again."
            )
            return
        }

        // Guard: daily limit
        val currentUsage = usage
        android.util.Log.d("PlantIdentifyVM", "Checking daily limit - Current usage: $currentUsage")
        if (currentUsage != null && currentUsage.isLimitReached) {
            android.util.Log.d("PlantIdentifyVM", "Daily limit reached - Count: ${currentUsage.identifyCount}")
            uiState = IdentifyUiState.Error(
                "You have reached your daily limit of " +
                        "${com.example.smartplantcare.plantnetnetwork.PlantIdentificationUsageRepository.DAILY_LIMIT}" +
                        " identifications.\nYour quota resets every day at 8:00 AM."
            )
            return
        }

        uiState = IdentifyUiState.Scanning

        viewModelScope.launch {
            NetworkMonitor.notifyIfUnstable()
            try {
                android.util.Log.d("PlantIdentify", "Starting plant identification...")
                val outcome = withTimeout(20000L) {
                    repository.identifyPlant(imageFile)
                }
                android.util.Log.d("PlantIdentify", "API outcome: $outcome")

                when (outcome) {
                    is PlantNetOutcome.Success -> {
                        android.util.Log.d("PlantIdentify",
                            "Success - Confidence: ${outcome.topMatch.confidenceScore}, Plant: ${outcome.topMatch.plantName}")

                        if (outcome.topMatch.confidenceScore < MIN_CONFIDENCE ||
                            outcome.topMatch.confidenceScore.isNaN()) {
                            // Invalid/too-low result — do NOT count
                            android.util.Log.d("PlantIdentifyVM", "Invalid confidence score: ${outcome.topMatch.confidenceScore} - NOT counting")
                            uiState = IdentifyUiState.Error(
                                NO_PLANT_FOUND_MESSAGE
                            )
                        } else {
                            // Valid result — increment quota
                            android.util.Log.d("PlantIdentifyVM", "Valid confidence score: ${outcome.topMatch.confidenceScore} - Incrementing count")
                            try {
                                usage = usageRepository.incrementCount()
                                android.util.Log.d("PlantIdentifyVM", "Count incremented successfully")
                            } catch (e: Exception) {
                                android.util.Log.e("PlantIdentifyVM", "Failed to increment usage", e)
                                // Non-blocking: show result even if quota write fails
                            }
                            uiState = IdentifyUiState.Result(
                                topMatch   = outcome.topMatch,
                                allResults = outcome.allResults
                            )
                        }
                    }

                    is PlantNetOutcome.NoResultsError -> {
                        android.util.Log.d("PlantIdentify", "NoResultsError: ${outcome.message}")
                        // Do NOT count
                        uiState = IdentifyUiState.Error(NO_PLANT_FOUND_MESSAGE)
                    }

                    is PlantNetOutcome.ApiError -> {
                        android.util.Log.d("PlantIdentify", "ApiError: ${outcome.code} - ${outcome.message}")
                        // Do NOT count
                        val errorMessage = when (outcome.code) {
                            401  -> "Invalid API key. Please check your PLANTNET_API_KEY in local.properties"
                            403  -> "API key forbidden. Please check your PlantNet account status"
                            429  -> "API rate limit exceeded. Please try again later"
                            else -> NO_PLANT_FOUND_MESSAGE
                        }
                        uiState = IdentifyUiState.Error(errorMessage)
                    }

                    is PlantNetOutcome.NetworkError -> {
                        android.util.Log.d("PlantIdentify", "NetworkError: ${outcome.message}")
                        // Do NOT count
                        uiState = IdentifyUiState.Error(
                            "Unable to identify the plant. Please check your internet connection and try again."
                        )
                    }

                    is PlantNetOutcome.UnknownError -> {
                        android.util.Log.d("PlantIdentify", "UnknownError: ${outcome.message}")
                        // Do NOT count
                        uiState = IdentifyUiState.Error(NO_PLANT_FOUND_MESSAGE)
                    }
                }

            } catch (e: TimeoutCancellationException) {
                android.util.Log.d("PlantIdentify", "TimeoutCancellationException")
                // Do NOT count — request timed out / cancelled
                uiState = IdentifyUiState.Error(NO_PLANT_FOUND_MESSAGE)
            } catch (e: Exception) {
                android.util.Log.e("PlantIdentify", "Unexpected error", e)
                // Do NOT count
                uiState = IdentifyUiState.Error(NO_PLANT_FOUND_MESSAGE)
            }
        }
    }

    /** Reset to Idle and refresh usage so the banner shows accurate data. */
    fun resetToIdle() {
        uiState = IdentifyUiState.Idle
        loadUsage()
    }
}
