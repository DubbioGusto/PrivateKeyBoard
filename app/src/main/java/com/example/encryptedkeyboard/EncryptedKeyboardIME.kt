package com.example.encryptedkeyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog

class EncryptedKeyboardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard

    private lateinit var toolbarView: View
    private lateinit var previewText: EditText
    private lateinit var decryptedView: TextView
    private lateinit var selectedKeyText: TextView
    private lateinit var encryptButton: Button
    private lateinit var decryptButton: Button
    private lateinit var selectKeyButton: Button
    private lateinit var clearButton: Button
    private lateinit var decryptPanel: LinearLayout

    private var selectedPublicKey: String? = null
    private var selectedContactName: String? = null
    private val cryptoHelper = CryptoHelper()
    private val keyStorage by lazy { KeyStorage(this) }
    private val unicodeMapper = UnicodeMapper()
    private val emojiEncoder = EmojiEncoder()

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

        // Initialize keyboard
        keyboardView = container.findViewById(R.id.keyboard_view)
        keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)

        setupListeners()

        return container
    }

    private fun setupListeners() {
        // Preview text changes
        previewText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                encryptButton.isEnabled = !s.isNullOrEmpty() && selectedPublicKey != null
            }
        })

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
            previewText.text.clear()
            decryptPanel.visibility = View.GONE
        }
    }

    private fun showKeySelector() {
        val contacts = keyStorage.getAllContacts()
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No public keys saved. Add them in Key Management.", Toast.LENGTH_SHORT).show()
            return
        }

        val names = contacts.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Recipient")
            .setItems(names) { _, which ->
                selectedContactName = contacts[which].name
                selectedPublicKey = contacts[which].publicKey
                selectedKeyText.text = "To: $selectedContactName"
                encryptButton.isEnabled = previewText.text.isNotEmpty()
            }
            .show()
    }

    private fun encryptAndSend() {
        // Decode the obfuscated text from preview to get readable plaintext
        val obfuscatedText = previewText.text.toString()
        val plaintext = unicodeMapper.decode(obfuscatedText)
        val publicKey = selectedPublicKey ?: return

        try {
            val encrypted = cryptoHelper.encrypt(plaintext, publicKey)
            val formattedMessage = "-----BEGIN ENCRYPTED MESSAGE-----\n$encrypted\n-----END ENCRYPTED MESSAGE-----"

            // Insert encrypted text into input field (no obfuscation for encrypted output)
            currentInputConnection?.commitText(formattedMessage, 1)

            // Clear preview
            previewText.text.clear()
            Toast.makeText(this, "Message encrypted and sent!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
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

        // Extract encrypted message
        val encryptedMatch = Regex("-----BEGIN ENCRYPTED MESSAGE-----\\s*(.+?)\\s*-----END ENCRYPTED MESSAGE-----", RegexOption.DOT_MATCHES_ALL)
            .find(clipText)

        if (encryptedMatch == null) {
            Toast.makeText(this, "No encrypted message found in clipboard", Toast.LENGTH_SHORT).show()
            return
        }

        val encryptedText = encryptedMatch.groupValues[1].trim()

        // Get private key
        val privateKey = keyStorage.getPrivateKey()
        if (privateKey == null) {
            Toast.makeText(this, "No private key found. Generate one in Key Management.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val decrypted = cryptoHelper.decrypt(encryptedText, privateKey)
            decryptedView.text = decrypted
            decryptPanel.visibility = View.VISIBLE
            Toast.makeText(this, "Message decrypted!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Decryption failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Keyboard action listeners
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                // Delete from preview (obfuscated)
                val currentText = previewText.text.toString()
                if (currentText.isNotEmpty()) {
                    previewText.setText(currentText.dropLast(1))
                    previewText.setSelection(previewText.text.length)
                }
                // Delete from input field normally
                ic.deleteSurroundingText(1, 0)
            }
            Keyboard.KEYCODE_DONE -> ic.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            Keyboard.KEYCODE_SHIFT -> handleShift()
            else -> {
                val standardChar = primaryCode.toChar()

                // Add obfuscated character to preview
                val obfuscatedChar = unicodeMapper.encode(standardChar)
                previewText.append(obfuscatedChar.toString())

                // Add normal character to input field
                ic.commitText(standardChar.toString(), 1)
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