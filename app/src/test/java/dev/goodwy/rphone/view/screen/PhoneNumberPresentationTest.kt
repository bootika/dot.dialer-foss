package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.em
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[29,36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class PhoneNumberPresentationTest {
    @Before fun setup() { org.koin.core.context.startKoin {
        modules(org.koin.dsl.module { single { dev.goodwy.rphone.controller.util.PreferenceManager(org.robolectric.RuntimeEnvironment.getApplication()) } })
    } }
    @After fun cleanup() { org.koin.core.context.stopKoin() }
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun spacesUseOrdinarySizeAndRawPickerCallbackSurvivesFormatting() {
        val raw="+40723123456"
        val text=phoneDisplayAnnotated(raw,"RO")
        assertEquals("+40 723 123 456",text.text)
        assertEquals(3,text.spanStyles.size)
        assertTrue(text.spanStyles.all { it.item.fontSize==1.em && text.text.substring(it.start,it.end)==" " })
        var chosen=""
        rule.setContent { DotDialerTheme {
            NumberPickerDialog(title="Choose number", numbers=listOf(raw), onNumberSelected={chosen=it}, onDismissRequest={})
        } }
        rule.onNodeWithText("+40 723 123 456").performClick()
        rule.runOnIdle { assertEquals(raw,chosen) }
    }
    @Test fun actualHomeRowsRenderWorldNumbersInLightAndDarkLargeText() {
        val dark=mutableStateOf(false)
        rule.setContent { DotDialerTheme(systemDark=dark.value, interfaceScale=if(dark.value) 1.2f else 1f) {
            Column(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                listOf("+40723123456","+493614283555","+33612345678","+442079460018").forEach { raw ->
                    HomeResultText("Test Alexandru Butica",raw,null,"10:19",20,null,false)
                }
            }
        } }
        for (value in listOf("+40 723 123 456","+49 361 4283555","+33 6 12 34 56 78","+44 20 7946 0018")) rule.onNodeWithText(value,substring=true).assertExists()
        capture("light")
        rule.runOnIdle { dark.value=true }
        rule.waitForIdle(); capture("dark")
    }
    @Test fun dialpadDigitTapsKeepRawCursorAndIncomingNumber() {
        shadowOf(rule.activity.getSystemService(TelephonyManager::class.java)).setSimCountryIso("ro")
        val raw=mutableStateOf("+40723123456")
        var cursor=-1
        rule.setContent { DotDialerTheme {
            DialpadNumberDisplay(raw.value,28,onCursorPositionChange={cursor=it},enableAnimations=false)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("+").performTouchInput { click(centerRight) }
        rule.runOnIdle { assertEquals(1,cursor);assertEquals("+40723123456",raw.value);raw.value="*222" }
        rule.waitForIdle()
        rule.onNodeWithText("*").assertExists()
        rule.runOnIdle { assertEquals("*222",raw.value) }
    }
    @Test fun editorOffsetsRoundTripAtEveryDigitAndServiceCodesStayRaw() {
        val raw="+40723123456"
        val result=PhoneNumberVisualTransformation("RO").filter(androidx.compose.ui.text.AnnotatedString(raw))
        assertEquals("+40 723 123 456",result.text.text)
        for(i in 0..raw.length) assertEquals(i,result.offsetMapping.transformedToOriginal(result.offsetMapping.originalToTransformed(i)))
        for(i in 0..result.text.length) assertTrue(result.offsetMapping.transformedToOriginal(i) in 0..raw.length)
        assertEquals("*222",PhoneNumberVisualTransformation("RO").filter(androidx.compose.ui.text.AnnotatedString("*222")).text.text)
    }
    private fun capture(name:String) {
        val decor=rule.activity.window.decorView
        val image=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888)
        rule.runOnIdle { decor.draw(Canvas(image)) }
        val out=File("build/reports/visual/phone-number-display/$name.png");requireNotNull(out.parentFile).mkdirs()
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
