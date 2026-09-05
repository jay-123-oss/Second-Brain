package com.example.brain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Production-grade cryptographic manager for Second Brain's Private Vault.
 *
 * Security Architecture:
 * - Encryption: AES-256 in Galois/Counter Mode (AES/GCM/NoPadding) with 128-bit authentication tag.
 * - Hardware Protection: Encryption key stored securely in the Android Keystore ("AndroidKeyStore").
 * - IV/Nonce: 12-byte cryptographically secure random IV generated per encryption operation via SecureRandom.
 * - File Format: [1-byte VERSION (0x01)][12-byte IV][N-byte CIPHERTEXT + 16-byte AUTH TAG].
 * - Storage Isolation: All Vault files are stored in app-private `filesDir/vault_media/`, strictly
 *   excluded from FileProvider and external storage.
 * - Zero Plaintext Leakage: Previews/thumbnails are decrypted purely in-memory and never written to disk.
 */
class VaultCryptoManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "SecondBrainVaultMasterKey"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val FORMAT_VERSION: Byte = 0x01
        private const val VAULT_MEDIA_DIR = "vault_media"
    }

    val vaultDirectory: File by lazy {
        File(context.filesDir, VAULT_MEDIA_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    init {
        getOrCreateMasterKey()
    }

    /**
     * Retrieves existing AES key from Android Keystore or generates a new one.
     */
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts an input stream and writes the versioned AES-GCM ciphertext to a vault file.
     * Streaming is performed in IO dispatcher to prevent UI thread starvation.
     */
    suspend fun encryptStreamToVault(
        inputStream: InputStream,
        targetFileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val destinationFile = File(vaultDirectory, targetFileName)
        val tempFile = File(vaultDirectory, "${targetFileName}.tmp")

        try {
            val key = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv

            FileOutputStream(tempFile).use { fos ->
                // Write Header: Version (1 byte) + IV (12 bytes)
                fos.write(FORMAT_VERSION.toInt())
                fos.write(iv)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val updateBytes = cipher.update(buffer, 0, bytesRead)
                    if (updateBytes != null && updateBytes.isNotEmpty()) {
                        fos.write(updateBytes)
                    }
                }
                val finalBytes = cipher.doFinal()
                if (finalBytes != null && finalBytes.isNotEmpty()) {
                    fos.write(finalBytes)
                }
                fos.flush()
            }

            if (tempFile.renameTo(destinationFile)) {
                Result.success(destinationFile)
            } else {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Encrypts raw byte array to a vault file.
     */
    suspend fun encryptBytesToVault(
        data: ByteArray,
        targetFileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        encryptStreamToVault(ByteArrayInputStream(data), targetFileName)
    }

    /**
     * Decrypts a vault file directly into an in-memory ByteArray without any plaintext disk writes.
     */
    suspend fun decryptVaultFileToBytes(file: File): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < (1 + GCM_IV_LENGTH_BYTES)) {
            return@withContext Result.failure(FileNotFoundException("Vault file does not exist or is invalid: ${file.name}"))
        }

        try {
            FileInputStream(file).use { fis ->
                val version = fis.read()
                if (version != FORMAT_VERSION.toInt()) {
                    return@withContext Result.failure(SecurityException("Unsupported vault encryption format version: $version"))
                }

                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                val ivRead = fis.read(iv)
                if (ivRead != GCM_IV_LENGTH_BYTES) {
                    return@withContext Result.failure(SecurityException("Corrupt IV in vault file: ${file.name}"))
                }

                val key = getOrCreateMasterKey()
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, spec)

                val ciphertextWithTag = fis.readBytes()
                val plaintext = cipher.doFinal(ciphertextWithTag)
                Result.success(plaintext)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * In-memory decryption for images to render safely in Jetpack Compose.
     * Prevents any unencrypted bitmap file from ever touching disk.
     */
    suspend fun decryptVaultImageToBitmap(file: File): Result<Bitmap> = withContext(Dispatchers.IO) {
        val bytesResult = decryptVaultFileToBytes(file)
        bytesResult.mapCatching { bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmap ?: throw IllegalStateException("Could not decode decrypted bytes as bitmap")
        }
    }

    /**
     * Safely deletes a vault file from disk.
     */
    suspend fun deleteVaultFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists() && file.canonicalPath.startsWith(vaultDirectory.canonicalPath)) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if a given file path is located inside the protected vault media directory.
     */
    fun isVaultFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.canonicalPath.startsWith(vaultDirectory.canonicalPath)
        } catch (_: Exception) {
            false
        }
    }
}
