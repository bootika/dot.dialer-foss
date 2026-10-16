package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import dev.goodwy.rphone.controller.license.PlanAccessManager

internal val LocalPlanAccessManager=staticCompositionLocalOf<PlanAccessManager?> {null}

@Composable
internal fun rememberPlanAccessManager(): PlanAccessManager {
    val provided=LocalPlanAccessManager.current
    val context=LocalContext.current
    return remember(provided,context) {provided ?: PlanAccessManager.get(context)}
}
