package dev.goodwy.rphone.controller

import android.app.Application
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.Navigator
import com.ramcosta.composedestinations.generated.destinations.ContactPhotosScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactManagementScreenDestination
import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class ContactIntentRoutesTest {
    @Navigator.Name("fixture")
    private class FixtureNavigator: Navigator<NavDestination>() {
        override fun createDestination()=NavDestination("fixture")
    }
    @Test fun generatedReviewDirectionRequiresItsStringRouteAndPreservesArguments() {
        for(account in listOf(null,"test+one@example.test")) {
            val nav=NavController(RuntimeEnvironment.getApplication())
            nav.navigatorProvider.addNavigator(FixtureNavigator())
            val graph=NavGraph(nav.navigatorProvider.getNavigator(NavGraphNavigator::class.java)).apply { route="test_root" }
            graph.addDestination(NavDestination("fixture").apply { route="start" })
            graph.addDestination(NavDestination("fixture").apply {
                route=ContactPhotosScreenDestination.route
                for(arg in ContactPhotosScreenDestination.arguments) addArgument(arg.name,arg.argument)
            })
            graph.addDestination(NavDestination("fixture").apply { route=ContactManagementScreenDestination.route })
            graph.setStartDestination("start");nav.graph=graph
            assertThrows(SerializationException::class.java) { nav.navigate(ContactPhotosScreenDestination(account,true)) }
            nav.navigate(contactPhotoReviewRoute(account))
            assertEquals(ContactPhotosScreenDestination.route,nav.currentDestination?.route)
            assertEquals(account,nav.currentBackStackEntry?.arguments?.getString("requestedAccount"))
            assertEquals(true,nav.currentBackStackEntry?.arguments?.getBoolean("openReview"))
            nav.navigate(contactManagementIntentRoute())
            assertEquals(ContactManagementScreenDestination.route,nav.currentDestination?.route)
        }
    }
}
