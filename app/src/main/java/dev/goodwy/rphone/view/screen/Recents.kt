package dev.goodwy.rphone.view.screen

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.view.theme.TabTransitionStyle

/** Root route kept stable for shortcuts and upgrades; its UI is the unified Home surface. */
@Destination<RootGraph>(start = true, style = TabTransitionStyle::class)
@Composable
fun RecentScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    navigator: DestinationsNavigator,
) {
    UnifiedHomeScreen(navigator)
}
