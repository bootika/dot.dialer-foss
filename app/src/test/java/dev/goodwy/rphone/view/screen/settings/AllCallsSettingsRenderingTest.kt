package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class AllCallsSettingsRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @After fun clear() { stopKoin() }
    @Test fun `call management switch defaults off persists toggles and remains readable at large font`() {
        val app=RuntimeEnvironment.getApplication();val prefs=PreferenceManager(app)
        assertFalse(prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,false))
        prefs.setBoolean(PreferenceManager.KEY_APP_HAPTICS,false)
        startKoin { modules(module { single { prefs } }) }
        val navigator=Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,arrayOf(DestinationsNavigator::class.java)) { _,method,_ ->
            if(method.returnType==Boolean::class.javaPrimitiveType) true else null
        } as DestinationsNavigator
        val visual=object: ContextWrapper(rule.activity) {
            override fun getDisplay(): android.view.Display=rule.activity.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        }
        rule.setContent { CompositionLocalProvider(LocalContext provides visual,LocalDensity provides Density(1f,1.5f)) {
            DotDialerTheme(systemDark=false) { HomeGesturesScreen(navigator) }
        } }
        rule.mainClock.advanceTimeBy(1000)
        val label=rule.activity.getString(R.string.show_all_recent_calls)
        rule.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5000) { prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,false) }
        rule.waitForIdle(); assertTrue(prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,false))
        assertTrue(PreferenceManager(app).getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,false))
        rule.onNodeWithText(rule.activity.getString(R.string.show_all_recent_calls_subtitle)).assertIsDisplayed()
        rule.runOnIdle {
            val view=rule.activity.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));val directory=File(System.getProperty("user.dir"),"build/reports/visual/all-call-history");directory.mkdirs()
            File(directory,"settings-font150.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        rule.onNodeWithText(label).performClick();rule.waitForIdle()
        rule.waitUntil(5000) { !prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,true) }
        assertFalse(prefs.getBoolean(PreferenceManager.KEY_SHOW_ALL_RECENT_CALLS,true))
    }
}
