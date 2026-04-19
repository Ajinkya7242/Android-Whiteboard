package com.sandblaze.whiteboard.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sandblaze.whiteboard.domain.model.ColorHex
import com.sandblaze.whiteboard.domain.model.Point
import com.sandblaze.whiteboard.domain.model.Rect
import com.sandblaze.whiteboard.domain.model.ShapeEntity
import com.sandblaze.whiteboard.domain.model.StrokeEntity
import com.sandblaze.whiteboard.domain.model.TextEntity
import com.sandblaze.whiteboard.domain.model.WhiteboardState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class LocalWhiteboardStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : WhiteboardStorage {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private fun directory(): File {
        val external = context.getExternalFilesDir("whiteboards")
        val dir = external ?: File(context.filesDir, "whiteboards")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun save(state: WhiteboardState): File {
        val name = "whiteboard_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
        val out = File(directory(), name)
        out.writeText(gson.toJson(state.toJsonObject()))
        return out
    }

    override fun listSaved(): List<File> {
        val files = directory().listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
        return files.sortedByDescending { it.lastModified() }
    }

    override fun load(file: File): WhiteboardState {
        val obj = gson.fromJson(file.readText(), JsonObject::class.java)
        return obj.toWhiteboardState()
    }

    private fun WhiteboardState.toJsonObject(): JsonObject {
        val root = JsonObject()
        val strokesArray = JsonArray()
        for (stroke in strokes) {
            val s = JsonObject()
            val pointsArr = JsonArray()
            for (p in stroke.points) {
                val pointArr = JsonArray()
                pointArr.add(p.x)
                pointArr.add(p.y)
                pointsArr.add(pointArr)
            }
            s.add("points", pointsArr)
            s.addProperty("color", stroke.color.value)
            s.addProperty("width", stroke.width)
            strokesArray.add(s)
        }
        root.add("strokes", strokesArray)

        val shapesArray = JsonArray()
        for (shape in shapes) {
            val s = JsonObject()
            when (shape) {
                is ShapeEntity.Rectangle -> {
                    s.addProperty("type", "rectangle")
                    s.add("topLeft", JsonArray().apply { add(shape.rect.left); add(shape.rect.top) })
                    s.add("bottomRight", JsonArray().apply { add(shape.rect.right); add(shape.rect.bottom) })
                    s.addProperty("color", shape.color.value)
                }
                is ShapeEntity.Circle -> {
                    s.addProperty("type", "circle")
                    s.add("center", JsonArray().apply { add(shape.center.x); add(shape.center.y) })
                    s.addProperty("radius", shape.radius)
                    s.addProperty("color", shape.color.value)
                }
                is ShapeEntity.Line -> {
                    s.addProperty("type", "line")
                    s.add("start", JsonArray().apply { add(shape.start.x); add(shape.start.y) })
                    s.add("end", JsonArray().apply { add(shape.end.x); add(shape.end.y) })
                    s.addProperty("color", shape.color.value)
                }
                is ShapeEntity.Polygon -> {
                    s.addProperty("type", "polygon")
                    s.addProperty("sides", shape.sides)
                    s.add("topLeft", JsonArray().apply { add(shape.bounds.left); add(shape.bounds.top) })
                    s.add("bottomRight", JsonArray().apply { add(shape.bounds.right); add(shape.bounds.bottom) })
                    s.addProperty("color", shape.color.value)
                }
            }
            shapesArray.add(s)
        }
        root.add("shapes", shapesArray)

        val textsArray = JsonArray()
        for (text in texts) {
            val t = JsonObject()
            t.addProperty("text", text.text)
            t.add("position", JsonArray().apply { add(text.position.x); add(text.position.y) })
            t.addProperty("color", text.color.value)
            t.addProperty("size", text.sizeSp)
            textsArray.add(t)
        }
        root.add("texts", textsArray)
        return root
    }

    private fun JsonObject.toWhiteboardState(): WhiteboardState {
        val strokes = getAsJsonArray("strokes")?.mapNotNull { el ->
            val s = el.asJsonObject
            val points = s.getAsJsonArray("points")?.mapNotNull { pEl ->
                val arr = pEl.asJsonArray
                if (arr.size() < 2) null else Point(arr[0].asFloat, arr[1].asFloat)
            }.orEmpty()
            if (points.size < 2) return@mapNotNull null
            StrokeEntity(
                points = points,
                color = ColorHex(s.get("color")?.asString ?: "#000000"),
                width = s.get("width")?.asFloat ?: 6f
            )
        }.orEmpty()

        val shapes = getAsJsonArray("shapes")?.mapNotNull { el ->
            val s = el.asJsonObject
            val type = s.get("type")?.asString ?: return@mapNotNull null
            val color = ColorHex(s.get("color")?.asString ?: "#000000")
            when (type.lowercase(Locale.US)) {
                "rectangle" -> {
                    val tl = s.getAsJsonArray("topLeft") ?: return@mapNotNull null
                    val br = s.getAsJsonArray("bottomRight") ?: return@mapNotNull null
                    ShapeEntity.Rectangle(rect = Rect(tl[0].asFloat, tl[1].asFloat, br[0].asFloat, br[1].asFloat), color = color)
                }
                "circle" -> {
                    val c = s.getAsJsonArray("center") ?: return@mapNotNull null
                    ShapeEntity.Circle(center = Point(c[0].asFloat, c[1].asFloat), radius = s.get("radius")?.asFloat ?: 0f, color = color)
                }
                "line" -> {
                    val st = s.getAsJsonArray("start") ?: return@mapNotNull null
                    val en = s.getAsJsonArray("end") ?: return@mapNotNull null
                    ShapeEntity.Line(start = Point(st[0].asFloat, st[1].asFloat), end = Point(en[0].asFloat, en[1].asFloat), color = color)
                }
                "polygon" -> {
                    val tl = s.getAsJsonArray("topLeft") ?: return@mapNotNull null
                    val br = s.getAsJsonArray("bottomRight") ?: return@mapNotNull null
                    ShapeEntity.Polygon(bounds = Rect(tl[0].asFloat, tl[1].asFloat, br[0].asFloat, br[1].asFloat), sides = s.get("sides")?.asInt ?: 5, color = color)
                }
                else -> null
            }
        }.orEmpty()

        val texts = getAsJsonArray("texts")?.mapNotNull { el ->
            val t = el.asJsonObject
            val pos = t.getAsJsonArray("position") ?: return@mapNotNull null
            TextEntity(
                text = t.get("text")?.asString.orEmpty(),
                position = Point(pos[0].asFloat, pos[1].asFloat),
                color = ColorHex(t.get("color")?.asString ?: "#000000"),
                sizeSp = t.get("size")?.asFloat ?: 24f
            )
        }.orEmpty()

        return WhiteboardState(strokes = strokes, shapes = shapes, texts = texts)
    }
}
