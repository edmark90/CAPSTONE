package com.example.smartplantcare.ML

import android.content.Context
import android.graphics.Bitmap
import com.example.smartplantcare.data.ClassificationResult
import com.example.smartplantcare.data.PredictionResult.PredictionResult

/** Thin wrapper around [LeafClassifier] for legacy call sites. */
class Predictor(context: Context) {

    private val classifier = LeafClassifier(context)

    sealed class PredictionOutcome {
        data class Success(
            val result: PredictionResult,
            val classification: ClassificationResult.Accepted
        ) : PredictionOutcome()

        data class Failure(
            val message: String,
            val classification: ClassificationResult? = null
        ) : PredictionOutcome()
    }

    fun predict(bitmap: Bitmap, rotationDegrees: Int = 0): PredictionOutcome {
        return toOutcome(classifier.classify(bitmap, rotationDegrees))
    }

    fun predictPrepared(preparedBitmap: Bitmap, recyclePrepared: Boolean = false): PredictionOutcome {
        return try {
            toOutcome(classifier.classify(preparedBitmap, rotationDegrees = 0))
        } finally {
            if (recyclePrepared) {
                preparedBitmap.recycle()
            }
        }
    }

    private fun toOutcome(result: LeafClassifier.Result): PredictionOutcome {
        return when (val classification = result.classification) {
            is ClassificationResult.Accepted -> PredictionOutcome.Success(
                result = classification.prediction,
                classification = classification
            )
            is ClassificationResult.Rejected -> PredictionOutcome.Failure(
                message = result.message,
                classification = classification
            )
        }
    }

    fun cleanup() {
        classifier.cleanup()
    }
}
