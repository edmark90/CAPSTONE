package com.example.smartplantcare.ML

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object ModelLoader {
    private var interpreter: Interpreter? = null

    @Throws(Exception::class)
    fun getInterpreter(context: Context): Interpreter {
        if (interpreter == null) {
            try {
                val modelBuffer = loadModelFile(context, ModelConfig.MODEL_FILENAME)
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    // CRITICAL FIX: setAllowBufferHandleOutput is for GPU/delegate acceleration
                    // For CPU float32 inference, this should be false to avoid buffer handling issues
                    setAllowBufferHandleOutput(false)
                }
                interpreter = Interpreter(modelBuffer, options)
            } catch (e: Exception) {
                android.util.Log.e("ModelLoader", "Failed to load model", e)
                throw Exception("Failed to load ML model: ${e.message}", e)
            }
        }
        return interpreter!!
    }

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
            android.util.Log.e("ModelLoader", "Failed to load model file: $modelName", e)
            throw Exception("Model file not found or corrupted: $modelName", e)
        }
    }

    fun close() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            android.util.Log.e("ModelLoader", "Error closing interpreter", e)
        }
        interpreter = null
    }
}