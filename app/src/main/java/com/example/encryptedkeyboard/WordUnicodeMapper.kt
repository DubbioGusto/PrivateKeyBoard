package com.example.encryptedkeyboard

import kotlin.random.Random

/**
 * Maps each word to its own unique Unicode mapping
 * Each word gets encoded with a different random mapping
 */
class WordUnicodeMapper {

    // Standard keyboard characters
    private val standardChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,!?@#$%&*()-_=+[]{}:;\"'<>/\\|`~"

    // Store mapping for each word: word index -> (char -> unicode mapping)
    private val wordMappings = mutableMapOf<Int, MutableMap<Char, Char>>()

    // Store reverse mapping for decoding: word index -> (unicode -> char mapping)
    private val wordReverseMappings = mutableMapOf<Int, MutableMap<Char, Char>>()

    /**
     * Generate a new unique Unicode mapping for a word
     * Returns both forward and reverse mappings
     */
    private fun generateNewMapping(): Pair<MutableMap<Char, Char>, MutableMap<Char, Char>> {
        val charToUnicode = mutableMapOf<Char, Char>()
        val unicodeToChar = mutableMapOf<Char, Char>()
        val usedUnicodes = mutableSetOf<Char>()

        for (char in standardChars) {
            var randomUnicode: Char
            do {
                randomUnicode = generateRandomUnicode()
            } while (usedUnicodes.contains(randomUnicode) || randomUnicode in standardChars)

            charToUnicode[char] = randomUnicode
            unicodeToChar[randomUnicode] = char
            usedUnicodes.add(randomUnicode)
        }

        return Pair(charToUnicode, unicodeToChar)
    }

    /**
     * Generate a random valid Unicode character
     */
    private fun generateRandomUnicode(): Char {
        val codePoint = when (Random.nextInt(4)) {
            0 -> Random.nextInt(0x0020, 0xD800)
            1 -> Random.nextInt(0xE000, 0x10000)
            else -> Random.nextInt(0x0020, 0xD800)
        }
        return codePoint.toChar()
    }

    /**
     * Encode a word with a new unique mapping
     * @param wordIndex The index of the word (0-based)
     * @param word The word to encode
     * @return The obfuscated word
     */
    fun encodeWord(wordIndex: Int, word: String): String {
        // Generate new mapping for this word
        val (charToUnicode, unicodeToChar) = generateNewMapping()
        wordMappings[wordIndex] = charToUnicode
        wordReverseMappings[wordIndex] = unicodeToChar

        // Encode the word
        return word.map { charToUnicode[it] ?: it }.joinToString("")
    }

    /**
     * Decode a word using its stored mapping
     * @param wordIndex The index of the word
     * @param obfuscatedWord The obfuscated word to decode
     * @return The readable word
     */
    fun decodeWord(wordIndex: Int, obfuscatedWord: String): String {
        val reverseMapping = wordReverseMappings[wordIndex] ?: return obfuscatedWord
        return obfuscatedWord.map { reverseMapping[it] ?: it }.joinToString("")
    }

    /**
     * Delete mapping for a specific word
     */
    fun deleteWordMapping(wordIndex: Int) {
        wordMappings.remove(wordIndex)
        wordReverseMappings.remove(wordIndex)
    }

    /**
     * Clear all mappings
     */
    fun clearAllMappings() {
        wordMappings.clear()
        wordReverseMappings.clear()
    }

    /**
     * Check if a word has a mapping
     */
    fun hasMapping(wordIndex: Int): Boolean {
        return wordMappings.containsKey(wordIndex)
    }
}