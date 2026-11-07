package com.colorgrid.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Tiny vector-style icons drawn with Canvas so they never depend on font glyphs. */
class IconView(context: Context, private val kind: Kind, var tint: Int) : View(context) {

    enum class Kind { SHIFT, BACKSPACE, ENTER, GRID, CHEVRON_DOWN }

    var filled = false
    var locked = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) / 2f
        paint.strokeWidth = s * 0.16f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = tint
        path.reset()

        when (kind) {
            Kind.SHIFT -> {
                val dy = if (locked) -s * 0.12f else 0f
                path.moveTo(cx, cy - s * 0.6f + dy)
                path.lineTo(cx - s * 0.6f, cy + s * 0.05f + dy)
                path.lineTo(cx - s * 0.25f, cy + s * 0.05f + dy)
                path.lineTo(cx - s * 0.25f, cy + s * 0.55f + dy)
                path.lineTo(cx + s * 0.25f, cy + s * 0.55f + dy)
                path.lineTo(cx + s * 0.25f, cy + s * 0.05f + dy)
                path.lineTo(cx + s * 0.6f, cy + s * 0.05f + dy)
                path.close()
                paint.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                canvas.drawPath(path, paint)
                if (locked) {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(cx - s * 0.25f, cy + s * 0.8f, cx + s * 0.25f, cy + s * 0.8f, paint)
                }
            }
            Kind.BACKSPACE -> {
                path.moveTo(cx - s * 0.85f, cy)
                path.lineTo(cx - s * 0.4f, cy - s * 0.55f)
                path.lineTo(cx + s * 0.8f, cy - s * 0.55f)
                path.lineTo(cx + s * 0.8f, cy + s * 0.55f)
                path.lineTo(cx - s * 0.4f, cy + s * 0.55f)
                path.close()
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
                canvas.drawLine(cx + s * 0.0f, cy - s * 0.22f, cx + s * 0.4f, cy + s * 0.22f, paint)
                canvas.drawLine(cx + s * 0.4f, cy - s * 0.22f, cx + s * 0.0f, cy + s * 0.22f, paint)
            }
            Kind.ENTER -> {
                path.moveTo(cx + s * 0.55f, cy - s * 0.5f)
                path.lineTo(cx + s * 0.55f, cy + s * 0.1f)
                path.lineTo(cx - s * 0.55f, cy + s * 0.1f)
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(cx - s * 0.2f, cy - s * 0.25f)
                path.lineTo(cx - s * 0.55f, cy + s * 0.1f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.45f)
                canvas.drawPath(path, paint)
            }
            Kind.GRID -> {
                val colors = intArrayOf(
                    Color.parseColor("#F44336"), Color.parseColor("#2196F3"),
                    Color.parseColor("#4CAF50"), Color.parseColor("#FFC107")
                )
                val cell = s * 0.8f
                val gap = s * 0.16f
                paint.style = Paint.Style.FILL
                var n = 0
                for (j in 0..1) {
                    for (i in 0..1) {
                        val left = cx - gap / 2f - cell + i * (cell + gap)
                        val top = cy - gap / 2f - cell + j * (cell + gap)
                        rect.set(left, top, left + cell, top + cell)
                        paint.color = colors[n++]
                        canvas.drawRoundRect(rect, cell * 0.28f, cell * 0.28f, paint)
                    }
                }
            }
            Kind.CHEVRON_DOWN -> {
                path.moveTo(cx - s * 0.5f, cy - s * 0.2f)
                path.lineTo(cx, cy + s * 0.3f)
                path.lineTo(cx + s * 0.5f, cy - s * 0.2f)
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
            }
        }
    }
}
