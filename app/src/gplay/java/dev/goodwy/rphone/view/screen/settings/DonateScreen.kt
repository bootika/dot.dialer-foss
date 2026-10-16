package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.Composable
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.generated.destinations.PlanGuideReplayScreenDestination

@Destination<RootGraph>
@Composable
fun DonateScreen(navigator: DestinationsNavigator) {
    PlanAccessContent(navigator::navigateUp, onGuides = { navigator.navigate(PlanGuideReplayScreenDestination) })
}
