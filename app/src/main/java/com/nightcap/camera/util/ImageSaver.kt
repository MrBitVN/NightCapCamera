package com.nightcap.camera.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ImageSaver {
    private const val TAG = "ImageSaver"

    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, modeName: String): Uri? {
        return withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NIGHTCAP_${modeName.uppercase()}_$timestamp.jpg"

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NightCap")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                    uri?.let {
                        resolver.openOutputStream(it)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out)
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    uri
                } else {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val nightCapDir = File(picturesDir, "NightCap").also { if (!it.exists()) it.mkdirs() }
                    val file = File(nightCapDir, filename)

                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out)
                    }

                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, file.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    }
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving photo", e)
                null
            }
        }
    }

    suspend fun saveVideoToGallery(context: Context, videoFile: File): Uri? {
        return withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "NIGHTCAP_VIDEO_$timestamp.mp4"

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NightCap")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                    uri?.let {
                        resolver.openOutputStream(it)?.use { out ->
                            videoFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    videoFile.delete()
                    uri
                } else {
                    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    val nightCapDir = File(moviesDir, "NightCap").also { if (!it.exists()) it.mkdirs() }
                    val targetFile = File(nightCapDir, filename)
                    videoFile.copyTo(targetFile, overwrite = true)
                    videoFile.delete()

                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    }
                    context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving video", e)
                null
            }
        }
    }
}
