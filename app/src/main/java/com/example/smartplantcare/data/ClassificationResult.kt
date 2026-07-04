package com.example.smartplantcare.data

import com.example.smartplantcare.data.PredictionResult.PredictionResult

/**
 * PERMANENT THRESHOLD LOGIC
 *
 * Outcome of post-inference confidence and margin checks in [com.example.smartplantcare.ML.LeafClassifier].
 */
sealed class ClassificationResult {

    abstract val top1Index: Int
    abstract val top1ClassName: String
    abstract val top1Confidence: Float
    abstract val top2ClassName: String
    abstract val top2Confidence: Float
    abstract val margin: Float

    data class Accepted(
        val prediction: PredictionResult,
        override val top1Index: Int,
        override val top1ClassName: String,
        override val top1Confidence: Float,
        override val top2ClassName: String,
        override val top2Confidence: Float,
        override val margin: Float
    ) : ClassificationResult()

    data class Rejected(
        val reason: RejectedReason,
        override val top1Index: Int,
        override val top1ClassName: String,
        override val top1Confidence: Float,
        override val top2ClassName: String,
        override val top2Confidence: Float,
        override val margin: Float
    ) : ClassificationResult()
}

enum class RejectedReason {
    LowConfidence,
    LowMargin,
    UnknownLeaf
}

object ClassificationMessages {
    // PERMANENT THRESHOLD LOGIC
    const val LOW_CONFIDENCE =
        "Image is too blurry or unclear. Please capture the leaf again."
    const val LOW_MARGIN =
        "This leaf could not be confidently distinguished from similar classes."
    const val UNKNOWN_LEAF =
        "Uncertain — not a recognized leaf or image quality too low"

    fun messageFor(reason: RejectedReason): String = when (reason) {
        RejectedReason.LowConfidence -> LOW_CONFIDENCE
        RejectedReason.LowMargin -> LOW_MARGIN
        RejectedReason.UnknownLeaf -> UNKNOWN_LEAF
    }
}
