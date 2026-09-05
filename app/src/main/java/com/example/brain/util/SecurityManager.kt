package com.example.brain.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Enterprise-grade security manager for Second Brain:
 * - Master PIN & Duress Decoy PIN derivation via PBKDF2-HMAC-SHA256 (15,000 iterations, 16-byte random salt).
 * - Constant-time comparison to prevent timing side-channel attacks.
 * - Brute-force rate limiting with cooldown timer.
 * - Hardware-backed EncryptedSharedPreferences (Android Keystore).
 * - App-wide Lock with configurable background timeouts (Immediately, 30s, 1m, 5m, 15m).
 * - Dual-PIN Duress mode: activates restricted decoy view under coercion without destructive actions.
 */
class SecurityManager(context: Context) {

    private val prefs: SharedPreferences

    companion object {
        private const val PREFS_FILE = "second_brain_secure_vault_prefs"
        private const val KEY_VAULT_PIN_HASH = "vault_pin_pbkdf2_hash"
        private const val KEY_VAULT_SALT = "vault_salt_hex"
        private const val KEY_DURESS_PIN_HASH = "duress_pin_pbkdf2_hash"
        private const val KEY_DURESS_SALT = "duress_salt_hex"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_AUTO_LOCK_TIMEOUT_MS = "auto_lock_timeout_ms"
        private const val KEY_LAST_BACKGROUND_TIMESTAMP = "last_background_timestamp"
        private const val KEY_LAST_UNLOCKED_TIMESTAMP = "last_unlocked_timestamp"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_TIMESTAMP = "lockout_until_timestamp"

        const val TIMEOUT_IMMEDIATELY = 0L
        const val TIMEOUT_30_SEC = 30_000L
        const val TIMEOUT_1_MIN = 60_000L
        const val TIMEOUT_5_MIN = 300_000L
        const val TIMEOUT_15_MIN = 900_000L

        private const val PBKDF2_ITERATIONS = 15_000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds lockout
    }

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var isDecoyMode: Boolean = false
        private set

    var isAppLockedState: Boolean = isAppLockEnabled()
        private set

    // ---------------------------------------------------------------------
    // Status Checks
    // ---------------------------------------------------------------------

    fun isPinSet(): Boolean {
        return prefs.contains(KEY_VAULT_PIN_HASH)
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            isAppLockedState = false
        }
    }

    fun getAutoLockTimeoutMs(): Long {
        return prefs.getLong(KEY_AUTO_LOCK_TIMEOUT_MS, TIMEOUT_1_MIN)
    }

    fun setAutoLockTimeoutMs(timeoutMs: Long) {
        prefs.edit().putLong(KEY_AUTO_LOCK_TIMEOUT_MS, timeoutMs).apply()
    }

    // ---------------------------------------------------------------------
    // PIN Setup & Storage (PBKDF2-HMAC-SHA256)
    // ---------------------------------------------------------------------

    fun setMasterPin(pin: String) {
        val salt = generateSalt()
        val hash = derivePbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_VAULT_SALT, salt.toHex())
            .putString(KEY_VAULT_PIN_HASH, hash.toHex())
            .apply()
    }

    fun setDuressPin(pin: String) {
        val salt = generateSalt()
        val hash = derivePbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_DURESS_SALT, salt.toHex())
            .putString(KEY_DURESS_PIN_HASH, hash.toHex())
            .apply()
    }

    fun clearDuressPin() {
        prefs.edit()
            .remove(KEY_DURESS_PIN_HASH)
            .remove(KEY_DURESS_SALT)
            .apply()
    }

    fun hasDuressPin(): Boolean {
        return prefs.contains(KEY_DURESS_PIN_HASH)
    }

    // ---------------------------------------------------------------------
    // Verification & Rate Limiting
    // ---------------------------------------------------------------------

    enum class UnlockResult {
        SUCCESS,
        SUCCESS_DURESS,
        INCORRECT_PIN,
        RATE_LIMITED,
        NO_PIN_SET
    }

    fun getRemainingLockoutSeconds(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            (lockoutUntil - now + 999) / 1000
        } else {
            0L
        }
    }

    fun verifyPin(pin: String): UnlockResult {
        if (!isPinSet()) return UnlockResult.NO_PIN_SET

        val remainingLockout = getRemainingLockoutSeconds()
        if (remainingLockout > 0) {
            return UnlockResult.RATE_LIMITED
        }

        // Check Duress Decoy PIN
        val duressSaltHex = prefs.getString(KEY_DURESS_SALT, null)
        val duressHashHex = prefs.getString(KEY_DURESS_PIN_HASH, null)
        if (duressSaltHex != null && duressHashHex != null) {
            val inputDuressHash = derivePbkdf2(pin, duressSaltHex.hexToByteArray())
            if (constantTimeEquals(inputDuressHash, duressHashHex.hexToByteArray())) {
                isDecoyMode = true
                resetFailedAttempts()
                recordUnlock()
                isAppLockedState = false
                return UnlockResult.SUCCESS_DURESS
            }
        }

        // Check Master Vault PIN
        val masterSaltHex = prefs.getString(KEY_VAULT_SALT, null)
        val masterHashHex = prefs.getString(KEY_VAULT_PIN_HASH, null)
        if (masterSaltHex != null && masterHashHex != null) {
            val inputMasterHash = derivePbkdf2(pin, masterSaltHex.hexToByteArray())
            if (constantTimeEquals(inputMasterHash, masterHashHex.hexToByteArray())) {
                isDecoyMode = false
                resetFailedAttempts()
                recordUnlock()
                isAppLockedState = false
                return UnlockResult.SUCCESS
            }
        }

        handleFailedAttempt()
        return if (getRemainingLockoutSeconds() > 0) UnlockResult.RATE_LIMITED else UnlockResult.INCORRECT_PIN
    }

    fun unlockWithBiometric(): Boolean {
        if (!isPinSet()) return false
        isDecoyMode = false
        resetFailedAttempts()
        recordUnlock()
        isAppLockedState = false
        return true
    }

    fun exitDecoyMode(masterPin: String): Boolean {
        val masterSaltHex = prefs.getString(KEY_VAULT_SALT, null) ?: return false
        val masterHashHex = prefs.getString(KEY_VAULT_PIN_HASH, null) ?: return false
        val inputHash = derivePbkdf2(masterPin, masterSaltHex.hexToByteArray())
        return if (constantTimeEquals(inputHash, masterHashHex.hexToByteArray())) {
            isDecoyMode = false
            true
        } else {
            false
        }
    }

    private fun handleFailedAttempt() {
        val currentFailed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentFailed)
        if (currentFailed >= MAX_FAILED_ATTEMPTS) {
            editor.putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, System.currentTimeMillis() + LOCKOUT_DURATION_MS)
        }
        editor.apply()
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
            .apply()
    }

    // ---------------------------------------------------------------------
    // Lifecycle & Auto-Lock Tracking
    // ---------------------------------------------------------------------

    fun recordAppBackground() {
        prefs.edit().putLong(KEY_LAST_BACKGROUND_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    fun recordAppForeground() {
        if (!isAppLockEnabled()) {
            isAppLockedState = false
            return
        }

        val lastBackground = prefs.getLong(KEY_LAST_BACKGROUND_TIMESTAMP, 0L)
        if (lastBackground == 0L) return

        val timeout = getAutoLockTimeoutMs()
        val elapsed = System.currentTimeMillis() - lastBackground
        if (timeout == TIMEOUT_IMMEDIATELY || elapsed >= timeout) {
            isAppLockedState = true
        }
    }

    fun recordUnlock() {
        prefs.edit().putLong(KEY_LAST_UNLOCKED_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    fun isSessionExpired(): Boolean {
        val lastUnlocked = prefs.getLong(KEY_LAST_UNLOCKED_TIMESTAMP, 0L)
        val timeout = getAutoLockTimeoutMs().coerceAtLeast(TIMEOUT_1_MIN)
        return (System.currentTimeMillis() - lastUnlocked) > timeout
    }

    fun lock() {
        prefs.edit().putLong(KEY_LAST_UNLOCKED_TIMESTAMP, 0L).apply()
        if (isAppLockEnabled()) {
            isAppLockedState = true
        }
    }

    // ---------------------------------------------------------------------
    // Cryptographic Primitives
    // ---------------------------------------------------------------------

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    private fun derivePbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return MessageDigest.isEqual(a, b)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArray(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
