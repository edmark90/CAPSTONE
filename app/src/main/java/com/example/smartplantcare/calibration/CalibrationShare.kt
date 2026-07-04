package com.example.smartplantcare.calibration

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider

/**
 * TEMPORARY CALIBRATION CODE
 *
 * Shares [CalibrationLogger.getLogFile] via Android share sheet.
 */
object CalibrationShare {

    fun shareLog(context: Context) {
        val logFile = CalibrationLogger.getLogFile(context)
        if (!logFile.exists() || logFile.length() == 0L) {
            Toast.makeText(context, "No calibration data to share yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, logFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SmartPlantCare calibration log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "Share calibration log")
        )
    }
}
