package dev.goodwy.rphone

import android.content.Intent
import android.widget.Toast
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SearchScreenDestination
import dev.goodwy.rphone.controller.CallActivity
import dev.goodwy.rphone.controller.NavigationTarget
import dev.goodwy.rphone.controller.contactManagementIntentRoute
import dev.goodwy.rphone.controller.contactPhotoReviewRoute
import io.github.bootika.dotdialer.core.diagnostics.DialNumberStage
import io.github.bootika.dotdialer.diagnostics.recordDialNumber

internal fun MainActivity.navigateExternalTarget(navController: NavController, target: NavigationTarget) {
    when (target) {
        is NavigationTarget.OngoingCall -> startActivity(Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        })
        is NavigationTarget.Search -> navController.navigate(SearchScreenDestination(initialQuery = target.query).route)
        is NavigationTarget.Recents -> {
            navController.navigate(RecentScreenDestination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                    inclusive = false // Don't remove the home screen
                }
                launchSingleTop = true
                restoreState = true

                // Disable the animation for instant opening from a notification
                anim {
                    enter = 0
                    exit = 0
                    popEnter = 0
                    popExit = 0
                }
            }
        }
        is NavigationTarget.Dialpad -> {
            recordDialNumber(DialNumberStage.NAVIGATION, target.number)
            navController.navigate(DialPadScreenDestination(initialNumber = target.number).route)
        }
        is NavigationTarget.ContactUnavailable -> {
            Toast.makeText(this, R.string.no_contacts_found, Toast.LENGTH_SHORT).show()
        }
        is NavigationTarget.ContactDetails -> {
            navController.navigate(ContactDetailsScreenDestination(contactId = target.contactId).route)
        }
        is NavigationTarget.ContactEdit -> {
            navController.navigate(ContactEditScreenDestination(
                contactId = target.contactId,
                initialName = target.initialName,
                initialPhone = target.initialPhone
            ).route) {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        }
        is NavigationTarget.ContactPhotos -> {
            navController.navigate(contactPhotoReviewRoute(target.account)) { launchSingleTop = true }
        }
        is NavigationTarget.ContactManagement -> {
            navController.navigate(contactManagementIntentRoute()) {
                launchSingleTop = true
            }
        }
    }
}
