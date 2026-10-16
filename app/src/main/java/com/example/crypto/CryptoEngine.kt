package com.example.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val AES_KEY_SIZE = 32 // 256 bits
    private const val GCM_IV_LENGTH = 12 // 96 bits
    private const val GCM_TAG_LENGTH = 128 // bits

    // Secure pseudo-random key generator for session pairing
    fun generateSessionKey(): ByteArray {
        val random = SecureRandom()
        val key = ByteArray(AES_KEY_SIZE)
        random.nextBytes(key)
        return key
    }

    // Generate random 4-digit verification PIN for peer mutual authentication
    fun generateVerificationPin(): String {
        val random = SecureRandom()
        val pin = random.nextInt(9000) + 1000
        return pin.toString()
    }

    // Generate device fingerprint
    fun generateDeviceFingerprint(deviceName: String, ip: String): String {
        val raw = "$deviceName-$ip-${System.currentTimeMillis()}"
        return sha256(raw.toByteArray()).take(12).uppercase().chunked(4).joinToString(":")
    }

    // SHA-256 Checksum for files (Hex string)
    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    // Encrypt byte array with AES-GCM
    fun encrypt(data: ByteArray, secretKey: ByteArray): ByteArray {
        val random = SecureRandom()
        val iv = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(secretKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val cipherText = cipher.doFinal(data)
        // Prepend IV to cipher text
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return combined
    }

    // Decrypt byte array with AES-GCM
    fun decrypt(encryptedData: ByteArray, secretKey: ByteArray): ByteArray {
        if (encryptedData.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("Invalid encrypted payload")
        }
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH)

        val cipherTextSize = encryptedData.size - GCM_IV_LENGTH
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(secretKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(cipherText)
    }

    // Wrap output stream in encrypted cipher stream
    fun createEncryptingStream(outputStream: OutputStream, secretKey: ByteArray): Pair<OutputStream, ByteArray> {
        val random = SecureRandom()
        val iv = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(iv)

        outputStream.write(iv) // Write IV header

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(secretKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        return Pair(CipherOutputStream(outputStream, cipher), iv)
    }

    // Wrap input stream in decrypting cipher stream
    fun createDecryptingStream(inputStream: InputStream, secretKey: ByteArray): InputStream {
        val iv = ByteArray(GCM_IV_LENGTH)
        val read = inputStream.read(iv)
        if (read != GCM_IV_LENGTH) {
            throw IllegalArgumentException("Could not read IV from input stream")
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(secretKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return CipherInputStream(inputStream, cipher)
    }
}
