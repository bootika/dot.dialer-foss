package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.Composable
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Destination<RootGraph>
@Composable
fun RecentsSearchScreen(navigator: DestinationsNavigator) = HomeGesturesScreen(navigator)
