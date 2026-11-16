package com.example.smartplantcare.ML

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device plant identifier using the plantidentification.tflite model.
 * This model classifies images into three plant types: Tomato, Eggplant, and Chili.
 * 
 * The plant identification runs BEFORE disease classification in the pipeline.
 * If plant identification confidence is below threshold, disease classification is skipped.
 */
class PlantIdentifier(context: Context) {

    private val appContext = context.applicationContext

    private val interpreter: Interpreter = try {
        loadInterpreter().also {
            Log.i(TAG, "Plant identification interpreter initialized successfully")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load plant identification interpreter", e)
        throw RuntimeException("Failed to initialize plant identification model: ${e.message}", e)
    }

    private val inputSpec: TensorSpec = try {
        inspectTensor(interpreter.getInputTensor(0), "input")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to inspect input tensor", e)
        throw RuntimeException("Failed to initialize input tensor spec: ${e.message}", e)
    }

    private val outputSpec: TensorSpec = try {
        inspectTensor(interpreter.getOutputTensor(0), "output").also {
            Log.i(TAG, "Plant identification tensor specs initialized successfully")
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

    /**
     * Identify the plant type from a bitmap.
     * 
     * @param bitmap The input image bitmap
     * @param rotationDegrees Camera sensor rotation (pass 0 if bitmap is already upright)
     * @return PlantPrediction containing the detected plant type and confidence
     */
    fun identify(bitmap: Bitmap, rotationDegrees: Int = 0): PlantPrediction {
        return try {
            Log.d(TAG, "Identifying plant from bitmap: ${bitmap.width}x${bitmap.height}, rotation: $rotationDegrees")

            if (bitmap.width == 0 || bitmap.height == 0) {
                Log.e(TAG, "Invalid bitmap dimensions")
                return PlantPrediction(
                    plantType = PlantType.UNKNOWN,
                    confidence = 0f,
                    classIndex = -1
                )
            }

            if (bitmap.isRecycled) {
                Log.e(TAG, "Bitmap is already recycled")
                return PlantPrediction(
                    plantType = PlantType.UNKNOWN,
                    confidence = 0f,
                    classIndex = -1
                )
            }

            Log.d(TAG, "Building plant identification model input")
            val inputBuffer = buildModelInput(bitmap, rotationDegrees)
            
            Log.d(TAG, "Running plant identification inference")
            val probabilities = runInference(inputBuffer)
            
            Log.d(TAG, "Building plant prediction from probabilities")
            buildPrediction(probabilities)
        } catch (e: Exception) {
            Log.e(TAG, "Plant identification failed", e)
            PlantPrediction(
                plantType = PlantType.UNKNOWN,
                confidence = 0f,
                classIndex = -1
            )
        }
    }

    /**
     * Build the model input buffer from a bitmap.
     * Uses the same preprocessing as the disease model for consistency.
     */
    private fun buildModelInput(bitmap: Bitmap, rotationDegrees: Int): ByteBuffer {
        try {
            // Apply rotation if needed
            var working = if (rotationDegrees != 0) {
                ImageProcessor.orientBitmap(bitmap, rotationDegrees)
            } else {
                bitmap
            }

            // Center-crop to square so Stage 1 and Stage 2 inspect the exact same leaf region
            working = ImageProcessor.centerCropSquare(working)

            // Ensure ARGB_8888 format
            working = ImageProcessor.ensureArgb8888(working, recycleSource = false)

            // Resize to model input dimensions
            val resized = Bitmap.createScaledBitmap(
                working,
                AIConfig.PlantIdentification.INPUT_WIDTH,
                AIConfig.PlantIdentification.INPUT_HEIGHT,
                true
            )

            // Allocate and fill input buffer
            val buffer = allocateInputBuffer(inputSpec.dataType)
            fillInputBuffer(resized, buffer, inputSpec)

            buffer.rewind()

            Log.d(
                TAG,
                "Plant identification input: dtype=${inputSpec.dataType} " +
                    "shape=${inputSpec.shape.contentToString()} " +
                    "scale=${inputSpec.scale} zeroPoint=${inputSpec.zeroPoint}"
            )

            return buffer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build plant identification model input", e)
            throw RuntimeException("Failed to build model input: ${e.message}", e)
        }
    }

    /**
     * Run inference on the plant identification model.
     */
    private fun runInference(inputBuffer: ByteBuffer): FloatArray {
        return try {
            Log.d(TAG, "Running plant identification inference with output dtype: ${outputSpec.dataType}")
            val probabilities = when (outputSpec.dataType) {
                DataType.FLOAT32 -> {
                    val output = Array(1) { FloatArray(AIConfig.PlantIdentification.NUM_CLASSES) }
                    interpreter.run(inputBuffer, output)
                    output[0]
                }
                DataType.UINT8 -> {
                    val output = Array(1) { ByteArray(AIConfig.PlantIdentification.NUM_CLASSES) }
                    interpreter.run(inputBuffer, output)
                    dequantizeOutput(output[0], outputSpec)
                }
                DataType.INT8 -> {
                    val output = Array(1) { ByteArray(AIConfig.PlantIdentification.NUM_CLASSES) }
                    interpreter.run(inputBuffer, output)
                    dequantizeOutput(output[0], outputSpec)
                }
                else -> throw IllegalStateException("Unsupported output dtype: ${outputSpec.dataType}")
            }

            Log.d(
                TAG,
                "Plant identification probabilities=${probabilities.joinToString(prefix = "[", postfix = "]") { "%.6f".format(it) }}"
            )

            probabilities
        } catch (e: Exception) {
            Log.e(TAG, "Plant identification inference execution failed", e)
            throw RuntimeException("Inference failed: ${e.message}", e)
        }
    }

    /**
     * Build a PlantPrediction from the model output probabilities.
     */
    private fun buildPrediction(probabilities: FloatArray): PlantPrediction {
        val ranked = probabilities
            .mapIndexed { index, confidence -> index to confidence }
            .sortedByDescending { it.second }

        val top1 = ranked[0]
        val classIndex = top1.first
        val confidence = top1.second
        val plantType = PlantType.fromIndex(classIndex)

        Log.d(
            TAG,
            "Plant identification result: classIndex=$classIndex plantType=$plantType confidence=$confidence"
        )

        return PlantPrediction(
            plantType = plantType,
            confidence = confidence,
            classIndex = classIndex
        )
    }

    /**
     * Load the plant identification model file and create an interpreter.
     */
    private fun loadInterpreter(): Interpreter {
        val modelBuffer = loadModelFile(appContext, AIConfig.PlantIdentification.MODEL_FILENAME)
        val options = Interpreter.Options().apply {
            setNumThreads(AIConfig.TFLITE_NUM_THREADS)
            setAllowBufferHandleOutput(false)
        }
        return Interpreter(modelBuffer, options)
    }

    /**
     * Load the model file from assets.
     */
    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        return try {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plant identification model file: $modelName", e)
            throw Exception("Plant identification model file not found or corrupted: $modelName", e)
        }
    }

    /**
     * Inspect a tensor and extract its specifications.
     */
    private fun inspectTensor(tensor: Tensor, label: String): TensorSpec {
        val dataType = tensor.dataType()
        val shape = tensor.shape()
        val quantParams = tensor.quantizationParams()
        val scale = quantParams?.scale ?: 1f
        val zeroPoint = quantParams?.zeroPoint ?: 0

        Log.i(
            TAG,
            "Plant identification $label tensor — dtype=$dataType shape=${shape.contentToString()} " +
                "scale=$scale zeroPoint=$zeroPoint"
        )

        return TensorSpec(
            dataType = dataType,
            shape = shape,
            scale = scale,
            zeroPoint = zeroPoint
        )
    }

    /**
     * Dequantize uint8/int8 output to float probabilities.
     */
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

    /**
     * Allocate input buffer based on data type.
     */
    private fun allocateInputBuffer(dataType: DataType): ByteBuffer {
        val elementCount = AIConfig.PlantIdentification.INPUT_WIDTH * 
                          AIConfig.PlantIdentification.INPUT_HEIGHT * 
                          AIConfig.PlantIdentification.CHANNELS
        val capacity = when (dataType) {
            DataType.FLOAT32 -> elementCount * 4
            DataType.UINT8, DataType.INT8 -> elementCount
            else -> throw IllegalStateException("Unsupported input dtype: $dataType")
        }
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
    }

    /**
     * Fill input buffer with bitmap pixel data normalized to [0.0f, 1.0f].
     * Note: plantidentification.tflite's internal tfl.quantize operator uses
     * scale = 1/255 (0.0039215688) and zero_point = -128, which maps [0.0f, 1.0f]
     * to [-128, 127]. Passing [-1.0f, 1.0f] would clamp all negative values (< 128) to -128.
     */
    private fun fillInputBuffer(bitmap: Bitmap, buffer: ByteBuffer, inputSpec: TensorSpec) {
        val pixels = IntArray(
            AIConfig.PlantIdentification.INPUT_WIDTH * AIConfig.PlantIdentification.INPUT_HEIGHT
        )
        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )

        val NORMALIZATION_FACTOR = 255.0f
        val NORMALIZATION_OFFSET = 0.0f

        for (pixel in pixels) {
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF

            when (inputSpec.dataType) {
                DataType.FLOAT32 -> {
                    buffer.putFloat(r / NORMALIZATION_FACTOR - NORMALIZATION_OFFSET)
                    buffer.putFloat(g / NORMALIZATION_FACTOR - NORMALIZATION_OFFSET)
                    buffer.putFloat(b / NORMALIZATION_FACTOR - NORMALIZATION_OFFSET)
                }
                DataType.UINT8, DataType.INT8 -> {
                    buffer.put(quantizeChannel(r, inputSpec, NORMALIZATION_FACTOR, NORMALIZATION_OFFSET))
                    buffer.put(quantizeChannel(g, inputSpec, NORMALIZATION_FACTOR, NORMALIZATION_OFFSET))
                    buffer.put(quantizeChannel(b, inputSpec, NORMALIZATION_FACTOR, NORMALIZATION_OFFSET))
                }
                else -> throw IllegalStateException("Unsupported input dtype: ${inputSpec.dataType}")
            }
        }
    }

    /**
     * Quantize a single color channel.
     */
    private fun quantizeChannel(
        pixelValue: Int, 
        spec: TensorSpec, 
        normalizationFactor: Float, 
        normalizationOffset: Float
    ): Byte {
        val realValue = pixelValue / normalizationFactor - normalizationOffset
        val quantized = (realValue / spec.scale + spec.zeroPoint).toInt()
        return when (spec.dataType) {
            DataType.UINT8 -> quantized.coerceIn(0, 255).toByte()
            DataType.INT8 -> quantized.coerceIn(-128, 127).toByte()
            else -> quantized.toByte()
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing plant identification interpreter", e)
        }
    }

    companion object {
        private const val TAG = "PlantIdentifier"
    }
}
