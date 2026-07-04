package com.example.smartplantcare.data

class PredictionResult {

    data class PredictionResult(
        val classIndex: Int,
        val className: String,
        val confidence: Float
    )
}