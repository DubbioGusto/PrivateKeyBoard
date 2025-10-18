package com.example.encryptedkeyboard

import android.annotation.SuppressLint
import android.util.Base64
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class CryptoHelper {

    companion object {
        private const val RSA_ALGORITHM = "RSA"
        private const val AES_ALGORITHM = "AES"
        private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val AES_TRANSFORMATION = "AES/ECB/PKCS5Padding"
        private const val KEY_SIZE = 2048
    }

    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        keyGen.initialize(KEY_SIZE)
        return keyGen.generateKeyPair()
    }

    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
    }

    fun privateKeyToString(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)
    }

    fun stringToPublicKey(keyString: String): PublicKey {
        val keyBytes = Base64.decode(keyString, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePublic(spec)
    }

    fun stringToPrivateKey(keyString: String): PrivateKey {
        val keyBytes = Base64.decode(keyString, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePrivate(spec)
    }

    @SuppressLint("GetInstance")
    fun encrypt(plaintext: String, publicKeyString: String): String {
        val aesKey = generateAESKey()

        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val encryptedData = aesCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val publicKey = stringToPublicKey(publicKeyString)
        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedKey = rsaCipher.doFinal(aesKey.encoded)

        val encryptedKeyLength = encryptedKey.size
        val combined = ByteArray(4 + encryptedKey.size + encryptedData.size)

        combined[0] = (encryptedKeyLength shr 24).toByte()
        combined[1] = (encryptedKeyLength shr 16).toByte()
        combined[2] = (encryptedKeyLength shr 8).toByte()
        combined[3] = encryptedKeyLength.toByte()

        System.arraycopy(encryptedKey, 0, combined, 4, encryptedKey.size)
        System.arraycopy(encryptedData, 0, combined, 4 + encryptedKey.size, encryptedData.size)

        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    @SuppressLint("GetInstance")
    fun decrypt(encryptedString: String, privateKeyString: String): String {
        val combined = Base64.decode(encryptedString, Base64.DEFAULT)

        val encryptedKeyLength = ((combined[0].toInt() and 0xFF) shl 24) or
                ((combined[1].toInt() and 0xFF) shl 16) or
                ((combined[2].toInt() and 0xFF) shl 8) or
                (combined[3].toInt() and 0xFF)

        val encryptedKey = ByteArray(encryptedKeyLength)
        System.arraycopy(combined, 4, encryptedKey, 0, encryptedKeyLength)

        val encryptedData = ByteArray(combined.size - 4 - encryptedKeyLength)
        System.arraycopy(combined, 4 + encryptedKeyLength, encryptedData, 0, encryptedData.size)

        val privateKey = stringToPrivateKey(privateKeyString)
        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKeyBytes = rsaCipher.doFinal(encryptedKey)
        val aesKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)

        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey)
        val decryptedData = aesCipher.doFinal(encryptedData)

        return String(decryptedData, Charsets.UTF_8)
    }

    private fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGen.init(256)
        return keyGen.generateKey()
    }
}