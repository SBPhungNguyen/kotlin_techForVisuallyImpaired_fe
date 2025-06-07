package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.RectF
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class NetworkClient(baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val serverUrl = baseUrl

    suspend fun detectObjects(bitmap: Bitmap): DetectionResult {
        val byteArray = bitmapToJpegByteArray(bitmap)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "frame.jpg",
                byteArray.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$serverUrl/detect")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Server error: ${response.code}")

        val json = JSONObject(response.body!!.string())
        return parseDetectionResponse(json)
    }

    private fun parseDetectionResponse(json: JSONObject): DetectionResult {
        val objects = mutableListOf<DetectedObject>()
        val detections = json.getJSONArray("objects")

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

        val audioUrl = if (json.has("audio_url")) {
            json.getString("audio_url")
        } else null

        return DetectionResult(objects, audioUrl)
    }

    private fun bitmapToJpegByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }
}