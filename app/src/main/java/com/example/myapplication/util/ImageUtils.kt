package com.example.myapplication.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.ExifInterface
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {

    private const val CAMERA_PERMISSION_CODE = 101

    fun checkAndRequestPermissions(activity: Activity) {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // Check storage permissions based on Android version
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toTypedArray(),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    fun checkCameraPermission(activity: Activity): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
            return false
        }
        return true
    }

    fun enhanceImageQuality(imageFile: File): File {
        // Read image metadata
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)

        // Read bitmap with maximum quality
        val decodingOptions = BitmapFactory.Options().apply {
            inSampleSize = 1 // Don't reduce size
            inPreferredConfig = Bitmap.Config.ARGB_8888 // Maximum color quality
            inMutable = true // Allow bitmap editing
        }

        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, decodingOptions)

        // Handle orientation
        val exif = ExifInterface(imageFile.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

        val rotatedBitmap = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }

        // Enhance sharpness by boosting contrast and sharpness
        val enhancedBitmap = enhanceSharpness(rotatedBitmap)

        // Create temporary file for enhanced image
        val enhancedFile = File(imageFile.absolutePath)
        FileOutputStream(enhancedFile).use { out ->
            enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) // Save with maximum quality
        }

        return enhancedFile
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun enhanceSharpness(bitmap: Bitmap): Bitmap {
        // Only apply with Android N and higher as it needs newer APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // Use ColorMatrix to increase contrast
                val paint = android.graphics.Paint()
                val colorMatrix = android.graphics.ColorMatrix()
                colorMatrix.setSaturation(1.2f) // Increase color saturation

                // Increase contrast
                val enhancer = android.graphics.ColorMatrix()
                enhancer.set(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -10f,  // Red
                    0f, 1.2f, 0f, 0f, -10f,  // Green
                    0f, 0f, 1.2f, 0f, -10f,  // Blue
                    0f, 0f, 0f, 1f, 0f       // Alpha
                ))

                colorMatrix.postConcat(enhancer)

                val filter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                paint.colorFilter = filter

                // Create new bitmap to draw with filter
                val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(resultBitmap)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)

                return resultBitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return bitmap
    }

    fun imageToBitmap(image: android.media.Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}