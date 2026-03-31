package com.sandblaze.whiteboard.presentation

import com.sandblaze.whiteboard.domain.model.ColorHex
import com.sandblaze.whiteboard.domain.model.Point
import com.sandblaze.whiteboard.domain.model.Rect
import com.sandblaze.whiteboard.domain.model.ShapeEntity
import kotlin.math.max
import kotlin.math.min

internal object ShapeInteraction {
    fun bounds(shape: ShapeEntity): Rect = when (shape) {
        is ShapeEntity.Rectangle -> shape.rect
        is ShapeEntity.Circle -> {
            val r = shape.radius
            Rect(
                left = shape.center.x - r,
                top = shape.center.y - r,
                right = shape.center.x + r,
                bottom = shape.center.y + r
            )
        }
        is ShapeEntity.Line -> {
            Rect(
                left = min(shape.start.x, shape.end.x),
                top = min(shape.start.y, shape.end.y),
                right = max(shape.start.x, shape.end.x),
                bottom = max(shape.start.y, shape.end.y)
            )
        }
        is ShapeEntity.Polygon -> shape.bounds
    }

    fun fixedCornerForResize(touch: Point, bounds: Rect, tolerancePx: Float): Point? {
        val tl = Point(bounds.left, bounds.top)
        val tr = Point(bounds.right, bounds.top)
        val bl = Point(bounds.left, bounds.bottom)
        val br = Point(bounds.right, bounds.bottom)

        fun dist2(a: Point, b: Point): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return dx * dx + dy * dy
        }

        val tol2 = tolerancePx * tolerancePx
        val dTL = dist2(touch, tl)
        val dTR = dist2(touch, tr)
        val dBL = dist2(touch, bl)
        val dBR = dist2(touch, br)

        val nearest = minOf(dTL, dTR, dBL, dBR)
        if (nearest > tol2) return null

        return when (nearest) {
            dTL -> br
            dTR -> bl
            dBL -> tr
            else -> tl
        }
    }

    fun shapeFromDrag(type: ShapeType, start: Point, end: Point, color: ColorHex): ShapeEntity {
        val left = min(start.x, end.x)
        val top = min(start.y, end.y)
        val right = max(start.x, end.x)
        val bottom = max(start.y, end.y)
        return when (type) {
            ShapeType.Rectangle -> ShapeEntity.Rectangle(
                rect = Rect(left, top, right, bottom),
                color = color
            )
            ShapeType.Circle -> {
                val cx = (left + right) / 2f
                val cy = (top + bottom) / 2f
                val radius = min(right - left, bottom - top) / 2f
                ShapeEntity.Circle(center = Point(cx, cy), radius = radius, color = color)
            }
            ShapeType.Line -> ShapeEntity.Line(start = start, end = end, color = color)
            is ShapeType.Polygon -> ShapeEntity.Polygon(
                bounds = Rect(left, top, right, bottom),
                sides = max(4, type.sides),
                color = color
            )
        }
    }
}
