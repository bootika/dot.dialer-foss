package dev.goodwy.rphone.view.screen

import android.graphics.Bitmap
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.DialpadFloatingActionButton
import dev.goodwy.rphone.view.components.IPhoneFavoritesRow
import dev.goodwy.rphone.view.screen.onboarding.CallStyleChoiceContent
import dev.goodwy.rphone.view.screen.onboarding.EssentialOnboardingPage
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native captures with actual Compose components and an inert contact fixture. */
@RunWith(AndroidJUnit4::class)
class OnboardingMockupDeviceTest {
    @get:Rule val rule = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext
    private val themeIsDark get() = arguments.getString("captureDark") == "true"

    @OptIn(ExperimentalTestApi::class)
    @Test fun setupScreensAndSixTourSteps() {
        var screen by mutableStateOf(0)
        var fontScale = 0f
        var accidentalActions = 0
        var tourCompletions = 0
        var swipe by mutableStateOf(false)
        val targets = mutableStateMapOf<HomeTourTarget, Rect>()
        val continueLabel = context.getString(R.string.setup_call_style_continue)
        val doneLabel = context.getString(R.string.done)
        val settingsHint = context.getString(R.string.setup_call_style_later)

        rule.setContent {
            fontScale = LocalConfiguration.current.fontScale
            DotDialerTheme(systemDark = themeIsDark) {
                when (screen) {
                    0 -> EssentialOnboardingPage { screen++ }
                    1 -> CallStyleChoiceContent(
                        swipe = swipe,
                        onSelect = { swipe = it },
                        onBack = { screen-- },
                        onNext = { screen++ },
                    )
                    2 -> DefaultPhoneRoleContent(
                        pending = false,
                        denied = false,
                        onRequestRole = { accidentalActions++ },
                        onOpenSettings = { accidentalActions++ },
                    )
                    else -> Box(Modifier.fillMaxSize()) {
                        HomeFixture(targets) { accidentalActions++ }
                        if (screen == 3 && targets.keys.containsAll(HomeTourTarget.entries)) {
                            HomeCoachMarkTour(
                                targets,
                                HomeTourActions(
                                    tap = stringResource(R.string.call),
                                    swipeRight = stringResource(R.string.sms),
                                    swipeLeft = stringResource(R.string.setup_call_style_whatsapp),
                                ),
                            ) { tourCompletions++; screen = 4 }
                        }
                    }
                }
            }
        }

        rule.onNodeWithTag("plan-guide-next").assertIsDisplayed()
        val expectedFontScale = arguments.getString("expectedFontScale")?.toFloatOrNull()
        if (expectedFontScale != null) assertEquals(expectedFontScale, fontScale, 0.01f)
        capture("01-welcome")
        val welcomeBody = rule.onNodeWithTag("welcome-body").getUnclippedBoundsInRoot()
        val disclosure = rule.onNodeWithTag("welcome-disclosure")
        val disclosureBounds = disclosure.getUnclippedBoundsInRoot()
        assertTrue("Welcome disclosure must fit. Bounds: $disclosureBounds, viewport: $welcomeBody",
            disclosureBounds.top >= welcomeBody.top - 1.dp && disclosureBounds.bottom <= welcomeBody.bottom + 1.dp)
        disclosure.assertIsDisplayed()
        if (fontScale >= 1.3f) {
            val reportingText = context.getString(
                if (BuildConfig.FLAVOR == "gplay") R.string.onboarding_essential_body_gplay
                else R.string.onboarding_essential_body,
                context.getString(R.string.privacy_and_blocking),
                context.getString(R.string.settings_reporting),
            ).substringAfter("\n\n")
            disclosure.performClick()
            rule.onNodeWithText(reportingText)
                .performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("plan-guide-next").assertIsDisplayed()
            capture("01-welcome-reporting")
        }
        rule.onNodeWithTag("plan-guide-next").performClick()

        val tap = rule.onNodeWithTag("call-style-tap")
        val swipeCard = rule.onNodeWithTag("call-style-swipe")
        val footer = rule.onNodeWithTag("plan-guide-next")
        tap.assertIsDisplayed()
        swipeCard.assertIsDisplayed()
        footer.assertIsDisplayed()
        assertTrue(tap.getUnclippedBoundsInRoot().bottom <= swipeCard.getUnclippedBoundsInRoot().top)
        assertTrue(swipeCard.getUnclippedBoundsInRoot().bottom <= footer.getUnclippedBoundsInRoot().top)
        assertSettingsHintFits(settingsHint)
        capture("02-call-choice")
        swipeCard.performClick()
        assertSettingsHintFits(settingsHint)
        capture("02-call-choice-swipe-selected")
        footer.performClick()

        rule.onNodeWithTag("default-role-continue").assertIsDisplayed()
        capture("03-default-role")
        rule.runOnIdle { screen++ }

        rule.waitUntilAtLeastOneExists(hasTestTag("tour-card"), timeoutMillis = 8_000)
        for (step in 1..6) {
            val card = rule.onNodeWithTag("tour-card").getUnclippedBoundsInRoot()
            val target = rule.onNodeWithTag("tour-demo").getUnclippedBoundsInRoot()
            assertTrue("Tour step $step covers its target", card.bottom <= target.top || card.top >= target.bottom)
            val action = rule.onNodeWithText(if (step == 6) doneLabel else continueLabel)
            action.assertIsDisplayed()
            capture("tour-$step")
            action.performClick()
        }
        rule.runOnIdle {
            assertEquals(1, tourCompletions)
            assertEquals(0, accidentalActions)
        }
        rule.onNodeWithTag("tour-card").assertDoesNotExist()
    }

    private fun assertSettingsHintFits(label: String) {
        val hint = rule.onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = rule.onNodeWithTag("call-style-body").getUnclippedBoundsInRoot()
        assertTrue("The settings hint is clipped at the bottom", hint.bottom <= viewport.bottom + 1.dp)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        val screenshot = if (name.startsWith("0")) {
            // The setup screens live in one Compose window, whose presented pixels we can copy directly.
            rule.onRoot().captureToImage().asAndroidBitmap()
        } else {
            capturePopupWithHome(name)
        }
        val runLabel = arguments.getString("captureLabel") ?: "device"
        require(runLabel.matches(Regex("[A-Za-z0-9-]+")))
        val directory = File(context.cacheDir, "onboarding-mockups/$runLabel").apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val colors = buildSet {
                for (row in 4..45) for (column in 2..46) {
                    add(screenshot.getPixel(screenshot.width * column / 48, screenshot.height * row / 48))
                }
            }
            check(colors.size >= 8) { "Capture $name has only ${colors.size} sampled tones" }
        } finally {
            screenshot.recycle()
        }
    }

    private fun capturePopupWithHome(name: String): Bitmap {
        // Compose semantics may be ready before SurfaceFlinger presents the new screen.
        val presented = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val choreographer = Choreographer.getInstance()
            fun awaitFrames(remaining: Int) {
                choreographer.postFrameCallback {
                    if (remaining == 1) presented.countDown()
                    else awaitFrames(remaining - 1)
                }
            }
            awaitFrames(3)
        }
        check(presented.await(4, TimeUnit.SECONDS)) { "No presented frame for $name" }
        return checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    }

    @Composable private fun HomeFixture(targets: MutableMap<HomeTourTarget, Rect>, onAction: () -> Unit) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                UnifiedHomeTopBar(
                    query = "", onQueryChange = { onAction() }, filterActive = false, onFilter = onAction,
                    browseContacts = false, onExitBrowseContacts = onAction, onSettings = onAction,
                    onSearchBoundsChanged = { targets[HomeTourTarget.Search] = it },
                )
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)) {
                    item { FavoritesHeader(false, true, onAction, onAction) }
                    item {
                        IPhoneFavoritesRow(
                            listOf(
                                Contact("first", givenName = "Ada", familyName = "Marin"),
                                Contact("second", givenName = "Mara", familyName = "Ilin"),
                                Contact("third", givenName = "Tudor", familyName = "Radu"),
                            ),
                            false, onAction, { onAction() }, {}, { onAction() }, { onAction() },
                            displayOrder = 0, avatarSize = 52.dp,
                            tourAvatarModifier = Modifier.homeTourBounds { targets[HomeTourTarget.Favorites] = it },
                        )
                    }
                    item { HomeSectionHeader(stringResource(R.string.recents)) }
                    items(5) { index ->
                        HomeResultRow(
                            listOf("Ana Matei", "Doru Valean", "Cezar Tomescu", "Iulia Costin", "Dan Tanase")[index],
                            "", null, null,
                            swipeEnabled = true, swipeRightAction = 2, swipeLeftAction = 4,
                            haptics = false, hapticsStrength = "light", hapticsIntensity = 0.5f,
                            onAction = { onAction() }, onAvatarClick = onAction,
                            onClick = onAction, onLongClick = onAction,
                            isFirstInSection = index == 0, isLastInSection = index == 4,
                            contactNameSize = 18, avatarSize = 46,
                            onRowBoundsChanged = if (index == 0) {{ targets[HomeTourTarget.RecentRow] = it }} else null,
                            onAvatarBoundsChanged = if (index == 0) {{ targets[HomeTourTarget.RecentAvatar] = it }} else null,
                        )
                    }
                }
            }
            DialpadFloatingActionButton(onAction, Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
                .homeTourBounds { targets[HomeTourTarget.Dialpad] = it })
        }
    }
}
