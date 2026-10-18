package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.*
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
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h240dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactAvatarIdentityConsistencyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun homeDetailsCallSecondaryAndConferenceUseSameCanonicalColor() {
        val contact = Contact("sample", givenName = "Test Alexandru", familyName = "Butica", phoneNumbers = listOf("0700000001", "0700000002"))
        var order by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalRillAvatarAppearance provides RillAvatarAppearance(true, true, false, true, false, false)) {
                DotDialerTheme(systemDark = dark) {
                    val label = getDisplayName(contact, order)
                    val plan = buildHomeAvatarColorPlan(listOf(contact.displayName to contact.phoneNumbers[0]), listOf(contact.displayName))
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSelectableAvatar(label, null, contact.phoneNumbers[0], plan.contactIndices[0], 48, false, false, "light", 1f, {}, {}, Modifier.size(48.dp).testTag("home"), colorSeed = contact.displayName)
                        RillAvatar(contact.displayName, colorSeed = contact.displayName, modifier = Modifier.size(48.dp).testTag("details"))
                        CalmCallAvatar(null, contact.displayName, contact.phoneNumbers[1], 48.dp, Modifier.testTag("call"))
                        SmallCallAvatar(label, contact.phoneNumbers[0], null, Modifier.testTag("secondary"), colorSeed = contact.displayName)
                        SmallCallAvatar(label, contact.phoneNumbers[1], null, Modifier.testTag("participant"), colorSeed = contact.displayName)
                    }
                }
            }
        }
        val expected = modernPastelAvatarColor(contact.displayName).first.toArgb()
        for(theme in listOf(false, true)) for(displayOrder in listOf(0, 1)) {
            rule.runOnIdle { dark = theme; order = displayOrder }
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            rule.onAllNodesWithText("T", useUnmergedTree = true).assertCountEquals(5)
            val positions = listOf("home", "details", "call", "secondary", "participant").map { rule.onNodeWithTag(it, true).fetchSemanticsNode().boundsInRoot }
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                positions.forEach { bounds -> assertEquals("theme=$theme order=$displayOrder bounds=$bounds", expected, bitmap.getPixel(bounds.left.toInt() + 4, bounds.center.y.toInt())) }
                val file = File("build/reports/visual/avatar-d82/${if(theme) "dark" else "light"}-order$displayOrder.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val renamed = contact.copy(givenName = "Mihai")
        assertEquals(buildHomeAvatarColorPlan(emptyList(), listOf(renamed.displayName)).contactIndices.single(), modernPastelAvatarIndex(renamed.displayName))
        assertEquals(modernPastelAvatarIndex(contact.displayName), buildHomeAvatarColorPlan(listOf(contact.displayName to contact.phoneNumbers[1]), emptyList()).recentIndices.single())
    }
}

