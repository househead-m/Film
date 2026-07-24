package com.example.filmcamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val x1 = w / 3f
        val x2 = w * 2f / 3f
        val y1 = h / 3f
        val y2 = h * 2f / 3f

        canvas.drawLine(x1, 0f, x1, h, paint)
        canvas.drawLine(x2, 0f, x2, h, paint)
        canvas.drawLine(0f, y1, w, y1, paint)
        canvas.drawLine(0f, y2, w, y2, paint)
    }
}
