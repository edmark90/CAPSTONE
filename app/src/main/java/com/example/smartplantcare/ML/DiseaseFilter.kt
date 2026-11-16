package com.example.smartplantcare.ML

import android.util.Log

/**
 * Filters disease classification results based on the detected plant type.
 * 
 * The disease model (model_int8.tflite) outputs 22 classes covering diseases for
 * Tomato, Eggplant, and Chili. After plant identification, we must filter the
 * disease predictions to only consider classes belonging to the detected plant.
 * 
 * Class mapping from class_mapping.json:
 * - Chili: indices 0-5
 * - Eggplant: indices 6-11
 * - Tomato: indices 12-21
 */
object DiseaseFilter {
    private const val TAG = "DiseaseFilter"

    /**
     * Disease class indices for each plant type.
     * These indices correspond to the class_mapping.json file.
     */
    private val CHILI_INDICES = intArrayOf(0, 1, 2, 3, 4, 5)
    private val EGGPLANT_INDICES = intArrayOf(6, 7, 8, 9, 10, 11)
    private val TOMATO_INDICES = intArrayOf(12, 13, 14, 15, 16, 17, 18, 19, 20, 21)

    /**
     * Get the valid disease class indices for a given plant type.
     * 
     * @param plantType The detected plant type
     * @return Array of class indices that belong to this plant type
     */
    fun getValidClassIndices(plantType: PlantType): IntArray {
        return when (plantType) {
            PlantType.TOMATO -> TOMATO_INDICES
            PlantType.EGGPLANT -> EGGPLANT_INDICES
            PlantType.CHILI -> CHILI_INDICES
            PlantType.UNKNOWN -> intArrayOf() // No valid classes for unknown plant
        }
    }

    /**
     * Filter disease probabilities to only include classes for the detected plant.
     * 
     * @param probabilities The full 22-class probability array from the disease model
     * @param plantType The detected plant type
     * @return Filtered probabilities with invalid classes set to 0.0
     */
    fun filterProbabilities(probabilities: FloatArray, plantType: PlantType): FloatArray {
        val validIndices = getValidClassIndices(plantType)
        val filtered = FloatArray(probabilities.size)

        if (validIndices.isEmpty()) {
            Log.w(TAG, "No valid class indices for plant type: $plantType")
            return filtered
        }

        var sum = 0.0f
        for (i in probabilities.indices) {
            if (i in validIndices) {
                filtered[i] = probabilities[i]
                sum += probabilities[i]
            } else {
                filtered[i] = 0.0f
            }
        }

        if (sum > 0.0f) {
            for (i in validIndices) {
                if (i in filtered.indices) {
                    filtered[i] /= sum
                }
            }
        }

        Log.d(TAG, "Filtered probabilities for $plantType: valid indices=${validIndices.contentToString()}, preNormSum=$sum")
        return filtered
    }

    /**
     * Find the highest confidence disease class among valid classes for the plant.
     * 
     * @param probabilities The full 22-class probability array from the disease model
     * @param plantType The detected plant type
     * @return Pair of (classIndex, confidence) for the best valid class, or null if no valid classes
     */
    fun findBestValidClass(probabilities: FloatArray, plantType: PlantType): Pair<Int, Float>? {
        val validIndices = getValidClassIndices(plantType)

        if (validIndices.isEmpty()) {
            Log.w(TAG, "No valid class indices for plant type: $plantType")
            return null
        }

        var bestIndex = -1
        var bestConfidence = 0.0f

        for (index in validIndices) {
            if (index < probabilities.size && probabilities[index] > bestConfidence) {
                bestConfidence = probabilities[index]
                bestIndex = index
            }
        }

        return if (bestIndex >= 0) {
            Log.d(TAG, "Best valid class for $plantType: index=$bestIndex confidence=$bestConfidence")
            Pair(bestIndex, bestConfidence)
        } else {
            Log.w(TAG, "No valid class found for $plantType")
            null
        }
    }

    /**
     * Check if a given class index belongs to a specific plant type.
     * 
     * @param classIndex The disease class index
     * @param plantType The plant type to check against
     * @return True if the class belongs to the plant type
     */
    fun isClassValidForPlant(classIndex: Int, plantType: PlantType): Boolean {
        return when (plantType) {
            PlantType.TOMATO -> classIndex in TOMATO_INDICES
            PlantType.EGGPLANT -> classIndex in EGGPLANT_INDICES
            PlantType.CHILI -> classIndex in CHILI_INDICES
            PlantType.UNKNOWN -> false
        }
    }

    /**
     * Get the plant type for a given disease class index.
     * This is useful for validation and debugging.
     * 
     * @param classIndex The disease class index
     * @return The plant type this class belongs to, or UNKNOWN if invalid
     */
    fun getPlantTypeForClass(classIndex: Int): PlantType {
        return when {
            classIndex in CHILI_INDICES -> PlantType.CHILI
            classIndex in EGGPLANT_INDICES -> PlantType.EGGPLANT
            classIndex in TOMATO_INDICES -> PlantType.TOMATO
            else -> PlantType.UNKNOWN
        }
    }

    /**
     * Get the number of valid disease classes for a plant type.
     * 
     * @param plantType The plant type
     * @return Number of disease classes for this plant
     */
    fun getClassCount(plantType: PlantType): Int {
        return when (plantType) {
            PlantType.TOMATO -> TOMATO_INDICES.size
            PlantType.EGGPLANT -> EGGPLANT_INDICES.size
            PlantType.CHILI -> CHILI_INDICES.size
            PlantType.UNKNOWN -> 0
        }
    }
}
