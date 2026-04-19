package com.sandblaze.whiteboard.presentation

import android.content.res.ColorStateList
import android.graphics.Color
import com.google.android.material.button.MaterialButton
import com.sandblaze.whiteboard.databinding.ActivityMainBinding

class ColorPaletteController(
    private val binding: ActivityMainBinding,
    private val onColorSelected: (String) -> Unit
) {
    private var selectedColorHex: String = "#000000"
    
    private val colorButtons = listOf(
        binding.btnColorBlack to "#000000",
        binding.btnColorRed to "#E53935",
        binding.btnColorBlue to "#1E88E5",
        binding.btnColorGreen to "#43A047",
        binding.btnColorOrange to "#FB8C00",
        binding.btnColorPurple to "#8E24AA"
    )

    init {
        setupButtons()
        updateSelectionUi()
    }

    private fun setupButtons() {
        for ((button, hex) in colorButtons) {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
            button.setOnClickListener {
                selectedColorHex = hex
                onColorSelected(hex)
                updateSelectionUi()
            }
        }
    }

    private fun updateSelectionUi() {
        for ((button, hex) in colorButtons) {
            val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
            button.strokeWidth = if (isSelected) 5 else 2
            button.strokeColor = ColorStateList.valueOf(
                Color.parseColor(if (isSelected) "#FFD54F" else "#CBD5E1")
            )
        }
    }
}
