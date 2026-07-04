package com.example.smartplantcare.ML

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.smartplantcare.data.ClassificationMessages
import com.example.smartplantcare.data.ClassificationResult
import com.example.smartplantcare.data.PredictionResult.PredictionResult
import com.example.smartplantcare.data.RejectedReason
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor

/**
 * On-device leaf disease classifier using a shared preprocessing path for
 * camera captures and gallery uploads — matching Python training exactly.
 */
class LeafClassifier(context: Context) {

    private val appContext = context.applicationContext

    private val labels: Map<Int, String> = try {
        LabelLoader.loadLabels(appContext).also {
            Log.i(TAG, "Labels loaded successfully: ${it.size} classes")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load labels", e)
        emptyMap()
    }

    private val interpreter: Interpreter = try {
        ModelLoader.getInterpreter(appContext).also {
            Log.i(TAG, "Interpreter initialized successfully")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load interpreter", e)
        throw RuntimeException("Failed to initialize ML model: ${e.message}", e)
    }

    private val inputSpec: TensorSpec = try {
        inspectTensor(interpreter.getInputTensor(0), "input")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to inspect input tensor", e)
        throw RuntimeException("Failed to initialize input tensor spec: ${e.message}", e)
    }

    private val outputSpec: TensorSpec = try {
        inspectTensor(interpreter.getOutputTensor(0), "output").also {
            Log.i(TAG, "Tensor specs initialized successfully")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to inspect output tensor", e)
        throw RuntimeException("Failed to initialize output tensor spec: ${e.message}", e)
    }

    data class TensorSpec(
        val dataType: DataType,
        val shape: IntArray,
        val scale: Float,
        val zeroPoint: Int
    )

    data class Result(
        val classification: ClassificationResult,
        val message: String,
        val probabilities: FloatArray,
        val top1Index: Int,
        val top1Confidence: Float,
        val top2Confidence: Float,
        val margin: Float
    ) {
        val prediction: PredictionResult?
            get() = (classification as? ClassificationResult.Accepted)?.prediction

        val isAccepted: Boolean
            get() = classification is ClassificationResult.Accepted

        /** @deprecated Use [isAccepted] — kept for minimal call-site migration. */
        val isUncertain: Boolean
            get() = classification is ClassificationResult.Rejected
    }

    /**
     * Classify a bitmap that is already decoded.
     *
     * @param rotationDegrees Camera sensor rotation from [androidx.camera.core.ImageProxy.imageInfo].
     *   Pass 0 when the bitmap is already upright (e.g. after [ImageProcessor.loadBitmapFromUri]).
     */
    fun classify(bitmap: Bitmap, rotationDegrees: Int = 0): Result {
        return try {
            Log.d(TAG, "Classifying bitmap: ${bitmap.width}x${bitmap.height}, rotation: $rotationDegrees")

            if (bitmap.width == 0 || bitmap.height == 0) {
                Log.e(TAG, "Invalid bitmap dimensions")
                return errorResult(
                    message = "Invalid image dimensions",
                    top1Index = -1,
                    top1Confidence = 0f,
                    top2Confidence = 0f,
                    margin = 0f,
                    reason = RejectedReason.UnknownLeaf
                )
            }

            if (bitmap.isRecycled) {
                Log.e(TAG, "Bitmap is already recycled")
                return errorResult(
                    message = "Bitmap is recycled",
                    top1Index = -1,
                    top1Confidence = 0f,
                    top2Confidence = 0f,
                    margin = 0f,
                    reason = RejectedReason.UnknownLeaf
                )
            }

            Log.d(TAG, "Building model input")
            val inputBuffer = ImageProcessor.buildModelInput(bitmap, rotationDegrees, inputSpec)
            Log.d(TAG, "Running inference")
            val probabilities = runInference(inputBuffer)
            Log.d(TAG, "Building result from probabilities")
            buildResult(probabilities)
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            errorResult(
                message = "Classification error: ${e.message}",
                top1Index = -1,
                top1Confidence = 0f,
                top2Confidence = 0f,
                margin = 0f,
                reason = RejectedReason.UnknownLeaf
            )
        }
    }

    /** Load a gallery image with EXIF orientation applied, then classify. */
    fun classifyFromUri(uri: Uri): Result {
        val upright = ImageProcessor.loadBitmapFromUri(appContext, uri)
        return classify(upright, rotationDegrees = 0)
    }

    private fun runInference(inputBuffer: java.nio.ByteBuffer): FloatArray {
        return try {
            Log.d(TAG, "Running inference with output dtype: ${outputSpec.dataType}")
            val probabilities = when (outputSpec.dataType) {
                DataType.FLOAT32 -> {
                    val output = Array(1) { FloatArray(ModelConfig.NUM_CLASSES) }
                    interpreter.run(inputBuffer, output)
                    output[0]
                }
                DataType.UINT8 -> {
                    val output = Array(1) { ByteArray(ModelConfig.NUM_CLASSES) }
                    interpreter.run(inputBuffer, output)
                    dequantizeOutput(output[0], outputSpec)
                }
                DataType.INT8 -> {
                    val output = Array(1) { ByteArray(ModelConfig.NUM_CLASSES) }
                    interpreter.run(inputBuffer, output)
                    dequantizeOutput(output[0], outputSpec)
                }
                else -> throw IllegalStateException("Unsupported output dtype: ${outputSpec.dataType}")
            }

            Log.d(
                TAG,
                "probabilities=${probabilities.joinToString(prefix = "[", postfix = "]") { "%.6f".format(it) }}"
            )

            probabilities
        } catch (e: Exception) {
            Log.e(TAG, "Inference execution failed", e)
            throw RuntimeException("Inference failed: ${e.message}", e)
        }
    }

    private fun buildResult(probabilities: FloatArray): Result {
        val ranked = probabilities
            .mapIndexed { index, confidence -> index to confidence }
            .sortedByDescending { it.second }

        val top1 = ranked[0]
        val top2 = ranked.getOrElse(1) { 0 to 0f }

        val top1Index = top1.first
        val top1Confidence = top1.second
        val top2Index = top2.first
        val top2Confidence = top2.second
        val margin = top1Confidence - top2Confidence
        val top1ClassName = labels[top1Index]
        val top2ClassName = labels[top2Index] ?: "unknown"

        val classification = evaluateClassification(
            top1Index = top1Index,
            top1ClassName = top1ClassName,
            top1Confidence = top1Confidence,
            top2ClassName = top2ClassName,
            top2Confidence = top2Confidence,
            margin = margin
        )

        val message = when (classification) {
            is ClassificationResult.Accepted -> {
                val percent = String.format("%.1f", top1Confidence * 100)
                "Prediction: ${classification.top1ClassName} ($percent%)"
            }
            is ClassificationResult.Rejected -> ClassificationMessages.messageFor(classification.reason)
        }

        return Result(
            classification = classification,
            message = message,
            probabilities = probabilities,
            top1Index = top1Index,
            top1Confidence = top1Confidence,
            top2Confidence = top2Confidence,
            margin = margin
        )
    }

    // PERMANENT THRESHOLD LOGIC
    private fun evaluateClassification(
        top1Index: Int,
        top1ClassName: String?,
        top1Confidence: Float,
        top2ClassName: String,
        top2Confidence: Float,
        margin: Float
    ): ClassificationResult {
        if (top1ClassName == null || top1Index < 0) {
            return ClassificationResult.Rejected(
                reason = RejectedReason.UnknownLeaf,
                top1Index = top1Index,
                top1ClassName = top1ClassName ?: "unknown",
                top1Confidence = top1Confidence,
                top2ClassName = top2ClassName,
                top2Confidence = top2Confidence,
                margin = margin
            )
        }

        if (top1Confidence < CONFIDENCE_THRESHOLD) {
            return ClassificationResult.Rejected(
                reason = RejectedReason.LowConfidence,
                top1Index = top1Index,
                top1ClassName = top1ClassName,
                top1Confidence = top1Confidence,
                top2ClassName = top2ClassName,
                top2Confidence = top2Confidence,
                margin = margin
            )
        }

        if (margin < MARGIN_THRESHOLD) {
            return ClassificationResult.Rejected(
                reason = RejectedReason.LowMargin,
                top1Index = top1Index,
                top1ClassName = top1ClassName,
                top1Confidence = top1Confidence,
                top2ClassName = top2ClassName,
                top2Confidence = top2Confidence,
                margin = margin
            )
        }

        return ClassificationResult.Accepted(
            prediction = PredictionResult(
                classIndex = top1Index,
                className = top1ClassName,
                confidence = top1Confidence
            ),
            top1Index = top1Index,
            top1ClassName = top1ClassName,
            top1Confidence = top1Confidence,
            top2ClassName = top2ClassName,
            top2Confidence = top2Confidence,
            margin = margin
        )
    }

    private fun errorResult(
        message: String,
        top1Index: Int,
        top1Confidence: Float,
        top2Confidence: Float,
        margin: Float,
        reason: RejectedReason
    ): Result {
        val classification = ClassificationResult.Rejected(
            reason = reason,
            top1Index = top1Index,
            top1ClassName = "unknown",
            top1Confidence = top1Confidence,
            top2ClassName = "unknown",
            top2Confidence = top2Confidence,
            margin = margin
        )
        return Result(
            classification = classification,
            message = message,
            probabilities = FloatArray(ModelConfig.NUM_CLASSES),
            top1Index = top1Index,
            top1Confidence = top1Confidence,
            top2Confidence = top2Confidence,
            margin = margin
        )
    }

    private fun inspectTensor(tensor: Tensor, label: String): TensorSpec {
        val dataType = tensor.dataType()
        val shape = tensor.shape()
        val quantParams = tensor.quantizationParams()
        val scale = quantParams?.scale ?: 1f
        val zeroPoint = quantParams?.zeroPoint ?: 0

        Log.i(
            TAG,
            "$label tensor — dtype=$dataType shape=${shape.contentToString()} " +
                "scale=$scale zeroPoint=$zeroPoint"
        )

        return TensorSpec(
            dataType = dataType,
            shape = shape,
            scale = scale,
            zeroPoint = zeroPoint
        )
    }

    private fun dequantizeOutput(raw: ByteArray, spec: TensorSpec): FloatArray {
        return FloatArray(raw.size) { index ->
            val quantized = when (spec.dataType) {
                DataType.UINT8 -> raw[index].toInt() and 0xFF
                DataType.INT8 -> raw[index].toInt()
                else -> raw[index].toInt()
            }
            (quantized - spec.zeroPoint) * spec.scale
        }
    }

    fun cleanup() {
        ModelLoader.close()
    }

    companion object {
        private const val TAG = "LeafClassifier"

        // ------------------------------------------------
        // TEMPORARY PLACEHOLDER.
        // After collecting calibration CSV:
        // 1. Filter rows where markedCorrect = TRUE.
        // 2. Find the MIN confidence.
        // 3. Find the MIN margin.
        // 4. Subtract ~0.03 safety buffer.
        // These become the new thresholds.
        // Cross-check markedCorrect=FALSE rows.
        // False predictions should mostly fall BELOW these thresholds.
        // ------------------------------------------------
        // PERMANENT THRESHOLD LOGIC
        private const val CONFIDENCE_THRESHOLD = 0.52f
        private const val MARGIN_THRESHOLD = 0.12f

        const val UNCERTAIN_MESSAGE = ClassificationMessages.UNKNOWN_LEAF
    }
}
