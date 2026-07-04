package com.example.smartplantcare.ML

import android.graphics.Bitmap
import android.graphics.Color

object LeafValidator {

    data class ValidationResult(val isValid: Boolean, val message: String)

    fun validate(bitmap: Bitmap): ValidationResult {
        if (bitmap.width == 0 || bitmap.height == 0) {
            return ValidationResult(false, "Invalid image: Dimensions are zero.")
        }

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalLuminance = 0.0
        val step = 6
        var sampleCount = 0

        for (i in pixels.indices step step) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            totalLuminance += 0.299 * r + 0.587 * g + 0.114 * b
            sampleCount++
        }

        val avgLuminance = totalLuminance / sampleCount

        // More lenient luminance thresholds to allow more valid images
        return when {
            avgLuminance < 15.0 -> ValidationResult(
                false,
                "Image is too dark. Move to a brighter area or turn on more light."
            )
            avgLuminance > 250.0 -> ValidationResult(
                false,
                "Image is overexposed. Avoid direct glare and retake the photo."
            )
            else -> ValidationResult(true, "Image is valid for analysis.")
        }
    }
}
