package com.sandblaze.whiteboard.domain.model

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@JvmInline
value class ColorHex(val value: String)

data class Point(
    val x: Float,
    val y: Float
)

data class Rect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun expandedBy(delta: Float): Rect =
        Rect(left - delta, top - delta, right + delta, bottom + delta)

    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
    fun width(): Float = (right - left)
    fun height(): Float = (bottom - top)
}

data class StrokeEntity(
    val points: List<Point>,
    val color: ColorHex,
    val width: Float
) {
    fun erase(center: Point, radiusPx: Float): List<StrokeEntity> {
        if (points.isEmpty()) return emptyList()

        val keep = BooleanArray(points.size) { idx ->
            val p = points[idx]
            hypot((p.x - center.x).toDouble(), (p.y - center.y).toDouble()) > radiusPx
        }

        // Split into contiguous kept segments (pixel-ish eraser effect).
        val out = ArrayList<StrokeEntity>()
        var current = ArrayList<Point>()
        for (i in points.indices) {
            if (keep[i]) {
                current.add(points[i])
            } else {
                if (current.size >= 2) out.add(copy(points = current.toList()))
                current = ArrayList()
            }
        }
        if (current.size >= 2) out.add(copy(points = current.toList()))
        return out
    }
}

sealed interface ShapeEntity {
    val color: ColorHex

    fun hitTest(point: Point, radiusPx: Float): Boolean
    fun draw(canvas: Canvas, paint: Paint)

    data class Rectangle(
        val rect: Rect,
        override val color: ColorHex
    ) : ShapeEntity {
        override fun hitTest(point: Point, radiusPx: Float): Boolean {
            return rect.expandedBy(radiusPx).contains(point.x, point.y)
        }

        override fun draw(canvas: Canvas, paint: Paint) {
            canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
        }
    }

    data class Circle(
        val center: Point,
        val radius: Float,
        override val color: ColorHex
    ) : ShapeEntity {
        override fun hitTest(point: Point, radiusPx: Float): Boolean {
            val d = hypot((point.x - center.x).toDouble(), (point.y - center.y).toDouble()).toFloat()
            return d <= radius + radiusPx
        }

        override fun draw(canvas: Canvas, paint: Paint) {
            canvas.drawCircle(center.x, center.y, radius, paint)
        }
    }

    data class Line(
        val start: Point,
        val end: Point,
        override val color: ColorHex
    ) : ShapeEntity {
        override fun hitTest(point: Point, radiusPx: Float): Boolean {
            // Distance from point to segment (approx).
            val ax = start.x
            val ay = start.y
            val bx = end.x
            val by = end.y
            val px = point.x
            val py = point.y
            val abx = bx - ax
            val aby = by - ay
            val apx = px - ax
            val apy = py - ay
            val abLen2 = abx * abx + aby * aby
            if (abLen2 == 0f) return hypot((px - ax).toDouble(), (py - ay).toDouble()).toFloat() <= radiusPx
            val t = (apx * abx + apy * aby) / abLen2
            val clamped = min(1f, max(0f, t))
            val cx = ax + clamped * abx
            val cy = ay + clamped * aby
            val d = hypot((px - cx).toDouble(), (py - cy).toDouble()).toFloat()
            return d <= radiusPx
        }

        override fun draw(canvas: Canvas, paint: Paint) {
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
    }

    data class Polygon(
        val bounds: Rect,
        val sides: Int,
        override val color: ColorHex
    ) : ShapeEntity {
        override fun hitTest(point: Point, radiusPx: Float): Boolean {
            return bounds.expandedBy(radiusPx).contains(point.x, point.y)
        }

        override fun draw(canvas: Canvas, paint: Paint) {
            val vertices = vertices()
            if (vertices.isEmpty()) return
            val path = Path()
            path.moveTo(vertices.first().x, vertices.first().y)
            for (i in 1 until vertices.size) {
                path.lineTo(vertices[i].x, vertices[i].y)
            }
            path.close()
            canvas.drawPath(path, paint)
        }

        fun vertices(): List<Point> {
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val rx = bounds.width() / 2f
            val ry = bounds.height() / 2f
            val r = min(rx, ry)
            if (sides < 3 || r <= 0f) return emptyList()

            return (0 until sides).map { i ->
                val theta = (2.0 * Math.PI * i / sides) - Math.PI / 2.0
                Point(
                    x = (cx + r * cos(theta)).toFloat(),
                    y = (cy + r * sin(theta)).toFloat()
                )
            }
        }
    }
}

data class TextEntity(
    val text: String,
    val position: Point,
    val color: ColorHex,
    val sizeSp: Float
) {
    fun sizeSpToPx(scaledDensity: Float): Float = sizeSp * scaledDensity

    fun hitTest(point: Point, radiusPx: Float): Boolean {
        // Approx hit-box around insertion point (good enough for big IFP touches).
        val dx = kotlin.math.abs(point.x - position.x)
        val dy = kotlin.math.abs(point.y - position.y)
        return dx <= radiusPx * 2.2f && dy <= radiusPx * 2.2f
    }
}

data class WhiteboardState(
    val strokes: List<StrokeEntity>,
    val shapes: List<ShapeEntity>,
    val texts: List<TextEntity>
) {
    companion object {
        fun empty() = WhiteboardState(
            strokes = emptyList(),
            shapes = emptyList(),
            texts = emptyList()
        )
    }
}

