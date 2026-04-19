package com.sandblaze.whiteboard.presentation

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import com.google.android.material.button.MaterialButton
import com.sandblaze.whiteboard.R
import com.sandblaze.whiteboard.databinding.ActivityMainBinding

class ToolbarManager(
    private val binding: ActivityMainBinding,
    private val onToolSelected: (Tool) -> Unit
) {
    private var isExpanded: Boolean = false

    init {
        setupClickListeners()
        updateToggleUi()
    }

    private fun setupClickListeners() {
        binding.btnToggleToolbar.setOnClickListener {
            isExpanded = !isExpanded
            binding.toolbarContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.toolbarCollapsedQuickBar.visibility = if (isExpanded) View.GONE else View.VISIBLE
            updateToggleUi()
        }

        val toolMap = mapOf(
            binding.btnDraw to Tool.Draw,
            binding.btnEraser to Tool.Eraser,
            binding.btnText to Tool.Text,
            binding.btnQuickDraw to Tool.Draw,
            binding.btnQuickEraser to Tool.Eraser,
            binding.btnQuickText to Tool.Text,
            binding.btnRect to Tool.Shape(ShapeType.Rectangle),
            binding.btnCircle to Tool.Shape(ShapeType.Circle),
            binding.btnLine to Tool.Shape(ShapeType.Line),
            binding.btnPolygon to Tool.Shape(ShapeType.Polygon(sides = 5))
        )

        for ((button, tool) in toolMap) {
            button.setOnClickListener { onToolSelected(tool) }
        }
    }

    private fun updateToggleUi() {
        binding.btnToggleToolbar.setIconResource(
            if (isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up
        )
        val textRes = if (isExpanded) R.string.toolbar_shrink else R.string.toolbar_expand
        binding.btnToggleToolbar.text = binding.root.context.getString(textRes)
    }

    fun updateSelectedTool(tool: Tool) {
        val selectedButtons = getButtonsForTool(tool)
        val toolButtons = listOf(
            binding.btnDraw, binding.btnEraser, binding.btnText,
            binding.btnQuickDraw, binding.btnQuickEraser, binding.btnQuickText,
            binding.btnRect, binding.btnCircle, binding.btnLine, binding.btnPolygon
        )

        val context = binding.root.context
        val selectedBg = ColorStateList.valueOf(context.getColor(R.color.tool_selected_bg))
        val selectedStroke = ColorStateList.valueOf(context.getColor(R.color.tool_selected_stroke))
        val selectedText = ColorStateList.valueOf(context.getColor(R.color.tool_selected_text))
        val normalBg = ColorStateList.valueOf(Color.TRANSPARENT)
        val normalStroke = ColorStateList.valueOf(context.getColor(R.color.tool_unselected_stroke))
        val normalText = ColorStateList.valueOf(context.getColor(R.color.tool_unselected_text))

        for (button in toolButtons) {
            val isSelected = button in selectedButtons
            button.isChecked = isSelected
            button.backgroundTintList = if (isSelected) selectedBg else normalBg
            button.strokeColor = if (isSelected) selectedStroke else normalStroke
            button.setTextColor(if (isSelected) selectedText else normalText)
            button.iconTint = if (isSelected) selectedText else normalText
            button.strokeWidth = if (isSelected) 3 else 2
        }

        updateCollapsedQuickBar(tool)
    }

    private fun getButtonsForTool(tool: Tool): List<MaterialButton> {
        return when (tool) {
            Tool.Draw -> listOf(binding.btnDraw, binding.btnQuickDraw)
            Tool.Eraser -> listOf(binding.btnEraser, binding.btnQuickEraser)
            Tool.Text -> listOf(binding.btnText, binding.btnQuickText)
            is Tool.Shape -> when (tool.type) {
                ShapeType.Rectangle -> listOf(binding.btnRect)
                ShapeType.Circle -> listOf(binding.btnCircle)
                ShapeType.Line -> listOf(binding.btnLine)
                is ShapeType.Polygon -> listOf(binding.btnPolygon)
            }
        }
    }

    private fun updateCollapsedQuickBar(tool: Tool) {
        val iconRes = when (tool) {
            Tool.Draw -> R.drawable.ic_tool_draw
            Tool.Eraser -> R.drawable.ic_tool_eraser
            Tool.Text -> R.drawable.ic_tool_text
            is Tool.Shape -> when (tool.type) {
                ShapeType.Rectangle -> R.drawable.ic_tool_rect
                ShapeType.Circle -> R.drawable.ic_tool_circle
                ShapeType.Line -> R.drawable.ic_tool_line
                is ShapeType.Polygon -> R.drawable.ic_tool_polygon
            }
        }
        val nameRes = when (tool) {
            Tool.Draw -> R.string.tool_draw
            Tool.Eraser -> R.string.tool_eraser
            Tool.Text -> R.string.tool_text
            is Tool.Shape -> when (tool.type) {
                ShapeType.Rectangle -> R.string.tool_rectangle
                ShapeType.Circle -> R.string.tool_circle
                ShapeType.Line -> R.string.tool_line
                is ShapeType.Polygon -> R.string.tool_polygon
            }
        }
        binding.ivCollapsedCurrentTool.setImageResource(iconRes)
        binding.tvCollapsedCurrentTool.text = binding.root.context.getString(nameRes)
    }
}
