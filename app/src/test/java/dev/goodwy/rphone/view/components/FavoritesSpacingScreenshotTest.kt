package dev.goodwy.rphone.view.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class FavoritesSpacingScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `real favorite rail keeps avatars intact and tighter spacing with enlarged fonts`() {
        val fontScale = mutableFloatStateOf(1f)
        val contacts = listOf(
            Contact("first", givenName = "Alexandru", familyName = "Butica"),
            Contact("second", givenName = "Laura", familyName = "Pop"),
            Contact("third", givenName = "Mihai", familyName = "Ionescu"),
        )
        val appearance = RillAvatarAppearance(false, true, false, true, false, false)
        var clicked: String? = null
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale.floatValue),
                LocalRillAvatarAppearance provides appearance,
            ) {
                DotDialerTheme(systemDark = false) {
                    Column {
                        Text("Favorites")
                        IPhoneFavoritesRow(
                            favorites = contacts, isEditing = false, onAddFirst = {},
                            onUnfavorite = {}, onSaveOrder = {}, onClick = { clicked = it.id },
                            onLongClick = {}, displayOrder = 0, avatarSize = 56.dp,
                        )
                    }
                }
            }
        }
        listOf(1f, 1.15f, 1.5f, 2f).forEach { scale ->
            rule.runOnUiThread { fontScale.floatValue = scale }
            rule.waitForIdle()
            val first = rule.onNodeWithText("Alexandru Butica", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val second = rule.onNodeWithText("Laura Pop", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(
                favoriteItemWidthDp(56, scale) + FAVORITE_ITEM_SPACING_DP,
                second.center.x - first.center.x, 1.1f,
            )
            val avatars = rule.onAllNodes(
                hasClickAction() and hasAnyDescendant(hasText("A")), useUnmergedTree = true,
            ).fetchSemanticsNodes().filter { kotlin.math.abs(it.boundsInRoot.width - 56f) < 0.1f }
            assertTrue(avatars.isNotEmpty())
            assertEquals(56f, avatars.first().boundsInRoot.height, 0.1f)
            capture("favorites-font-${(scale * 100).toInt()}.png")
            rule.onNodeWithText("Alexandru Butica").performClick()
            assertEquals("first", clicked)
        }
    }

    private fun capture(name: String) {
        val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/$name")
        output.parentFile!!.mkdirs()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
}
