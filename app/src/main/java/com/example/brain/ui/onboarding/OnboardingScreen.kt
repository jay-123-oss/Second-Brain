package com.example.brain.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brain.ui.theme.EmeraldSuccess
import com.example.brain.ui.theme.IndigoPrimary
import com.example.brain.ui.theme.PurpleVault

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bullets: List<String>
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onConfigureSecurity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = remember {
        listOf(
            OnboardingStep(
                title = "Your Knowledge.\nOrganized Locally.",
                subtitle = "Second Brain is a private, local-first personal knowledge system designed to capture, connect, and retrieve what matters.",
                icon = Icons.Default.AutoAwesome,
                iconColor = IndigoPrimary,
                bullets = listOf(
                    "Capture anything from links to PDFs",
                    "Deterministic and semantic intelligence",
                    "Completely offline and private by default"
                )
            ),
            OnboardingStep(
                title = "Capture Anything\nIn Seconds",
                subtitle = "Share any link, article, text, image, video, or PDF directly to Second Brain via the Android Share Sheet.",
                icon = Icons.Default.AddCircleOutline,
                iconColor = IndigoPrimary,
                bullets = listOf(
                    "Instant saving with zero lag",
                    "Automatic background metadata enrichment",
                    "Deep text extraction from web pages and files"
                )
            ),
            OnboardingStep(
                title = "Structure Your Mind\nEffortlessly",
                subtitle = "Organize with hierarchical Projects, flexible Tags, dynamic Smart Collections, and automatic Topic clustering.",
                icon = Icons.Default.FolderOpen,
                iconColor = Color(0xFF0284C7),
                bullets = listOf(
                    "Projects with nested sub-projects",
                    "Smart Collections with automatic rules",
                    "Resurfacing of forgotten knowledge"
                )
            ),
            OnboardingStep(
                title = "Intelligent Search\n& Exploration",
                subtitle = "Find whatever you need through multi-dimensional filters, instant FTS5 search, and semantic understanding.",
                icon = Icons.Default.Explore,
                iconColor = Color(0xFF0D9488),
                bullets = listOf(
                    "Instant full-text search across all content",
                    "Related knowledge connections and graphs",
                    "Monthly topic evolution timelines"
                )
            ),
            OnboardingStep(
                title = "Private Vault\n& Real Security",
                subtitle = "Protect confidential notes and documents in an encrypted Vault with biometric authentication and zero cloud exposure.",
                icon = Icons.Default.Security,
                iconColor = PurpleVault,
                bullets = listOf(
                    "Hardware-backed AES-256 encryption",
                    "Biometric & Master PIN authentication",
                    "Duress PIN with stealth Decoy Mode",
                    "Vault data is strictly excluded from normal search"
                )
            ),
            OnboardingStep(
                title = "Your Second Brain\nIs Ready",
                subtitle = "Start building your personal knowledge repository with complete control, automated backups, and local AI assistance.",
                icon = Icons.Default.CheckCircleOutline,
                iconColor = EmeraldSuccess,
                bullets = listOf(
                    "Zero external AI APIs or cloud tracking",
                    "Full backup, restore, and data integrity verification",
                    "Ask questions and get grounded answers"
                )
            )
        )
    }

    var currentStep by remember { mutableIntStateOf(0) }
    val step = steps[currentStep]
    val isLastStep = currentStep == steps.size - 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Step indicators and Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Step dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { idx ->
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (idx == currentStep) 24.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx == currentStep)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                        )
                    }
                }

                // Skip button
                if (!isLastStep) {
                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Content Area with Animated Transition
            AnimatedContent(
                targetState = step,
                label = "onboarding_step_animation"
            ) { targetStep ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon Badge
                    Surface(
                        shape = CircleShape,
                        color = targetStep.iconColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(88.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = targetStep.icon,
                                contentDescription = targetStep.title,
                                tint = targetStep.iconColor,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Title
                    Text(
                        text = targetStep.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtitle
                    Text(
                        text = targetStep.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Bullets Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            targetStep.bullets.forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = targetStep.iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = bullet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Action Area
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLastStep) {
                    Button(
                        onClick = onComplete,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Get Started",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onComplete()
                            onConfigureSecurity()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configure Security (PIN / Vault)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Button(
                        onClick = { currentStep++ },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
