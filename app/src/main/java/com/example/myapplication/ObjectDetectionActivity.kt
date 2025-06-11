package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.databinding.ActivityObjectDetectionBinding
import com.example.myapplication.util.ImageUtils
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ObjectDetectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityObjectDetectionBinding 
    private lateinit var cameraExecutor: ExecutorService 
    private var camera: Camera? = null 
    private var isProcessing = false  
    private var frameCount = 0
    private val frameSkip = 3 // Process every 3rd frame 
    private lateinit var networkClient: NetworkClient 
    private var mediaPlayer: MediaPlayer? = null 

    //Audio queue
    private val audioQueue: ArrayDeque<String> = ArrayDeque()
    private var isAudioPlaying = false
    private var lastAudioPlayedTime = 0L
    private val audioCooldownMillis = 3000L //3sec cool down


    // Backend configuration
    private val flaskServerUrl = "https://visionassistant-production.up.railway.app/" // Replace with actual server IP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityObjectDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        networkClient = NetworkClient(flaskServerUrl)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    // Set up the CameraX pipeline
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        analyzeImage(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch(exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }
    // Analyze each camera frame (skip some for performance)
    @OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        frameCount++
        if (frameCount % frameSkip != 0 || isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val bitmap = ImageUtils.imageToBitmap(mediaImage) // Convert to Bitmap
            processFrameWithFlask(bitmap) // Send to server
        }
        imageProxy.close()
    }

    // Send frame to Flask backend and draw detection results
    private fun processFrameWithFlask(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = networkClient.detectObjects(bitmap)

                withContext(Dispatchers.Main) {
                    binding.overlayView.drawDetections(result.objects)
                    result.audioUrl?.let { playAudio(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed", e)
            } finally {
                isProcessing = false
            }
        }
    }

    // Enqueue and play audio from URL if cooldown allows
    private fun playAudio(audioUrl: String?) {
        if (audioUrl.isNullOrBlank() || !audioUrl.startsWith("http")) {
            Log.w(TAG, "Invalid or empty audio URL: $audioUrl")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastAudioPlayedTime < audioCooldownMillis) {
            Log.d(TAG, "Audio skipped due to cooldown.")
            return
        }

        audioQueue.add(audioUrl)

        if (!isAudioPlaying) {
            playNextAudioInQueue()
        }
    }
    
    // Play audio from the queue one by one
    private fun playNextAudioInQueue() {
        if (audioQueue.isEmpty()) {
            isAudioPlaying = false
            return
        }

        val nextAudioUrl = audioQueue.removeFirst()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(nextAudioUrl)
                setOnPreparedListener {
                    lastAudioPlayedTime = System.currentTimeMillis()
                    isAudioPlaying = true
                    start()
                }
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                    isAudioPlaying = false
                    playNextAudioInQueue() // Play the next one if available
                }
                setOnErrorListener { _, _, _ ->
                    release()
                    mediaPlayer = null
                    isAudioPlaying = false
                    playNextAudioInQueue() // Skip errored audio
                    true
                }
                prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Audio playback failed", e)
                release()
                mediaPlayer = null
                isAudioPlaying = false
                playNextAudioInQueue()
            }
        }
    }

    // Clean up camera and overlay
    private fun stopCamera() {
        cameraExecutor.shutdown()
        binding.overlayView.clearDetections()
    }

    // Check if camera permission is granted
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    // Release media player and camera on exit
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        stopCamera()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}