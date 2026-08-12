package com.example.smartplantcare.plantnetnetwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class PlantNetRepository(
    private val apiKey: String,
    private val project: String = "all" // pwede palitan kung gagamit ka ng specific flora
) {

    private val api = PlantNetClient.apiService


    suspend fun identifyPlant(
        imageFile: File,
        organ: String = "auto"
    ): PlantNetOutcome {
        // Compress image to max 2048px and 85% quality for faster upload and better API acceptance
        val compressed = compressImage(imageFile) ?: imageFile
        return identifyPlant(listOf(compressed), listOf(organ))
    }


    suspend fun identifyPlant(
        imageFiles: List<File>,
        organs: List<String>
    ): PlantNetOutcome = withContext(Dispatchers.IO) {
        if (imageFiles.isEmpty()) {
            return@withContext PlantNetOutcome.UnknownError("Walang image na ipinasa sa pipeline")
        }
        if (imageFiles.size != organs.size) {
            return@withContext PlantNetOutcome.UnknownError(
                "Bilang ng images (${imageFiles.size}) ay hindi tumutugma sa bilang ng organs (${organs.size})"
            )
        }

        try {
            val imageParts = imageFiles.map { file -> fileToMultipart(file) }
            val organParts = organs.map { organ -> organToMultipart(organ) }

            val response = api.identifyPlant(
                project = project,
                apiKey = apiKey,
                includeRelatedImages = true,
                images = imageParts,
                organs = organParts
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.results.isNullOrEmpty()) {
                    PlantNetOutcome.NoResultsError("Walang nakilalang species sa larawan. Subukan ulit nang mas malinaw na anggulo.")
                } else {
                    mapToOutcome(body!!)
                }
            } else {
                // Capture error body safely BEFORE the logging interceptor consumes it
                val errorBody = response.errorBody()
                val errorMsg = try {
                    errorBody?.string() ?: "No error details"
                } catch (e: Exception) {
                    "No error details (consumed)"
                }
                PlantNetOutcome.ApiError(response.code(), errorMsg)
            }
        } catch (e: IOException) {
            PlantNetOutcome.NetworkError(e.message ?: "Network connection failed")
        } catch (e: Exception) {
            PlantNetOutcome.UnknownError(e.message ?: "Unexpected error during identification")
        }
    }



    private fun mapToOutcome(response: PlantNetResponse): PlantNetOutcome {
        val summaries = response.results!!.map { result -> toSummary(result) }
        return PlantNetOutcome.Success(
            topMatch = summaries.first(),
            allResults = summaries,
            remainingRequests = response.remainingIdentificationRequests
        )
    }


    private fun toSummary(result: PlantNetResult): PlantIdentificationSummary {
        val species = result.species

        val plantName = species.commonNames?.firstOrNull()
            ?: species.scientificNameWithoutAuthor
            ?: "Unknown plant"

        val scientificName = species.scientificName
            ?: species.scientificNameWithoutAuthor
            ?: "Unknown"

        val speciesOnly = species.scientificNameWithoutAuthor ?: "Unknown species"

        val family = species.family?.scientificNameWithoutAuthor ?: "Unknown family"

        val genus = species.genus?.scientificNameWithoutAuthor ?: ""

        val confidencePercent = (result.score * 100).let { "%.1f".format(it) }


        val description = buildString {
            append(plantName)
            if (species.commonNames?.isNotEmpty() == true) {
                append(" (scientific name: $scientificName)")
            }
            append(" belongs to the $family family")
            if (genus.isNotEmpty()) append(", genus $genus")
            append(". Identification confidence: $confidencePercent%.")
            if (species.commonNames != null && species.commonNames.size > 1) {
                append(" Also known as: ${species.commonNames.drop(1).joinToString(", ")}.")
            }
        }

        val relatedImages = result.images
            ?.mapNotNull { it.url?.original ?: it.url?.medium }
            ?: emptyList()

        return PlantIdentificationSummary(
            plantName = plantName,
            scientificName = scientificName,
            species = speciesOnly,
            family = family,
            description = description,
            relatedImageUrls = relatedImages,
            confidenceScore = result.score
        )
    }



    /**
     * Compress and resize image to max 1024px on longest side, 75% JPEG quality.
     * This reduces upload size significantly and improves API acceptance rates.
     */
    private fun compressImage(file: File): File? {
        return try {
            android.util.Log.d("PlantNetRepo", "Starting compression for ${file.name}, original size: ${file.length()} bytes")
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)

            val maxDimension = 1024  // Reduced from 2048 for faster upload
            var scale = 1
            while (opts.outWidth / scale > maxDimension || opts.outHeight / scale > maxDimension) {
                scale *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            val outputFile = File(file.parent, "comp_${System.currentTimeMillis()}_${file.name}")
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)  // Reduced from 85 for smaller size
                FileOutputStream(outputFile).use { fos ->
                    fos.write(baos.toByteArray())
                }
            }

            bitmap.recycle()

            android.util.Log.d("PlantNetRepo", "Compressed ${file.name} (${opts.outWidth}x${opts.outHeight}, ${file.length()} bytes) -> ${outputFile.length()} bytes")
            outputFile
        } catch (e: Exception) {
            android.util.Log.e("PlantNetRepo", "Failed to compress image", e)
            null // Return null to fall back to original file
        }
    }

    private fun fileToMultipart(file: File): MultipartBody.Part {
        val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("images", file.name, requestBody)
    }

    private fun organToMultipart(organ: String): MultipartBody.Part {
        val requestBody = organ.toRequestBodyPlain()
        return MultipartBody.Part.createFormData("organs", null, requestBody)
    }

    private fun String.toRequestBodyPlain() =
        okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), this)
}