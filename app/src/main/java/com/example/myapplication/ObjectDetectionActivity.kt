package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.util.ImageUtils
import com.example.myapplication.util.NetworkUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ObjectDetectionActivity : AppCompatActivity() {
    private val CAMERA_REQUEST_CODE = 100
    private val CAMERA_PERMISSION_CODE = 101

    private var imageFile: File? = null
    private lateinit var tvStatus: TextView
    private var mediaPlayer: MediaPlayer? = null

    private val serverUrl = "http://192.168.131.88:5000"  // Replace with actual server IP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_detection)

        val btnCapture: Button = findViewById(R.id.btnCapture)
        val btnUpload: Button = findViewById(R.id.btnUpload)
        tvStatus = findViewById(R.id.tvStatus)

        ImageUtils.checkAndRequestPermissions(this)

        btnCapture.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            } else {
                openCamera()
            }
        }

        btnUpload.setOnClickListener {
            if (imageFile != null) {
                uploadImage(imageFile!!)
            } else {
                Toast.makeText(this, "No picture captured yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val bitmap = data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                imageFile = saveBitmapToFile(it)
                tvStatus.text = "Picture captured, ready for detection"
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val file = File(cacheDir, "captured_image.jpg")
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        fos.flush()
        fos.close()
        return file
    }

    private fun uploadImage(file: File) {
        tvStatus.text = "Uploading and detecting objects..."

        // Clean up any existing media player
        mediaPlayer?.release()
        mediaPlayer = null

        // Truyền thêm context vào phương thức uploadImageForObjectDetection
        NetworkUtils.uploadImageForObjectDetection(file, serverUrl, this, object : NetworkUtils.ObjectDetectionCallback {
            override fun onSuccess(audioFile: File) {
                runOnUiThread {
                    tvStatus.text = "Objects detected"
                    playAudio(audioFile)
                }
            }

            override fun onFailure(errorMessage: String) {
                runOnUiThread {
                    tvStatus.text = "Error: $errorMessage"
                    Toast.makeText(this@ObjectDetectionActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun playAudio(audioFile: File) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    Toast.makeText(this@ObjectDetectionActivity, "Audio playback complete", Toast.LENGTH_SHORT).show()
                }
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