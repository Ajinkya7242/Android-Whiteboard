package com.sandblaze.whiteboard.presentation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.sandblaze.whiteboard.domain.model.ShapeEntity

internal fun ShapeEntity.draw(canvas: Canvas, paint: Paint) {
    paint.color = Color.parseColor(color.value)
    when (this) {
        is ShapeEntity.Rectangle -> {
            canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
        }
        is ShapeEntity.Circle -> {
            canvas.drawCircle(center.x, center.y, radius, paint)
        }
        is ShapeEntity.Line -> {
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
        is ShapeEntity.Polygon -> {
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
    }
}

