package com.example.smartplantcare.ML

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import org.tensorflow.lite.DataType
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    private const val TAG = "ImageProcessor"
    
    // Image processing constants
    private const val RGB_MAX_VALUE = 255
    private const val MOBILENET_NORMALIZATION_FACTOR = 127.5f
    private const val MOBILENET_NORMALIZATION_OFFSET = 1.0f

    /**
     * Decode a gallery URI and apply EXIF orientation so the bitmap is upright.
     * Note: On Android 9+ (API 28+), ImageDecoder automatically applies EXIF orientation,
     * so manual rotation is only needed on API < 28 to avoid double-rotating the image.
     */
    @Throws(Exception::class)
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return try {
            val decoded = decodeBitmap(context, uri)
            val result = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val rotation = readExifRotationDegrees(context, uri)
                orientBitmap(decoded, rotation)
            } else {
                decoded
            }
            // Don't recycle - let GC handle it to prevent "recycled bitmap" crashes
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI", e)
            throw Exception("Failed to load image: ${e.message}", e)
        }
    }

    /**
     * Decode an image file from disk and apply EXIF orientation so the bitmap is upright.
     */
    fun loadBitmapFromFile(file: File): Bitmap? {
        return try {
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val rotation = readExifRotationDegrees(file.absolutePath)
            orientBitmap(decoded, rotation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from file: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Shared preprocessing for camera and gallery — matches Python training:
     * rotate → center-crop square → ARGB_8888 → resize 224×224 → MobileNetV2 normalize/quantize.
     */
    fun buildModelInput(
        source: Bitmap,
        rotationDegrees: Int,
        inputSpec: LeafClassifier.TensorSpec
    ): ByteBuffer {
        try {
            android.util.Log.d(TAG, "Building model input from ${source.width}x${source.height} bitmap")
            
            var working = orientBitmap(source, rotationDegrees)
            working = centerCropSquare(working)

            // Training uses RGB 3-channel images; Android Bitmaps are ARGB — drop alpha.
            working = ensureArgb8888(working, recycleSource = false)

            val resized = Bitmap.createScaledBitmap(
                working,
                ModelConfig.INPUT_WIDTH,
                ModelConfig.INPUT_HEIGHT,
                true
            )
            // Don't recycle - let GC handle it to prevent "recycled bitmap" crashes

            val buffer = allocateInputBuffer(inputSpec.dataType)
            fillInputBuffer(resized, buffer, inputSpec)
            // Don't recycle - let GC handle it to prevent "recycled bitmap" crashes

            buffer.rewind()

            Log.d(
                TAG,
                "input dtype=${inputSpec.dataType} shape=${inputSpec.shape.contentToString()} " +
                    "scale=${inputSpec.scale} zeroPoint=${inputSpec.zeroPoint}"
            )

            return buffer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build model input", e)
            e.printStackTrace()
            throw RuntimeException("Failed to build model input: ${e.message}", e)
        }
    }

    /** Apply rotation so pixel layout matches an upright photo (camera sensor / EXIF). */
    fun orientBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        if (normalizedRotation == 0) {
            return source
        }
        val matrix = Matrix().apply { postRotate(normalizedRotation.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Center-crop a rectangular bitmap to a 1:1 square so the leaf aspect ratio is preserved
     * and surrounding background outside the square viewfinder is excluded before 224x224 scaling.
     */
    fun centerCropSquare(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width == height || width <= 0 || height <= 0) {
            return source
        }
        val size = min(width, height)
        val xOffset = (width - size) / 2
        val yOffset = (height - size) / 2
        return Bitmap.createBitmap(source, xOffset, yOffset, size, size)
    }

    fun readExifRotationDegrees(filePath: String): Int {
        return try {
            exifRotationFromInterface(ExifInterface(filePath))
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF orientation from file: $filePath", e)
            0
        }
    }

    fun ensureArgb8888(source: Bitmap, recycleSource: Boolean): Bitmap {
        return if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true).also {
                if (recycleSource) source.recycle()
            }
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from URI", e)
            throw Exception("Failed to decode image: ${e.message}", e)
        }
    }

    private fun readExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) 0 else exifRotationFromStream(stream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF orientation", e)
            0
        }
    }

    private fun exifRotationFromStream(stream: InputStream): Int {
        return exifRotationFromInterface(ExifInterface(stream))
    }

    private fun exifRotationFromInterface(exif: ExifInterface): Int {
        return when (
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }

    private fun allocateInputBuffer(dataType: DataType): ByteBuffer {
        val elementCount = ModelConfig.INPUT_WIDTH * ModelConfig.INPUT_HEIGHT * ModelConfig.CHANNELS
        val capacity = when (dataType) {
            DataType.FLOAT32 -> elementCount * 4
            DataType.UINT8, DataType.INT8 -> elementCount
            else -> throw IllegalStateException("Unsupported input dtype: $dataType")
        }
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
    }

    private fun fillInputBuffer(bitmap: Bitmap, buffer: ByteBuffer, inputSpec: LeafClassifier.TensorSpec) {
        val pixels = IntArray(ModelConfig.INPUT_WIDTH * ModelConfig.INPUT_HEIGHT)
        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )

        for (pixel in pixels) {
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF

            // tf.keras.applications.mobilenet_v2.preprocess_input: (pixel / 127.5) - 1.0 → [-1, 1]
            when (inputSpec.dataType) {
                DataType.FLOAT32 -> {
                    buffer.putFloat(r / MOBILENET_NORMALIZATION_FACTOR - MOBILENET_NORMALIZATION_OFFSET)
                    buffer.putFloat(g / MOBILENET_NORMALIZATION_FACTOR - MOBILENET_NORMALIZATION_OFFSET)
                    buffer.putFloat(b / MOBILENET_NORMALIZATION_FACTOR - MOBILENET_NORMALIZATION_OFFSET)
                }
                DataType.UINT8, DataType.INT8 -> {
                    buffer.put(quantizeChannel(r, inputSpec))
                    buffer.put(quantizeChannel(g, inputSpec))
                    buffer.put(quantizeChannel(b, inputSpec))
                }
                else -> throw IllegalStateException("Unsupported input dtype: ${inputSpec.dataType}")
            }
        }
    }

    private fun quantizeChannel(pixelValue: Int, spec: LeafClassifier.TensorSpec): Byte {
        val realValue = pixelValue / MOBILENET_NORMALIZATION_FACTOR - MOBILENET_NORMALIZATION_OFFSET
        val quantized = (realValue / spec.scale + spec.zeroPoint).roundToInt()
        return when (spec.dataType) {
            DataType.UINT8 -> quantized.coerceIn(0, RGB_MAX_VALUE).toByte()
            DataType.INT8 -> quantized.coerceIn(-128, 127).toByte()
            else -> quantized.toByte()
        }
    }
}
