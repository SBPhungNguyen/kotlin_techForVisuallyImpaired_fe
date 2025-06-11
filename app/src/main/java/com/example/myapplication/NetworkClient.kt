package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.RectF
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// This class handles network communication with an object detection server
class NetworkClient(baseUrl: String) {

    // OkHttpClient instance with 15-second timeouts for connect and read operations
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Base URL of the detection server
    private val serverUrl = baseUrl

     // Function to send an image (Bitmap) to the server and receive detection results
    suspend fun detectObjects(bitmap: Bitmap): DetectionResult {
        // Convert the Bitmap into a JPEG byte array
        val byteArray = bitmapToJpegByteArray(bitmap)
        // Build multipart/form-data request body with image data
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "frame.jpg",
                byteArray.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        // Build the HTTP POST request to the /detect endpoint
        val request = Request.Builder()
            .url("$serverUrl/detect")
            .post(requestBody)
            .build()

        // Execute the request synchronously
        val response = client.newCall(request).execute()
        // Throw an error if the response is not successful
        if (!response.isSuccessful) throw Exception("Server error: ${response.code}")

        // Parse the response body as JSON
        val json = JSONObject(response.body!!.string())
        // Parse the JSON into a DetectionResult object
        return parseDetectionResponse(json)
    }

    // Helper function to parse the detection results from the JSON response
    private fun parseDetectionResponse(json: JSONObject): DetectionResult {
        val objects = mutableListOf<DetectedObject>()
        val detections = json.getJSONArray("objects")

        // Loop through each object in the array
        for (i in 0 until detections.length()) {
            val obj = detections.getJSONObject(i)
            objects.add(DetectedObject(
                label = obj.getString("label"),
                confidence = obj.getDouble("confidence").toFloat(),
                boundingBox = RectF(
                    obj.getJSONObject("box").getDouble("left").toFloat(),
                    obj.getJSONObject("box").getDouble("top").toFloat(),
                    obj.getJSONObject("box").getDouble("right").toFloat(),
                    obj.getJSONObject("box").getDouble("bottom").toFloat()
                )
            ))
        }

        // Check if the response includes an audio URL (optional)
        val audioUrl = if (json.has("audio_url")) {
            json.getString("audio_url")
        } else null

        // Return the detection result
        return DetectionResult(objects, audioUrl)
    }

    // Helper function to convert a Bitmap to a JPEG byte array
    private fun bitmapToJpegByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }
}