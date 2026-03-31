package com.sandblaze.whiteboard.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sandblaze.whiteboard.data.WhiteboardRepositoryImpl
import com.sandblaze.whiteboard.data.local.LocalWhiteboardStorage
import com.sandblaze.whiteboard.domain.model.ColorHex
import com.sandblaze.whiteboard.domain.model.Point
import com.sandblaze.whiteboard.domain.model.ShapeEntity
import com.sandblaze.whiteboard.domain.model.StrokeEntity
import com.sandblaze.whiteboard.domain.model.Rect
import com.sandblaze.whiteboard.domain.model.TextEntity
import com.sandblaze.whiteboard.domain.model.WhiteboardState
import com.sandblaze.whiteboard.domain.usecase.WhiteboardStateReducer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WhiteboardViewModel(
    private val repository: WhiteboardRepositoryImpl
) : ViewModel() {

    private val _state = MutableStateFlow(WhiteboardState.empty())
    val state: StateFlow<WhiteboardState> = _state

    private val undoStack = ArrayDeque<WhiteboardState>()
    private val redoStack = ArrayDeque<WhiteboardState>()

    // Used to group continuous gesture updates (dragging) into a single undo step.
    private var gestureBaseState: WhiteboardState? = null

    private val _tool = MutableStateFlow<Tool>(Tool.Draw)
    val tool: StateFlow<Tool> = _tool

    private val _strokeWidth = MutableStateFlow(6f)
    val strokeWidth: StateFlow<Float> = _strokeWidth

    private val _color = MutableStateFlow(ColorHex("#000000"))
    val color: StateFlow<ColorHex> = _color

    fun beginGesture() {
        if (gestureBaseState == null) {
            gestureBaseState = _state.value
        }
    }

    fun endGesture() {
        val base = gestureBaseState ?: return
        gestureBaseState = null

        val current = _state.value
        if (current != base) {
            undoStack.addLast(base)
            redoStack.clear()
        }
    }

    private fun setState(next: WhiteboardState) {
        val current = _state.value
        if (next == current) return

        if (gestureBaseState != null) {
            _state.value = next
            return
        }

        undoStack.addLast(current)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        _state.value = next
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _state.value
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        _state.value = prev
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _state.value
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        _state.value = next
    }

    fun setTool(tool: Tool) {
        _tool.value = tool
    }

    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }

    fun setColor(colorHex: String) {
        _color.value = ColorHex(colorHex)
    }

    fun commitStroke(points: List<Point>) {
        if (points.size < 2) return
        val stroke = StrokeEntity(
            points = points,
            color = _color.value,
            width = _strokeWidth.value
        )
        val next = _state.value.copy(strokes = _state.value.strokes + stroke)
        setState(next)
    }

    fun commitShape(shape: ShapeEntity) {
        val current = _state.value
        setState(current.copy(shapes = current.shapes + shape))
    }

    fun commitText(text: TextEntity) {
        val current = _state.value
        setState(current.copy(texts = current.texts + text))
    }

    fun findTextIndexNear(point: Point, radiusPx: Float): Int? {
        val texts = _state.value.texts
        for (i in texts.indices.reversed()) {
            if (texts[i].hitTest(point, radiusPx)) return i
        }
        return null
    }

    fun moveText(index: Int, newPosition: Point) {
        val current = _state.value
        setState(WhiteboardStateReducer.moveText(current, index, newPosition))
    }

    fun editText(index: Int, newValue: String) {
        val value = newValue.trim()
        if (value.isEmpty()) return
        val current = _state.value
        if (index !in current.texts.indices) return
        val updated = current.texts.toMutableList()
        updated[index] = updated[index].copy(text = value)
        setState(current.copy(texts = updated))
    }

    fun updateText(index: Int, newValue: String, color: ColorHex, sizeSp: Float) {
        val current = _state.value
        setState(WhiteboardStateReducer.updateText(current, index, newValue, color, sizeSp))
    }

    /**
     * Removes a character range from a text entity.
     * If the resulting string becomes empty, the whole text entity is removed.
     */
    fun eraseTextRange(index: Int, startInclusive: Int, endExclusive: Int) {
        val current = _state.value
        setState(WhiteboardStateReducer.eraseTextRange(current, index, startInclusive, endExclusive))
    }

    fun findShapeIndexNear(point: Point, radiusPx: Float): Int? {
        val shapes = _state.value.shapes
        for (i in shapes.indices.reversed()) {
            if (shapes[i].hitTest(point, radiusPx)) return i
        }
        return null
    }

    fun moveShape(index: Int, dx: Float, dy: Float) {
        val current = _state.value
        setState(WhiteboardStateReducer.moveShape(current, index, dx, dy))
    }

    fun resizeShape(index: Int, newBounds: Rect) {
        val current = _state.value
        setState(WhiteboardStateReducer.resizeShape(current, index, newBounds))
    }

    fun eraseAt(point: Point, radiusPx: Float) {
        val current = _state.value
        setState(WhiteboardStateReducer.eraseAt(current, point, radiusPx))
    }

    fun save(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.save(_state.value) }
                .onSuccess { onSuccess() }
                .onFailure { onError(it) }
        }
    }

    fun loadLatest(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.loadLatest() }
                .onSuccess { loaded ->
                    _state.value = loaded
                    onSuccess()
                }
                .onFailure { onError(it) }
        }
    }

    fun listSavedFileNames(onSuccess: (List<String>) -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.listSavedFiles().map { it.name } }
                .onSuccess { onSuccess(it) }
                .onFailure { onError(it) }
        }
    }

    fun loadByFileName(fileName: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.loadByFileName(fileName) }
                .onSuccess {
                    _state.value = it
                    onSuccess()
                }
                .onFailure { onError(it) }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val storage = LocalWhiteboardStorage(appContext)
            val repo = WhiteboardRepositoryImpl(storage)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WhiteboardViewModel(repo) as T
                }
            }
        }
    }
}

