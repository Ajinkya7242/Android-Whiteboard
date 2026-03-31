package com.sandblaze.whiteboard.presentation

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withSave
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sandblaze.whiteboard.domain.model.ColorHex
import com.sandblaze.whiteboard.domain.model.Point
import com.sandblaze.whiteboard.domain.model.Rect
import com.sandblaze.whiteboard.domain.model.ShapeEntity
import com.sandblaze.whiteboard.domain.model.TextEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

class WhiteboardCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var viewModel: WhiteboardViewModel? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 6f
        color = Color.BLACK
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        textSize = 48f
    }
    private val eraseTextMeasurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val eraserHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(160, 255, 193, 7) // amber-ish
    }

    private val previewPath = Path()
    private val currentPoints = ArrayList<Point>(256)

    private var activeTool: Tool = Tool.Draw
    private var activeColor: ColorHex = ColorHex("#000000")
    private var activeStrokeWidthPx: Float = 6f
    // Infinite-canvas style viewport offset in world coordinates.
    private var viewportOffsetX: Float = 0f
    private var viewportOffsetY: Float = 0f
    private var viewportScale: Float = 1f
    private var isPanning: Boolean = false
    private var lastPanFocusX: Float = 0f
    private var lastPanFocusY: Float = 0f
    private var lastPinchDistance: Float = 0f

    private var shapeStart: Point? = null
    private var shapePreview: ShapeEntity? = null

    private var draggingTextIndex: Int? = null
    private var draggingTextOffset: Point? = null

    private var selectedShapeIndex: Int? = null
    private var shapeLastTouch: Point? = null
    private var shapeResizeFixedCorner: Point? = null

    private var erasingTextIndex: Int? = null
    private var erasingTextDownPoint: Point? = null
    private var eraseHighlightCenter: Point? = null
    private var eraseHighlightRadiusPx: Float = 0f
    private var lastEraseProcessPoint: Point? = null

    fun bind(viewModel: WhiteboardViewModel) {
        this.viewModel = viewModel
        this.lifecycleOwner = findViewTreeLifecycleOwner()
        val owner = lifecycleOwner ?: return

        owner.lifecycleScope.launch {
            combine(viewModel.state, viewModel.tool, viewModel.strokeWidth, viewModel.color) { state, tool, width, color ->
                Quad(state, tool, width, color)
            }.collect { quad ->
                activeTool = quad.tool
                activeStrokeWidthPx = quad.width
                activeColor = quad.color
                invalidate()
            }
        }
    }



    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val vm = viewModel ?: return
        val state = vm.state.value
        canvas.withSave {
            canvas.scale(viewportScale, viewportScale)
            canvas.translate(-viewportOffsetX, -viewportOffsetY)

            // Strokes
            for (stroke in state.strokes) {
                drawPaint.color = Color.parseColor(stroke.color.value)
                drawPaint.strokeWidth = stroke.width
                val p = Path()
                val points = stroke.points
                if (points.isNotEmpty()) {
                    p.moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val cur = points[i]
                        val midX = (prev.x + cur.x) / 2f
                        val midY = (prev.y + cur.y) / 2f
                        p.quadTo(prev.x, prev.y, midX, midY)
                    }
                }
                canvas.drawPath(p, drawPaint)
            }

            // Shapes
            shapePaint.strokeWidth = max(2f, activeStrokeWidthPx)
            for (shape in state.shapes) {
                shape.draw(canvas, shapePaint)
            }

            // Selection outline + corner handles (for shape move/resize).
            val selectedIdx = selectedShapeIndex
            if (selectedIdx != null && selectedIdx in state.shapes.indices) {
                val bounds = ShapeInteraction.bounds(state.shapes[selectedIdx])
                val selectionColor = Color.parseColor("#FFD54F")
                val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = selectionColor
                    strokeWidth = max(3f, activeStrokeWidthPx * 0.7f)
                    pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    alpha = 220
                }
                canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, selectionPaint)

                val handleR = max(8f, activeStrokeWidthPx * 0.9f)
                canvas.drawCircle(bounds.left, bounds.top, handleR, selectionPaint)
                canvas.drawCircle(bounds.right, bounds.top, handleR, selectionPaint)
                canvas.drawCircle(bounds.left, bounds.bottom, handleR, selectionPaint)
                canvas.drawCircle(bounds.right, bounds.bottom, handleR, selectionPaint)
            }

            shapePreview?.let { preview ->
                canvas.withSave {
                    shapePaint.alpha = 160
                    preview.draw(canvas, shapePaint)
                    shapePaint.alpha = 255
                }
            }

            // Text
            for (text in state.texts) {
                textPaint.color = Color.parseColor(text.color.value)
                textPaint.textSize = text.sizeSpToPx(resources.displayMetrics.scaledDensity)
                textPaint.isSubpixelText = true
                textPaint.isLinearText = true

                val fm = textPaint.fontMetrics          // ← capture AFTER textSize is set
                val lineHeight = (fm.descent - fm.ascent) * 1.2f
                val baseline = text.position.y - fm.ascent   // first line baseline
                val lines = text.text.split('\n')

                for (i in lines.indices) {
                    canvas.drawText(lines[i], text.position.x, baseline + (i * lineHeight), textPaint)
                }
            }

            // In-progress drawing
            if (!previewPath.isEmpty) {
                drawPaint.color = Color.parseColor(activeColor.value)
                drawPaint.strokeWidth = activeStrokeWidthPx
                canvas.drawPath(previewPath, drawPaint)
            }

            // Eraser cursor highlight (optional UX requirement).
            if (activeTool == Tool.Eraser && eraseHighlightCenter != null) {
                val center = eraseHighlightCenter!!
                val r = eraseHighlightRadiusPx
                eraserHighlightPaint.strokeWidth = max(2f, activeStrokeWidthPx * 0.35f)
                canvas.drawCircle(center.x, center.y, r, eraserHighlightPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val vm = viewModel ?: return false
        if (event.pointerCount >= 2 || isPanning) {
            return handlePan(event)
        }

        val point = screenToWorld(event.x, event.y)

        when (activeTool) {
            Tool.Draw -> handleDraw(event, point, vm)
            Tool.Eraser -> handleEraser(event, point, vm)
            Tool.Text -> handleText(event, point, vm)
            is Tool.Shape -> handleShape(event, point, vm, (activeTool as Tool.Shape).type)
        }

        return true
    }

    private fun handlePan(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount >= 2) {
                    isPanning = true
                    lastPanFocusX = focusX(event)
                    lastPanFocusY = focusY(event)
                    lastPinchDistance = pinchDistance(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isPanning || event.pointerCount < 2) return true
                val fx = focusX(event)
                val fy = focusY(event)
                val worldFocusBefore = screenToWorld(fx, fy)

                val currentDistance = pinchDistance(event)
                if (lastPinchDistance > 0f && currentDistance > 0f) {
                    val scaleFactor = currentDistance / lastPinchDistance
                    viewportScale = (viewportScale * scaleFactor).coerceIn(0.5f, 3f)
                    val worldFocusAfterScale = Point(
                        x = fx / viewportScale + viewportOffsetX,
                        y = fy / viewportScale + viewportOffsetY
                    )
                    viewportOffsetX += (worldFocusBefore.x - worldFocusAfterScale.x)
                    viewportOffsetY += (worldFocusBefore.y - worldFocusAfterScale.y)
                }

                val dx = fx - lastPanFocusX
                val dy = fy - lastPanFocusY
                // Move content with fingers: shift viewport opposite to finger movement.
                viewportOffsetX -= dx / viewportScale
                viewportOffsetY -= dy / viewportScale
                lastPanFocusX = fx
                lastPanFocusY = fy
                lastPinchDistance = currentDistance
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    isPanning = false
                    lastPinchDistance = 0f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                lastPinchDistance = 0f
            }
        }
        return true
    }

    private fun focusX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getX(i)
        return sum / event.pointerCount
    }

    private fun focusY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getY(i)
        return sum / event.pointerCount
    }

    private fun pinchDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun screenToWorld(screenX: Float, screenY: Float): Point {
        return Point(
            x = screenX / viewportScale + viewportOffsetX,
            y = screenY / viewportScale + viewportOffsetY
        )
    }

    private fun handleDraw(event: MotionEvent, point: Point, vm: WhiteboardViewModel) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                previewPath.reset()
                currentPoints.add(point)
                previewPath.moveTo(point.x, point.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val last = currentPoints.lastOrNull() ?: return
                if (abs(point.x - last.x) + abs(point.y - last.y) < 2f) return
                currentPoints.add(point)
                val midX = (last.x + point.x) / 2f
                val midY = (last.y + point.y) / 2f
                previewPath.quadTo(last.x, last.y, midX, midY)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vm.commitStroke(currentPoints.toList())
                currentPoints.clear()
                previewPath.reset()
                invalidate()
            }
        }
    }

    private fun handleEraser(event: MotionEvent, point: Point, vm: WhiteboardViewModel) {
        val eraseRadius = max(18f, activeStrokeWidthPx * 1.8f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                vm.beginGesture()
                eraseHighlightCenter = point
                eraseHighlightRadiusPx = eraseRadius
                lastEraseProcessPoint = point

                // Use bounding-box hit test instead of radius from anchor point
                val textIdx = findTextIndexAtPoint(point, vm)
                if (textIdx != null) {
                    erasingTextIndex = textIdx
                    erasingTextDownPoint = point

                    val deleted = charIndexForErase(
                        point,
                        vm.state.value.texts[textIdx],
                        scaledDensity = resources.displayMetrics.scaledDensity
                    )
                    deleted?.let { range ->
                        vm.eraseTextRange(textIdx, range.startInclusive, range.endExclusive)
                    }
                } else {
                    erasingTextIndex = null
                    erasingTextDownPoint = null
                    vm.eraseAt(point, radiusPx = eraseRadius)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                eraseHighlightCenter = point
                eraseHighlightRadiusPx = eraseRadius

                val last = lastEraseProcessPoint
                if (last != null) {
                    val dx = point.x - last.x
                    val dy = point.y - last.y
                    if ((dx * dx + dy * dy) < 36f) {
                        invalidate()
                        return
                    }
                }
                lastEraseProcessPoint = point

                // Use bounding-box hit test here too
                val textIdx = findTextIndexAtPoint(point, vm)
                if (textIdx != null) {
                    val activeIdx = erasingTextIndex ?: textIdx
                    val text = vm.state.value.texts.getOrNull(activeIdx) ?: run {
                        // activeIdx may be stale if chars were deleted; fall back to current hit
                        val t = vm.state.value.texts.getOrNull(textIdx) ?: return
                        val range = charIndexForErase(point, t, resources.displayMetrics.scaledDensity) ?: return
                        vm.eraseTextRange(textIdx, range.startInclusive, range.endExclusive)
                        return
                    }
                    val range = charIndexForErase(
                        point,
                        text,
                        scaledDensity = resources.displayMetrics.scaledDensity
                    ) ?: return
                    vm.eraseTextRange(activeIdx, range.startInclusive, range.endExclusive)
                } else {
                    erasingTextIndex = null
                    erasingTextDownPoint = null
                    vm.eraseAt(point, radiusPx = eraseRadius)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                eraseHighlightCenter = null
                lastEraseProcessPoint = null

                val idx = erasingTextIndex
                if (idx != null) {
                    val down = erasingTextDownPoint ?: point
                    val movedDist2 = (point.x - down.x) * (point.x - down.x) +
                            (point.y - down.y) * (point.y - down.y)
                    // Tap (no drag) = delete entire text entity
                    if (movedDist2 < 16f * 16f) {
                        vm.eraseTextRange(idx, 0, Int.MAX_VALUE)
                    }
                }
                erasingTextIndex = null
                erasingTextDownPoint = null
                vm.endGesture()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                eraseHighlightCenter = null
                lastEraseProcessPoint = null
                erasingTextIndex = null
                erasingTextDownPoint = null
                vm.endGesture()
                invalidate()
            }
        }
    }



    private data class CharEraseRange(val startInclusive: Int, val endExclusive: Int)

    /**
     * Maps a touch point to a character deletion range for a single text entity.
     * This is an approximation of "pixel-based partial erasing" by deleting characters near the touch.
     */
    private fun charIndexForErase(
        touchPoint: Point,
        text: TextEntity,
        scaledDensity: Float
    ): CharEraseRange? {
        if (text.text.isEmpty()) return null

        val paint = eraseTextMeasurePaint
        paint.color = Color.parseColor(text.color.value)
        paint.textSize = text.sizeSpToPx(scaledDensity)

        val fm = paint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.2f
        // text.position.y is the TOP of the first line (same as onDraw: baseline = position.y - ascent)
        val firstBaseline = text.position.y - fm.ascent
        val firstLineTop = firstBaseline + fm.ascent   // == text.position.y

        val lines = text.text.split('\n')

        // 1. Find which line the touch Y falls on
        val lineIndex = ((touchPoint.y - firstLineTop) / lineHeight).toInt()
        if (lineIndex < 0 || lineIndex >= lines.size) return null

        // 2. Check touch Y is actually within that line's vertical bounds (with tolerance)
        val lineTop = firstLineTop + lineIndex * lineHeight
        val lineBottom = lineTop + lineHeight
        val yTolerance = lineHeight * 0.4f
        if (touchPoint.y < lineTop - yTolerance || touchPoint.y > lineBottom + yTolerance) return null

        // 3. Find which character within that line the touch X hits
        val line = lines[lineIndex]
        val relX = touchPoint.x - text.position.x
        if (relX < -40f || relX > paint.measureText(line) + 40f) return null

        // Calculate the absolute character offset into the full string
        var charOffsetInFullString = 0
        for (i in 0 until lineIndex) {
            charOffsetInFullString += lines[i].length + 1 // +1 for the '\n'
        }

        // Walk characters in this line to find which one was touched
        var accumulated = 0f
        for (i in line.indices) {
            val w = paint.measureText(line[i].toString())
            if (relX >= accumulated && relX <= accumulated + w) {
                val absIndex = charOffsetInFullString + i
                return CharEraseRange(absIndex, absIndex + 1)
            }
            accumulated += w
        }

        // Touch is past the last character of this line — delete last char of line
        if (line.isNotEmpty()) {
            val lastIdx = charOffsetInFullString + line.length - 1
            return CharEraseRange(lastIdx, lastIdx + 1)
        }
        // Line is empty (just a '\n') — delete the newline itself
        return CharEraseRange(charOffsetInFullString, charOffsetInFullString + 1)
            .takeIf { charOffsetInFullString < text.text.length }
    }


    private fun findTextIndexAtPoint(point: Point, vm: WhiteboardViewModel): Int? {
        val texts = vm.state.value.texts
        val scaledDensity = resources.displayMetrics.scaledDensity

        // Check in reverse so topmost text gets hit first
        for (i in texts.indices.reversed()) {
            val text = texts[i]
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = text.sizeSpToPx(scaledDensity)
            }

            val lines = text.text.split('\n')
            val lineHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent) * 1.2f
            val totalHeight = lineHeight * lines.size
            val textWidth = lines.maxOf { paint.measureText(it) }

            // Build a generous bounding box around the text
            val padding = 20f
            val left = text.position.x - padding
            val top = text.position.y + paint.fontMetrics.ascent - padding
            val right = text.position.x + textWidth + padding
            val bottom = text.position.y + totalHeight + padding

            android.util.Log.d("TextDebug", "Text '${ text.text}' bounds: ($left,$top) -> ($right,$bottom), touch: (${point.x},${point.y})")

            if (point.x in left..right && point.y in top..bottom) {
                return i
            }
        }
        return null
    }

    private var textDragDidMove: Boolean = false
    private var textDragDownPoint: Point? = null  // tracks original finger-down position

    private fun handleText(event: MotionEvent, point: Point, vm: WhiteboardViewModel) {
        val hitRadius = max(24f, activeStrokeWidthPx * 2f)
        val dragThresholdPx = 10f



        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                android.util.Log.d("TextDebug", "Touch down at world: $point")
                android.util.Log.d("TextDebug", "All texts: ${vm.state.value.texts.map { "${it.text} @ ${it.position}" }}")

                val idx = findTextIndexAtPoint(point, vm)  // ← use this instead of vm.findTextIndexNear
                android.util.Log.d("TextDebug", "Hit index: $idx")

                if (idx != null) {
                    val text = vm.state.value.texts[idx]
                    draggingTextIndex = idx
                    draggingTextOffset = Point(point.x - text.position.x, point.y - text.position.y)
                    textDragDidMove = false
                    textDragDownPoint = point
                    vm.beginGesture()
                } else {
                    draggingTextIndex = null
                    draggingTextOffset = null
                    textDragDidMove = false
                    textDragDownPoint = null
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val idx = draggingTextIndex ?: return
                val offset = draggingTextOffset ?: Point(0f, 0f)
                val downPoint = textDragDownPoint ?: return

                // Measure distance from original finger-down, not from current text position
                val dx = point.x - downPoint.x
                val dy = point.y - downPoint.y
                val distFromDown = kotlin.math.sqrt(dx * dx + dy * dy)

                if (distFromDown > dragThresholdPx) {
                    textDragDidMove = true
                }

                if (textDragDidMove) {
                    val newPos = Point(point.x - offset.x, point.y - offset.y)
                    vm.moveText(idx, newPos)
                }
            }

            MotionEvent.ACTION_UP -> {
                val idx = draggingTextIndex
                draggingTextIndex = null
                draggingTextOffset = null
                val didMove = textDragDidMove
                textDragDidMove = false
                textDragDownPoint = null

                if (idx != null) {
                    vm.endGesture()
                    if (!didMove) {
                        // True tap — open edit dialog
                        showEditTextDialog(vm, index = idx, anchor = point)
                    }
                } else {
                    // Tapped empty space — insert new text
                    showInsertTextDialog(vm, point)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (draggingTextIndex != null) vm.endGesture()
                draggingTextIndex = null
                draggingTextOffset = null
                textDragDidMove = false
                textDragDownPoint = null
            }
        }
    }

    private fun showInsertTextDialog(vm: WhiteboardViewModel, point: Point) {
        showTextEditorDialog(
            context = context,
            title = "Insert text",
            positiveButtonText = "Add",
            initialText = "",
            initialColor = activeColor,
            initialSizeSp = 24f,
            onSubmit = { result ->
                vm.commitText(
                    TextEntity(
                        text = result.text,
                        position = point,
                        color = result.color,
                        sizeSp = result.sizeSp
                    )
                )
            }
        )
    }

    private fun showEditTextDialog(vm: WhiteboardViewModel, index: Int, anchor: Point) {
        val existing = vm.state.value.texts.getOrNull(index) ?: return
        showTextEditorDialog(
            context = context,
            title = "Edit text",
            positiveButtonText = "Update",
            initialText = existing.text,
            initialColor = existing.color,
            initialSizeSp = existing.sizeSp,
            onSubmit = { result ->
                if (result.text.isEmpty()) {
                    // Empty text = delete the entity
                    vm.eraseTextRange(index, 0, Int.MAX_VALUE)
                } else {
                    vm.updateText(index, result.text, result.color, result.sizeSp)
                }
            },
            onDelete = {
                vm.eraseAt(anchor, radiusPx = max(36f, activeStrokeWidthPx * 2.2f))
            }
        )
    }


    private fun handleShape(event: MotionEvent, point: Point, vm: WhiteboardViewModel, type: ShapeType) {
        val hitRadius = max(28f, activeStrokeWidthPx * 2.2f)
        val handleTol = max(18f, activeStrokeWidthPx * 2.6f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val selectedIdx = vm.findShapeIndexNear(point, hitRadius)
                if (selectedIdx != null) {
                    selectedShapeIndex = selectedIdx
                    shapeLastTouch = point
                    shapeStart = null
                    shapePreview = null

                    vm.beginGesture()

                    val shape = vm.state.value.shapes[selectedIdx]
                    val bounds = ShapeInteraction.bounds(shape)
                    val fixed = ShapeInteraction.fixedCornerForResize(point, bounds, handleTol)
                    shapeResizeFixedCorner = fixed
                } else {
                    selectedShapeIndex = null
                    shapeResizeFixedCorner = null
                    shapeLastTouch = null
                    shapeStart = point
                    shapePreview = null
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = selectedShapeIndex
                if (idx != null) {
                    val fixedCorner = shapeResizeFixedCorner
                    if (fixedCorner != null) {
                        val newBounds = Rect(
                            left = kotlin.math.min(fixedCorner.x, point.x),
                            top = kotlin.math.min(fixedCorner.y, point.y),
                            right = kotlin.math.max(fixedCorner.x, point.x),
                            bottom = kotlin.math.max(fixedCorner.y, point.y)
                        )
                        vm.resizeShape(idx, newBounds)
                    } else {
                        val last = shapeLastTouch ?: point
                        val dx = point.x - last.x
                        val dy = point.y - last.y
                        vm.moveShape(idx, dx, dy)
                        shapeLastTouch = point
                    }
                    invalidate()
                } else {
                    val start = shapeStart ?: return
                    shapePreview = ShapeInteraction.shapeFromDrag(type, start, point, activeColor)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = selectedShapeIndex
                if (idx != null) {
                    vm.endGesture()
                    selectedShapeIndex = null
                    shapeLastTouch = null
                    shapeResizeFixedCorner = null
                    invalidate()
                    return
                }

                val start = shapeStart
                val end = point
                if (start != null) {
                    val committed = ShapeInteraction.shapeFromDrag(type, start, end, activeColor)
                    vm.commitShape(committed)
                }
                shapeStart = null
                shapePreview = null
                invalidate()
            }
        }
    }

    private data class Quad(
        val state: com.sandblaze.whiteboard.domain.model.WhiteboardState,
        val tool: Tool,
        val width: Float,
        val color: ColorHex
    )

}

