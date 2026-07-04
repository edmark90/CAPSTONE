package com.example.smartplantcare.ML

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.smartplantcare.calibration.CalibrationConfig
import com.example.smartplantcare.data.ClassificationResult
import com.example.smartplantcare.data.PredictionResult.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [LeafClassifier] on camera or gallery images using one shared preprocessing path.
 * Use the singleton instance via getInstance() to avoid multiple model loads.
 */
class InferencePipeline private constructor(context: Context) {

    private val classifier: LeafClassifier

    init {
        try {
            classifier = LeafClassifier(context.applicationContext)
            android.util.Log.i("InferencePipeline", "Pipeline initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("InferencePipeline", "Failed to initialize classifier", e)
            throw RuntimeException("Failed to initialize ML pipeline: ${e.message}", e)
        }
    }

    companion object {
        @Volatile
        private var instance: InferencePipeline? = null

        fun getInstance(context: Context): InferencePipeline {
            return instance ?: synchronized(this) {
                instance ?: InferencePipeline(context.applicationContext).also { instance = it }
            }
        }

        fun isInitialized(): Boolean = instance != null
    }

    data class PipelineResult(
        val isSuccess: Boolean,
        val classification: ClassificationResult? = null,
        val predictionResult: PredictionResult? = null,
        val message: String,
        val stepsCompleted: List<String> = emptyList(),
        val debugInfo: Map<String, Any> = emptyMap()
    )

    suspend fun runPipeline(bitmap: Bitmap, rotationDegrees: Int = 0): PipelineResult =
        withContext(Dispatchers.Default) {
            try {
                android.util.Log.d("InferencePipeline", "Starting pipeline with bitmap: ${bitmap.width}x${bitmap.height}")
                runClassification { classifier.classify(bitmap, rotationDegrees) }
            } catch (e: Exception) {
                android.util.Log.e("InferencePipeline", "Pipeline execution failed", e)
                PipelineResult(
                    isSuccess = false,
                    classification = null,
                    message = "Pipeline error: ${e.message}",
                    stepsCompleted = emptyList(),
                    debugInfo = mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

    suspend fun runPipelineFromUri(uri: Uri): PipelineResult =
        withContext(Dispatchers.Default) {
            runClassification { classifier.classifyFromUri(uri) }
        }

    private fun runClassification(block: () -> LeafClassifier.Result): PipelineResult {
        return try {
            val result = block()
            val classification = result.classification
            val debugInfo = mapOf(
                "top1Index" to result.top1Index,
                "top1Confidence" to result.top1Confidence,
                "top2Confidence" to result.top2Confidence,
                "margin" to result.margin,
                "isAccepted" to result.isAccepted,
                "probabilities" to result.probabilities.toList()
            )

            val predictionForUi = predictionForUi(classification)
            val navigateToResult = when (classification) {
                is ClassificationResult.Accepted -> true
                is ClassificationResult.Rejected -> CalibrationConfig.CALIBRATION_MODE &&
                    classification.top1Index >= 0 &&
                    classification.top1ClassName != "unknown"
            }

            if (navigateToResult && predictionForUi != null) {
                PipelineResult(
                    isSuccess = true,
                    classification = classification,
                    predictionResult = predictionForUi,
                    message = result.message,
                    stepsCompleted = listOf("Shared preprocessing", "TFLite inference"),
                    debugInfo = debugInfo
                )
            } else {
                PipelineResult(
                    isSuccess = false,
                    classification = classification,
                    predictionResult = predictionForUi,
                    message = result.message,
                    stepsCompleted = listOf("Shared preprocessing", "TFLite inference"),
                    debugInfo = debugInfo
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("InferencePipeline", "Pipeline error", e)
            PipelineResult(
                isSuccess = false,
                classification = null,
                message = "An error occurred during analysis: ${e.message}"
            )
        }
    }

    private fun predictionForUi(classification: ClassificationResult): PredictionResult? {
        return when (classification) {
            is ClassificationResult.Accepted -> classification.prediction
            is ClassificationResult.Rejected -> {
                if (classification.top1Index < 0) return null
                PredictionResult(
                    classIndex = classification.top1Index,
                    className = classification.top1ClassName,
                    confidence = classification.top1Confidence
                )
            }
        }
    }

    fun cleanup() {
        classifier.cleanup()
    }
}
