package com.example.smartplantcare.ML

/**
 * Data class representing the result of plant identification inference.
 * Contains the detected plant type and the confidence score from the model.
 *
 * @param plantType The detected plant type (Tomato, Eggplant, Chili, or Unknown)
 * @param confidence The confidence score from the plant identification model (0.0 to 1.0)
 * @param classIndex The raw class index from the model output (0-2 for known plants)
 */
data class PlantPrediction(
    val plantType: PlantType,
    val confidence: Float,
    val classIndex: Int
) {
    /**
     * Returns true if the plant identification confidence meets the minimum threshold.
     */
    fun meetsThreshold(threshold: Float): Boolean {
        return confidence >= threshold && plantType != PlantType.UNKNOWN
    }

    /**
     * Returns the confidence as a percentage string (e.g., "95%").
     */
    fun getConfidencePercentage(): String {
        return "${(confidence * 100).toInt()}%"
    }
}
