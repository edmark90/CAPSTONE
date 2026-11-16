package com.example.smartplantcare

import com.example.smartplantcare.ML.AIConfig
import com.example.smartplantcare.ML.DiseaseFilter
import com.example.smartplantcare.ML.PlantType
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun plantThreshold_isAboveThreeClassUniformChance() {
        // For a 3-class softmax (Chili, Eggplant, Tomato), max probability is always >= 1/3 (~0.333f).
        // PLANT_THRESHOLD must be strictly greater than 1/3 to reject unknown/non-plant inputs.
        assertTrue(AIConfig.PLANT_THRESHOLD > 0.34f)
    }

    @Test
    fun diseaseFilter_filtersAndRenormalizesProbabilities() {
        val rawProbs = FloatArray(22) { 0f }
        // Suppose Chili (index 0) got 0.40, Tomato Early Blight (index 12) got 0.30, Tomato Healthy (index 15) got 0.20, Eggplant (index 6) got 0.10
        rawProbs[0] = 0.40f
        rawProbs[6] = 0.10f
        rawProbs[12] = 0.30f
        rawProbs[15] = 0.20f

        val filteredTomato = DiseaseFilter.filterProbabilities(rawProbs, PlantType.TOMATO)

        // Non-Tomato classes must be 0f
        assertEquals(0.0f, filteredTomato[0], 1e-6f)
        assertEquals(0.0f, filteredTomato[6], 1e-6f)

        // Tomato classes (12 and 15) must be renormalized to sum to 1.0f (0.30 / 0.50 = 0.60, 0.20 / 0.50 = 0.40)
        assertEquals(0.60f, filteredTomato[12], 1e-5f)
        assertEquals(0.40f, filteredTomato[15], 1e-5f)
        assertEquals(1.0f, filteredTomato.sum(), 1e-5f)
    }
}