package com.example.myapplication

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.content.Intent

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize buttons
        val btnOcr: Button = findViewById(R.id.btnOcr)
        val btnObjectDetection: Button = findViewById(R.id.btnObjectDetection)

        // Set click listeners for navigation
        btnOcr.setOnClickListener {
            startActivity(Intent(this, OcrActivity::class.java))
        }

        btnObjectDetection.setOnClickListener {
            startActivity(Intent(this, ObjectDetectionActivity::class.java))
        }
    }
}