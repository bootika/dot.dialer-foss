package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.PreferenceManager

/** A single preference-driven renderer; call-waiting context belongs only to its callbacks. */
@Composable
internal fun IncomingCallResponseControls(
    incomingMode: Int,
    answerOnStart: Boolean,
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    secondaryCount: Int,
    secondaryActions: @Composable () -> Unit,
    rowGap: Dp,
    actionGap: Dp,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    val mode = incomingMode.takeIf { it in listOf(
        PreferenceManager.INCOMING_CALL_UI_HORIZONTAL_SWIPE,
        PreferenceManager.INCOMING_CALL_UI_TAP_BUTTONS,
        PreferenceManager.INCOMING_CALL_UI_HORIZONTAL_SLIDER,
        PreferenceManager.INCOMING_CALL_UI_VERTICAL_SWIPE,
        PreferenceManager.INCOMING_CALL_UI_SAMSUNG_RADIAL,
        PreferenceManager.INCOMING_CALL_UI_DEFAULT_SWIPE,
    ) }
        ?: PreferenceManager.DEFAULT_INCOMING_CALL_UI_MODE
    val answer by rememberUpdatedState(onAnswer)
    val decline by rememberUpdatedState(onDecline)
    val combinedGrid = mode == PreferenceManager.INCOMING_CALL_UI_TAP_BUTTONS ||
        mode == PreferenceManager.INCOMING_CALL_UI_DEFAULT_SWIPE

    // Changing direction/style cancels an unfinished gesture, not an incoming call.
    key(mode, answerOnStart) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag("incomingCallMode-$mode"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            if (!combinedGrid) {
                CallActionGrid(rowCounts = listOf(secondaryCount), modifier = Modifier.fillMaxWidth()) {
                    secondaryActions()
                }
            }
            when (mode) {
                PreferenceManager.INCOMING_CALL_UI_TAP_BUTTONS -> IncomingCallButtons(
                    useLg, useBlur, blurIntensity, answerOnStart, { answer() }, { decline() },
                    secondaryActions, secondaryCount,
                )
                PreferenceManager.INCOMING_CALL_UI_SAMSUNG_RADIAL -> SamsungRadialAnswerButtons(
                    answerOnStart, { answer() }, { decline() },
                )
                PreferenceManager.INCOMING_CALL_UI_HORIZONTAL_SLIDER -> DualActionHorizontalSlider(
                    useLg, useBlur, blurIntensity, answerOnStart, { answer() }, { decline() },
                )
                PreferenceManager.INCOMING_CALL_UI_VERTICAL_SWIPE -> VerticalSwipeToAnswer(
                    useLg, useBlur, blurIntensity, answerOnStart, { answer() }, { decline() },
                )
                PreferenceManager.INCOMING_CALL_UI_HORIZONTAL_SWIPE -> HorizontalSwipeToAnswer(
                    useLg, useBlur, blurIntensity, answerOnStart, { answer() }, { decline() },
                )
                else -> DefaultSwipeToAnswer(
                    useLg, useBlur, blurIntensity, answerOnStart, actionGap, 0.dp,
                    { answer() }, { decline() }, secondaryActions, secondaryCount,
                )
            }
        }
    }
}
