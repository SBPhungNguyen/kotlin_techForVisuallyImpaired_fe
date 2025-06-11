package com.example.myapplication

import android.graphics.RectF

data class DetectedObject(
    val label: String, // Label of the detected object
    val confidence: Float, // Confidence score (probability of prediction), value from 0.0 to 1.0
    val boundingBox: RectF // Bounding box around the object, used to determine position and size (rectangle coordinates)
)