package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class ContactEditorKeyboardTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun focusedWholeFieldFollowsKeyboardGrowthAndFocusChanges() = verify(false)
    @Test @Config(qualifiers="w800dp-h360dp-land-mdpi")
    fun landscapeLargeFontFollowsKeyboardGrowthAndFocusChanges() = verify(true)
    private fun verify(landscape:Boolean) {
        var keyboard by mutableIntStateOf(0)
        var name by mutableStateOf("Test Alexandru Butica")
        var company by mutableStateOf("Atelier")
        var fixed by mutableStateOf(false)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(landscape) 1.5f else 1f)) {
            DotDialerTheme {
                val insets=WindowInsets(0,0,0,keyboard)
                Box(Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxSize().then(if(fixed) Modifier.windowInsetsPadding(insets) else Modifier)) {
                        LazyColumn(Modifier.fillMaxSize().testTag("viewport"),contentPadding=PaddingValues(12.dp)) {
                            item {
                                Column {
                                    Spacer(Modifier.height(if(landscape) 150.dp else 400.dp))
                                    OutlinedTextField(name,{name=it},label={Text("First name")},
                                        modifier=Modifier.fillMaxWidth().testTag("name").then(if(fixed) Modifier.keepFocusedEditorFieldVisible(insets) else Modifier))
                                    Spacer(Modifier.height(16.dp))
                                    OutlinedTextField(company,{company=it},label={Text("Company")},
                                        modifier=Modifier.fillMaxWidth().testTag("company").then(if(fixed) Modifier.keepFocusedEditorFieldVisible(insets) else Modifier))
                                    Spacer(Modifier.height(400.dp))
                                }
                            }
                        }
                    }
                    if(keyboard>0) Box(Modifier.fillMaxWidth().height(keyboard.dp).align(Alignment.BottomCenter).background(Color.LightGray)) {
                        Text("Keyboard area")
                    }
                }
            }
        } }
        val field=rule.onNodeWithTag("name")
        field.performClick();rule.mainClock.advanceTimeBy(1000)
        val initial=field.fetchSemanticsNode().boundsInRoot
        val height=if(landscape) 360 else 800
        val firstKeyboard=if(landscape) 140 else 360
        assertTrue(initial.bottom>height-firstKeyboard)
        rule.runOnIdle { keyboard=firstKeyboard };rule.mainClock.advanceTimeBy(1000)
        // The uncorrected grouped item leaves part of the selected field behind the keyboard.
        assertTrue(field.fetchSemanticsNode().boundsInRoot.bottom>height-firstKeyboard)
        capture(if(landscape) "landscape-before" else "portrait-before")
        rule.runOnIdle { fixed=true };rule.mainClock.advanceTimeBy(1000)
        assertAbove("name",height-firstKeyboard)
        rule.runOnIdle { keyboard=if(landscape) 180 else 480 };rule.mainClock.advanceTimeBy(1000)
        assertAbove("name",height-(if(landscape) 180 else 480))
        rule.onNodeWithTag("viewport").performScrollToNode(hasText("Company"))
        rule.onNodeWithTag("company").performClick();rule.mainClock.advanceTimeBy(1000)
        assertAbove("company",height-(if(landscape) 180 else 480))
        rule.onNodeWithTag("company").performTextReplacement("Studio")
        assertEquals("Studio",company);assertEquals("Test Alexandru Butica",name)
        capture(if(landscape) "landscape-after" else "portrait-after")
        rule.runOnIdle { keyboard=0 };rule.mainClock.advanceTimeBy(1000)
        assertAbove("company",height)
    }
    private fun assertAbove(tag:String,top:Int) {
        val bounds=rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag bounds=$bounds keyboardTop=$top",bounds.bottom<=top+1f)
        assertTrue(bounds.top>=0f)
    }
    @Suppress("DEPRECATION") // Exercise the API29-compatible window mode policy.
    @Config(sdk=[29,36],application=Application::class)
    @Test fun resizePolicyIsScopedAndRestoresOriginalWindowMode() {
        var editing by mutableStateOf(true)
        val original=WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        rule.activity.window.setSoftInputMode(original)
        rule.setContent { if(editing) ContactEditorKeyboardMode() }
        rule.runOnIdle {
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                rule.activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            editing=false
        }
        rule.runOnIdle { assertEquals(original,rule.activity.window.attributes.softInputMode) }
    }
    private fun capture(name:String) { rule.runOnIdle {
        val view=rule.activity.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap));val file=File("build/reports/visual/contact-ime-d99/$name.png")
        requireNotNull(file.parentFile).mkdirs();file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    } }
}
