package com.sandblaze.whiteboard.presentation

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or  InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 2
        maxLines = 4
        isSingleLine = false
        setPadding(20, 16, 20, 16)
        setBackgroundColor(Color.parseColor("#F8FAFC"))
        setTextColor(Color.parseColor("#1E293B"))        // dark text
        setHintTextColor(Color.parseColor("#94A3B8"))    // subtle hint
        hint = "Enter text..."
        setText(initialText)
        setSelection(initialText.length)
    }

    var selectedSizeSp = initialSizeSp.coerceIn(12f, 96f)
    val initialColorIndex = colorOptions.indexOfFirst { it.hex.equals(initialColor.value, ignoreCase = true) }
        .let { if (it >= 0) it else 0 }
    var selectedColor = ColorHex(colorOptions[initialColorIndex].hex)

    // Root container with ScrollView so nothing gets cut on short devices
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 10, 28, 8)
    }

    val scrollView = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = true
        val displayMetrics = context.resources.displayMetrics
        val maxDialogHeight = (displayMetrics.heightPixels * 0.75).toInt()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            maxDialogHeight  // Set height directly instead of maximumHeight
        )
    }

    // --- Content label ---
    val editLabel = TextView(context).apply {
        text = "Content"
        setTextColor(Color.parseColor("#64748B"))
        textSize = 13f
    }
    container.addView(editLabel)
    container.addView(input)

    // --- Preview card ---
    val previewCard = MaterialCardView(context).apply {
        radius = 14f
        cardElevation = 0f
        setCardBackgroundColor(Color.parseColor("#F8FAFC"))
        strokeWidth = 2
        strokeColor = Color.parseColor("#E2E8F0")
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 10
        }
    }
    val previewText = TextView(context).apply {
        setPadding(18, 12, 18, 12)
        text = if (initialText.isBlank()) "Preview text" else initialText
        setTextColor(Color.parseColor(selectedColor.value))
        textSize = selectedSizeSp.coerceAtMost(36f) // Cap preview size so card doesn't explode
        setLineSpacing(2f, 1.15f)
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    previewCard.addView(previewText)
    container.addView(previewCard)

    // --- Size slider ---
    val sizeLabel = TextView(context).apply {
        text = "Size: ${selectedSizeSp.toInt()}sp"
        setPadding(0, 12, 0, 2)
        setTextColor(Color.parseColor("#64748B"))
        textSize = 13f
    }
    container.addView(sizeLabel)

    val sizeSlider = Slider(context).apply {
        valueFrom = 12f
        valueTo = 96f
        stepSize = 1f
        value = selectedSizeSp
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = -8  // Compensate for Slider's internal padding
            marginEnd = -8
        }
        addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            selectedSizeSp = value
            sizeLabel.text = "Size: ${value.toInt()}sp"
            previewText.textSize = value.coerceAtMost(36f)
        }
    }
    container.addView(sizeSlider)

    // --- Color picker ---
    val colorLabel = TextView(context).apply {
        text = "Color"
        setPadding(0, 8, 0, 6)
        setTextColor(Color.parseColor("#64748B"))
        textSize = 13f
    }
    container.addView(colorLabel)

    val colorRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 4
        }
    }
    val colorButtons = mutableListOf<MaterialButton>()
    colorOptions.forEachIndexed { _, option ->
        val btn = MaterialButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 48, 1f).apply { // Slightly smaller height
                marginEnd = 6
            }
            minWidth = 0
            minimumHeight = 0
            text = ""
            cornerRadius = 24
            strokeWidth = 2
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(option.hex))
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
            setOnClickListener {
                selectedColor = ColorHex(option.hex)
                previewText.setTextColor(Color.parseColor(option.hex))
                colorButtons.forEach { it.strokeWidth = 2 }
                strokeWidth = 4
            }
        }
        colorButtons += btn
        colorRow.addView(btn)
    }
    container.addView(colorRow)
    colorButtons.getOrNull(initialColorIndex)?.strokeWidth = 4

    // Text change listener for live preview
    input.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            previewText.text = s?.toString().orEmpty().ifBlank { "Preview text" }
        }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    })

    scrollView.addView(container)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(scrollView)
        .setPositiveButton(positiveButtonText) { _, _ ->
            val value = input.text?.toString().orEmpty().trim()
            onSubmit(TextEditorResult(value, selectedColor, selectedSizeSp))

        }
        .setNegativeButton("Cancel", null)

    if (onDelete != null) {
        dialog.setNeutralButton("Delete") { _, _ -> onDelete() }
    }
    dialog.show()
}