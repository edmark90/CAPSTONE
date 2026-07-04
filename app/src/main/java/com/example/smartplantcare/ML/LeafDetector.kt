package com.example.smartplantcare.ML

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * LeafDetector
 * 
 * PURPOSE: Detect if a plant leaf is present in the captured image.
 * 
 * WHY IT IMPROVES CONFIDENCE:
 * - Prevents inference on non-leaf images (background, hands, etc.)
 * - Ensures model receives only relevant plant data
 * - Reduces false positives and low-confidence predictions
 * 
 * PERFORMANCE IMPACT:
 * - CPU: Low (~10-15ms per detection)
 * - Memory: Minimal (uses existing bitmap pixels)
 * - Suitable for TensorFlow Lite: Yes, runs before model inference
 */
object LeafDetector {

    data class DetectionResult(
        val isLeafDetected: Boolean,
        val message: String,
        val greenCoverage: Float = 0f
    )

    /**
     * Minimum green pixel percentage required to consider image as containing a leaf
     * 15% is conservative threshold - leaves typically have significant green coverage
     */
    private const val MIN_GREEN_COVERAGE = 0.15f

    /**
     * HSV color ranges for green detection
     * Green in HSV: Hue 35-85, Saturation > 30%, Value > 20%
     */
    private const val HUE_MIN = 35
    private const val HUE_MAX = 85
    private const val SATURATION_MIN = 30
    private const val VALUE_MIN = 20

    /**
     * Detects if a plant leaf is present in the image
     * 
     * ALGORITHM: Color-based detection using HSV color space
     * 1. Convert RGB to HSV
     * 2. Count pixels within green hue range
     * 3. Calculate green coverage percentage
     * 4. Compare against threshold
     * 
     * WHY HSV: Better color separation than RGB
     * - Hue separates color types (green vs red vs blue)
     * - Saturation separates vivid from dull colors
     * - Value separates brightness
     * 
     * @param bitmap Image to analyze
     * @return DetectionResult with leaf presence status
     */
    fun detectLeaf(bitmap: Bitmap): DetectionResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var greenPixelCount = 0
        val step = 2 // Sample every 2nd pixel for performance
        var totalSampled = 0

        for (i in pixels.indices step step) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            if (isGreenPixel(r, g, b)) {
                greenPixelCount++
            }
            totalSampled++
        }

        val greenCoverage = greenPixelCount.toFloat() / totalSampled

        return if (greenCoverage >= MIN_GREEN_COVERAGE) {
            DetectionResult(
                isLeafDetected = true,
                message = "Leaf detected successfully.",
                greenCoverage = greenCoverage
            )
        } else {
            DetectionResult(
                isLeafDetected = false,
                message = "No leaf detected. Please capture a plant leaf.",
                greenCoverage = greenCoverage
            )
        }
    }

    /**
     * Determines if a pixel is green using HSV color space
     * 
     * ALGORITHM:
     * 1. Convert RGB to HSV
     * 2. Check if hue is in green range (35-85)
     * 3. Check if saturation is sufficient (>30%)
     * 4. Check if value is sufficient (>20%)
     * 
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return True if pixel is green
     */
    private fun isGreenPixel(r: Int, g: Int, b: Int): Boolean {
        val hsv = rgbToHsv(r, g, b)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        return (hue >= HUE_MIN.toFloat() && hue <= HUE_MAX.toFloat()) &&
               (saturation >= SATURATION_MIN.toFloat()) &&
               (value >= VALUE_MIN.toFloat())
    }

    /**
     * Converts RGB to HSV color space
     * 
     * FORMULA:
     * H = 0-360 degrees
     * S = 0-100 percentage
     * V = 0-100 percentage
     * 
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return FloatArray [hue, saturation, value]
     */
    private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min

        val hue: Float
        val saturation: Float
        val value: Float

        // Calculate hue
        hue = when {
            delta == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / delta) % 6f)
            max == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }

        // Ensure hue is positive
        val normalizedHue = if (hue < 0) hue + 360f else hue

        // Calculate saturation
        saturation = if (max == 0f) 0f else (delta / max) * 100f

        // Calculate value
        value = max * 100f

        return floatArrayOf(normalizedHue, saturation, value)
    }
}
