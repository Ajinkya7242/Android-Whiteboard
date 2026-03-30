package com.sandblaze.whiteboard.presentation

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sandblaze.whiteboard.R
import com.sandblaze.whiteboard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WhiteboardViewModel by viewModels { WhiteboardViewModel.factory(this) }
    private var isToolbarExpanded: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.whiteboardView.bind(viewModel)

        binding.btnDraw.setOnClickListener { viewModel.setTool(Tool.Draw) }
        binding.btnEraser.setOnClickListener { viewModel.setTool(Tool.Eraser) }
        binding.btnText.setOnClickListener { viewModel.setTool(Tool.Text) }

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
        binding.btnToggleToolbar.setOnClickListener { toggleToolbar() }

        binding.btnSave.setOnClickListener {
            viewModel.save(
                onSuccess = { showMessage("Saved", "Whiteboard saved locally.") },
                onError = { showMessage("Save failed", it.message ?: "Unknown error") }
            )
        }

        binding.btnExportPng.setOnClickListener {
            runCatching { binding.whiteboardView.exportToPng() }
                .onSuccess { showMessage("Export PNG", "Exported ${it.name}") }
                .onFailure { showMessage("Export failed", it.message ?: "Unknown error") }
        }

        binding.btnLoad.setOnClickListener {
            viewModel.listSavedFileNames(
                onSuccess = { names -> showLoadDialog(names) },
                onError = { showMessage("Load failed", it.message ?: "No saved files found.") }
            )
        }
    }

    private fun toggleToolbar() {
        isToolbarExpanded = !isToolbarExpanded
        binding.toolbarContent.visibility = if (isToolbarExpanded) View.VISIBLE else View.GONE
        binding.btnToggleToolbar.setText(
            if (isToolbarExpanded) R.string.toolbar_shrink else R.string.toolbar_expand
        )
    }

    private fun bindColorPalette() {
        val colors = listOf(
            binding.btnColorBlack to "#000000",
            binding.btnColorRed to "#E53935",
            binding.btnColorBlue to "#1E88E5",
            binding.btnColorGreen to "#43A047",
            binding.btnColorOrange to "#FB8C00",
            binding.btnColorPurple to "#8E24AA"
        )

        for ((button, hex) in colors) {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
            button.setOnClickListener { viewModel.setColor(hex) }
        }
    }

    private fun showLoadDialog(fileNames: List<String>) {
        if (fileNames.isEmpty()) {
            showMessage("Load", "No saved files found.")
            return
        }

        val items = fileNames.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Load whiteboard")
            .setItems(items) { _, which ->
                val fileName = items[which]
                viewModel.loadByFileName(
                    fileName = fileName,
                    onSuccess = { showMessage("Loaded", "Loaded $fileName") },
                    onError = { showMessage("Load failed", it.message ?: "Unknown error") }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMessage(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}

