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
import kotlin.math.min

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

    fun exportToPng(): File {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) {
            throw IllegalStateException("Canvas not measured yet.")
        }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        draw(canvas)

        val dir = context.getExternalFilesDir("whiteboards") ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val name = "whiteboard_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
        val out = File(dir, name)
        FileOutputStream(out).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        return out
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val vm = viewModel ?: return
        val state = vm.state.value

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
            drawShape(canvas, shapePaint, shape)
        }

        // Selection outline + corner handles (for shape move/resize).
        val selectedIdx = selectedShapeIndex
        if (selectedIdx != null && selectedIdx in state.shapes.indices) {
            val bounds = shapeBounds(state.shapes[selectedIdx])
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
                drawShape(canvas, shapePaint, preview)
                shapePaint.alpha = 255
            }
        }

        // Text
        for (text in state.texts) {
            textPaint.color = Color.parseColor(text.color.value)
            textPaint.textSize = text.sizeSpToPx(resources.displayMetrics.scaledDensity)
            canvas.drawText(text.text, text.position.x, text.position.y, textPaint)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val vm = viewModel ?: return false
        val x = event.x
        val y = event.y
        val point = Point(x, y)

        when (activeTool) {
            Tool.Draw -> handleDraw(event, point, vm)
            Tool.Eraser -> handleEraser(event, point, vm)
            Tool.Text -> handleText(event, point, vm)
            is Tool.Shape -> handleShape(event, point, vm, (activeTool as Tool.Shape).type)
        }

        return true
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

                val textIdx = vm.findTextIndexNear(point, radiusPx = max(24f, eraseRadius * 0.9f))
                if (textIdx != null) {
                    // If we are erasing a text: tap deletes full text, drag deletes chars near touch.
                    erasingTextIndex = textIdx
                    erasingTextDownPoint = point

                    // For immediate feedback, delete a single character under the initial touch.
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
            }
            MotionEvent.ACTION_MOVE -> {
                eraseHighlightCenter = point
                eraseHighlightRadiusPx = eraseRadius

                val last = lastEraseProcessPoint
                if (last != null) {
                    val dx = point.x - last.x
                    val dy = point.y - last.y
                    // Skip very tiny move deltas to reduce erase churn on large canvases.
                    if ((dx * dx + dy * dy) < 36f) {
                        invalidate()
                        return
                    }
                }
                lastEraseProcessPoint = point

                val textIdx = vm.findTextIndexNear(point, radiusPx = max(24f, eraseRadius * 0.9f))
                if (textIdx != null) {
                    // ACTION_MOVE: partial erase while dragging.
                    val activeIdx = erasingTextIndex ?: textIdx
                    val text = vm.state.value.texts.getOrNull(activeIdx) ?: return
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
            }
            MotionEvent.ACTION_UP -> {
                eraseHighlightCenter = null
                eraseHighlightRadiusPx = eraseRadius
                lastEraseProcessPoint = null

                val idx = erasingTextIndex
                if (idx != null) {
                    val down = erasingTextDownPoint ?: point
                    val movedDist2 = (point.x - down.x).let { dx -> dx * dx } + (point.y - down.y).let { dy -> dy * dy }
                    // If user "tapped" on the text (no drag), remove whole text entity.
                    if (movedDist2 < 16f * 16f) {
                        vm.eraseTextRange(idx, 0, Int.MAX_VALUE)
                    }
                }
                erasingTextIndex = null
                erasingTextDownPoint = null
                vm.endGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                eraseHighlightCenter = null
                lastEraseProcessPoint = null
                erasingTextIndex = null
                erasingTextDownPoint = null
                vm.endGesture()
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

        // Android draws text with the baseline at y; we use the insertion point as baseline (same as drawText()).
        val touchX = touchPoint.x
        val startX = text.position.x
        val relX = touchX - startX
        if (relX < -40f || relX > paint.measureText(text.text) + 40f) return null

        var accumulated = 0f
        var index = 0
        for (ch in text.text) {
            val chStr = ch.toString()
            val w = paint.measureText(chStr)
            if (relX >= accumulated && relX <= accumulated + w) {
                // Delete around this character index (1 char).
                return CharEraseRange(startInclusive = index, endExclusive = index + 1)
            }
            accumulated += w
            index++
        }

        // If we fall outside, delete last char if touch is near the end.
        return CharEraseRange(startInclusive = text.text.length - 1, endExclusive = text.text.length)
    }

    private fun handleText(event: MotionEvent, point: Point, vm: WhiteboardViewModel) {
        val hitRadius = max(24f, activeStrokeWidthPx * 2f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = vm.findTextIndexNear(point, hitRadius)
                if (idx != null) {
                    val text = vm.state.value.texts[idx]
                    draggingTextIndex = idx
                    draggingTextOffset = Point(point.x - text.position.x, point.y - text.position.y)
                    vm.beginGesture()
                } else {
                    draggingTextIndex = null
                    draggingTextOffset = null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = draggingTextIndex ?: return
                val offset = draggingTextOffset ?: Point(0f, 0f)
                vm.moveText(idx, Point(point.x - offset.x, point.y - offset.y))
            }
            MotionEvent.ACTION_UP -> {
                val idx = draggingTextIndex
                val moved = idx != null
                draggingTextIndex = null
                draggingTextOffset = null

                if (idx != null) {
                    vm.endGesture()
                }

                if (moved) {
                    // Tap-to-edit if finger didn't move much: treat as "edit existing".
                    if (event.eventTime - event.downTime < 220) {
                        showEditTextDialog(vm, index = idx!!, anchor = point)
                    }
                    return
                }

                // Insert new
                showInsertTextDialog(vm, point)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (draggingTextIndex != null) {
                    vm.endGesture()
                }
                draggingTextIndex = null
                draggingTextOffset = null
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
                vm.updateText(index, result.text, result.color, result.sizeSp)
            },
            onDelete = {
                // Delete by erasing at anchor point
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
                    val bounds = shapeBounds(shape)
                    val fixed = fixedCornerForResize(point, bounds, handleTol)
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
                            left = min(fixedCorner.x, point.x),
                            top = min(fixedCorner.y, point.y),
                            right = max(fixedCorner.x, point.x),
                            bottom = max(fixedCorner.y, point.y)
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
                    shapePreview = shapeFromDrag(type, start, point, activeColor)
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
                    val committed = shapeFromDrag(type, start, end, activeColor)
                    vm.commitShape(committed)
                }
                shapeStart = null
                shapePreview = null
                invalidate()
            }
        }
    }

    private fun shapeBounds(shape: ShapeEntity): Rect = when (shape) {
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

    /**
     * Returns the opposite corner to the one near which the user touched.
     * When the finger drags, we build a new rect from (fixedCorner, currentTouch).
     */
    private fun fixedCornerForResize(touch: Point, bounds: Rect, tolerancePx: Float): Point? {
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
            else -> tl // dBR
        }
    }

    private fun shapeFromDrag(type: ShapeType, start: Point, end: Point, color: ColorHex): ShapeEntity {
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

    private data class Quad(
        val state: com.sandblaze.whiteboard.domain.model.WhiteboardState,
        val tool: Tool,
        val width: Float,
        val color: ColorHex
    )

    private fun drawShape(canvas: Canvas, paint: Paint, shape: ShapeEntity) {
        paint.color = Color.parseColor(shape.color.value)
        when (shape) {
            is ShapeEntity.Rectangle -> {
                canvas.drawRect(shape.rect.left, shape.rect.top, shape.rect.right, shape.rect.bottom, paint)
            }
            is ShapeEntity.Circle -> {
                canvas.drawCircle(shape.center.x, shape.center.y, shape.radius, paint)
            }
            is ShapeEntity.Line -> {
                canvas.drawLine(shape.start.x, shape.start.y, shape.end.x, shape.end.y, paint)
            }
            is ShapeEntity.Polygon -> {
                val vertices = shape.vertices()
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
}

