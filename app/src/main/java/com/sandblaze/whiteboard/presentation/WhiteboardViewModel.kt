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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.sandblaze.whiteboard.domain.WhiteboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WhiteboardViewModel @Inject constructor(
    private val repository: WhiteboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhiteboardUiState())
    val uiState: StateFlow<WhiteboardUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<WhiteboardUiEvent>()
    val uiEvent: SharedFlow<WhiteboardUiEvent> = _uiEvent.asSharedFlow()

    private val undoStack = ArrayDeque<WhiteboardState>()
    private val redoStack = ArrayDeque<WhiteboardState>()

    // Used to group continuous gesture updates (dragging) into a single undo step.
    private var gestureBaseState: WhiteboardState? = null

    fun beginGesture() {
        if (gestureBaseState == null) {
            gestureBaseState = _uiState.value.whiteboardState
        }
    }

    fun endGesture() {
        val base = gestureBaseState ?: return
        gestureBaseState = null

        val current = _uiState.value.whiteboardState
        if (current != base) {
            undoStack.addLast(base)
            redoStack.clear()
            updateUndoRedoState()
        }
    }

    private fun setState(next: WhiteboardState) {
        val current = _uiState.value.whiteboardState
        if (next == current) return

        if (gestureBaseState != null) {
            _uiState.update { it.copy(whiteboardState = next) }
            return
        }

        undoStack.addLast(current)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(whiteboardState = next) }
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _uiState.update {
            it.copy(
                isUndoEnabled = undoStack.isNotEmpty(),
                isRedoEnabled = redoStack.isNotEmpty()
            )
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.whiteboardState
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        _uiState.update { it.copy(whiteboardState = prev) }
        updateUndoRedoState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.whiteboardState
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        _uiState.update { it.copy(whiteboardState = next) }
        updateUndoRedoState()
    }

    fun setTool(tool: Tool) {
        _uiState.update { it.copy(selectedTool = tool) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(strokeWidth = width) }
    }

    fun setColor(colorHex: String) {
        _uiState.update { it.copy(selectedColor = ColorHex(colorHex)) }
    }

    fun commitStroke(points: List<Point>) {
        if (points.size < 2) return
        val stroke = StrokeEntity(
            points = points,
            color = _uiState.value.selectedColor,
            width = _uiState.value.strokeWidth
        )
        val next = _uiState.value.whiteboardState.copy(
            strokes = _uiState.value.whiteboardState.strokes + stroke
        )
        setState(next)
    }

    fun commitShape(shape: ShapeEntity) {
        val current = _uiState.value.whiteboardState
        setState(current.copy(shapes = current.shapes + shape))
    }

    fun commitText(text: TextEntity) {
        val current = _uiState.value.whiteboardState
        setState(current.copy(texts = current.texts + text))
    }

    fun findTextIndexNear(point: Point, radiusPx: Float): Int? {
        val texts = _uiState.value.whiteboardState.texts
        for (i in texts.indices.reversed()) {
            if (texts[i].hitTest(point, radiusPx)) return i
        }
        return null
    }

    fun moveText(index: Int, newPosition: Point) {
        val current = _uiState.value.whiteboardState
        setState(WhiteboardStateReducer.moveText(current, index, newPosition))
    }

    fun editText(index: Int, newValue: String) {
        val value = newValue.trim()
        if (value.isEmpty()) return
        val current = _uiState.value.whiteboardState
        if (index !in current.texts.indices) return
        val updated = current.texts.toMutableList()
        updated[index] = updated[index].copy(text = value)
        setState(current.copy(texts = updated))
    }

    fun updateText(index: Int, newValue: String, color: ColorHex, sizeSp: Float) {
        val current = _uiState.value.whiteboardState
        setState(WhiteboardStateReducer.updateText(current, index, newValue, color, sizeSp))
    }

    /**
     * Removes a character range from a text entity.
     * If the resulting string becomes empty, the whole text entity is removed.
     */
    fun eraseTextRange(index: Int, startInclusive: Int, endExclusive: Int) {
        val current = _uiState.value.whiteboardState
        setState(WhiteboardStateReducer.eraseTextRange(current, index, startInclusive, endExclusive))
    }

    fun findShapeIndexNear(point: Point, radiusPx: Float): Int? {
        val shapes = _uiState.value.whiteboardState.shapes
        for (i in shapes.indices.reversed()) {
            if (shapes[i].hitTest(point, radiusPx)) return i
        }
        return null
    }

    fun moveShape(index: Int, dx: Float, dy: Float) {
        val current = _uiState.value.whiteboardState
        setState(WhiteboardStateReducer.moveShape(current, index, dx, dy))
    }

    fun resizeShape(index: Int, newBounds: Rect) {
        val current = _uiState.value.whiteboardState
        setState(WhiteboardStateReducer.resizeShape(current, index, newBounds))
    }

    fun eraseAt(point: Point, radiusPx: Float) {
        val current = _uiState.value.whiteboardState
        setState(WhiteboardStateReducer.eraseAt(current, point, radiusPx))
    }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.save(_uiState.value.whiteboardState) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(WhiteboardUiEvent.ShowMessage("Saved", "Whiteboard saved locally."))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(WhiteboardUiEvent.ShowMessage("Save failed", error.message ?: "Unknown error"))
                }
        }
    }

    fun loadLatest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.loadLatest() }
                .onSuccess { loaded ->
                    _uiState.update { it.copy(isLoading = false, whiteboardState = loaded) }
                    _uiEvent.emit(WhiteboardUiEvent.ShowMessage("Loaded", "Latest whiteboard loaded."))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(WhiteboardUiEvent.ShowMessage("Load failed", error.message ?: "No saved files found."))
                }
        }
    }

    fun listSavedFileNames() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.listSavedFiles().map { it.name } }
                .onSuccess { names ->
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(WhiteboardUiEvent.ShowLoadDialog(names))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(WhiteboardUiEvent.ShowMessage("Load failed", error.message ?: "No saved files found."))
                }
        }
    }

    fun loadByFileName(fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.loadByFileName(fileName) }
                .onSuccess { loaded ->
                    _uiState.update { it.copy(isLoading = false, whiteboardState = loaded) }
                    _uiEvent.emit(WhiteboardUiEvent.ShowMessage("Loaded", "Loaded $fileName"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(WhiteboardUiEvent.ShowMessage("Load failed", error.message ?: "Unknown error"))
                }
        }
    }
}

data class WhiteboardUiState(
    val whiteboardState: WhiteboardState = WhiteboardState.empty(),
    val selectedTool: Tool = Tool.Draw,
    val selectedColor: ColorHex = ColorHex("#000000"),
    val strokeWidth: Float = 6f,
    val isUndoEnabled: Boolean = false,
    val isRedoEnabled: Boolean = false,
    val isLoading: Boolean = false
)

sealed interface WhiteboardUiEvent {
    data class ShowMessage(val title: String, val message: String) : WhiteboardUiEvent
    data class ShowLoadDialog(val fileNames: List<String>) : WhiteboardUiEvent
}

