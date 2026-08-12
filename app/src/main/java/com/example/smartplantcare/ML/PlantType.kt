package com.example.smartplantcare.ML

/**
 * Enum representing the plant types that can be identified by the plant identification model.
 * These correspond to the output classes of plantidentification.tflite.
 */
enum class PlantType {
    TOMATO,
    EGGPLANT,
    CHILI,
    UNKNOWN;

    companion object {
        /**
         * Maps plant identification model output indices to PlantType enum values.
         * The plantidentification.tflite model outputs 3 classes (alphabetical Keras order):
         * - Index 0: Chili
         * - Index 1: Eggplant
         * - Index 2: Tomato
         */
        fun fromIndex(index: Int): PlantType {
            return when (index) {
                0 -> CHILI
                1 -> EGGPLANT
                2 -> TOMATO
                else -> UNKNOWN
            }
        }
    }

    /**
     * Returns the display name for the plant type.
     */
    fun getDisplayName(): String {
        return when (this) {
            TOMATO -> "Tomato"
            EGGPLANT -> "Eggplant"
            CHILI -> "Chili"
            UNKNOWN -> "Unknown"
        }
    }
}
