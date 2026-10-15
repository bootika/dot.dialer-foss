package dev.goodwy.rphone.view.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.goodwy.rphone.R
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.view.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class AboutBrandRenderingTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun `guest logos keep their own brand in every host accent theme and font size`() {
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var accent by mutableStateOf(DotAccent.Jade)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,font)) {
                DotDialerTheme(systemDark=dark,accent=accent) {
                    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) { AboutOtherApps("en") }
                }
            }
        }
        for(mode in listOf(false,true)) for(scale in listOf(1f,1.5f)) for(color in DotAccent.entries) {
            rule.runOnIdle { dark=mode;font=scale;accent=color }
            rule.mainClock.advanceTimeBy(300);rule.waitForIdle()
            lateinit var bitmap:Bitmap
            rule.runOnIdle { val view=rule.activity.window.decorView;bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap)) }
            val dir=File(System.getProperty("user.dir"),"build/reports/visual/about-brand/${BuildConfig.FLAVOR}");dir.mkdirs()
            File(dir,"${if(mode)"dark"else"light"}-font${(scale*100).toInt()}-${color.key}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            val dictate=rule.onNodeWithTag("other-app-logo-${R.drawable.ic_dot_dictate_about}",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertEquals(0xFF6857E5.toInt(),bitmap.getPixel(dictate.center.x.toInt(),dictate.top.toInt()+4))
            val radio=rule.onNodeWithTag("other-app-logo-${R.drawable.ic_dark_edge_radio}",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            for(y in radio.top.toInt()+12 until radio.bottom.toInt()-12) for(x in radio.left.toInt()+12 until radio.right.toInt()-12) {
                val p=bitmap.getPixel(x,y);assertEquals((p shr 16) and 255,(p shr 8) and 255);assertEquals((p shr 8) and 255,p and 255)
            }
        }
    }
}
