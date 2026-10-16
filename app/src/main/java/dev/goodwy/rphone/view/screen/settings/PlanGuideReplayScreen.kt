package dev.goodwy.rphone.view.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.bootika.dotdialer.core.license.AppPlan
import io.github.bootika.dotdialer.core.license.PlanGuidePolicy

/** Explicit replay never changes the automatic presentation marker or grants access. */
@Destination<RootGraph>
@Composable
fun PlanGuideReplayScreen(navigator: DestinationsNavigator) {
    val manager = rememberPlanAccessManager()
    val state by manager.state.collectAsState()
    val effective = remember(state) { manager.effective }
    if (effective == AppPlan.FREE) {
        PlanAccessContent(onClose = { navigator.navigateUp() })
        return
    }
    PlanGuideReplayContent(effective, onClose = { navigator.navigateUp() }, onMore = { tier ->
        if (tier == AppPlan.PLUS) navigator.navigate(ContactPhotosScreenDestination())
        else navigator.navigate(InterfaceScreenDestination())
    })
}

@Composable
internal fun PlanGuideReplayContent(effective: AppPlan, onClose: () -> Unit, onMore: (AppPlan) -> Unit) {
    val pages = PlanGuidePolicy.pending(effective, AppPlan.FREE)
    var index by rememberSaveable { mutableIntStateOf(0) }
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val current = index.coerceAtMost(pages.lastIndex)
    BackHandler {
        if (current > 0) index = current - 1 else onClose()
    }
    key(pages[current]) {
        PlanGuideContent(pages[current], onNext = {
            if (current < pages.lastIndex) index = current + 1 else onClose()
        }, onMore = { onMore(pages[current]) })
    }
}
