package com.skillverse.app.ui.common

import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.skillverse.app.data.settings.UserSettings

/** Plays the user-approved sound and haptic cues for a result. Uses only system effects, no bundled audio. */
fun interface ResultFeedback {
    fun play(correct: Boolean)
}

@Composable
fun rememberResultFeedback(settings: UserSettings): ResultFeedback {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    return remember(settings.soundEnabled, settings.hapticsEnabled, view, haptics) {
        ResultFeedback { correct ->
            if (settings.soundEnabled) view.playSoundEffect(SoundEffectConstants.CLICK)
            if (settings.hapticsEnabled) {
                haptics.performHapticFeedback(if (correct) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
            }
        }
    }
}
