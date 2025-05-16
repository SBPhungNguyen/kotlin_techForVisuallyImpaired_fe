package com.example.myapplication

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.myapplication.DetectedObject

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val detections = mutableListOf<DetectedObject>()
    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 40f
        style = Paint.Style.FILL
        typeface = Typeface.DEFAULT_BOLD
    }
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 255, 0)
        style = Paint.Style.FILL
    }

    fun drawDetections(newDetections: List<DetectedObject>) {
        detections.clear()
        detections.addAll(newDetections)
        invalidate()
    }

    fun clearDetections() {
        detections.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
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