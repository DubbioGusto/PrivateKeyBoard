package com.example.encryptedkeyboard

import kotlin.random.Random

/**
 * UnicodeMapper - Character Obfuscation System
 *
 * WHAT IT DOES:
 * - Maps each standard keyboard character to a random Unicode character
 * - Automatically rotates the mapping every 5-10 seconds (random interval)
 * - Provides encode/decode functions for seamless conversion
 * - Shows only the current word as readable while typing
 *
 * SECURITY BENEFITS:
 * - If someone intercepts keyboard memory, they see random Unicode symbols
 * - Mapping changes every 5-10 seconds, making pattern analysis harder
 * - User can still see what they're typing (current word is readable)
 *
 * HOW TO USE:
 * 1. Create instance: val mapper = UnicodeMapper()
 * 2. Encode text: val obfuscated = mapper.encode("hello")
 * 3. Decode text: val readable = mapper.decode(obfuscated)
 * 4. Display with current word readable: mapper.getDisplayText(text, cursorPos)
 * 5. Check rotation: mapper.checkAndRotate()
 */
class UnicodeMapper {

    // Standard keyboard characters we want to map
    private val standardChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,!?@#$%&*()-_=+[]{}:;\"'<>/\\|`~\n\t"

    // Current mapping: standard char -> random unicode
    private var charToUnicode = mutableMapOf<Char, Char>()

    // Reverse mapping: random unicode -> standard char
    private var unicodeToChar = mutableMapOf<Char, Char>()

    // Last rotation timestamp
    private var lastRotationTime = System.currentTimeMillis()

    // Rotation interval (random between 5-10 seconds)
    private var rotationInterval = getRandomInterval()

    init {
        generateNewMapping()
    }

    /**
     * Get random interval between 5000-10000 ms (5-10 seconds)
     */
    private fun getRandomInterval(): Long {
        return Random.nextLong(5000, 10001)
    }

    /**
     * Generate a new random Unicode character mapping
     * Called on init and every rotation
     */
    private fun generateNewMapping() {
        charToUnicode.clear()
        unicodeToChar.clear()

        val usedUnicodes = mutableSetOf<Char>()

        // Map each standard character to a unique random Unicode character
        for (char in standardChars) {
            var randomUnicode: Char
            do {
                randomUnicode = generateRandomUnicode()
            } while (usedUnicodes.contains(randomUnicode) || randomUnicode in standardChars)

            charToUnicode[char] = randomUnicode
            unicodeToChar[randomUnicode] = char
            usedUnicodes.add(randomUnicode)
        }

        lastRotationTime = System.currentTimeMillis()
        rotationInterval = getRandomInterval()
    }

    /**
     * Generate a random valid Unicode character between U+0000 and U+10FFFF
     * Excludes surrogate pairs and invalid ranges for compatibility
     */
    private fun generateRandomUnicode(): Char {
        val codePoint = when (Random.nextInt(4)) {
            0 -> Random.nextInt(0x0020, 0xD800) // Basic Multilingual Plane (exclude control chars)
            1 -> Random.nextInt(0xE000, 0x10000) // BMP continuation (exclude surrogates)
            else -> Random.nextInt(0x0020, 0xD800) // Favor BMP for better compatibility
        }
        return codePoint.toChar()
    }

    /**
     * Check if mapping should be rotated and do so if needed
     * Should be called periodically (e.g., every second)
     */
    fun checkAndRotate() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRotationTime >= rotationInterval) {
            generateNewMapping()
        }
    }

    /**
     * Encode a single standard character to obfuscated unicode
     *
     * Example: 'h' -> '⌘'
     */
    fun encode(char: Char): Char {
        checkAndRotate()
        return charToUnicode[char] ?: char
    }

    /**
     * Encode a string to obfuscated unicode
     *
     * Example: "hello" -> "⌘ψ₪℗◊"
     */
    fun encode(text: String): String {
        checkAndRotate()
        return text.map { charToUnicode[it] ?: it }.joinToString("")
    }

    /**
     * Decode obfuscated unicode back to standard character
     *
     * Example: '⌘' -> 'h'
     */
    fun decode(char: Char): Char {
        return unicodeToChar[char] ?: char
    }

    /**
     * Decode an obfuscated string back to readable text
     *
     * Example: "⌘ψ₪℗◊" -> "hello"
     */
    fun decode(text: String): String {
        return text.map { unicodeToChar[it] ?: it }.joinToString("")
    }

    /**
     * Create display text showing only the current word as readable
     * This is the KEY FEATURE for user experience!
     *
     * WHAT IT DOES:
     * - Finds the word boundaries around the cursor position
     * - Decodes ONLY that word to make it readable
     * - Keeps all other words obfuscated
     *
     * EXAMPLE:
     * Text: "⌘ψ₪℗◊ ₩◊Ω₪∂"  (obfuscated "hello world")
     * Cursor at position 3 (in first word)
     * Returns: "hello ₩◊Ω₪∂"  (first word readable, second still obfuscated)
     *
     * @param text The full obfuscated text
     * @param cursorPosition Current cursor position
     * @return Text with current word decoded, rest obfuscated
     */
    fun getDisplayText(text: String, cursorPosition: Int): String {
        if (text.isEmpty()) return text

        // Find word boundaries around cursor
        val safePos = cursorPosition.coerceIn(0, text.length)
        var wordStart = safePos
        var wordEnd = safePos

        // Find start of current word (go backwards until we hit a non-letter/digit)
        while (wordStart > 0 && text[wordStart - 1].isLetterOrDigit()) {
            wordStart--
        }

        // Find end of current word (go forwards until we hit a non-letter/digit)
        while (wordEnd < text.length && text[wordEnd].isLetterOrDigit()) {
            wordEnd++
        }

        // Build display string: obfuscated before + readable word + obfuscated after
        val before = text.substring(0, wordStart)
        val currentWord = text.substring(wordStart, wordEnd)
        val after = text.substring(wordEnd)

        val decodedWord = decode(currentWord)

        return before + decodedWord + after
    }

    /**
     * Get time until next rotation in milliseconds
     * Useful for debugging or UI display
     */
    fun getTimeUntilRotation(): Long {
        val elapsed = System.currentTimeMillis() - lastRotationTime
        return maxOf(0, rotationInterval - elapsed)
    }
}

/*
 * USAGE EXAMPLE IN EncryptedKeyboardIME:
 *
 * // Initialize
 * private val unicodeMapper = UnicodeMapper()
 *
 * // When user types a character
 * val standardChar = 'h'
 * val obfuscatedChar = unicodeMapper.encode(standardChar)
 * previewText.append(obfuscatedChar.toString())  // Shows random Unicode
 *
 * // Update display to show current word as readable
 * val obfuscatedText = previewText.text.toString()
 * val cursorPosition = previewText.selectionStart
 * val displayText = unicodeMapper.getDisplayText(obfuscatedText, cursorPosition)
 * previewText.setText(displayText)  // Now current word is readable!
 *
 * // When encrypting, decode full text back to readable
 * val plaintext = unicodeMapper.decode(obfuscatedText)
 * // Now encrypt this plaintext...
 *
 * // Periodic rotation check (call every second)
 * rotationHandler.post {
 *     unicodeMapper.checkAndRotate()
 *     rotationHandler.postDelayed(this, 1000)
 * }
 */