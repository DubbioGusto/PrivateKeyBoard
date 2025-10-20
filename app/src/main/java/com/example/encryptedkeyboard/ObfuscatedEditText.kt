package com.example.encryptedkeyboard

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import android.widget.PopupWindow
import android.widget.TextView

class ObfuscatedEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var messageBuffer: MessageBuffer? = null

    private var decodedPopup: PopupWindow? = null
    private var decodedTextView: TextView? = null

    init {
        createDecodedPopup()

        // Disable default paste/cut/copy menu
        customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                return false // Return false to prevent menu from showing
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?) = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }

        customInsertionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                return false // Return false to prevent menu from showing
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?) = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }

        // Also disable text selection
        setTextIsSelectable(false)
        isCursorVisible = true
    }

    private fun createDecodedPopup() {
        decodedTextView = TextView(context).apply {
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            setPadding(32, 20, 32, 20)
            textSize = 20f
            setTextColor(Color.BLACK)
            elevation = 10f
            gravity = Gravity.CENTER
        }

        decodedPopup = PopupWindow(
            decodedTextView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            elevation = 12f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Update popup when cursor moves
        post { updatePopup() }
        return super.onTouchEvent(event)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // Update popup when cursor position changes
        updatePopup()
    }

    private fun updatePopup() {
        val buffer = messageBuffer ?: return
        val popup = decodedPopup ?: return
        val textView = decodedTextView ?: return

        if (buffer.isEmpty()) {
            popup.dismiss()
            return
        }

        // Get cursor position
        val cursorPos = selectionStart.coerceIn(0, buffer.length())

        // Get the readable word at cursor position
        val readableWord = buffer.getReadableWordAtPosition(cursorPos)

        if (readableWord != null && readableWord.isNotEmpty()) {
            textView.text = readableWord

            // Show popup if not showing
            if (!popup.isShowing) {
                try {
                    popup.showAsDropDown(this, 0, -(height + 120), Gravity.CENTER_HORIZONTAL)
                } catch (e: Exception) {
                    // Ignore errors if view not attached
                }
            }
        } else {
            popup.dismiss()
        }
    }

    /**
     * Update the displayed text from the buffer
     */
    fun updateDisplay() {
        val buffer = messageBuffer ?: return
        val obfuscatedText = buffer.getObfuscatedText()

        // Set text first
        setText(obfuscatedText)

        // Then set cursor to end
        setSelection(obfuscatedText.length)

        // Update the popup to show current word
        updatePopup()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        decodedPopup?.dismiss()
        decodedPopup = null
        decodedTextView = null
    }
}