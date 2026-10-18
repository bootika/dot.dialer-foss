package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.LocalRillAvatarAppearance
import dev.goodwy.rphone.view.components.RillAvatarAppearance
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class HomeAvatarLongPressTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `long press on letter avatar selects row and replaces avatar with check`() {
        renderRecentRow(title = "Alexandru", phoneNumber = "+40700000000")

        composeRule.onNodeWithContentDescription("Alexandru")
            .performTouchInput { longClick() }
            .assertIsSelected()
        captureSelectedRow()
    }

    @Test
    fun `long press on unknown caller icon selects row`() {
        renderRecentRow(title = "+40700000000", phoneNumber = "+40700000000")

        composeRule.onNodeWithContentDescription("+40700000000")
            .performTouchInput { longClick() }
            .assertIsSelected()
    }

    @Test
    fun `long press on photo avatar selects row`() {
        val photoUri = "content://dev.goodwy.rphone.test/avatar"
        registerTestPhoto(photoUri)
        renderRecentRow(
            title = "Photo contact",
            phoneNumber = "+40700000001",
            photoUri = photoUri,
        )

        composeRule.onNodeWithContentDescription("Photo contact")
            .performTouchInput { longClick() }
            .assertIsSelected()
    }

    private fun registerTestPhoto(uri: String) {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            stream.toByteArray()
        }
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(Uri.parse(uri), ByteArrayInputStream(bytes))
    }

    private fun renderRecentRow(title: String, phoneNumber: String, photoUri: String? = null) {
        composeRule.setContent {
            var selected by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalRillAvatarAppearance provides TEST_AVATAR_APPEARANCE) {
                DotDialerTheme(systemDark = false) {
                    HomeResultRow(
                        title = title,
                        subtitle = phoneNumber.takeUnless { it == title },
                        photoUri = photoUri,
                        phoneNumber = phoneNumber,
                        swipeEnabled = true,
                        swipeRightAction = PreferenceManager.SWIPE_ACTION_CALL,
                        swipeLeftAction = PreferenceManager.SWIPE_ACTION_MESSAGE,
                        haptics = false,
                        hapticsStrength = "light",
                        hapticsIntensity = 0.5f,
                        onAction = {},
                        onAvatarClick = {},
                        onAvatarLongClick = { selected = !selected },
                        onClick = {},
                        selected = selected,
                        isLastInSection = true,
                        contactNameSize = 18,
                        avatarSize = 46,
                    )
                }
            }
        }
    }

    private fun captureSelectedRow() {
        val output = File(
            requireNotNull(System.getProperty("user.dir")),
            "build/reports/visual/recent-selected-check.png",
        )
        requireNotNull(output.parentFile).mkdirs()
        composeRule.runOnIdle {
            val view = composeRule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }

    private companion object {
        val TEST_AVATAR_APPEARANCE = RillAvatarAppearance(
            showPicture = true,
            showFirstLetter = true,
            colorfulAvatars = false,
            modernPastelAvatars = true,
            secondaryColorAvatars = false,
            googleContactColorAvatars = false,
        )
    }
}
