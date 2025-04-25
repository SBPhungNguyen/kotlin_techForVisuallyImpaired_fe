package com.example.myapplication.util

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okio.buffer
import okio.sink

object NetworkUtils {
    // OkHttpClient with longer timeout to handle large images
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    interface OcrCallback {
        fun onSuccess(text: String, audioUrl: String)
        fun onFailure(errorMessage: String)
    }

    interface ObjectDetectionCallback {
        fun onSuccess(audioFile: File)
        fun onFailure(errorMessage: String)
    }

    fun uploadImageForOcr(file: File, serverUrl: String, callback: OcrCallback) {
        // Create request body with "image" as the key (matching the backend)
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, RequestBody.create("image/jpeg".toMediaTypeOrNull(), file))
            .build()

        // Create request to the correct endpoint
        val request = Request.Builder()
            .url("$serverUrl/api/ocr-tts")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure("Server connection error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onFailure("Server error: ${response.code}")
                    return
                }

                try {
                    val responseBody = response.body?.string()
                    val jsonObject = JSONObject(responseBody)

                    // Extract text and audio URL from response (use cleaned_text if available)
                    val detectedText = if (jsonObject.has("cleaned_text"))
                        jsonObject.getString("cleaned_text")
                    else
                        jsonObject.getString("text")
                    val audioUrl = serverUrl + jsonObject.getString("audio_url")

                    callback.onSuccess(detectedText, audioUrl)
                } catch (e: Exception) {
                    callback.onFailure("Response handling error: ${e.message}")
                }
            }
        })
    }

    fun uploadImageForObjectDetection(file: File, serverUrl: String, context: Context, callback: ObjectDetectionCallback) {
        // Create request body with "file" as the key (matching the backend)
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, RequestBody.create("image/jpeg".toMediaTypeOrNull(), file))
            .build()

        // Create request to the root endpoint for object detection
        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure("Server connection error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onFailure("Server error: ${response.code}")
                    return
                }

                try {
                    // Save audio response to temporary file in the app's cache directory
                    val cacheDir = context.cacheDir
                    val audioFile = File.createTempFile("audio", ".mp3", cacheDir)
                    val sink = audioFile.sink().buffer()
                    sink.writeAll(response.body!!.source())
                    sink.close()

                    callback.onSuccess(audioFile)
                } catch (e: Exception) {
                    callback.onFailure("Audio processing error: ${e.message}")
                }
            }
        })
    }
}
