package com.example.brain.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.brain.ui.theme.IndigoPrimary
import com.example.brain.ui.theme.PurpleVault
import com.example.brain.util.BiometricAuthManager
import com.example.brain.util.SecurityManager
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    securityManager: SecurityManager,
    biometricAuthManager: BiometricAuthManager,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableStateOf(securityManager.getRemainingLockoutSeconds()) }

    // Countdown effect if locked out
    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = securityManager.getRemainingLockoutSeconds()
        }
    }

    // Auto-trigger biometric on appearance if enabled and available
    val isBiometricEnabled = securityManager.isBiometricEnabled()
    val isBiometricAvailable = remember { biometricAuthManager.isBiometricAvailable() }

    fun triggerBiometric() {
        if (!isBiometricEnabled || !isBiometricAvailable) return
        val activity = context as? FragmentActivity ?: return
        biometricAuthManager.showBiometricPrompt(
            activity = activity,
            title = "Unlock Second Brain",
            subtitle = "Verify your biometric identity to access your knowledge",
            onSuccess = {
                securityManager.unlockWithBiometric()
                onUnlocked()
            },
            onError = { _, errString ->
                if (errString.isNotBlank() && errString != "Cancel" && errString != "Use PIN") {
                    errorMessage = errString.toString()
                }
            },
            onFailed = {
                errorMessage = "Biometric authentication failed. Enter your PIN."
            }
        )
    }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled && isBiometricAvailable && lockoutSeconds == 0L) {
            triggerBiometric()
        }
    }

    fun submitPin(pin: String) {
        val result = securityManager.verifyPin(pin)
        when (result) {
            SecurityManager.UnlockResult.SUCCESS -> {
                pinInput = ""
                errorMessage = null
                onUnlocked()
            }
            SecurityManager.UnlockResult.SUCCESS_DURESS -> {
                pinInput = ""
                errorMessage = null
                onUnlocked()
            }
            SecurityManager.UnlockResult.INCORRECT_PIN -> {
                pinInput = ""
                errorMessage = "Incorrect PIN. Try again."
                lockoutSeconds = securityManager.getRemainingLockoutSeconds()
            }
            SecurityManager.UnlockResult.RATE_LIMITED -> {
                pinInput = ""
                lockoutSeconds = securityManager.getRemainingLockoutSeconds()
                errorMessage = "Too many attempts. Cooldown active."
            }
            SecurityManager.UnlockResult.NO_PIN_SET -> {
                onUnlocked()
            }
        }
    }

    fun onDigit(d: String) {
        if (lockoutSeconds > 0) return
        if (pinInput.length < 8) {
            val updated = pinInput + d
            pinInput = updated
            errorMessage = null
            if (updated.length >= 4 && (updated.length == 4 || updated.length == 6)) {
                submitPin(updated)
            }
        }
    }

    fun onBackspace() {
        if (pinInput.isNotEmpty()) {
            pinInput = pinInput.dropLast(1)
            errorMessage = null
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(IndigoPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (lockoutSeconds > 0) "Locked out for $lockoutSeconds seconds" else "Second Brain is protected",
                style = MaterialTheme.typography.bodyMedium,
                color = if (lockoutSeconds > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // PIN Indicator Dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < pinInput.length) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            // Error / Status Message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Keypad Layout
            val keypad = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(if (isBiometricEnabled && isBiometricAvailable) "BIO" else "", "0", "DEL")
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (row in keypad) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        for (btn in row) {
                            when (btn) {
                                "" -> {
                                    Spacer(modifier = Modifier.size(68.dp))
                                }
                                "BIO" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(PurpleVault.copy(alpha = 0.15f))
                                            .clickable { triggerBiometric() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Biometric Authentication",
                                            tint = PurpleVault,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                "DEL" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .clickable { onBackspace() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Backspace",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { onDigit(btn) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = btn,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
