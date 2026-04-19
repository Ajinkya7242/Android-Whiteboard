package com.sandblaze.whiteboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandblaze.whiteboard.core.WhiteboardConfig
import com.sandblaze.whiteboard.domain.model.ColorHex
import com.sandblaze.whiteboard.domain.model.Point
import com.sandblaze.whiteboard.domain.model.Rect
import com.sandblaze.whiteboard.domain.model.ShapeEntity
import com.sandblaze.whiteboard.domain.model.StrokeEntity
import com.sandblaze.whiteboard.domain.model.TextEntity
import com.sandblaze.whiteboard.domain.model.WhiteboardState
import com.sandblaze.whiteboard.domain.usecase.ListSavedWhiteboardsUseCase
import com.sandblaze.whiteboard.domain.usecase.LoadLatestWhiteboardUseCase
import com.sandblaze.whiteboard.domain.usecase.LoadWhiteboardByFileNameUseCase
import com.sandblaze.whiteboard.domain.usecase.SaveWhiteboardUseCase
import com.sandblaze.whiteboard.domain.usecase.WhiteboardStateReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhiteboardViewModel @Inject constructor(
    private val saveWhiteboardUseCase: SaveWhiteboardUseCase,
    private val loadLatestWhiteboardUseCase: LoadLatestWhiteboardUseCase,
    private val loadWhiteboardByFileNameUseCase: LoadWhiteboardByFileNameUseCase,
    private val listSavedWhiteboardsUseCase: ListSavedWhiteboardsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(WhiteboardState.empty())
    val state: StateFlow<WhiteboardState> = _state.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val undoStack = ArrayDeque<WhiteboardState>()
    private val redoStack = ArrayDeque<WhiteboardState>()
    private var gestureBaseState: WhiteboardState? = null

    private val _tool = MutableStateFlow<Tool>(Tool.Draw)
    val tool: StateFlow<Tool> = _tool.asStateFlow()

    private val _strokeWidth = MutableStateFlow(WhiteboardConfig.DEFAULT_STROKE_WIDTH_PX)
    val strokeWidth: StateFlow<Float> = _strokeWidth.asStateFlow()

    private val _color = MutableStateFlow(ColorHex("#000000"))
    val color: StateFlow<ColorHex> = _color.asStateFlow()

    fun beginGesture() {
        if (gestureBaseState == null) gestureBaseState = _state.value
    }

    fun endGesture() {
        val base = gestureBaseState ?: return
        gestureBaseState = null
        if (_state.value != base) { undoStack.addLast(base); redoStack.clear() }
    }

    private fun setState(next: WhiteboardState) {
        val current = _state.value
        if (next == current) return
        if (gestureBaseState != null) { _state.value = next; return }
        undoStack.addLast(current)
        if (undoStack.size > WhiteboardConfig.MAX_UNDO_STACK_SIZE) undoStack.removeFirst()
        redoStack.clear()
        _state.value = next
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_state.value)
        _state.value = undoStack.removeLast()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_state.value)
        _state.value = redoStack.removeLast()
    }

    fun setTool(tool: Tool) { _tool.value = tool }
    fun setStrokeWidth(width: Float) { _strokeWidth.value = width }
    fun setColor(colorHex: String) { _color.value = ColorHex(colorHex) }

    fun commitStroke(points: List<Point>) {
        if (points.size < 2) return
        setState(_state.value.copy(strokes = _state.value.strokes + StrokeEntity(points, _color.value, _strokeWidth.value)))
    }

    fun commitShape(shape: ShapeEntity) = setState(_state.value.copy(shapes = _state.value.shapes + shape))
    fun commitText(text: TextEntity) = setState(_state.value.copy(texts = _state.value.texts + text))

    fun findTextIndexNear(point: Point, radiusPx: Float): Int? =
        _state.value.texts.indices.reversed().firstOrNull { _state.value.texts[it].hitTest(point, radiusPx) }

    fun moveText(index: Int, newPosition: Point) = setState(WhiteboardStateReducer.moveText(_state.value, index, newPosition))
    fun updateText(index: Int, newValue: String, color: ColorHex, sizeSp: Float) = setState(WhiteboardStateReducer.updateText(_state.value, index, newValue, color, sizeSp))
    fun eraseTextRange(index: Int, startInclusive: Int, endExclusive: Int) = setState(WhiteboardStateReducer.eraseTextRange(_state.value, index, startInclusive, endExclusive))

    fun findShapeIndexNear(point: Point, radiusPx: Float): Int? =
        _state.value.shapes.indices.reversed().firstOrNull { _state.value.shapes[it].hitTest(point, radiusPx) }

    fun moveShape(index: Int, dx: Float, dy: Float) = setState(WhiteboardStateReducer.moveShape(_state.value, index, dx, dy))
    fun resizeShape(index: Int, newBounds: Rect) = setState(WhiteboardStateReducer.resizeShape(_state.value, index, newBounds))
    fun eraseAt(point: Point, radiusPx: Float) = setState(WhiteboardStateReducer.eraseAt(_state.value, point, radiusPx))

    fun save() {
        viewModelScope.launch {
            saveWhiteboardUseCase(_state.value)
                .onSuccess { _uiEvent.emit(UiEvent.SaveSuccess(it)) }
                .onFailure { _uiEvent.emit(UiEvent.SaveError(it.message ?: "Unknown error")) }
        }
    }

    fun loadLatest() {
        viewModelScope.launch {
            loadLatestWhiteboardUseCase()
                .onSuccess { _state.value = it; _uiEvent.emit(UiEvent.LoadSuccess) }
                .onFailure { _uiEvent.emit(UiEvent.LoadError(it.message ?: "Unknown error")) }
        }
    }

    fun listSavedFileNames() {
        viewModelScope.launch {
            listSavedWhiteboardsUseCase()
                .onSuccess { _uiEvent.emit(UiEvent.FilesListed(it)) }
                .onFailure { _uiEvent.emit(UiEvent.FileListError(it.message ?: "No saved files found.")) }
        }
    }

    fun loadByFileName(fileName: String) {
        viewModelScope.launch {
            loadWhiteboardByFileNameUseCase(fileName)
                .onSuccess { _state.value = it; _uiEvent.emit(UiEvent.LoadSuccess) }
                .onFailure { _uiEvent.emit(UiEvent.LoadError(it.message ?: "Unknown error")) }
        }
    }
}
