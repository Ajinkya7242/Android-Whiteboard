package com.sandblaze.whiteboard.presentation

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.sandblaze.whiteboard.domain.model.ColorHex

internal data class TextEditorResult(
    val text: String,
    val color: ColorHex,
    val sizeSp: Float
)

internal fun showTextEditorDialog(
    context: Context,
    title: String,
    positiveButtonText: String,
    initialText: String,
    initialColor: ColorHex,
    initialSizeSp: Float,
    onSubmit: (TextEditorResult) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    data class ColorOption(val name: String, val hex: String)
    val colorOptions = listOf(
        ColorOption("Black", "#000000"),
        ColorOption("Red", "#E53935"),
        ColorOption("Blue", "#1E88E5"),
        ColorOption("Green", "#43A047"),
        ColorOption("Orange", "#FB8C00"),
        ColorOption("Purple", "#8E24AA")
    )

    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setText(initialText)
        setSelection(initialText.length)
    }
    var selectedSizeSp = initialSizeSp.coerceIn(12f, 96f)
    val initialColorIndex = colorOptions.indexOfFirst { it.hex.equals(initialColor.value, ignoreCase = true) }
        .let { if (it >= 0) it else 0 }
    var selectedColor = ColorHex(colorOptions[initialColorIndex].hex)

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 8, 24, 8)
    }
    container.addView(input)

    val sizeLabel = TextView(context).apply {
        text = "Text size: ${selectedSizeSp.toInt()}sp"
        setPadding(0, 16, 0, 4)
    }
    container.addView(sizeLabel)

    val sizeSlider = Slider(context).apply {
        valueFrom = 12f
        valueTo = 96f
        stepSize = 1f
        value = selectedSizeSp
        addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            selectedSizeSp = value
            sizeLabel.text = "Text size: ${value.toInt()}sp"
        }
    }
    container.addView(sizeSlider)

    val colorButton = MaterialButton(context).apply {
        val option = colorOptions[initialColorIndex]
        text = "Text color: ${option.name}"
        setTextColor(Color.parseColor(option.hex))
        setOnClickListener {
            var selectedIndex = colorOptions.indexOfFirst { it.hex.equals(selectedColor.value, ignoreCase = true) }
                .let { if (it >= 0) it else 0 }
            MaterialAlertDialogBuilder(context)
                .setTitle("Select text color")
                .setSingleChoiceItems(colorOptions.map { it.name }.toTypedArray(), selectedIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton("OK") { _, _ ->
                    val chosen = colorOptions[selectedIndex]
                    selectedColor = ColorHex(chosen.hex)
                    text = "Text color: ${chosen.name}"
                    setTextColor(Color.parseColor(chosen.hex))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    container.addView(colorButton)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(container)
        .setPositiveButton(positiveButtonText) { _, _ ->
            val value = input.text?.toString().orEmpty().trim()
            if (value.isNotEmpty()) {
                onSubmit(TextEditorResult(value, selectedColor, selectedSizeSp))
            }
        }
        .setNegativeButton("Cancel", null)

    if (onDelete != null) {
        dialog.setNeutralButton("Delete") { _, _ -> onDelete() }
    }
    dialog.show()
}
