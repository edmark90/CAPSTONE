package com.example.smartplantcare.calibration

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * TEMPORARY CALIBRATION CODE
 *
 * Append-only CSV logger for on-device threshold calibration.
 * Disk I/O runs on [Dispatchers.IO] and never blocks inference or the UI thread.
 */
object CalibrationLogger {

    private const val LOG_FILENAME = "calibration_log.csv"
    private const val CSV_HEADER =
        "timestamp,className,confidence,top2Class,top2Confidence,margin,markedCorrect"

    @RequiresApi(Build.VERSION_CODES.O)
    private val timestampFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getLogFile(context: Context): File {
        val dir = context.applicationContext.getExternalFilesDir(null)
            ?: context.applicationContext.filesDir
        return File(dir, LOG_FILENAME)
    }

    fun log(
        context: Context,
        className: String,
        confidence: Float,
        top2Class: String,
        top2Confidence: Float,
        markedCorrect: Boolean
    ) {
        val appContext = context.applicationContext
        ioScope.launch {
            appendRow(
                file = getLogFile(appContext),
                className = className,
                confidence = confidence,
                top2Class = top2Class,
                top2Confidence = top2Confidence,
                markedCorrect = markedCorrect
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun appendRow(
        file: File,
        className: String,
        confidence: Float,
        top2Class: String,
        top2Confidence: Float,
        markedCorrect: Boolean
    ) {
        val margin = confidence - top2Confidence
        val timestamp = timestampFormatter.format(Instant.now())
        val row = listOf(
            timestamp,
            className.escapeCsv(),
            confidence.formatCsv(),
            top2Class.escapeCsv(),
            top2Confidence.formatCsv(),
            margin.formatCsv(),
            markedCorrect.toString().uppercase()
        ).joinToString(",")

        synchronized(this) {
            val writeHeader = !file.exists() || file.length() == 0L
            file.parentFile?.mkdirs()

            BufferedWriter(
                OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8)
            ).use { writer ->
                if (writeHeader) {
                    writer.write(CSV_HEADER)
                    writer.newLine()
                }
                writer.write(row)
                writer.newLine()
            }
        }
    }

    private fun String.escapeCsv(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
    }

    private fun Float.formatCsv(): String = "%.6f".format(this)
}
