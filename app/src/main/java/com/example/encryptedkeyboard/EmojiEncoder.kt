package com.example.encryptedkeyboard

import android.util.Base64

/**
 * Encodes encrypted messages as emoji sequences
 */
class EmojiEncoder {

    // Large emoji set for encoding (256 emojis for byte values 0-255)
    private val emojiSet = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
        "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
        "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁",
        "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣",
        "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹",
        "👺", "👻", "👽", "👾", "🤖", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "🙈", "🙉",
        "🙊", "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞", "💕", "💟", "❣️", "💔", "❤️", "🧡", "💛",
        "💚", "💙", "💜", "🤎", "🖤", "🤍", "💯", "💢", "💥", "💫", "💦", "💨", "🕳️", "💣", "💬", "👁️",
        "🗨️", "🗯️", "💭", "💤", "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
        "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲",
        "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🦷", "🦴",
        "👀", "👁️", "👅", "👄", "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩", "🧓", "👴", "👵",
        "🙍", "🙎", "🙅", "🙆", "💁", "🙋", "🧏", "🙇", "🤦", "🤷", "👮", "🕵️", "💂", "👷", "🤴", "👸",
        "👳", "👲", "🧕", "🤵", "👰", "🤰", "🤱", "👼", "🎅", "🤶", "🦸", "🦹", "🧙", "🧚", "🧛", "🧜",
        "🧝", "🧞", "🧟", "💆", "💇", "🚶", "🧍", "🧎", "🏃", "💃", "🕺", "🕴️", "👯", "🧖", "🧗", "🤺"
    )

    /**
     * Encode encrypted Base64 string to emoji sequence (no markers)
     */
    fun encodeToEmoji(encryptedBase64: String): String {
        val bytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
        return bytes.map { byte ->
            val index = byte.toInt() and 0xFF  // Convert to unsigned 0-255
            emojiSet[index]
        }.joinToString("")
    }

    /**
     * Decode emoji sequence back to encrypted Base64 string
     */
    fun decodeFromEmoji(emojiMessage: String): String? {
        // Convert emojis back to bytes
        val bytes = mutableListOf<Byte>()
        var i = 0

        while (i < emojiMessage.length) {
            // Handle potential multi-codepoint emojis
            var found = false

            // Try 2-character emojis first (some emojis use 2 code units)
            if (i + 1 < emojiMessage.length) {
                val twoChar = emojiMessage.substring(i, i + 2)
                val index = emojiSet.indexOf(twoChar)
                if (index != -1) {
                    bytes.add(index.toByte())
                    i += 2
                    found = true
                }
            }

            // Try single character
            if (!found) {
                val oneChar = emojiMessage[i].toString()
                val index = emojiSet.indexOf(oneChar)
                if (index != -1) {
                    bytes.add(index.toByte())
                    i++
                } else {
                    // Skip unknown characters (spaces, newlines, etc.)
                    i++
                }
            }
        }

        if (bytes.isEmpty()) {
            return null
        }

        return Base64.encodeToString(bytes.toByteArray(), Base64.DEFAULT)
    }

    /**
     * Check if a message appears to be emoji-encoded
     * Heuristic: contains multiple emojis from our set
     */
    fun isEmojiEncoded(message: String): Boolean {
        var emojiCount = 0
        var i = 0

        while (i < message.length && emojiCount < 5) {
            // Try 2-character emojis
            if (i + 1 < message.length) {
                val twoChar = message.substring(i, i + 2)
                if (emojiSet.contains(twoChar)) {
                    emojiCount++
                    i += 2
                    continue
                }
            }

            // Try single character
            val oneChar = message[i].toString()
            if (emojiSet.contains(oneChar)) {
                emojiCount++
            }
            i++
        }

        // If we found 5+ of our specific emojis, likely emoji-encoded
        return emojiCount >= 5
    }
}