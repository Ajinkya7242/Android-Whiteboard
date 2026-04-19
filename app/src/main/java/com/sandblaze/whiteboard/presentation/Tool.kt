package com.sandblaze.whiteboard.presentation

sealed interface Tool {
    data object Draw : Tool
    data object Eraser : Tool
    data object Text : Tool
    data class Shape(val type: ShapeType) : Tool
}

sealed interface ShapeType {
    data object Rectangle : ShapeType
    data object Circle : ShapeType
    data object Line : ShapeType
    data class Polygon(val sides: Int) : ShapeType
}
