package dev.goodwy.rphone.view.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], application=Application::class, qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OriginalAlphabetAppearanceTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Test fun restoredRailMatchesIndependentV211AppearancePixelForPixel() {
        var reference by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var selected by mutableStateOf('A')
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,font)) {
                DotDialerTheme(systemDark=dark) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) {
                            if(reference) V211Rail(selected) else AlphabetSideBar(
                                ('A'..'Z').toList()+'#',selected,Modifier.testTag("actualRail"),{},{} )
                        }
                    }
                }
            }
        }
        for(theme in listOf(false,true)) for(scale in listOf(1f,1.5f)) for(letter in listOf('A','M','#')) {
            rule.runOnIdle { dark=theme; font=scale; selected=letter; reference=false }
            rule.waitForIdle()
            val actualBounds=rule.onNodeWithTag("actualRail",true).fetchSemanticsNode().boundsInRoot
            assertEquals(24f,actualBounds.width,0.01f)
            val selectedBounds=rule.onNode(isSelected(),true).fetchSemanticsNode().boundsInRoot
            assertEquals(18f,selectedBounds.width,0.01f)
            assertEquals(18f,selectedBounds.height,0.01f)
            val actual=capture()
            rule.runOnIdle { reference=true };rule.waitForIdle()
            val expected=capture()
            assertTrue("Original V211 pixels differ: theme=$theme font=$scale letter=$letter",actual.sameAs(expected))
            if(letter=='M') {
                val file=File("build/reports/visual/contact-directory-restore-d85/rail-${if(theme) "dark" else "light"}-${(scale*100).toInt()}.png")
                file.parentFile!!.mkdirs();file.outputStream().use { actual.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            actual.recycle();expected.recycle()
        }
    }

    private fun capture(): Bitmap {
        var result:Bitmap?=null
        rule.runOnIdle {
            val view=rule.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            result=bitmap
        }
        return result!!
    }

    /** Independent V211 visual reference, grounded in the inspected original source and APK. */
    @Composable private fun V211Rail(selected:Char) {
        Surface(Modifier.width(24.dp).wrapContentHeight(),color=Color.Transparent,shape=RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(vertical=8.dp),verticalArrangement=Arrangement.Center,
                horizontalAlignment=Alignment.CenterHorizontally) {
                (('A'..'Z').toList()+'#').forEach { letter ->
                    val active=letter==selected
                    Box(Modifier.size(18.dp).clip(CircleShape)
                        .background(if(active) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment=Alignment.Center) {
                        Text(letter.toString(),style=MaterialTheme.typography.labelSmall,fontSize=10.sp,
                            fontWeight=if(active) FontWeight.Bold else FontWeight.Normal,
                            color=if(active) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.8f))
                    }
                }
            }
        }
    }
}
