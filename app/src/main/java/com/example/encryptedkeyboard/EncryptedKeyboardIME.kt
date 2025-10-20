package com.example.encryptedkeyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*

class EncryptedKeyboardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private val TAG = "EncryptedKeyboard"

    private lateinit var keyboardView: KeyboardView
    private var currentKeyboard: Keyboard? = null
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private lateinit var numbersKeyboard: Keyboard

    private lateinit var toolbarView: View
    private lateinit var previewText: ObfuscatedEditText
    private lateinit var decryptedView: TextView
    private lateinit var selectedKeyText: TextView
    private lateinit var encryptButton: Button
    private lateinit var decryptButton: Button
    private lateinit var selectKeyButton: Button
    private lateinit var clearButton: Button
    private lateinit var pasteButton: Button
    private lateinit var decryptPanel: LinearLayout
    private lateinit var emojifyCheckbox: CheckBox

    private var selectedPublicKey: String? = null
    private var selectedContactName: String? = null
    private val cryptoHelper = CryptoHelper()
    private val keyStorage by lazy { KeyStorage(this) }
    private val emojiEncoder = EmojiEncoder()

    // NEW: Message buffer handles all text management
    private val messageBuffer = MessageBuffer()

    private var isInternalFieldActive = false
    private var isCapsOn = false

    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView called")

        return try {
            val container = layoutInflater.inflate(R.layout.keyboard_layout, null) as LinearLayout
            Log.d(TAG, "Layout inflated successfully")

            toolbarView = container.findViewById(R.id.toolbar)
            previewText = container.findViewById(R.id.preview_text)

            // Connect message buffer to EditText
            previewText.messageBuffer = messageBuffer

            decryptedView = container.findViewById(R.id.decrypted_text)
            selectedKeyText = container.findViewById(R.id.selected_key_text)
            encryptButton = container.findViewById(R.id.encrypt_button)
            decryptButton = container.findViewById(R.id.decrypt_button)
            selectKeyButton = container.findViewById(R.id.select_key_button)
            pasteButton = container.findViewById(R.id.paste_button)
            clearButton = container.findViewById(R.id.clear_button)
            decryptPanel = container.findViewById(R.id.decrypt_panel)
            emojifyCheckbox = container.findViewById(R.id.emojify_checkbox)
            Log.d(TAG, "Toolbar views initialized")

            keyboardView = container.findViewById(R.id.keyboard_view)
            qwertyKeyboard = Keyboard(this, R.xml.qwerty)
            symbolsKeyboard = Keyboard(this, R.xml.symbols)
            numbersKeyboard = Keyboard(this, R.xml.numbers)

            currentKeyboard = qwertyKeyboard
            keyboardView.keyboard = currentKeyboard
            keyboardView.setOnKeyboardActionListener(this)

            setupListeners()

            Log.d(TAG, "Keyboard view created successfully")
            container
        } catch (e: Exception) {
            Log.e(TAG, "ERROR creating input view", e)
            Toast.makeText(this, "Keyboard error: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun setupListeners() {
        try {
            previewText.setOnTouchListener { _, _ ->
                activateInternalField()
                false
            }

            selectKeyButton.setOnClickListener {
                showKeySelector()
            }

            encryptButton.setOnClickListener {
                encryptAndSend()
            }

            decryptButton.setOnClickListener {
                decryptFromClipboard()
            }

            clearButton.setOnClickListener {
                if (isInternalFieldActive) {
                    messageBuffer.clear()
                    previewText.updateDisplay()
                    encryptButton.isEnabled = false
                }
                decryptPanel.visibility = View.GONE
            }

            pasteButton.setOnClickListener {
                if (isInternalFieldActive) {
                    pasteTextIntoBuffer()
                }
            }

            Log.d(TAG, "Listeners setup successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up listeners", e)
        }
    }

    private fun activateInternalField() {
        isInternalFieldActive = true
        previewText.setBackgroundColor(0xFFE3F2FD.toInt())
        Toast.makeText(this, "✓ Internal field active (encryption mode)", Toast.LENGTH_SHORT).show()
    }

    private fun pasteTextIntoBuffer() {
        try {
            Log.d(TAG, "Paste button clicked")

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip

            if (clipData == null || clipData.itemCount == 0) {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Clipboard is empty")
                return
            }

            val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
            Log.d(TAG, "Pasted text: $pastedText")

            if (pastedText.isEmpty()) {
                Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show()
                return
            }

            // Process pasted text character by character, splitting into words
            for (char in pastedText) {
                if (char == ' ' || char == '\n') {
                    // Finalize word when we hit space or newline
                    messageBuffer.finalizeWord(char.toString())
                } else {
                    // Add character to current word
                    messageBuffer.addCharacter(char)
                }
            }

            previewText.updateDisplay()
            encryptButton.isEnabled = !messageBuffer.isEmpty() && selectedPublicKey != null
            Toast.makeText(this, "Text pasted and encoded", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Paste completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error pasting text", e)
            Toast.makeText(this, "Paste error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (::previewText.isInitialized) {
            isInternalFieldActive = false
            previewText.setBackgroundColor(0xFFEEEEEE.toInt())
        }
    }

    private fun showKeySelector() {
        val contacts = keyStorage.getAllContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No public keys saved. Add them in Key Management.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val popup = PopupMenu(this, selectKeyButton)
            contacts.forEachIndexed { index, contact ->
                popup.menu.add(0, index, index, contact.name)
            }

            popup.setOnMenuItemClickListener { menuItem ->
                val selectedContact = contacts[menuItem.itemId]
                selectedContactName = selectedContact.name
                selectedPublicKey = selectedContact.publicKey
                selectedKeyText.text = "To: $selectedContactName"
                encryptButton.isEnabled = !messageBuffer.isEmpty()
                true
            }

            popup.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing key selector", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encryptAndSend() {
        if (!isInternalFieldActive) return

        // Get the READABLE text (not obfuscated)
        val plaintext = messageBuffer.getReadableText()
        val publicKey = selectedPublicKey ?: return

        try {
            val encrypted = cryptoHelper.encrypt(plaintext, publicKey)

            val finalMessage = if (emojifyCheckbox.isChecked) {
                emojiEncoder.encodeToEmoji(encrypted)
            } else {
                encrypted
            }

            currentInputConnection?.commitText(finalMessage, 1)

            messageBuffer.clear()
            previewText.updateDisplay()
            encryptButton.isEnabled = false

            val messageType = if (emojifyCheckbox.isChecked) "emojified" else "encrypted"
            Toast.makeText(this, "Message $messageType and sent!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error", e)
            Toast.makeText(this, "Encryption failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Log.e(TAG, "Decryption error", e)
            Toast.makeText(this, "Decryption failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        try {
            when (primaryCode) {
                -10 -> {
                    currentKeyboard = symbolsKeyboard
                    keyboardView.keyboard = currentKeyboard
                    keyboardView.invalidateAllKeys()
                    return
                }
                -11 -> {
                    currentKeyboard = numbersKeyboard
                    keyboardView.keyboard = currentKeyboard
                    keyboardView.invalidateAllKeys()
                    return
                }
                -12 -> {
                    currentKeyboard = qwertyKeyboard
                    keyboardView.keyboard = currentKeyboard
                    keyboardView.invalidateAllKeys()
                    return
                }
                Keyboard.KEYCODE_SHIFT -> {
                    handleShift()
                    return
                }
                Keyboard.KEYCODE_MODE_CHANGE -> {
                    currentKeyboard = when (currentKeyboard) {
                        qwertyKeyboard -> symbolsKeyboard
                        symbolsKeyboard -> numbersKeyboard
                        else -> qwertyKeyboard
                    }
                    keyboardView.keyboard = currentKeyboard
                    keyboardView.invalidateAllKeys()
                    return
                }
            }

            if (isInternalFieldActive) {
                handleInternalFieldInput(primaryCode, ic)
            } else {
                handleExternalFieldInput(primaryCode, ic)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling key: $primaryCode", e)
        }
    }

    private fun handleInternalFieldInput(primaryCode: Int, ic: android.view.inputmethod.InputConnection) {
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                messageBuffer.deleteCharacter()
                previewText.updateDisplay()
                encryptButton.isEnabled = !messageBuffer.isEmpty() && selectedPublicKey != null
            }
            Keyboard.KEYCODE_DONE -> {
                ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            10 -> {  // Enter
                messageBuffer.finalizeWord("\n")
                previewText.updateDisplay()
                encryptButton.isEnabled = !messageBuffer.isEmpty() && selectedPublicKey != null
            }
            32 -> {  // Space - finalize current word with new mapping
                messageBuffer.finalizeWord(" ")
                previewText.updateDisplay()
                encryptButton.isEnabled = !messageBuffer.isEmpty() && selectedPublicKey != null
            }
            else -> {
                var char = primaryCode.toChar()
                if (isCapsOn && char.isLetter()) {
                    char = char.uppercase()[0]
                }
                messageBuffer.addCharacter(char)
                previewText.updateDisplay()
                encryptButton.isEnabled = !messageBuffer.isEmpty() && selectedPublicKey != null
            }
        }
    }

    private fun handleExternalFieldInput(primaryCode: Int, ic: android.view.inputmethod.InputConnection) {
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_DONE -> ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
            10 -> ic.commitText("\n", 1)
            32 -> ic.commitText(" ", 1)
            else -> {
                var char = primaryCode.toChar()
                if (isCapsOn && char.isLetter()) {
                    char = char.uppercase()[0]
                }
                ic.commitText(char.toString(), 1)
            }
        }
    }

    private fun handleShift() {
        isCapsOn = !isCapsOn
        currentKeyboard?.isShifted = isCapsOn
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