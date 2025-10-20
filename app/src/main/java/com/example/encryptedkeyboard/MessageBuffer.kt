package com.example.encryptedkeyboard

/**
 * Manages the message with both readable and obfuscated representations
 * Tracks words separately with their own Unicode mappings
 */
class MessageBuffer {

    // List of words in readable form
    private val readableWords = mutableListOf<String>()

    // List of words in obfuscated form
    private val obfuscatedWords = mutableListOf<String>()

    // Current word being typed (not yet finalized)
    private var currentWord = ""

    // Spaces and punctuation between words
    private val separators = mutableListOf<String>()

    // Unicode mapper
    private val wordMapper = WordUnicodeMapper()

    /**
     * Add a character to the current word
     */
    fun addCharacter(char: Char) {
        currentWord += char
    }

    /**
     * Finalize current word (called when spacebar is pressed)
     * Encodes it with a new unique Unicode mapping
     */
    fun finalizeWord(separator: String = " ") {
        if (currentWord.isNotEmpty()) {
            // Add readable word
            readableWords.add(currentWord)

            // Encode with new unique mapping
            val wordIndex = readableWords.size - 1
            val obfuscated = wordMapper.encodeWord(wordIndex, currentWord)
            obfuscatedWords.add(obfuscated)

            // Store separator
            separators.add(separator)

            // Reset current word
            currentWord = ""
        } else if (separator.isNotEmpty()) {
            // Just add separator if no word
            if (separators.isEmpty()) {
                separators.add(separator)
            } else {
                separators[separators.size - 1] += separator
            }
        }
    }

    /**
     * Delete last character
     */
    fun deleteCharacter() {
        if (currentWord.isNotEmpty()) {
            // Delete from current word
            currentWord = currentWord.dropLast(1)
        } else if (obfuscatedWords.isNotEmpty()) {
            // Delete last finalized word
            val lastIndex = obfuscatedWords.size - 1
            obfuscatedWords.removeAt(lastIndex)
            readableWords.removeAt(lastIndex)
            wordMapper.deleteWordMapping(lastIndex)

            if (separators.isNotEmpty()) {
                separators.removeAt(separators.size - 1)
            }
        }
    }

    /**
     * Get the full obfuscated text for display
     */
    fun getObfuscatedText(): String {
        val builder = StringBuilder()

        for (i in obfuscatedWords.indices) {
            builder.append(obfuscatedWords[i])
            if (i < separators.size) {
                builder.append(separators[i])
            }
        }

        // Add current word (not obfuscated yet since not finalized)
        builder.append(currentWord)

        return builder.toString()
    }

    /**
     * Get the full readable text
     */
    fun getReadableText(): String {
        val builder = StringBuilder()

        for (i in readableWords.indices) {
            builder.append(readableWords[i])
            if (i < separators.size) {
                builder.append(separators[i])
            }
        }

        // Add current word
        builder.append(currentWord)

        return builder.toString()
    }

    /**
     * Get the readable word at a specific character position in the obfuscated text
     */
    fun getReadableWordAtPosition(position: Int): String? {
        var currentPos = 0

        // Check each word
        for (i in obfuscatedWords.indices) {
            val wordLength = obfuscatedWords[i].length

            if (position >= currentPos && position < currentPos + wordLength) {
                // Position is in this word
                return readableWords[i]
            }

            currentPos += wordLength

            // Add separator length
            if (i < separators.size) {
                currentPos += separators[i].length
            }
        }

        // Check if in current word
        if (position >= currentPos && position < currentPos + currentWord.length) {
            return currentWord
        }

        return null
    }

    /**
     * Clear all content
     */
    fun clear() {
        readableWords.clear()
        obfuscatedWords.clear()
        separators.clear()
        currentWord = ""
        wordMapper.clearAllMappings()
    }

    /**
     * Check if buffer is empty
     */
    fun isEmpty(): Boolean {
        return readableWords.isEmpty() && currentWord.isEmpty()
    }

    /**
     * Get length of obfuscated text
     */
    fun length(): Int {
        return getObfuscatedText().length
    }
}