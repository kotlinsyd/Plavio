package com.pulsestream.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private const val PLAVIO_META_MANIFEST = "https://plaviometa.vercel.app/manifest.json"

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val STEPS = listOf(
    OnboardingStep(
        icon = Icons.Filled.PlayArrow,
        title = "Welcome to Plavio",
        body = "Plavio is your own streaming hub — movies, shows and live TV, " +
            "all in one beautiful dark app. No accounts, no subscriptions. " +
            "You choose the sources, Plavio plays them."
    ),
    OnboardingStep(
        icon = Icons.Filled.Source,
        title = "Add your first source",
        body = "Sources are small addons that bring you catalogs. PlavioMeta is " +
            "the free starter — trending movies, shows, anime and live picks. " +
            "One tap installs it; you can add more anytime in Addons."
    ),
    OnboardingStep(
        icon = Icons.Filled.LiveTv,
        title = "You're all set",
        body = "Browse the Home tab, search everything at once, pin your " +
            "favourite live channels, and keep a watchlist. Enjoy the show!"
    )
)

/**
 * First-run 3-step welcome. Skippable, shows exactly once
 * (SettingsStore.onboardingDone persists the flag).
 *
 * Step 2 offers the PlavioMeta one-tap install — the button really
 * installs the addon through the same path as Addons > Add.
 */
@Composable
fun OnboardingScreen(
    repository: StreamRepository,
    onDone: () -> Unit
) {
    val settings = repository.settings
    val scope = rememberCoroutineScope()
    // Rotation-safe: the step survives; "installing" is restored too, but a
    // rotation kills the install coroutine, so it resets to false on
    // restore (otherwise the spinner would spin forever with no job).
    var step by rememberSaveable { mutableIntStateOf(0) }
    var installing by rememberSaveable { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installedName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { installing = false }

    fun finish() {
        settings.onboardingDone = true
        onDone()
    }

    // Back steps through the guide; on the first step the handler is
    // disabled so the system back exits the app (never traps the user).
    androidx.activity.compose.BackHandler(enabled = step > 0) { step-- }

    GlowBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip (not on the last step — there the button says Done).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (step < STEPS.size - 1) {
                    TextButton(onClick = { finish() }) {
                        Text("Skip", color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
                },
                label = "onboardingStep"
            ) { s ->
                val info = STEPS[s]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                LocalPalette.current.accent.copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            info.icon,
                            contentDescription = null,
                            tint = LocalPalette.current.accent,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        info.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        info.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )

                    // Step 2: the real one-tap PlavioMeta install.
                    if (s == 1) {
                        Spacer(Modifier.height(24.dp))
                        when {
                            installing -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = LocalPalette.current.accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("Installing PlavioMeta…", color = TextSecondary)
                                }
                            }
                            installedName != null -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "$installedName installed ✓",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        installing = true
                                        installError = null
                                        scope.launch {
                                            val result =
                                                repository.addAddonFromUrl(PLAVIO_META_MANIFEST)
                                            installing = false
                                            if (result.isSuccess) {
                                                installedName =
                                                    result.getOrNull()?.name ?: "PlavioMeta"
                                            } else {
                                                installError = result.exceptionOrNull()?.message
                                                    ?: "Couldn't install — check your connection and try again."
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LocalPalette.current.accent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        "Install PlavioMeta",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 4.dp
                                        )
                                    )
                                }
                            }
                        }
                        if (installError != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                installError!!,
                                color = Color(0xFFFF6B6B),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Step dots.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STEPS.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == step) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == step) LocalPalette.current.accent
                                else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Next / Done.
            Button(
                onClick = {
                    if (step < STEPS.size - 1) step++ else finish()
                },
                enabled = !(step == 1 && installing),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalPalette.current.accent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    if (step < STEPS.size - 1) "Next" else "Start watching",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
