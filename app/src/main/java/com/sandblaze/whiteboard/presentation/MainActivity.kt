package com.sandblaze.whiteboard.presentation

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sandblaze.whiteboard.R
import com.sandblaze.whiteboard.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WhiteboardViewModel by viewModels()
    private lateinit var toolbarManager: ToolbarManager
    private lateinit var colorPaletteController: ColorPaletteController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.whiteboardView.bind(viewModel)

        toolbarManager = ToolbarManager(binding) { viewModel.setTool(it) }
        colorPaletteController = ColorPaletteController(binding) { viewModel.setColor(it) }

        binding.strokeWidthSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setStrokeWidth(value)
        }

        binding.btnUndo.setOnClickListener { viewModel.undo() }
        binding.btnRedo.setOnClickListener { viewModel.redo() }
        binding.btnQuickUndo.setOnClickListener { viewModel.undo() }
        binding.btnQuickRedo.setOnClickListener { viewModel.redo() }

        binding.btnSave.setOnClickListener { viewModel.save() }
        binding.btnLoad.setOnClickListener { viewModel.listSavedFileNames() }

        observeUiState()
        observeUiEvents()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    toolbarManager.updateSelectedTool(state.selectedTool)
                    binding.btnUndo.isEnabled = state.isUndoEnabled
                    binding.btnQuickUndo.isEnabled = state.isUndoEnabled
                    binding.btnRedo.isEnabled = state.isRedoEnabled
                    binding.btnQuickRedo.isEnabled = state.isRedoEnabled
                    binding.strokeWidthSlider.value = state.strokeWidth
                    binding.loadingOverlay.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun observeUiEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is WhiteboardUiEvent.ShowMessage -> showMessage(event.title, event.message)
                        is WhiteboardUiEvent.ShowLoadDialog -> showLoadDialog(event.fileNames)
                    }
                }
            }
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
                viewModel.loadByFileName(items[which])
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

