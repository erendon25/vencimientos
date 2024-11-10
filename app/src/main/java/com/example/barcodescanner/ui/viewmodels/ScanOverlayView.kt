package com.example.barcodescanner.ui.viewmodels

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import com.example.barcodescanner.R

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val darkPaint = Paint().apply {
        color = Color.parseColor("#80000000")  // Semi-transparent black
        style = Paint.Style.FILL
    }

    private var scanArea = RectF()
    private var initialScanArea = RectF()
    private var isDragging = false
    private var dragCorner: Int = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val resetIcon = ContextCompat.getDrawable(context, R.drawable.ic_reset)
    private val resetIconSize = 48f
    private val resetIconBounds = RectF()

    init {
        post {
            val centerX = width / 2f
            val centerY = height / 2f
            val size = minOf(width, height) * 0.7f
            initialScanArea.set(
                centerX - size / 2,
                centerY - size / 2,
                centerX + size / 2,
                centerY + size / 2
            )
            scanArea.set(initialScanArea)
            updateResetIconBounds()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibujar el área oscurecida
        canvas.drawRect(0f, 0f, width.toFloat(), scanArea.top, darkPaint)
        canvas.drawRect(0f, scanArea.top, scanArea.left, scanArea.bottom, darkPaint)
        canvas.drawRect(scanArea.right, scanArea.top, width.toFloat(), scanArea.bottom, darkPaint)
        canvas.drawRect(0f, scanArea.bottom, width.toFloat(), height.toFloat(), darkPaint)

        // Dibujar el marco del área de escaneo
        canvas.drawRect(scanArea, paint)

        // Dibujar el icono de reset
        resetIcon?.bounds = resetIconBounds.toRect()
        resetIcon?.draw(canvas)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                if (resetIconBounds.contains(event.x, event.y)) {
                    resetToInitialSize()
                    return true
                }
                dragCorner = getCorner(event.x, event.y)
                isDragging = dragCorner != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    resizeScanArea(dragCorner, dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    updateResetIconBounds()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                dragCorner = -1
            }
        }
        return true
    }

    private fun getCorner(x: Float, y: Float): Int {
        val cornerSize = 50f
        return when {
            x < scanArea.left + cornerSize && y < scanArea.top + cornerSize -> 0
            x > scanArea.right - cornerSize && y < scanArea.top + cornerSize -> 1
            x < scanArea.left + cornerSize && y > scanArea.bottom - cornerSize -> 2
            x > scanArea.right - cornerSize && y > scanArea.bottom - cornerSize -> 3
            else -> -1
        }
    }

    private fun resizeScanArea(corner: Int, dx: Float, dy: Float) {
        val minSize = 100f
        when (corner) {
            0 -> {
                scanArea.left = (scanArea.left + dx).coerceIn(0f, scanArea.right - minSize)
                scanArea.top = (scanArea.top + dy).coerceIn(0f, scanArea.bottom - minSize)
            }
            1 -> {
                scanArea.right = (scanArea.right + dx).coerceIn(scanArea.left + minSize, width.toFloat())
                scanArea.top = (scanArea.top + dy).coerceIn(0f, scanArea.bottom - minSize)
            }
            2 -> {
                scanArea.left = (scanArea.left + dx).coerceIn(0f, scanArea.right - minSize)
                scanArea.bottom = (scanArea.bottom + dy).coerceIn(scanArea.top + minSize, height.toFloat())
            }
            3 -> {
                scanArea.right = (scanArea.right + dx).coerceIn(scanArea.left + minSize, width.toFloat())
                scanArea.bottom = (scanArea.bottom + dy).coerceIn(scanArea.top + minSize, height.toFloat())
            }
        }
    }

    fun resetToInitialSize() {
        scanArea.set(initialScanArea)
        updateResetIconBounds()
        invalidate()
    }


    private fun updateResetIconBounds() {
        resetIconBounds.set(
            scanArea.right - resetIconSize,
            scanArea.top - resetIconSize,
            scanArea.right,
            scanArea.top
        )
    }

    fun getScanArea(): RectF = scanArea
}