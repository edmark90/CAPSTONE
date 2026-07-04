package com.example.smartplantcare.ML

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LabelLoader {
    /**
     * Reads class_mapping.json from the assets folder and returns a map tying
     * class indices to their string names.
     */
    @Throws(Exception::class)
    fun loadLabels(context: Context): Map<Int, String> {
        val mapping = mutableMapOf<Int, String>()
        val jsonString = try {
            context.assets.open(ModelConfig.LABELS_FILENAME).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e("LabelLoader", "Failed to read labels file", e)
            throw Exception("Failed to load class labels: ${e.message}", e)
        }

        return try {
            // First attempt: Parse as a standard JSON Object (e.g. {"0": "Disease A", "1": "Disease B"})
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                mapping[key.toInt()] = jsonObject.getString(key)
            }
            mapping
        } catch (e: Exception) {
            android.util.Log.w("LabelLoader", "Failed to parse as object, trying array", e)
            try {
                // Fallback: Parse as a JSON Array (e.g. ["Disease A", "Disease B"])
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    mapping[i] = jsonArray.getString(i)
                }
                mapping
            } catch (e2: Exception) {
                android.util.Log.e("LabelLoader", "Failed to parse labels as array", e2)
                throw Exception("Failed to parse class labels file", e2)
            }
        }
    }
}