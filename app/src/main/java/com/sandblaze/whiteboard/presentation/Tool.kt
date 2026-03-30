package com.sandblaze.whiteboard.presentation

sealed interface Tool {
    object Draw : Tool
    object Eraser : Tool
    object Text : Tool
    data class Shape(val type: ShapeType) : Tool
}

sealed interface ShapeType {
    object Rectangle : ShapeType
    object Circle : ShapeType
    object Line : ShapeType
    data class Polygon(val sides: Int) : ShapeType
}

