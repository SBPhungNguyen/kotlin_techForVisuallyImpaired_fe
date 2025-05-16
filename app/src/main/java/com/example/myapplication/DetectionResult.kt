package com.example.myapplication

data class DetectionResult(
    val objects: List<DetectedObject>,
    val audioUrl: String?
)