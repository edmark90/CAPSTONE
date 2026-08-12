package com.example.smartplantcare.ML

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

import com.example.smartplantcare.data.ClassificationResult
import com.example.smartplantcare.data.PredictionResult.PredictionResult
import com.example.smartplantcare.data.RejectedReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the 2-stage inference pipeline on camera or gallery images using shared preprocessing:
 * 1. Plant Species Identification (plantidentification.tflite) via [PlantIdentifier]
 * 2. Plant-Specific Disease Classification (model_int8.tflite) via [LeafClassifier] & [DiseaseFilter]
 * 
 * Use the singleton instance via getInstance() to avoid multiple model loads.
 */
class InferencePipeline private constructor(context: Context) {

    private val plantIdentifier: PlantIdentifier
    private val classifier: LeafClassifier

    init {
        try {
            val appContext = context.applicationContext
            plantIdentifier = PlantIdentifier(appContext)
            classifier = LeafClassifier(appContext)
            android.util.Log.i("InferencePipeline", "2-stage pipeline initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("InferencePipeline", "Failed to initialize ML models", e)
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
        val plantPrediction: PlantPrediction? = null,
        val classification: ClassificationResult? = null,
        val predictionResult: PredictionResult? = null,
        val message: String,
        val stepsCompleted: List<String> = emptyList(),
        val debugInfo: Map<String, Any> = emptyMap()
    )

    suspend fun runPipeline(bitmap: Bitmap, rotationDegrees: Int = 0): PipelineResult =
        withContext(Dispatchers.Default) {
            try {
                android.util.Log.d("InferencePipeline", "Starting 2-stage pipeline with bitmap: ${bitmap.width}x${bitmap.height}")
                
                // Stage 1: Plant Species Identification
                android.util.Log.d("InferencePipeline", "Stage 1: Plant Identification")
                val plantPrediction = plantIdentifier.identify(bitmap, rotationDegrees)
                android.util.Log.d("InferencePipeline", "Plant prediction result: $plantPrediction")

                if (!plantPrediction.meetsThreshold(AIConfig.PLANT_THRESHOLD)) {
                    android.util.Log.w("InferencePipeline", "Plant confidence below threshold (${plantPrediction.confidence} < ${AIConfig.PLANT_THRESHOLD})")
                    val rejectedClassification = ClassificationResult.Rejected(
                        reason = RejectedReason.UnknownLeaf,
                        top1Index = plantPrediction.classIndex,
                        top1ClassName = plantPrediction.plantType.getDisplayName(),
                        top1Confidence = plantPrediction.confidence,
                        top2ClassName = "Unknown",
                        top2Confidence = 0f,
                        margin = 0f
                    )
                    return@withContext PipelineResult(
                        isSuccess = false,
                        plantPrediction = plantPrediction,
                        classification = rejectedClassification,
                        predictionResult = null,
                        message = "Unable to identify plant species (confidence: ${plantPrediction.getConfidencePercentage()}). Please ensure the image clearly shows a Tomato, Eggplant, or Chili leaf.",
                        stepsCompleted = listOf("Shared preprocessing", "Plant identification (Rejected)"),
                        debugInfo = mapOf(
                            "plantType" to plantPrediction.plantType.name,
                            "plantConfidence" to plantPrediction.confidence,
                            "threshold" to AIConfig.PLANT_THRESHOLD
                        )
                    )
                }

                // Stage 2: Disease Classification with plant type filtering
                android.util.Log.d("InferencePipeline", "Stage 2: Disease Classification for ${plantPrediction.plantType}")
                val diseaseResult = classifier.classify(bitmap, rotationDegrees, plantPrediction.plantType)
                val classification = diseaseResult.classification

                val debugInfo = mapOf(
                    "plantType" to plantPrediction.plantType.name,
                    "plantConfidence" to plantPrediction.confidence,
                    "diseaseTop1Index" to diseaseResult.top1Index,
                    "diseaseTop1Confidence" to diseaseResult.top1Confidence,
                    "diseaseTop2Confidence" to diseaseResult.top2Confidence,
                    "margin" to diseaseResult.margin,
                    "isAccepted" to diseaseResult.isAccepted,
                    "probabilities" to diseaseResult.probabilities.toList()
                )

                val predictionForUi = predictionForUi(classification)
                val isAccepted = classification is ClassificationResult.Accepted

                PipelineResult(
                    isSuccess = isAccepted && predictionForUi != null,
                    plantPrediction = plantPrediction,
                    classification = classification,
                    predictionResult = predictionForUi,
                    message = diseaseResult.message,
                    stepsCompleted = listOf("Shared preprocessing", "Plant identification", "Disease classification & filtering"),
                    debugInfo = debugInfo
                )
            } catch (e: Exception) {
                android.util.Log.e("InferencePipeline", "Pipeline execution failed", e)
                PipelineResult(
                    isSuccess = false,
                    plantPrediction = null,
                    classification = null,
                    message = "Pipeline error: ${e.message}",
                    stepsCompleted = emptyList(),
                    debugInfo = mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

    suspend fun runPipelineFromUri(context: Context, uri: Uri): PipelineResult =
        withContext(Dispatchers.Default) {
            try {
                val bitmap = ImageProcessor.loadBitmapFromUri(context, uri)
                runPipeline(bitmap, rotationDegrees = 0)
            } catch (e: Exception) {
                android.util.Log.e("InferencePipeline", "Failed to run pipeline from URI", e)
                PipelineResult(
                    isSuccess = false,
                    plantPrediction = null,
                    classification = null,
                    message = "Failed to process image: ${e.message}"
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
        try {
            plantIdentifier.cleanup()
            classifier.cleanup()
        } catch (e: Exception) {
            android.util.Log.e("InferencePipeline", "Error during cleanup", e)
        }
    }
}

