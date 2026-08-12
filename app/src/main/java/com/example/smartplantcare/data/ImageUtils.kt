package com.example.smartplantcare.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    private const val IMAGE_DIR = "plant_images"
    private const val FILENAME_PREFIX = "plant_"
    private const val FILENAME_SUFFIX = ".jpg"
    private const val JPEG_QUALITY = 90

    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, IMAGE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val filename = FILENAME_PREFIX + System.currentTimeMillis().toString() + FILENAME_SUFFIX
        val file = File(dir, filename)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            outputStream.flush()
        }
        return file.absolutePath
    }

    fun loadBitmapFromPath(filePath: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteImage(filePath: String): Boolean {
        return try {
            File(filePath).delete()
        } catch (e: Exception) {
            false
        }
    }

    fun loadResizedBitmap(context: Context, imagePathOrUri: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (imagePathOrUri.isBlank()) return null
        return try {
            val resolver = context.contentResolver
            val isUri = imagePathOrUri.startsWith("content://") || imagePathOrUri.startsWith("file://")

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            if (isUri) {
                val uri = Uri.parse(imagePathOrUri)
                resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            } else {
                BitmapFactory.decodeFile(imagePathOrUri, options)
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            if (isUri) {
                val uri = Uri.parse(imagePathOrUri)
                resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            } else {
                BitmapFactory.decodeFile(imagePathOrUri, options)
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Error loading resized bitmap: $imagePathOrUri", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }



}
