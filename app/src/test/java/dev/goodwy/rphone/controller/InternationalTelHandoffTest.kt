package dev.goodwy.rphone.controller

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.Navigator
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import dev.goodwy.rphone.controller.util.OutgoingNumberNormalizer
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class InternationalTelHandoffTest {
    @Navigator.Name("fixture")
    private class FixtureNavigator: Navigator<NavDestination>() {
        override fun createDestination()=NavDestination("fixture")
    }
    @Test fun explicitInternationalNumbersSurviveAndroidIntentsNavigationAndOutgoingPolicy() {
        val context=RuntimeEnvironment.getApplication()
        val vm=MainViewModel(PreferenceManager(context))
        for(action in listOf(Intent.ACTION_VIEW,Intent.ACTION_DIAL,Intent.ACTION_CALL)) {
            for(uri in listOf("tel: +493614283555","tel:%20%2B493614283555","tel:+493614283555","tel:%2B493614283555","tel:+39%2006%2012345678","tel:00493614283555")) {
                val parsed=Uri.parse(uri)
                val expected=parsed.schemeSpecificPart
                val target=vm.getNavigationTarget(Intent(action,parsed),context) as NavigationTarget.Dialpad
                assertEquals(expected,target.number)
                val nav=NavController(context)
                nav.navigatorProvider.addNavigator(FixtureNavigator())
                val graph=NavGraph(nav.navigatorProvider.getNavigator(NavGraphNavigator::class.java)).apply {route="test_root"}
                graph.addDestination(NavDestination("fixture").apply {route="start"})
                graph.addDestination(NavDestination("fixture").apply {
                    route=DialPadScreenDestination.route
                    for(arg in DialPadScreenDestination.arguments) addArgument(arg.name,arg.argument)
                })
                graph.setStartDestination("start");nav.graph=graph
                nav.navigate(DialPadScreenDestination(initialNumber=target.number).route)
                val number=DialPadScreenDestination.argsFrom(nav.currentBackStackEntry!!.arguments).initialNumber!!
                assertEquals(expected,number)
                for(region in listOf("RO","DE","IT",null)) {
                    assertEquals(expected,OutgoingNumberNormalizer.normalize(number,true,region) {_,_->fail("International number must not be reinterpreted");null})
                }
                assertEquals(expected,Uri.fromParts("tel",number,null).schemeSpecificPart)
            }
        }
    }
}
