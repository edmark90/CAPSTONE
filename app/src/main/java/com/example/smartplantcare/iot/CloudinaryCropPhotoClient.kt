package com.example.smartplantcare.iot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.smartplantcare.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

data class CloudinaryUploadResult(
    val secureUrl: String,
    val publicId: String
)

/**
 * Lightweight, 100% Free-Tier Cloudinary Signed REST Client (upload + permanent destroy).
 * Uses OkHttp + SHA-1 signing with credentials sourced from `local.properties` -> `BuildConfig`.
 */
object CloudinaryCropPhotoClient {

    private const val TAG = "CloudinaryCropPhoto"
    private const val MAX_EDGE_PX = 720
    private const val JPEG_QUALITY = 84

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun isConfigured(): Boolean {
        return BuildConfig.CLOUDINARY_CLOUD_NAME.isNotBlank() &&
            BuildConfig.CLOUDINARY_API_KEY.isNotBlank() &&
            BuildConfig.CLOUDINARY_API_SECRET.isNotBlank()
    }

    /**
     * Generates a Cloudinary SHA-1 request signature over lexicographically sorted parameters.
     */
    internal fun computeSignature(params: Map<String, String>, apiSecret: String): String {
        val canonical = params.entries
            .filter { it.value.isNotBlank() }
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        val payload = "$canonical$apiSecret"
        val digest = MessageDigest.getInstance("SHA-1").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun sanitizeSegment(raw: String): String {
        return raw.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    }

    suspend fun uploadCropPhoto(
        context: Context,
        deviceId: String,
        cropSlug: String,
        sourceUri: Uri
    ): Result<CloudinaryUploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
            val apiKey = BuildConfig.CLOUDINARY_API_KEY.trim()
            val apiSecret = BuildConfig.CLOUDINARY_API_SECRET.trim()
            require(cloudName.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()) {
                "Cloudinary credentials are not configured."
            }

            val jpegBytes = compressUriToJpegBytes(context, sourceUri)
            val timestampSec = (System.currentTimeMillis() / 1000L).toString()
            val safeDev = sanitizeSegment(deviceId.ifBlank { "default" })
            val safeSlug = sanitizeSegment(cropSlug.ifBlank { "crop" })
            val publicId = "smartplantcare/$safeDev/${safeSlug}_${System.currentTimeMillis()}"

            val signParams = mapOf(
                "public_id" to publicId,
                "timestamp" to timestampSec
            )
            val signature = computeSignature(signParams, apiSecret)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "${safeSlug}.jpg",
                    jpegBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart("public_id", publicId)
                .addFormDataPart("timestamp", timestampSec)
                .addFormDataPart("api_key", apiKey)
                .addFormDataPart("signature", signature)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(multipartBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    error(errMsg.ifBlank { "Cloudinary upload failed (HTTP ${response.code})." })
                }
                val json = JSONObject(rawBody)
                val secureUrl = json.optString("secure_url").trim()
                val returnedPublicId = json.optString("public_id").trim().ifBlank { publicId }
                require(secureUrl.startsWith("https://res.cloudinary.com/")) {
                    "Unexpected Cloudinary URL returned."
                }
                CloudinaryUploadResult(
                    secureUrl = secureUrl,
                    publicId = returnedPublicId
                )
            }
        }
    }

    suspend fun deleteCropPhoto(publicId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedId = publicId.trim()
            if (trimmedId.isBlank()) return@runCatching
            val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
            val apiKey = BuildConfig.CLOUDINARY_API_KEY.trim()
            val apiSecret = BuildConfig.CLOUDINARY_API_SECRET.trim()
            require(cloudName.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()) {
                "Cloudinary credentials are not configured."
            }

            val timestampSec = (System.currentTimeMillis() / 1000L).toString()
            val signParams = mapOf(
                "invalidate" to "true",
                "public_id" to trimmedId,
                "timestamp" to timestampSec
            )
            val signature = computeSignature(signParams, apiSecret)

            val formBody = FormBody.Builder()
                .add("public_id", trimmedId)
                .add("invalidate", "true")
                .add("timestamp", timestampSec)
                .add("api_key", apiKey)
                .add("signature", signature)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/destroy")
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    error(errMsg.ifBlank { "Cloudinary delete failed (HTTP ${response.code})." })
                }
                val resultStr = runCatching { JSONObject(rawBody).optString("result") }.getOrDefault("")
                if (resultStr != "ok" && resultStr != "not found") {
                    Log.w(TAG, "Unexpected Cloudinary destroy result for $trimmedId: $rawBody")
                }
            }
        }
    }

    private fun compressUriToJpegBytes(context: Context, sourceUri: Uri): ByteArray {
        val resolver = context.contentResolver

        // Prefer ImageDecoder on Android 9+ (handles HEIC/WebP/EXIF orientation natively in a single pass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedViaImageDecoder = runCatching {
                val source = ImageDecoder.createSource(resolver, sourceUri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val maxDim = max(w, h)
                    if (maxDim > MAX_EDGE_PX) {
                        val scale = MAX_EDGE_PX.toFloat() / maxDim.toFloat()
                        val dstW = (w * scale).roundToInt().coerceAtLeast(1)
                        val dstH = (h * scale).roundToInt().coerceAtLeast(1)
                        decoder.setTargetSize(dstW, dstH)
                    }
                }
            }.getOrNull()

            if (decodedViaImageDecoder != null) {
                val outStream = ByteArrayOutputStream()
                try {
                    if (decodedViaImageDecoder.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outStream)) {
                        return outStream.toByteArray()
                    }
                } finally {
                    decodedViaImageDecoder.recycle()
                }
            }
        }

        // Single-open fallback for Android 7-8 or providers where ImageDecoder fails
        val rawBytes = resolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: error("Could not open selected image.")
        if (rawBytes.isEmpty()) {
            error("Selected image file is empty.")
        }

        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)

        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) {
            error("Selected file is not a valid image.")
        }

        var sampleSize = 1
        while ((srcWidth / sampleSize) > MAX_EDGE_PX * 2 || (srcHeight / sampleSize) > MAX_EDGE_PX * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: error("Failed to decode selected image.")

        val rotated = applyExifOrientation(decoded, orientation)
        val maxDim = max(rotated.width, rotated.height)
        val finalBitmap = if (maxDim > MAX_EDGE_PX) {
            val scale = MAX_EDGE_PX.toFloat() / maxDim.toFloat()
            val dstW = (rotated.width * scale).roundToInt().coerceAtLeast(1)
            val dstH = (rotated.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(rotated, dstW, dstH, true).also {
                if (it !== rotated) rotated.recycle()
            }
        } else {
            rotated
        }

        val outStream = ByteArrayOutputStream()
        try {
            if (!finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outStream)) {
                error("Failed to compress crop photo.")
            }
            return outStream.toByteArray()
        } finally {
            finalBitmap.recycle()
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }.getOrDefault(bitmap)
    }
}
