package com.example.myapplication

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.myapplication.DetectedObject

// A custom View to draw detection results (bounding boxes and labels) on the screen
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // List of detected objects to draw
    private val detections = mutableListOf<DetectedObject>()
    // Paint used to draw text (labels)
    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 40f
        style = Paint.Style.FILL
        typeface = Typeface.DEFAULT_BOLD
    }

    // Paint used to draw bounding box outlines
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Paint used to fill bounding boxes with semi-transparent color
    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 255, 0)
        style = Paint.Style.FILL
    }

    // Public function to update the view with new detections
    fun drawDetections(newDetections: List<DetectedObject>) {
        detections.clear()
        detections.addAll(newDetections)
        invalidate()
    }

    // Public function to clear detections from the view
    fun clearDetections() {
        detections.clear()
        invalidate()
    }

    // Called when the view is drawn on screen
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw each detection on the canvas
        detections.forEach { detection ->
            canvas.drawRect(detection.boundingBox, fillPaint)
            canvas.drawRect(detection.boundingBox, boxPaint)
            canvas.drawText(
                "${detection.label} (${"%.2f".format(detection.confidence)})",
                detection.boundingBox.left,
                detection.boundingBox.top - 10,
                textPaint
            )
        }
    }
}