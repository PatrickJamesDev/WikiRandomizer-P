package org.nsh07.wikireader.ui.tutorial

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.nsh07.wikireader.R

private enum class StepAction { NONE, TOPICS, RANDOM, SETTINGS }

private class TutorialPage(
    val icon: Int,
    val title: Int,
    val body: Int,
    val action: StepAction = StepAction.NONE
)

private val tutorialPages = listOf(
    TutorialPage(R.drawable.feed, R.string.tutorialWelcomeTitle, R.string.tutorialWelcomeBody),
    TutorialPage(
        R.drawable.expand_all, R.string.tutorialTopicsTitle, R.string.tutorialTopicsBody,
        action = StepAction.TOPICS
    ),
    TutorialPage(
        R.drawable.shuffle, R.string.tutorialRandomTitle, R.string.tutorialRandomBody,
        action = StepAction.RANDOM
    ),
    TutorialPage(
        R.drawable.keyboard_arrow_down, R.string.tutorialPullTitle, R.string.tutorialPullBody
    ),
    TutorialPage(R.drawable.menu, R.string.tutorialSwipeTitle, R.string.tutorialSwipeBody),
    TutorialPage(R.drawable.tab, R.string.tutorialTabsTitle, R.string.tutorialTabsBody),
    TutorialPage(R.drawable.thumb_up, R.string.tutorialTuneTitle, R.string.tutorialTuneBody),
    TutorialPage(
        R.drawable.palette, R.string.tutorialSettingsTitle, R.string.tutorialSettingsBody,
        action = StepAction.SETTINGS
    ),
    TutorialPage(R.drawable.search, R.string.tutorialMoreTitle, R.string.tutorialMoreBody),
    TutorialPage(R.drawable.check, R.string.tutorialDoneTitle, R.string.tutorialDoneBody)
)

/**
 * The first-launch tour. It is a dialog, so it sits above everything (search bar included).
 * Steps are interactive: buttons really open the topics screen / load a random article, and the
 * gesture steps have a small practice card. The current step lives in the ViewModel, so the tour
 * carries on where it left off after the topics screen.
 *
 * @param onOpenTopics opens the topics screen (the tour is paused there and resumes on return)
 * @param onTryRandom loads a random article for real
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WelcomeTutorial(
    step: Int,
    onStepChange: (Int) -> Unit,
    onOpenTopics: () -> Unit,
    onTryRandom: () -> Unit,
    onOpenSettings: () -> Unit,
    onFinish: () -> Unit
) {
    val index = step.coerceIn(0, tutorialPages.lastIndex)
    val page = tutorialPages[index]
    val last = index == tutorialPages.lastIndex

    // A gentle pulse on the call-to-action button draws the eye to the control the page is
    // talking about, so "open Settings" looks like something to press instead of body text.
    val pulse = rememberInfiniteTransition(label = "tutorialCta")
    val ctaScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tutorialCtaScale"
    )
    val ctaModifier = Modifier.graphicsLayer {
        scaleX = ctaScale
        scaleY = ctaScale
    }

    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        painterResource(page.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                when (page.action) {
                    StepAction.TOPICS -> {
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                onStepChange(index + 1) // resume after the topics screen
                                onOpenTopics()
                            },
                            modifier = ctaModifier
                        ) { Text(stringResource(R.string.tutorialOpenTopics)) }
                    }

                    StepAction.RANDOM -> {
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                onStepChange(index + 1)
                                onTryRandom()
                            },
                            modifier = ctaModifier
                        ) { Text(stringResource(R.string.tutorialTryRandom)) }
                    }

                    StepAction.SETTINGS -> {
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                onStepChange(index + 1) // resume after Settings
                                onOpenSettings()
                            },
                            modifier = ctaModifier
                        ) { Text(stringResource(R.string.tutorialOpenSettings)) }
                    }

                    StepAction.NONE -> {}
                }

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in tutorialPages.indices) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == index) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!last) {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = onFinish) {
                            Text(stringResource(R.string.tutorialSkip))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (index > 0) {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = { onStepChange(index - 1) }
                        ) { Text(stringResource(R.string.tutorialBack)) }
                    }
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { if (last) onFinish() else onStepChange(index + 1) }
                    ) {
                        Text(
                            stringResource(
                                if (last) R.string.tutorialFinish else R.string.tutorialNext
                            )
                        )
                    }
                }
            }
        }
    }
}
