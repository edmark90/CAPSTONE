package com.example.smartplantcare.ML

import com.example.smartplantcare.data.PredictionResult.PredictionResult

/**
 * Sealed class representing the result of the complete detection pipeline:
 * Plant Identification -> Disease Classification -> Filtering
 *
 * The pipeline follows these states:
 * 1. PlantRejected: Plant identification confidence below threshold
 * 2. PlantAccepted: Plant identified successfully, proceed to disease classification
 * 3. DiseaseRejected: Disease classification confidence below threshold
 * 4. DiseaseAccepted: Both plant and disease identified successfully
 */
sealed class DetectionResult {
    /**
     * Plant identification failed - confidence below threshold.
     * User should capture a clearer image.
     */
    data class PlantRejected(
        val plantPrediction: PlantPrediction,
        val message: String
    ) : DetectionResult()

    /**
     * Plant identified successfully, but disease classification failed.
     * This can happen if the disease model cannot confidently identify the disease.
     */
    data class DiseaseRejected(
        val plantPrediction: PlantPrediction,
        val message: String
    ) : DetectionResult()

    /**
     * Both plant and disease identified successfully.
     * Contains the final filtered disease prediction.
     */
    data class DiseaseAccepted(
        val plantPrediction: PlantPrediction,
        val diseasePrediction: PredictionResult,
        val diseaseConfidence: Float,
        val plantConfidence: Float
    ) : DetectionResult()

    /**
     * Returns true if the detection was successful (both plant and disease accepted).
     */
    val isSuccess: Boolean
        get() = this is DiseaseAccepted

    /**
     * Returns true if plant identification was successful (regardless of disease result).
     */
    val isPlantIdentified: Boolean
        get() = this is PlantAccepted || this is DiseaseAccepted

    /**
     * Returns the plant prediction if available, null otherwise.
     */
    fun extractPlantPrediction(): PlantPrediction? {
        return when (this) {
            is PlantRejected -> plantPrediction
            is DiseaseRejected -> plantPrediction
            is DiseaseAccepted -> plantPrediction
            is PlantAccepted -> plantPrediction
        }
    }

    /**
     * Returns the disease prediction if available, null otherwise.
     */
    fun extractDiseasePrediction(): PredictionResult? {
        return when (this) {
            is DiseaseAccepted -> diseasePrediction
            else -> null
        }
    }
}

/**
 * Internal state for plant acceptance (not exposed to UI).
 * Used internally in the pipeline before disease classification.
 */
internal data class PlantAccepted(
    val plantPrediction: PlantPrediction
) : DetectionResult()
