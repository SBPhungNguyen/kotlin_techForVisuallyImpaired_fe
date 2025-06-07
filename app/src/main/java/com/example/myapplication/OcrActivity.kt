package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.myapplication.util.ImageUtils
import com.example.myapplication.util.NetworkUtils
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class OcrActivity : AppCompatActivity() {
    private val CAMERA_REQUEST_CODE = 100
    private val CAMERA_PERMISSION_CODE = 101

    private var currentPhotoPath: String = ""
    private var imageFile: File? = null
    private lateinit var tvStatus: TextView
    private var mediaPlayer: MediaPlayer? = null

    private val serverUrl = "https://visionassistant-production.up.railway.app/"  // Replace with actual server IP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)

        val btnCapture: Button = findViewById(R.id.btnCapture)
        val btnUpload: Button = findViewById(R.id.btnUpload)
        tvStatus = findViewById(R.id.tvStatus)

        ImageUtils.checkAndRequestPermissions(this)

        btnCapture.setOnClickListener {
            if (ImageUtils.checkCameraPermission(this)) {
                openCamera()
            }
        }

        btnUpload.setOnClickListener {
            if (imageFile != null && imageFile!!.exists()) {
                uploadImage(imageFile!!)
            } else {
                Toast.makeText(this, "Please capture a picture first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Toast.makeText(this, "Cannot create image file", Toast.LENGTH_SHORT).show()
                    null
                }

                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.provider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

                    // Set additional options for higher quality image
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        takePictureIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 0)
                        takePictureIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
                    }
                    // Maximize quality
                    takePictureIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                    takePictureIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 0)

                    startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
                }
            } ?: run {
                Toast.makeText(this, "Cannot find camera app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name with timestamp
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Camera permission needed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            try {
                imageFile = File(currentPhotoPath)
                if (imageFile!!.exists()) {
                    // Enhance image quality before uploading
                    imageFile = ImageUtils.enhanceImageQuality(imageFile!!)
                    tvStatus.text = "Picture captured successfully"
                } else {
                    tvStatus.text = "Cannot find captured image"
                }
            } catch (e: Exception) {
                tvStatus.text = "Error processing image: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    private fun uploadImage(file: File) {
        tvStatus.text = "Uploading and processing..."

        // Clean up any existing media player
        mediaPlayer?.release()
        mediaPlayer = null

        NetworkUtils.uploadImageForOcr(file, serverUrl, object : NetworkUtils.OcrCallback {
            override fun onSuccess(text: String, audioUrl: String) {
                runOnUiThread {
                    tvStatus.text = "Text:\n$text"
                    playAudio(audioUrl)
                }
            }

            override fun onFailure(errorMessage: String) {
                runOnUiThread {
                    tvStatus.text = "Error: $errorMessage"
                    Toast.makeText(this@OcrActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun playAudio(audioUrl: String) {
        try {
            // Create a new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioUrl)
                setOnPreparedListener {
                    start()
                    Toast.makeText(this@OcrActivity, "Playing audio", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    Toast.makeText(this@OcrActivity, "Audio playback complete", Toast.LENGTH_SHORT).show()
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@OcrActivity, "Error playing audio", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync() // Prepare asynchronously to not block the UI thread
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}