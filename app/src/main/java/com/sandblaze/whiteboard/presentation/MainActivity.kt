package com.sandblaze.whiteboard.presentation

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sandblaze.whiteboard.R
import com.sandblaze.whiteboard.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WhiteboardViewModel by viewModels()
    private var isToolbarExpanded: Boolean = false
    private var selectedColorHex: String = "#000000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.whiteboardView.bind(viewModel)

        binding.btnDraw.setOnClickListener { viewModel.setTool(Tool.Draw) }
        binding.btnEraser.setOnClickListener { viewModel.setTool(Tool.Eraser) }
        binding.btnText.setOnClickListener { viewModel.setTool(Tool.Text) }
        binding.btnQuickDraw.setOnClickListener { viewModel.setTool(Tool.Draw) }
        binding.btnQuickEraser.setOnClickListener { viewModel.setTool(Tool.Eraser) }
        binding.btnQuickText.setOnClickListener { viewModel.setTool(Tool.Text) }
        binding.btnQuickDraw.contentDescription = getString(R.string.tool_draw)
        binding.btnQuickEraser.contentDescription = getString(R.string.tool_eraser)
        binding.btnQuickText.contentDescription = getString(R.string.tool_text)
        binding.btnRect.setOnClickListener { viewModel.setTool(Tool.Shape(ShapeType.Rectangle)) }
        binding.btnCircle.setOnClickListener { viewModel.setTool(Tool.Shape(ShapeType.Circle)) }
        binding.btnLine.setOnClickListener { viewModel.setTool(Tool.Shape(ShapeType.Line)) }
        binding.btnPolygon.setOnClickListener { viewModel.setTool(Tool.Shape(ShapeType.Polygon(sides = 5))) }

        binding.strokeWidthSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setStrokeWidth(value)
        }

        bindColorPalette()

        binding.btnUndo.setOnClickListener { viewModel.undo() }
        binding.btnRedo.setOnClickListener { viewModel.redo() }
        binding.btnQuickUndo.setOnClickListener { viewModel.undo() }
        binding.btnQuickRedo.setOnClickListener { viewModel.redo() }
        binding.btnQuickUndo.contentDescription = getString(R.string.undo)
        binding.btnQuickRedo.contentDescription = getString(R.string.redo)
        binding.btnToggleToolbar.setOnClickListener { toggleToolbar() }
        binding.toolbarContent.visibility = View.GONE
        binding.toolbarCollapsedQuickBar.visibility = View.VISIBLE
        updateToolbarToggleUi()

        binding.btnSave.setOnClickListener { viewModel.save() }
        binding.btnLoad.setOnClickListener { viewModel.listSavedFileNames() }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.tool.collect { updateSelectedToolUi(it) } }
                launch {
                    viewModel.uiEvent.collect { event ->
                        when (event) {
                            is UiEvent.SaveSuccess -> showMessage("Saved", "Saved as ${event.fileName}")
                            is UiEvent.SaveError -> showMessage("Save failed", event.message)
                            is UiEvent.LoadSuccess -> showMessage("Loaded", "Whiteboard loaded successfully.")
                            is UiEvent.LoadError -> showMessage("Load failed", event.message)
                            is UiEvent.FilesListed -> showLoadDialog(event.names)
                            is UiEvent.FileListError -> showMessage("Load failed", event.message)
                        }
                    }
                }
            }
        }
    }

    private fun toggleToolbar() {
        isToolbarExpanded = !isToolbarExpanded
        binding.toolbarContent.visibility = if (isToolbarExpanded) View.VISIBLE else View.GONE
        binding.toolbarCollapsedQuickBar.visibility = if (isToolbarExpanded) View.GONE else View.VISIBLE
        updateToolbarToggleUi()
    }

    private fun updateToolbarToggleUi() {
        binding.btnToggleToolbar.setIconResource(if (isToolbarExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up)
        val textRes = if (isToolbarExpanded) R.string.toolbar_shrink else R.string.toolbar_expand
        binding.btnToggleToolbar.text = getString(textRes)
        binding.btnToggleToolbar.contentDescription = getString(textRes)
    }

    private fun bindColorPalette() {
        val colors = listOf(
            binding.btnColorBlack to "#000000", binding.btnColorRed to "#E53935",
            binding.btnColorBlue to "#1E88E5", binding.btnColorGreen to "#43A047",
            binding.btnColorOrange to "#FB8C00", binding.btnColorPurple to "#8E24AA"
        )
        fun updateColorSelection() {
            for ((button, hex) in colors) {
                button.backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
                val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
                button.strokeWidth = if (isSelected) 5 else 2
                button.strokeColor = ColorStateList.valueOf(Color.parseColor(if (isSelected) "#FFD54F" else "#CBD5E1"))
            }
        }
        for ((button, hex) in colors) {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
            button.setOnClickListener { selectedColorHex = hex; viewModel.setColor(hex); updateColorSelection() }
        }
        updateColorSelection()
    }

    private fun updateSelectedToolUi(tool: Tool) {
        val selectedButtons = mutableSetOf<MaterialButton>()
        when (tool) {
            Tool.Draw -> selectedButtons += binding.btnDraw
            Tool.Eraser -> selectedButtons += binding.btnEraser
            Tool.Text -> selectedButtons += binding.btnText
            is Tool.Shape -> when (tool.type) {
                ShapeType.Rectangle -> selectedButtons += binding.btnRect
                ShapeType.Circle -> selectedButtons += binding.btnCircle
                ShapeType.Line -> selectedButtons += binding.btnLine
                is ShapeType.Polygon -> selectedButtons += binding.btnPolygon
            }
        }
        val toolButtons = listOf(binding.btnDraw, binding.btnEraser, binding.btnText,
            binding.btnQuickDraw, binding.btnQuickEraser, binding.btnQuickText,
            binding.btnRect, binding.btnCircle, binding.btnLine, binding.btnPolygon)
        val selectedBg = ColorStateList.valueOf(getColor(R.color.tool_selected_bg))
        val selectedStroke = ColorStateList.valueOf(getColor(R.color.tool_selected_stroke))
        val selectedText = ColorStateList.valueOf(getColor(R.color.tool_selected_text))
        val normalBg = ColorStateList.valueOf(Color.TRANSPARENT)
        val normalStroke = ColorStateList.valueOf(getColor(R.color.tool_unselected_stroke))
        val normalText = ColorStateList.valueOf(getColor(R.color.tool_unselected_text))
        for (button in toolButtons) {
            val selected = button in selectedButtons
            button.isChecked = selected
            button.backgroundTintList = if (selected) selectedBg else normalBg
            button.strokeColor = if (selected) selectedStroke else normalStroke
            button.setTextColor(if (selected) selectedText else normalText)
            button.iconTint = if (selected) selectedText else normalText
            button.strokeWidth = if (selected) 3 else 2
        }
        val currentToolName = when (tool) {
            Tool.Draw -> getString(R.string.tool_draw)
            Tool.Eraser -> getString(R.string.tool_eraser)
            Tool.Text -> getString(R.string.tool_text)
            is Tool.Shape -> when (tool.type) {
                ShapeType.Rectangle -> getString(R.string.tool_rectangle)
                ShapeType.Circle -> getString(R.string.tool_circle)
                ShapeType.Line -> getString(R.string.tool_line)
                is ShapeType.Polygon -> getString(R.string.tool_polygon)
            }
        }
        val currentToolIcon = when (tool) {
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
        binding.ivCollapsedCurrentTool.setImageResource(currentToolIcon)
        binding.tvCollapsedCurrentTool.text = currentToolName
    }

    private fun showLoadDialog(fileNames: List<String>) {
        if (fileNames.isEmpty()) { showMessage("Load", "No saved files found."); return }
        val items = fileNames.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Load whiteboard")
            .setItems(items) { _, which -> viewModel.loadByFileName(items[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMessage(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
