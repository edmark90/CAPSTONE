package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.ML.AIConfig
import com.example.smartplantcare.ML.DetectionResult
import com.example.smartplantcare.ML.LeafClassifier
import com.example.smartplantcare.ML.PlantIdentifier
import com.example.smartplantcare.ML.PlantPrediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for the complete plant identification and disease detection pipeline.
 * 
 * Pipeline:
 * 1. Plant Identification (plantidentification.tflite) -> PlantPrediction
 * 2. If plant confidence >= threshold -> Disease Classification (model_int8.tflite)
 * 3. Filter disease classes by detected plant type
 * 4. Return DetectionResult with plant and disease information
 */
class DiseaseDetectionViewModel(
    private val context: Context
) : ViewModel() {

    var uiState by mutableStateOf<DiseaseDetectionUiState>(DiseaseDetectionUiState.Idle)
        private set

    private var plantIdentifier: PlantIdentifier? = null
    private var leafClassifier: LeafClassifier? = null

    init {
        initializeModels()
    }

    /**
     * Initialize the ML models on a background thread.
     */
    private fun initializeModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing ML models...")
                plantIdentifier = PlantIdentifier(context)
                leafClassifier = LeafClassifier(context)
                Log.d(TAG, "ML models initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ML models", e)
                uiState = DiseaseDetectionUiState.Error(
                    "Failed to initialize ML models: ${e.message}"
                )
            }
        }
    }

    /**
     * Run the complete detection pipeline on an image file.
     * 
     * @param imageFile The captured image file
     */
    fun detectDisease(imageFile: File) {
        if (plantIdentifier == null || leafClassifier == null) {
            uiState = DiseaseDetectionUiState.Error("ML models not initialized")
            return
        }

        uiState = DiseaseDetectionUiState.Scanning

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting detection pipeline for: ${imageFile.absolutePath}")

                // Load bitmap from file
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                if (bitmap == null) {
                    uiState = DiseaseDetectionUiState.Error("Failed to load image")
                    return@launch
                }

                // Step 1: Plant Identification
                Log.d(TAG, "Step 1: Running plant identification...")
                val plantPrediction = plantIdentifier!!.identify(bitmap, rotationDegrees = 0)
                Log.d(TAG, "Plant identification result: $plantPrediction")

                // Check plant confidence threshold
                if (!plantPrediction.meetsThreshold(AIConfig.PLANT_THRESHOLD)) {
                    Log.d(TAG, "Plant confidence below threshold: ${plantPrediction.confidence} < ${AIConfig.PLANT_THRESHOLD}")
                    withContext(Dispatchers.Main) {
                        uiState = DiseaseDetectionUiState.Result(
                            detectionResult = DetectionResult.PlantRejected(
                                plantPrediction = plantPrediction,
                                message = "Unable to confidently identify the plant. Please try again."
                            )
                        )
                    }
                    return@launch
                }

                // Step 2: Disease Classification (only if plant identification succeeded)
                Log.d(TAG, "Step 2: Running disease classification for ${plantPrediction.plantType}...")
                val diseaseResult = leafClassifier!!.classify(
                    bitmap = bitmap,
                    rotationDegrees = 0,
                    plantType = plantPrediction.plantType
                )
                Log.d(TAG, "Disease classification result: ${diseaseResult.classification}")

                // Step 3: Build final DetectionResult
                val detectionResult = when (diseaseResult.classification) {
                    is com.example.smartplantcare.data.ClassificationResult.Accepted -> {
                        DetectionResult.DiseaseAccepted(
                            plantPrediction = plantPrediction,
                            diseasePrediction = diseaseResult.prediction!!,
                            diseaseConfidence = diseaseResult.top1Confidence,
                            plantConfidence = plantPrediction.confidence
                        )
                    }
                    is com.example.smartplantcare.data.ClassificationResult.Rejected -> {
                        DetectionResult.DiseaseRejected(
                            plantPrediction = plantPrediction,
                            message = diseaseResult.message.ifBlank { "Unable to confidently identify the disease. Please try again." }
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    uiState = DiseaseDetectionUiState.Result(detectionResult = detectionResult)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Detection pipeline failed", e)
                withContext(Dispatchers.Main) {
                    uiState = DiseaseDetectionUiState.Error(
                        "Detection failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Reset the UI state to Idle for a new detection.
     */
    fun resetToIdle() {
        uiState = DiseaseDetectionUiState.Idle
    }

    /**
     * Clean up resources when the ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        plantIdentifier?.cleanup()
        leafClassifier?.cleanup()
    }

    companion object {
        private const val TAG = "DiseaseDetectionViewModel"
    }
}

/**
 * UI state for the disease detection pipeline.
 */
sealed class DiseaseDetectionUiState {
    data object Idle : DiseaseDetectionUiState()
    data object Scanning : DiseaseDetectionUiState()
    data class Result(val detectionResult: DetectionResult) : DiseaseDetectionUiState()
    data class Error(val message: String) : DiseaseDetectionUiState()
}
