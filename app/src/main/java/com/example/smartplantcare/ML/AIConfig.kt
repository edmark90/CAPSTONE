package com.example.smartplantcare.ML

/**
 * Central configuration object for all AI/ML thresholds and parameters.
 * This eliminates magic numbers and provides a single source of truth for
 * plant identification and disease classification thresholds.
 */
object AIConfig {
    /**
     * Minimum confidence threshold for plant identification.
     * Value: 0.60 (60% confidence required; a 3-class softmax always has max >= 0.333f)
     */
    const val PLANT_THRESHOLD = 0.60f

    /**
     * Minimum confidence threshold for disease classification.
     * Value: 0.35 (35% confidence required)
     * Optimized for 22-class classification to avoid rejecting valid predictions.
     */
    const val DISEASE_CONFIDENCE_THRESHOLD = 0.35f

    /**
     * Minimum margin threshold for disease classification.
     * Value: 0.05 (5% margin required)
     */
    const val DISEASE_MARGIN_THRESHOLD = 0.05f

    /**
     * Number of threads to use for TensorFlow Lite inference.
     * Using multiple threads can improve inference performance on multi-core devices.
     */
    const val TFLITE_NUM_THREADS = 4

    /**
     * Plant identification model configuration.
     */
    object PlantIdentification {
        const val MODEL_FILENAME = "plantidentification.tflite"
        const val NUM_CLASSES = 3  // Tomato, Eggplant, Chili
        const val INPUT_WIDTH = 224
        const val INPUT_HEIGHT = 224
        const val CHANNELS = 3
    }

    /**
     * Disease classification model configuration.
     * These values match the existing ModelConfig for backward compatibility.
     */
    object DiseaseClassification {
        const val MODEL_FILENAME = "model_int8.tflite"
        const val LABELS_FILENAME = "class_mapping.json"
        const val NUM_CLASSES = 22
        const val INPUT_WIDTH = 224
        const val INPUT_HEIGHT = 224
        const val CHANNELS = 3
    }
}
