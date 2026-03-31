package com.sandblaze.whiteboard.domain.usecase

import com.sandblaze.whiteboard.domain.model.ColorHex
import com.sandblaze.whiteboard.domain.model.Point
import com.sandblaze.whiteboard.domain.model.Rect
import com.sandblaze.whiteboard.domain.model.ShapeEntity
import com.sandblaze.whiteboard.domain.model.TextEntity
import com.sandblaze.whiteboard.domain.model.WhiteboardState
import kotlin.math.min

internal object WhiteboardStateReducer {
    fun moveText(state: WhiteboardState, index: Int, newPosition: Point): WhiteboardState {
        if (index !in state.texts.indices) return state
        val updated = state.texts.toMutableList()
        updated[index] = updated[index].copy(position = newPosition)
        return state.copy(texts = updated)
    }

    fun updateText(
        state: WhiteboardState,
        index: Int,
        newValue: String,
        color: ColorHex,
        sizeSp: Float
    ): WhiteboardState {
        val value = newValue.trim()
        if (value.isEmpty() || index !in state.texts.indices) return state
        val safeSize = sizeSp.coerceIn(12f, 96f)
        val updated = state.texts.toMutableList()
        updated[index] = updated[index].copy(text = value, color = color, sizeSp = safeSize)
        return state.copy(texts = updated)
    }

    fun eraseTextRange(state: WhiteboardState, index: Int, startInclusive: Int, endExclusive: Int): WhiteboardState {
        if (index !in state.texts.indices) return state
        val original = state.texts[index]
        if (original.text.isEmpty()) return state

        val safeStart = startInclusive.coerceIn(0, original.text.length)
        val safeEnd = endExclusive.coerceIn(safeStart, original.text.length)
        if (safeStart == safeEnd) return state

        val updatedText = original.text.removeRange(safeStart, safeEnd).trimEnd()
        val updated = state.texts.toMutableList()
        if (updatedText.isEmpty()) {
            updated.removeAt(index)
        } else {
            updated[index] = original.copy(text = updatedText)
        }
        return state.copy(texts = updated)
    }

    fun moveShape(state: WhiteboardState, index: Int, dx: Float, dy: Float): WhiteboardState {
        if (index !in state.shapes.indices) return state
        val shapes = state.shapes.toMutableList()
        shapes[index] = when (val old = shapes[index]) {
            is ShapeEntity.Rectangle -> old.copy(
                rect = Rect(old.rect.left + dx, old.rect.top + dy, old.rect.right + dx, old.rect.bottom + dy)
            )
            is ShapeEntity.Circle -> old.copy(center = Point(old.center.x + dx, old.center.y + dy))
            is ShapeEntity.Line -> old.copy(
                start = Point(old.start.x + dx, old.start.y + dy),
                end = Point(old.end.x + dx, old.end.y + dy)
            )
            is ShapeEntity.Polygon -> old.copy(
                bounds = Rect(old.bounds.left + dx, old.bounds.top + dy, old.bounds.right + dx, old.bounds.bottom + dy)
            )
        }
        return state.copy(shapes = shapes)
    }

    fun resizeShape(state: WhiteboardState, index: Int, newBounds: Rect): WhiteboardState {
        if (index !in state.shapes.indices) return state
        val shapes = state.shapes.toMutableList()
        shapes[index] = when (val old = shapes[index]) {
            is ShapeEntity.Rectangle -> old.copy(rect = newBounds)
            is ShapeEntity.Circle -> {
                val radius = min(newBounds.width(), newBounds.height()) / 2f
                old.copy(center = Point(newBounds.centerX(), newBounds.centerY()), radius = radius)
            }
            is ShapeEntity.Line -> old.copy(
                start = Point(newBounds.left, newBounds.top),
                end = Point(newBounds.right, newBounds.bottom)
            )
            is ShapeEntity.Polygon -> old.copy(bounds = newBounds)
        }
        return state.copy(shapes = shapes)
    }

    fun eraseAt(state: WhiteboardState, point: Point, radiusPx: Float): WhiteboardState {
        val newStrokes = state.strokes.flatMap { it.erase(point, radiusPx) }
        val newShapes = state.shapes.filterNot { it.hitTest(point, radiusPx) }
        val newTexts = state.texts.filterNot { it.hitTest(point, radiusPx) }
        return state.copy(strokes = newStrokes, shapes = newShapes, texts = newTexts)
    }
}
