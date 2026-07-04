package com.example.smartplantcare.ML

object ModelConfig {
    const val MODEL_FILENAME = "model_int8.tflite"
    const val LABELS_FILENAME = "class_mapping.json"

    const val INPUT_WIDTH = 224
    const val INPUT_HEIGHT = 224
    const val CHANNELS = 3

    const val NUM_CLASSES = 22
}
