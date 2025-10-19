package com.example.encryptedkeyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog

class EncryptedKeyboardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard

    private lateinit var toolbarView: View
    private lateinit var previewText: TextView
    private lateinit var decryptedView: TextView
    private lateinit var selectedKeyText: TextView
    private lateinit var encryptButton: Button
    private lateinit var decryptButton: Button
    private lateinit var selectKeyButton: Button
    private lateinit var clearButton: Button
    private lateinit var decryptPanel: LinearLayout
    private lateinit var emojifyCheckbox: CheckBox
    private lateinit var modeToggle: Button

    private var selectedPublicKey: String? = null
    private var selectedContactName: String? = null
    private val cryptoHelper = CryptoHelper()
    private val keyStorage by lazy { KeyStorage(this) }

    // Unicode mapper for obfuscation
    private val unicodeMapper = UnicodeMapper()

    // Emoji encoder
    private val emojiEncoder = EmojiEncoder()

    // Mode: true = internal preview, false = direct to app
    private var useInternalMode = true

    // Internal buffer to store OBFUSCATED text
    private val obfuscatedBuffer = StringBuilder()
    private var cursorPosition = 0

    // Handler for periodic rotation checks and preview updates
    private val rotationHandler = Handler(Looper.getMainLooper())
    private val rotationCheckRunnable = object : Runnable {
        override fun run() {
            unicodeMapper.checkAndRotate()
            if (useInternalMode) {
                updatePreviewDisplay()
            }
            rotationHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreateInputView(): View {
        val container = layoutInflater.inflate(R.layout.keyboard_layout, null) as LinearLayout

        // Initialize toolbar
        toolbarView = container.findViewById(R.id.toolbar)
        previewText = container.findViewById(R.id.preview_text)
        decryptedView = container.findViewById(R.id.decrypted_text)
        selectedKeyText = container.findViewById(R.id.selected_key_text)
        encryptButton = container.findViewById(R.id.encrypt_button)
        decryptButton = container.findViewById(R.id.decrypt_button)
        selectKeyButton = container.findViewById(R.id.select_key_button)
        clearButton = container.findViewById(R.id.clear_button)
        decryptPanel = container.findViewById(R.id.decrypt_panel)
        emojifyCheckbox = container.findViewById(R.id.emojify_checkbox)
        modeToggle = container.findViewById(R.id.mode_toggle)

        // Initialize keyboard
        keyboardView = container.findViewById(R.id.keyboard_view)
        keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)

        setupListeners()
        updateModeUI()

        // Start rotation checker
        rotationHandler.post(rotationCheckRunnable)

        return container
    }

    override fun onDestroy() {
        super.onDestroy()
        rotationHandler.removeCallbacks(rotationCheckRunnable)
    }

    private fun setupListeners() {
        // Mode toggle button
        modeToggle.setOnClickListener {
            useInternalMode = !useInternalMode
            updateModeUI()
        }

        // Select key button
        selectKeyButton.setOnClickListener {
            showKeySelector()
        }

        // Encrypt button
        encryptButton.setOnClickListener {
            encryptAndSend()
        }

        // Decrypt button
        decryptButton.setOnClickListener {
            decryptFromClipboard()
        }

        // Clear button
        clearButton.setOnClickListener {
            obfuscatedBuffer.clear()
            cursorPosition = 0
            previewText.text = ""
            decryptPanel.visibility = View.GONE
            encryptButton.isEnabled = false
        }
    }

    private fun updateModeUI() {
        if (useInternalMode) {
            modeToggle.text = "Mode: Internal"
            previewText.visibility = View.VISIBLE
            encryptButton.visibility = View.VISIBLE
            selectKeyButton.visibility = View.VISIBLE
            emojifyCheckbox.visibility = View.VISIBLE
            selectedKeyText.visibility = View.VISIBLE
        } else {
            modeToggle.text = "Mode: Direct"
            previewText.visibility = View.GONE
            encryptButton.visibility = View.GONE
            selectKeyButton.visibility = View.GONE
            emojifyCheckbox.visibility = View.GONE
            selectedKeyText.visibility = View.GONE
        }
    }

    /**
     * Update preview to show current word as readable
     */
    private fun updatePreviewDisplay() {
        if (!useInternalMode || obfuscatedBuffer.isEmpty()) return

        val obfuscatedText = obfuscatedBuffer.toString()
        val displayText = unicodeMapper.getDisplayText(obfuscatedText, cursorPosition)

        previewText.text = displayText
        encryptButton.isEnabled = obfuscatedBuffer.isNotEmpty() && selectedPublicKey != null
    }

    private fun showKeySelector() {
        val contacts = keyStorage.getAllContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No public keys saved. Add them in Key Management.", Toast.LENGTH_SHORT).show()
            return
        }

        val names = contacts.map { it.name }.toTypedArray()

        try {
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            builder.setTitle("Select Recipient")
                .setItems(names) { dialog, which ->
                    selectedContactName = contacts[which].name
                    selectedPublicKey = contacts[which].publicKey
                    selectedKeyText.text = "To: $selectedContactName"
                    encryptButton.isEnabled = obfuscatedBuffer.isNotEmpty()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }

            val dialog = builder.create()
            dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening dialog: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("EncryptedKeyboard", "Error showing dialog", e)
        }
    }

    private fun encryptAndSend() {
        if (!useInternalMode) return

        val obfuscatedText = obfuscatedBuffer.toString()
        val plaintext = unicodeMapper.decode(obfuscatedText)
        val publicKey = selectedPublicKey ?: return

        try {
            val encrypted = cryptoHelper.encrypt(plaintext, publicKey)

            val finalMessage = if (emojifyCheckbox.isChecked) {
                emojiEncoder.encodeToEmoji(encrypted)
            } else {
                encrypted
            }

            currentInputConnection?.commitText(finalMessage, 1)

            // Clear buffer
            obfuscatedBuffer.clear()
            cursorPosition = 0
            previewText.text = ""
            encryptButton.isEnabled = false

            val messageType = if (emojifyCheckbox.isChecked) "emojified" else "encrypted"
            Toast.makeText(this, "Message $messageType and sent!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Encryption failed: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("EncryptedKeyboard", "Encryption error", e)
        }
    }

    private fun decryptFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip

        if (clipData == null || clipData.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val clipText = clipData.getItemAt(0).text?.toString() ?: ""

        val privateKey = keyStorage.getPrivateKey()
        if (privateKey == null) {
            Toast.makeText(this, "No private key found. Generate one in Key Management.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val encryptedText = if (emojiEncoder.isEmojiEncoded(clipText)) {
                val decoded = emojiEncoder.decodeFromEmoji(clipText)
                if (decoded == null) {
                    Toast.makeText(this, "Invalid emoji-encoded message", Toast.LENGTH_SHORT).show()
                    return
                }
                decoded
            } else {
                clipText.trim()
            }

            val decrypted = cryptoHelper.decrypt(encryptedText, privateKey)
            decryptedView.text = decrypted
            decryptPanel.visibility = View.VISIBLE
            Toast.makeText(this, "Message decrypted!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Decryption failed: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("EncryptedKeyboard", "Decryption error", e)
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (useInternalMode) {
                    // Delete from buffer
                    if (cursorPosition > 0 && obfuscatedBuffer.isNotEmpty()) {
                        obfuscatedBuffer.deleteCharAt(cursorPosition - 1)
                        cursorPosition--
                        updatePreviewDisplay()
                    }
                } else {
                    // Delete from app field
                    ic.deleteSurroundingText(1, 0)
                }
            }
            Keyboard.KEYCODE_DONE -> {
                ic.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            }
            Keyboard.KEYCODE_SHIFT -> handleShift()
            else -> {
                val standardChar = primaryCode.toChar()

                if (useInternalMode) {
                    // Add obfuscated character to buffer
                    val obfuscatedChar = unicodeMapper.encode(standardChar)
                    obfuscatedBuffer.insert(cursorPosition, obfuscatedChar)
                    cursorPosition++

                    // Update display immediately
                    updatePreviewDisplay()
                } else {
                    // Add normal character directly to app field
                    ic.commitText(standardChar.toString(), 1)
                }
            }
        }
    }

    private fun handleShift() {
        keyboard.isShifted = !keyboard.isShifted
        keyboardView.invalidateAllKeys()
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}