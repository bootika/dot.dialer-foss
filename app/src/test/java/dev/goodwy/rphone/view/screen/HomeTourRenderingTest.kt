package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.theme.*
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class HomeTourRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var completions = 0
    private var actualActions = 0
    private val contacts = listOf(Contact("first", givenName = "Alexandru", familyName = "Butica"),
        Contact("second", givenName = "Laura", familyName = "Pop"),
        Contact("third", givenName = "Mihai", familyName = "Ionescu"))

    @Composable private fun Fixture(targets: MutableMap<HomeTourTarget, Rect>) {
        var header by remember { mutableStateOf<Rect?>(null) }
        val appearance = RillAvatarAppearance(false, true, false, true, false, false, false)
        CompositionLocalProvider(LocalRillAvatarAppearance provides appearance) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                LazyColumn(Modifier.fillMaxSize().testTag("fixture-column"),
                    contentPadding = PaddingValues(top = 26.dp, bottom = 112.dp)) {
                    item {
                    Surface(Modifier.fillMaxWidth().height(56.dp).homeTourBounds {
                        targets[HomeTourTarget.Search] = it
                    }) { Text("Search contacts and calls", Modifier.padding(16.dp)) }
                    Spacer(Modifier.height(12.dp))
                    }
                    item {
                    FavoritesHeader(false, true, {}, {}, Modifier.homeTourBounds { header = it })
                    }
                    item {
                    IPhoneFavoritesRow(contacts, false, {}, {}, {}, { actualActions++ }, { actualActions++ },
                        displayOrder = 0, avatarSize = 56.dp, modifier = Modifier.homeTourBounds {
                            targets[HomeTourTarget.Favorites] = homeTourFavoritesBounds(header, it)
                        })
                    }
                    item { HomeSectionHeader("Recents") }
                    item {
                    HomeResultRow("George Pop", "+40700000102", null, "+40700000102",
                        swipeEnabled = true, swipeRightAction = 2, swipeLeftAction = 4,
                        haptics = false, hapticsStrength = "light", hapticsIntensity = 0.5f,
                        onAction = { actualActions++ }, onAvatarClick = { actualActions++ },
                        onClick = { actualActions++ }, onLongClick = { actualActions++ },
                        isLastInSection = true, contactNameSize = 18, avatarSize = 46,
                        onRowBoundsChanged = { targets[HomeTourTarget.RecentRow] = it },
                        onAvatarBoundsChanged = { targets[HomeTourTarget.RecentAvatar] = it })
                    }
                }
                DialpadFloatingActionButton({ actualActions++ },
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                        .homeTourBounds { targets[HomeTourTarget.Dialpad] = it })
            }
        }
    }

    @Test fun `all six production tour scenes keep opaque pointer measured spotlight and readable cards`() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var progress by mutableFloatStateOf(0.1f)
        var origin by mutableStateOf(Offset.Zero)
        val targets = mutableStateMapOf<HomeTourTarget, Rect>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                DotDialerTheme(systemDark = dark) {
                    key(dark, scale) {
                        Box(Modifier.homeTourBounds { origin = it.topLeft }) {
                            Fixture(targets)
                            if (targets.size == 5) HomeCoachMarkTourContent(targets.mapValues { it.value.translate(-origin) }, { completions++ }, progress)
                        }
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (font in listOf(1f, 1.5f)) {
            rule.runOnIdle { dark = theme; scale = font; progress = 0.1f }
            rule.mainClock.advanceTimeBy(500)
            val steps = homeTourSteps(targets.keys)
            for ((index, step) in steps.withIndex()) {
                for (pressed in listOf(false, true)) {
                    rule.runOnIdle { progress = if (step.demo == HomeTourDemo.SearchTyping) 0.57f else if (pressed) 0.3f else 0.1f }
                    rule.mainClock.advanceTimeBy(100)
                    val card = rule.onNodeWithTag("tour-card").fetchSemanticsNode().boundsInRoot
                    val demo = rule.onNodeWithTag("tour-demo").fetchSemanticsNode().boundsInRoot
                    assertTrue("card must not cover target: theme=$theme font=$font step=$index card=$card demo=$demo", card.bottom <= demo.top + 1f || card.top >= demo.bottom - 1f)
                    assertTrue(card.top >= 0f && card.bottom <= 801f)
                    if (step.target == HomeTourTarget.RecentRow) {
                        assertEquals(targets[step.target]!!.top - origin.y, demo.top, 0.1f)
                        assertEquals(targets[step.target]!!.bottom - origin.y, demo.bottom, 0.1f)
                    }
                    if (step.target == HomeTourTarget.Favorites) {
                        assertEquals(targets[step.target]!!.top - origin.y, demo.top, 0.1f)
                    }
                    if (step.demo == HomeTourDemo.SearchTyping) rule.onNodeWithText("George").assertExists()
                    else {
                        val pointer = rule.onNodeWithTag("tour-pointer", useUnmergedTree = true).fetchSemanticsNode()
                        assertEquals(if (pressed) 0.95f else 1f, pointer.config[CallPressScaleKey], 0.001f)
                        val bounds = pointer.boundsInRoot
                        assertTrue("pointer must be completely visible", bounds.left >= 0 && bounds.top >= 0 && bounds.right <= 360 && bounds.bottom <= 800)
                    }
                    capture("${if (theme) "dark" else "light"}-font${(font * 100).toInt()}-step${index + 1}-${if (pressed) "press" else "idle"}",
                        checkPointer = step.demo != HomeTourDemo.SearchTyping)
                }
                rule.onNodeWithText(if (index == steps.lastIndex) "Done" else "Next").performClick()
            }
        }
        rule.runOnIdle { assertEquals(4, completions); assertEquals(0, actualActions) }
    }

    @Test fun `separate popup uses window-aware bounds instead of root coordinates`() {
        val targets = mutableStateMapOf<HomeTourTarget, Rect>()
        rule.setContent {
            DotDialerTheme {
                Box(Modifier.fillMaxSize().padding(top = 100.dp)) {
                    Text("George Pop", Modifier.fillMaxWidth().height(80.dp)
                        .testTag("popup-target").homeTourBounds { targets[HomeTourTarget.RecentRow] = it })
                    if (targets.isNotEmpty()) HomeCoachMarkTour(targets) { completions++ }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val target = targets[HomeTourTarget.RecentRow]!!
        val origin = rule.onNodeWithTag("tour-popup-host").fetchSemanticsNode().config[HomeTourPopupOriginKey]
        val demo = rule.onNodeWithTag("tour-demo").fetchSemanticsNode().boundsInRoot.translate(origin)
        assertEquals(target.left, demo.left, 1f)
        assertEquals(target.top, demo.top, 1f)
        assertEquals(target.bottom, demo.bottom, 1f)
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Skip").performClick()
        rule.runOnIdle { assertEquals(1, completions) }
    }

    private fun capture(name: String, checkPointer: Boolean) {
        rule.waitForIdle()
        val bounds = if (checkPointer) rule.onNodeWithTag("tour-pointer", true).fetchSemanticsNode().boundsInRoot else null
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            if (bounds != null) {
                var green = 0
                var white = 0
                fun matches(pixel: Int, expected: Int) = abs(AndroidColor.red(pixel) - AndroidColor.red(expected)) <= 2 &&
                    abs(AndroidColor.green(pixel) - AndroidColor.green(expected)) <= 2 &&
                    abs(AndroidColor.blue(pixel) - AndroidColor.blue(expected)) <= 2
                for (y in bounds.top.toInt() until bounds.bottom.toInt()) for (x in bounds.left.toInt() until bounds.right.toInt()) {
                    val pixel = bitmap.getPixel(x, y)
                    if (matches(pixel, CallActionColors.answerCommitted.toArgb())) green++
                    if (matches(pixel, CallActionColors.onAction.toArgb())) white++
                }
                assertTrue("opaque Jade pointer in $name: $green", green > 800)
                assertTrue("white hand in $name: $white", white > 50)
            }
            val folder = File(System.getProperty("user.dir"), "build/reports/visual/home-tour/" + dev.goodwy.rphone.BuildConfig.FLAVOR)
            folder.mkdirs()
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
