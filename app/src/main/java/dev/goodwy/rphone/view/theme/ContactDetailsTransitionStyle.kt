package dev.goodwy.rphone.view.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle

private const val CONTACT_DETAILS_ENTER_DURATION_MS = 190
private const val CONTACT_DETAILS_EXIT_DURATION_MS = 150
private val ContactDetailsEaseOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * Keeps the contact card transition quick and spatially connected to its source row.
 * The previous destination remains nearly stationary while the card enters or leaves.
 */
object ContactDetailsTransitionStyle : NavHostAnimatedDestinationStyle() {

    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            animationSpec = tween(CONTACT_DETAILS_ENTER_DURATION_MS, easing = ContactDetailsEaseOut),
            initialOffsetX = { width -> width / 5 },
        ) + fadeIn(tween(CONTACT_DETAILS_ENTER_DURATION_MS))
    }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(CONTACT_DETAILS_EXIT_DURATION_MS))
    }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(CONTACT_DETAILS_EXIT_DURATION_MS))
    }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            animationSpec = tween(CONTACT_DETAILS_EXIT_DURATION_MS, easing = ContactDetailsEaseOut),
            targetOffsetX = { width -> width / 6 },
        ) + fadeOut(tween(CONTACT_DETAILS_EXIT_DURATION_MS))
    }
}
