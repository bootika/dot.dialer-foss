package dev.goodwy.rphone.view.screen

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.WindowManager
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.controller.util.AmbientDropletPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

/** Renders the production screen, not a reconstructed mockup. All Telecom data is synthetic. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class CallScreenFlowScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val repository = CallRepositoryImpl()
    private val scenario = mutableStateOf(Scenario("initial", Call.STATE_RINGING))
    private lateinit var prefs: PreferenceManager
    private lateinit var primary: Call
    private lateinit var photoFixture: String
    private lateinit var squarePhotoFixture: String
    private lateinit var landscapePhotoFixture: String
    private lateinit var neutralPhotoFixture: String
    private var referencePhotoFixture: String? = null
    private val output = File(requireNotNull(System.getProperty("user.dir")), "build/reports/visual/call-participants")
    private val accountHandle = PhoneAccountHandle(ComponentName("dot.dialer.test", "TestPhoneAccountService"), "sim-test")
    private val commonGridPitches = mutableMapOf<String, Float>()
    private val telecomCommands = CopyOnWriteArrayList<String>()

    private fun assertSquareActionGrid(id: String) {
        val grids = rule.onAllNodesWithTag("callRoundActionGrid", useUnmergedTree = true).fetchSemanticsNodes()
        grids.forEachIndexed { gridIndex, grid ->
            fun descendants(node: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
                listOf(node) + node.children.flatMap { descendants(it) }
            val icons = descendants(grid).filter { it.config.getOrNull(SemanticsProperties.TestTag) == "callRoundActionSurface" }
            org.junit.Assert.assertTrue("$id: grid has actual action icons to measure", icons.isNotEmpty())
            val centers = icons.map { it.boundsInRoot.center }.distinct().sortedBy { it.y }
            val rows = mutableListOf<MutableList<androidx.compose.ui.geometry.Offset>>()
            centers.forEach { center ->
                if (rows.isEmpty() || kotlin.math.abs(rows.last().first().y - center.y) > 1f) rows.add(mutableListOf())
                rows.last().add(center)
            }
            val horizontal = rows.flatMap { row -> row.sortedBy { it.x }.zipWithNext { a, b -> b.x - a.x } }
            val vertical = rows.zipWithNext { a, b -> b.first().y - a.first().y }
            val pitch = (horizontal + vertical).firstOrNull() ?: return@forEachIndexed
            val sharedGroup = when (id) {
                "77-default-grid-photo", "09-active-voice", "45-full-photo-active" -> "font100"
                "78-default-grid-font150", "80-tap-grid-font150", "75-hd-badge-font150" -> "font150"
                else -> null
            }
            sharedGroup?.let { group ->
                val expected = commonGridPitches.getOrPut(group) { pitch }
                org.junit.Assert.assertEquals("$id: same pitch across incoming and active screens", expected, pitch, 0f)
            }
            (horizontal + vertical).forEach { distance ->
                org.junit.Assert.assertEquals("$id: identical X/Y center pitch; rows=$rows", pitch, distance, 0f)
            }
            rows.forEach { row ->
                org.junit.Assert.assertEquals("$id: partial row centered without changing pitch", grid.boundsInRoot.center.x,
                    (row.minOf { it.x } + row.maxOf { it.x }) / 2f, 1f)
            }
            icons.forEach { icon ->
                org.junit.Assert.assertTrue("$id: icon inside grid", icon.boundsInRoot.left >= grid.boundsInRoot.left && icon.boundsInRoot.right <= grid.boundsInRoot.right)
            }
            output.mkdirs()
            File(output, "$id-grid-$gridIndex.json").writeText(JSONObject()
                .put("pitch_px", pitch).put("horizontal_steps", JSONArray(horizontal))
                .put("vertical_steps", JSONArray(vertical))
                .put("centers", JSONArray(centers.map { JSONObject().put("x", it.x).put("y", it.y) })).toString(2))
        }
    }

    @After fun clearDependencies() { stopKoin() }

    private fun assertFullPhotoTranslation(current: Scenario) {
        val header = rule.onNodeWithTag("callIdentityHeader", useUnmergedTree = true).fetchSemanticsNode()
        val controlsTag = if (current.state == Call.STATE_RINGING) "callIncomingControls" else "callActionControls"
        val controls = rule.onNodeWithTag(controlsTag, useUnmergedTree = true).fetchSemanticsNode()
        val start = header.positionInRoot.y + header.size.height
        val end = controls.positionInRoot.y
        val offset = ((end - start) * .03f).roundToInt()
        val echo = rule.onNodeWithTag("callFullPhotoEcho", useUnmergedTree = true).fetchSemanticsNode()
        val sharp = rule.onNodeWithTag("callFullPhotoSharp", useUnmergedTree = true).fetchSemanticsNode()
        org.junit.Assert.assertEquals("${current.id}: echo stays full viewport without an uncovered top strip", 0f, echo.positionInRoot.y, 0f)
        org.junit.Assert.assertEquals("${current.id}: unchanged viewport echo size", IntSize(360, 800), echo.size)
        org.junit.Assert.assertEquals("${current.id}: unchanged sharp width", 360, sharp.size.width)
        val ratio = when (current.photoShape) { "square" -> 1f; "landscape" -> 1.15f; else -> null }
        if (ratio != null) {
            val panelHeight = (360f / ratio).roundToInt()
            org.junit.Assert.assertEquals("${current.id}: unchanged sharp dimensions", panelHeight, sharp.size.height)
            val baselineTop = ((start + end) / 2f - 360f / ratio / 2f).roundToInt().coerceAtLeast(0)
            org.junit.Assert.assertEquals("${current.id}: sharp moves3% of the gap", (baselineTop + offset).toFloat(), sharp.positionInRoot.y, 0f)
            if (baselineTop > 0) {
                val desiredCentre = start + (end - start) * .53f
                val actualCentre = sharp.positionInRoot.y + sharp.size.height / 2f
                org.junit.Assert.assertEquals("${current.id}: 3% below geometric header/control midpoint", desiredCentre, actualCentre, 1.5f)
            }
        } else {
            org.junit.Assert.assertEquals("${current.id}: portrait remains full-bleed size", IntSize(360, 800), sharp.size)
            org.junit.Assert.assertEquals("${current.id}: portrait moves3% of the gap", offset.toFloat(), sharp.positionInRoot.y, 0f)
        }
        File(output, "${current.id}-photo-geometry.json").writeText(JSONObject()
            .put("zone_start", start).put("zone_end", end).put("offset_px", offset)
            .put("sharp_top", sharp.positionInRoot.y).put("sharp_width", sharp.size.width)
            .put("sharp_height", sharp.size.height).put("echo_top", echo.positionInRoot.y)
            .put("echo_width", echo.size.width).put("echo_height", echo.size.height).toString(2))
    }

    @Test fun `render the complete production call flow and expanded panels`() {
        val context = RuntimeEnvironment.getApplication()
        prefs = PreferenceManager(context)
        photoFixture = createPhotoFixture(context)
        squarePhotoFixture = createPhotoFixture(context, 480, 480)
        landscapePhotoFixture = createPhotoFixture(context, 640, 360)
        neutralPhotoFixture = createNeutralPhotoFixture(context)
        referencePhotoFixture = createReferencePhotoFixture(context)
        referencePhotoFixture?.let { recordReferencePalette(it) }
        prefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS, false)
        prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, false)
        prefs.setBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, false)
        prefs.setBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false)
        val knownContact = Contact(id = "local-test", givenName = "Test Alexandru", familyName = "Butica", phoneNumbers = listOf(NUMBER))
        val contacts = Proxy.newProxyInstance(IContactsRepository::class.java.classLoader, arrayOf(IContactsRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getChanges" -> kotlinx.coroutines.flow.emptyFlow<Unit>()

                "getContactByNumber", "getContactByNumberInRegion" -> when (args?.firstOrNull()) {
                    NUMBER -> knownContact
                    "0700000001" -> Contact(id = "local-second", givenName = "Mara", familyName = "Ionescu", phoneNumbers = listOf("0700000001"))
                    "0700000011" -> if (scenario.value.participantPhotos) Contact(id = "local-participant", givenName = "Radu", familyName = "Popescu", phoneNumbers = listOf("0700000011"), photoUri = photoFixture) else null
                    else -> null
                }
                "toString" -> "Local contacts fixture"
                else -> null
            }
        } as IContactsRepository
        val stateManager = CallStateManager(GetCallerNameUseCase(object : ICallerRepository {
            override suspend fun getContactByNumber(number: String): Contact? = null
        }))
        val viewModel = CallViewModel(context, repository, stateManager)
        startKoin { modules(module {
            single { prefs }; single<IContactsRepository> { contacts }; single { viewModel }
        }) }
        (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).registerPhoneAccount(
            PhoneAccount.builder(accountHandle, "Vodafone").setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER).build(),
        )
        setup(scenario.value)
        rule.setContent {
            val current = scenario.value
            val audioSnapshot by repository.audioState.collectAsState()
            key(current.id) {
                CompositionLocalProvider(LocalDensity provides Density(1f, current.fontScale)) {
                DotDialerTheme(systemDark = current.darkTheme) {
                    val sharedBackdrop = rememberLayerBackdrop()
                    CompositionLocalProvider(LocalLiquidGlassBackdrop provides sharedBackdrop) {
                    // Explicit content-window fixture, not fabricated OS bars or an ADB capture.
                    Box(Modifier.fillMaxSize().padding(
                        top = if (current.referenceComposition) 28.dp else 0.dp,
                        bottom = if (current.referenceComposition) 64.dp else 0.dp,
                    )) {
                    ExpressiveCallScreen(
                        call = primary, callState = current.state,
                        contactName = when {
                            current.participants > 0 || current.conference -> "Conference call"
                            current.unknown -> "0700000099"
                            current.referenceComposition -> "Loredana"
                            else -> "Test Alexandru Butica"
                        },
                        companyName = null, phoneNumber = if (current.unknown) "0700000099" else NUMBER,
                        photoUri = when {
                            current.photoShape == "square" -> squarePhotoFixture
                            current.photoShape == "landscape" -> landscapePhotoFixture
                            current.neutralPhoto -> neutralPhotoFixture
                            current.referencePhoto -> referencePhotoFixture
                            current.withPhoto -> photoFixture
                            else -> null
                        },
                        audioState = audioSnapshot,
                        liquidGlassBackdrop = sharedBackdrop,
                    )
                    }
                    }
                }
                }
            }
        }
        val scenarios = listOf(
            Scenario("77-default-grid-photo", Call.STATE_RINGING, incomingMode = 10, withPhoto = true, fullPhoto = true),
            Scenario("78-default-grid-font150", Call.STATE_RINGING, incomingMode = 10, fontScale = 1.5f),
            Scenario("79-default-grid-dark", Call.STATE_RINGING, incomingMode = 10, darkTheme = true),
            Scenario("80-tap-grid-font150", Call.STATE_RINGING, incomingMode = 1, fontScale = 1.5f),
            Scenario("81-default-grid-unknown", Call.STATE_RINGING, incomingMode = 10, unknown = true),
            Scenario("82-radial-grid-font150", Call.STATE_RINGING, incomingMode = 4, fontScale = 1.5f),
            Scenario("83-radial-grid-unknown", Call.STATE_RINGING, incomingMode = 4, unknown = true),
            Scenario("01-incoming", Call.STATE_RINGING),
            Scenario("02-incoming-tap", Call.STATE_RINGING, incomingMode = 1),
            Scenario("03-incoming-horizontal", Call.STATE_RINGING, incomingMode = 0),
            Scenario("04-incoming-slider", Call.STATE_RINGING, incomingMode = 2),
            Scenario("05-incoming-vertical", Call.STATE_RINGING, incomingMode = 3),
            Scenario("06-incoming-radial", Call.STATE_RINGING, incomingMode = 4),
            Scenario("07-connecting", Call.STATE_CONNECTING),
            Scenario("08-dialing", Call.STATE_DIALING),
            Scenario("09-active-voice", Call.STATE_ACTIVE),
            Scenario("10-active-hd", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_AMR_WB),
            Scenario("11-active-hd-plus", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB),
            Scenario("73-hd-badge-dark", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_AMR_WB, darkTheme = true),
            Scenario("74-hd-plus-badge-dark", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, darkTheme = true),
            Scenario("75-hd-badge-font150", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_AMR_WB, fontScale = 1.5f),
            Scenario("76-hd-plus-badge-font150", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, fontScale = 1.5f),
            Scenario("12-active-muted-speaker", Call.STATE_ACTIVE, muted = true, route = CallAudioState.ROUTE_SPEAKER),
            Scenario("13-on-hold", Call.STATE_HOLDING),
            Scenario("101-on-hold-dark", Call.STATE_HOLDING, darkTheme = true),
            Scenario("102-on-hold-font150", Call.STATE_HOLDING, fontScale = 1.5f),
            Scenario("14-call-waiting", Call.STATE_RINGING, otherState = Call.STATE_ACTIVE),
            Scenario("15-second-call-connecting", Call.STATE_DIALING, otherState = Call.STATE_HOLDING),
            Scenario("16-two-calls-merge", Call.STATE_ACTIVE, otherState = Call.STATE_HOLDING, merge = true),
            Scenario("17-two-calls-no-merge", Call.STATE_ACTIVE, otherState = Call.STATE_HOLDING),
            Scenario("18-conference-two", Call.STATE_ACTIVE, participants = 2),
            Scenario("19-conference-three", Call.STATE_ACTIVE, participants = 3),
            Scenario("20-incoming-over-conference", Call.STATE_RINGING, otherState = Call.STATE_ACTIVE, otherConference = true),
            Scenario("21-add-to-conference", Call.STATE_ACTIVE, otherState = Call.STATE_HOLDING, otherConference = true, merge = true),
            Scenario("22-disconnecting", Call.STATE_DISCONNECTING),
            Scenario("23-disconnected", Call.STATE_DISCONNECTED),
            Scenario("33-conference-four", Call.STATE_ACTIVE, participants = 4),
            Scenario("34-conference-six", Call.STATE_ACTIVE, participants = 6),
            Scenario("35-conference-no-disconnect", Call.STATE_ACTIVE, participants = 3, participantDisconnect = false),
            Scenario("36-conference-large-font", Call.STATE_ACTIVE, participants = 4, fontScale = 1.5f),
            Scenario("37-conference-unreported", Call.STATE_ACTIVE, conference = true),
            Scenario("44-full-photo-calling", Call.STATE_DIALING, withPhoto = true, fullPhoto = true),
            Scenario("45-full-photo-active", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, withPhoto = true, fullPhoto = true),
            Scenario("46-full-photo-large-font", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, withPhoto = true, fullPhoto = true, fontScale = 1.5f),
            Scenario("47-full-photo-dark", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, withPhoto = true, fullPhoto = true, darkTheme = true),
            Scenario("48-circular-photo-active", Call.STATE_ACTIVE, withPhoto = true),
            Scenario("49-full-photo-incoming", Call.STATE_RINGING, withPhoto = true, fullPhoto = true),
            Scenario("87-full-photo-square", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true, photoShape = "square"),
            Scenario("88-full-photo-landscape", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true, photoShape = "landscape"),
            Scenario("103-full-photo-square-font150", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true, photoShape = "square", fontScale = 1.5f),
            Scenario("104-full-photo-square-incoming", Call.STATE_RINGING, withPhoto = true, fullPhoto = true, photoShape = "square"),
            Scenario("105-full-photo-square-secondary", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true, photoShape = "square", otherState = Call.STATE_HOLDING, merge = true),
            Scenario("106-full-photo-square-dark", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true, photoShape = "square", darkTheme = true),
            Scenario("89-full-photo-secondary", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true, otherState = Call.STATE_HOLDING, merge = true),
            Scenario("50-circular-photo-dark", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, withPhoto = true, darkTheme = true),
            Scenario("51-circular-photo-large-font", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, withPhoto = true, fontScale = 1.5f),
            Scenario("52-initials-dark", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, darkTheme = true),
            Scenario("53-initials-large-font", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, fontScale = 1.5f),
            Scenario("54-photo-call-waiting", Call.STATE_RINGING, otherState = Call.STATE_ACTIVE, withPhoto = true, darkTheme = true),
            Scenario("55-approved-photo-light", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, referencePhoto = true),
            Scenario("56-approved-photo-dark", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, referencePhoto = true, darkTheme = true),
            Scenario("57-approved-photo-large-font", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, referencePhoto = true, fontScale = 1.5f),
            Scenario("58-conference-four-dark", Call.STATE_ACTIVE, participants = 4, darkTheme = true),
            Scenario("59-conference-mixed-avatars", Call.STATE_ACTIVE, participants = 4, participantPhotos = true),
            Scenario("60-conference-full-photo-preference", Call.STATE_ACTIVE, participants = 4, fullPhoto = true),
            Scenario("61-reference-content-light", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, referencePhoto = true, referenceComposition = true),
            Scenario("62-reference-content-dark", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, referencePhoto = true, referenceComposition = true, darkTheme = true),
            Scenario("63-reference-content-font150", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, referencePhoto = true, referenceComposition = true, fontScale = 1.5f),
            Scenario("64-grey-photo-light", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, neutralPhoto = true),
            Scenario("65-grey-photo-dark", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB, neutralPhoto = true, darkTheme = true),
            Scenario("66-two-calls-font150", Call.STATE_ACTIVE, otherState = Call.STATE_HOLDING, merge = true, fontScale = 1.5f),
            Scenario("67-two-calls-dark", Call.STATE_ACTIVE, otherState = Call.STATE_HOLDING, merge = true, darkTheme = true),
            Scenario("68-secondary-hour-font150", Call.STATE_ACTIVE, otherState = Call.STATE_HOLDING, fontScale = 1.5f, secondaryDurationMillis = 3_605_000),
            Scenario("69-secondary-unknown-time", Call.STATE_RINGING, otherState = Call.STATE_ACTIVE, secondaryDurationMillis = 0),
            Scenario("70-conference-on-hold", Call.STATE_HOLDING, participants = 3),
        ).filter { !it.referencePhoto || referencePhotoFixture != null }
        val stableConnectionPortraits = mutableMapOf<String, androidx.compose.ui.geometry.Rect>()
        scenarios.forEach { current ->
            present(current)
            rule.mainClock.advanceTimeBy(650)
            rule.waitForIdle()
            assertSquareActionGrid(current.id)
            if (current.state == Call.STATE_RINGING &&
                current.incomingMode == PreferenceManager.INCOMING_CALL_UI_DEFAULT_SWIPE) {
                rule.onNodeWithTag("callAnswerHandle").assertExists()
            }
            if (current.state == Call.STATE_ACTIVE && current.codec != null && !current.fullPhoto) {
                val pill = rule.onNodeWithTag(CALL_CONNECTION_QUALITY_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val glyph = rule.onNodeWithTag("callQualityGlyph", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val label = rule.onNodeWithTag("callQualityLabel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                org.junit.Assert.assertTrue("Quality glyph needs at least8dp left inset", glyph.left - pill.left >= 7.9f)
                org.junit.Assert.assertTrue("Quality label needs at least8dp right inset", pill.right - label.right >= 7.9f)
                rule.onNodeWithTag("callQualityLabel", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    action(layouts)
                    org.junit.Assert.assertTrue("${current.id}: quality label must remain readable; pill=$pill label=$label layouts=${layouts.map { "${it.size} widthOverflow=${it.didOverflowWidth} heightOverflow=${it.didOverflowHeight} lineRight=${it.getLineRight(0)}" }}", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
                }
                if (current.fontScale == 1f) {
                    org.junit.Assert.assertEquals("Compact quality badge stays page-centered", 180f, pill.center.x, 1f)
                }
            }
            if (current.state == Call.STATE_CONNECTING || current.state == Call.STATE_DIALING) {
                rule.onNodeWithContentDescription("Add call").assertIsNotEnabled()
                rule.onNodeWithContentDescription("Keypad").assertIsNotEnabled()
                rule.onNodeWithContentDescription("More").assertIsNotEnabled()
            }
            val establishedPair = current.state in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING) &&
                current.otherState in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING)
            if (establishedPair) {
                rule.onNodeWithTag("secondaryMergeAction").let {
                    if (current.merge) it.assertIsEnabled() else it.assertIsNotEnabled()
                }
                rule.onNodeWithContentDescription("Add call").assertIsNotEnabled()
                rule.onAllNodesWithText("Merge").assertCountEquals(1)
            } else rule.onNodeWithTag("secondaryMergeAction").assertDoesNotExist()
            if (current.state != Call.STATE_RINGING) {
                rule.onNodeWithText(if (current.muted) "Unmute" else "Mute").assertExists()
                rule.onNodeWithContentDescription(if (current.muted) "Unmute" else "Mute").assertExists()
            }
            if (current.participants > 0) org.junit.Assert.assertEquals(current.participants, primary.children.size)
            if (current.participants > 0) {
                rule.onNodeWithText("${current.participants} participants").assertExists()
                rule.onNodeWithTag("conferenceParticipantList").assertExists()
                rule.onNodeWithContentDescription("End call with Radu Popescu").let {
                    if (current.participantDisconnect) it.assertIsEnabled() else it.assertIsNotEnabled()
                }
            }
            if (current.otherState != null) {
                rule.onNodeWithTag("secondaryCallAvatar").assertExists()
                val timer = rule.onNodeWithTag("secondaryCallTimer").fetchSemanticsNode()
                val timerText = timer.config[SemanticsProperties.Text].first().text
                val layouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithTag("secondaryCallTimer").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                org.junit.Assert.assertTrue("Timer exposes its actual text layout", layouts.isNotEmpty())
                org.junit.Assert.assertFalse("Secondary timer must never be truncated: ${current.id}, width=${layouts.first().didOverflowWidth}, height=${layouts.first().didOverflowHeight}, size=${layouts.first().size}, lineBottom=${layouts.first().getLineBottom(0)}", layouts.first().hasVisualOverflow)
                if (current.secondaryDurationMillis == 0L) rule.onNodeWithTag("secondaryCallTimer").assertTextEquals("—")
                else {
                    val expected = dev.goodwy.rphone.controller.util.formatDuration(current.secondaryDurationMillis / 1_000)
                    org.junit.Assert.assertEquals("Secondary has its own connect time", expected, timerText)
                }
                val identity = rule.onNodeWithTag("secondaryCallIdentity", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                org.junit.Assert.assertEquals("Secondary timer is centered in identity row", identity.center.y, timer.boundsInRoot.center.y, 1f)
                if (current.state == Call.STATE_ACTIVE && current.otherState == Call.STATE_HOLDING) {
                    val actions = rule.onNodeWithTag("secondaryCallActions", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    org.junit.Assert.assertTrue("Actions must not crowd the identity at phone width", actions.top >= identity.bottom)
                    val swap = rule.onNodeWithTag("secondarySwapAction", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val merge = rule.onNodeWithTag("secondaryMergeAction", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val end = rule.onNodeWithTag("secondaryEndAction", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    org.junit.Assert.assertEquals("End circle is48dp", 48f, end.width, 1f)
                    org.junit.Assert.assertEquals("Merge shares action width", swap.width, merge.width, 1f)
                    org.junit.Assert.assertEquals("Merge shares action height", swap.height, merge.height, 1f)
                    org.junit.Assert.assertEquals("End circle is48dp high", 48f, end.height, 1f)
                    org.junit.Assert.assertEquals("Shared action gap", merge.left - swap.right, end.left - merge.right, 0f)
                    org.junit.Assert.assertEquals("Merge glyph is22dp", 22f,
                        rule.onNodeWithTag("secondaryMergeAction-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width, 1f)
                    org.junit.Assert.assertTrue("Swap touch surface is at least48dp", swap.height >= 48f)
                    org.junit.Assert.assertTrue("End touch surface is at least48dp", end.height >= 48f)
                    org.junit.Assert.assertEquals("Swap glyph is22dp", 22f,
                        rule.onNodeWithTag("secondarySwapAction-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width, 1f)
                    org.junit.Assert.assertEquals("End glyph is26dp", 26f,
                        rule.onNodeWithTag("secondaryEndAction-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width, 1f)
                    org.junit.Assert.assertTrue("Separate actions have explicit spacing", actions.top >= identity.bottom + 3.9f)
                    val card = rule.onNodeWithTag("secondaryCallCard", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    org.junit.Assert.assertTrue("Actions fit card", end.right <= card.right && actions.bottom <= card.bottom)
                    if (current.fontScale == 1f) org.junit.Assert.assertTrue("Three-action secondary card stays compact", card.height <= 120f)
                }
            }
            if (current.state == Call.STATE_ACTIVE || current.state == Call.STATE_HOLDING) {
                rule.onNodeWithTag("callPrimaryTimer").assertExists()
            } else {
                rule.onNodeWithTag("callPrimaryTimer").assertDoesNotExist()
            }
            if (current.participants > 0) {
                val rows = rule.onAllNodesWithTag("conferenceParticipantRow", useUnmergedTree = true).fetchSemanticsNodes()
                val timers = rule.onAllNodesWithTag("conferenceParticipantTimer", useUnmergedTree = true).fetchSemanticsNodes()
                rows.zip(timers).forEach { (row, timer) ->
                    // LazyColumn clips partially visible rows; compare the actual layout centers,
                    // not the clipped semantics rectangles at the viewport edge.
                    org.junit.Assert.assertEquals("Conference timer must be vertically centered",
                        row.positionInRoot.y + row.size.height / 2f,
                        timer.positionInRoot.y + timer.size.height / 2f, 1f)
                    org.junit.Assert.assertTrue("Timer fits its row", timer.boundsInRoot.right <= row.boundsInRoot.right)
                }
            }
            if (current.withPhoto) waitForPhotoFixture()
            if (current.referencePhoto) waitForReferencePhoto()
            if (current.neutralPhoto) {
                rule.waitUntil(5_000) {
                    val bounds = rule.onNodeWithTag("callPrimaryAvatar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    var greyVisible = false
                    rule.runOnIdle {
                        val view = rule.activity.window.decorView
                        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(bitmap))
                        greyVisible = bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()) == Color.GRAY
                        bitmap.recycle()
                    }
                    greyVisible
                }
            }
            if (!current.fullPhoto && current.participants == 0 && !current.conference) {
                val avatar = rule.onNodeWithTag("callPrimaryAvatar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val zone = rule.onNodeWithTag("callAvatarMiddleZone", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val header = rule.onNodeWithTag("callIdentityHeader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                if (zone.top < header.bottom - 1f) {
                    capture("${current.id}-layout-failure")
                    File(output, "layout-failure-semantics.txt").writeText(rule.onNode(isRoot()).printToString())
                }
                org.junit.Assert.assertEquals("${current.id}: Middle zone must start immediately after the identity and timer", header.bottom, zone.top, 1f)
                org.junit.Assert.assertEquals(avatar.width, avatar.height, 1f)
                if (current.id in setOf("07-connecting", "08-dialing", "09-active-voice", "10-active-hd", "11-active-hd-plus")) {
                    stableConnectionPortraits[current.id] = avatar
                }
                org.junit.Assert.assertEquals(minOf(zone.width, zone.height * .76f), avatar.height, 2f)
                val topGap = avatar.top - zone.top
                val bottomGap = zone.bottom - avatar.bottom
                org.junit.Assert.assertEquals("Portrait must be vertically centered", topGap, bottomGap, 1f)
                org.junit.Assert.assertTrue("Top clearance must be at least twelve percent", topGap + 1f >= zone.height * .12f)
                org.junit.Assert.assertTrue("Bottom clearance must be at least twelve percent", bottomGap + 1f >= zone.height * .12f)
                if (zone.width >= zone.height * .76f) {
                    org.junit.Assert.assertEquals("Top visual clearance is twelve percent", zone.height * .12f, topGap, 1f)
                    org.junit.Assert.assertEquals("Bottom visual clearance is twelve percent", zone.height * .12f, bottomGap, 1f)
                }
                output.mkdirs()
                File(output, "${current.id}-geometry.json").writeText(
                    """{"zone_top":${zone.top},"zone_bottom":${zone.bottom},"zone_height":${zone.height},"avatar_top":${avatar.top},"avatar_bottom":${avatar.bottom},"diameter":${avatar.height},"top_clearance_percent":${100 * topGap / zone.height},"bottom_clearance_percent":${100 * bottomGap / zone.height}}""",
                )
                if (current.state != Call.STATE_RINGING) {
                    val controls = rule.onNodeWithTag("callActionControls", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    org.junit.Assert.assertEquals("Middle zone must end where action controls begin", zone.bottom, controls.top, 1f)
                } else {
                    val controls = rule.onNodeWithTag("callIncomingControls", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    org.junit.Assert.assertEquals("Middle zone must end where incoming controls begin", zone.bottom, controls.top, 1f)
                }
            }
            rule.mainClock.advanceTimeBy(700)
            rule.waitForIdle()
            if (current.fullPhoto && current.withPhoto && current.participants == 0) {
                assertFullPhotoTranslation(current)
            }
            capture(current.id)
            if (current.participants == 6) {
                rule.onNodeWithTag("conferenceParticipantList").performScrollToIndex(5)
                rule.waitForIdle()
                rule.onNodeWithText("Vlad Ionescu").assertExists()
                capture("38-conference-six-scrolled")
            }
            if (current.fontScale > 1f && current.participants > 0) {
                rule.onNodeWithTag("conferenceParticipantList").performScrollToIndex(current.participants - 1)
                rule.waitForIdle()
                rule.onNodeWithText("Andrei Dumitrescu").assertExists()
                capture("39-conference-large-font-scrolled")
            }
        }
        val dialingPortrait = requireNotNull(stableConnectionPortraits["08-dialing"])
        stableConnectionPortraits.forEach { (state, portrait) ->
            org.junit.Assert.assertEquals("Avatar center must stay fixed while timer appears: $state", dialingPortrait.center.y, portrait.center.y, 1f)
            org.junit.Assert.assertEquals("Avatar diameter must stay fixed while timer appears: $state", dialingPortrait.height, portrait.height, 1f)
        }
        verifyResumeControls()
        verifyIncomingModeConsistency()
        verifyAccessibleIncomingResponses()
        verifyLiveSecondaryTimerUpdates()
        verifyLiveMergeAvailability()
        verifyLiveLogicalCallTopology()
        verifyLiveParticipantUpdates()
        verifySmallCallPhotoRendering()
        verifyShiftedPhotoAppearanceMenu()
        showActivePanel("24-keypad", "Keypad")
        verifyCompactGlassKeypad()
        showActivePanel("25-note", "Add note")
        showActivePanel("26-more", "More")
        showIncomingPanel("27-quick-response", "Message")
        showIncomingPanel("28-reminder", "Remind me")
        val connection = Scenario("29-connection-details", Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB)
        present(connection)
        rule.onNodeWithText("HD+ Voice").performClick()
        rule.mainClock.advanceTimeBy(650)
        rule.waitForIdle()
        rule.onNodeWithText("Call connection").assertExists()
        capture(connection.id)
        val unknown = Scenario("30-unknown-incoming", Call.STATE_RINGING, incomingMode = 1, unknown = true)
        present(unknown)
        rule.mainClock.advanceTimeBy(650)
        rule.waitForIdle()
        capture(unknown.id)
        rule.onNodeWithContentDescription("More").performClick()
        rule.mainClock.advanceTimeBy(650)
        rule.waitForIdle()
        rule.onNodeWithText("Unknown caller").assertExists()
        capture("31-unknown-actions")
        rule.onNodeWithText("Block and report spam").performClick()
        rule.mainClock.advanceTimeBy(650)
        rule.waitForIdle()
        rule.onNodeWithText("Block this number?").assertExists()
        capture("32-block-confirmation")
    }

    private fun verifyLiveMergeAvailability() {
        present(Scenario("84-merge-live-disabled", Call.STATE_ACTIVE, otherState = Call.STATE_HOLDING))
        rule.onNodeWithTag("secondaryMergeAction").assertIsNotEnabled()
        val secondary = repository.allCalls.value.last()
        // Same Call instances and equal repository lists: only the Telecom callback changes.
        rule.runOnIdle {
            ReflectionHelpers.setField(secondary, "mUnmodifiableConferenceableCalls", listOf(primary))
            callbacks(secondary).forEach { it.onConferenceableCallsChanged(secondary, mutableListOf(primary)) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryMergeAction").assertIsEnabled()
        capture("85-merge-live-secondary-enabled")
        rule.runOnIdle {
            ReflectionHelpers.setField(secondary, "mUnmodifiableConferenceableCalls", emptyList<Call>())
            callbacks(secondary).forEach { it.onConferenceableCallsChanged(secondary, mutableListOf()) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryMergeAction").assertIsNotEnabled()
        rule.runOnIdle {
            ReflectionHelpers.setField(primary, "mUnmodifiableConferenceableCalls", listOf(secondary))
            callbacks(primary).forEach { it.onConferenceableCallsChanged(primary, mutableListOf(secondary)) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryMergeAction").assertIsEnabled()
        rule.runOnIdle {
            ReflectionHelpers.setField(primary, "mUnmodifiableConferenceableCalls", emptyList<Call>())
            callbacks(primary).forEach { it.onConferenceableCallsChanged(primary, mutableListOf()) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryMergeAction").assertIsNotEnabled()
        capture("86-merge-live-capability-removed")
    }

    private fun verifyLiveLogicalCallTopology() {
        present(Scenario("87-merge-live-topology", Call.STATE_ACTIVE, otherState = Call.STATE_HOLDING, merge = true))
        rule.onNodeWithTag("secondaryMergeAction").assertExists()
        val secondary = repository.allCalls.value.last()
        val sameList = repository.allCalls.value
        rule.runOnIdle {
            // Android mutates the existing Call objects; no repository emission or remount.
            val phone = Shadow.newInstanceOf(Class.forName("android.telecom.Phone"))
            ReflectionHelpers.setField(phone, "mLock", Any())
            val parentId = ReflectionHelpers.getField<String>(primary, "mTelecomCallId")
            ReflectionHelpers.setField(phone, "mCallByTelecomCallId", mapOf(parentId to primary))
            ReflectionHelpers.setField(secondary, "mPhone", phone)
            ReflectionHelpers.setField(secondary, "mParentId", parentId)
            org.junit.Assert.assertSame("Fixture must expose the actual Telecom parent", primary, secondary.parent)
            callbacks(secondary).forEach { it.onParentChanged(secondary, primary) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryMergeAction").assertDoesNotExist()
        org.junit.Assert.assertSame(sameList, repository.allCalls.value)
        rule.runOnIdle {
            children(primary, 2)
            callbacks(primary).forEach { it.onChildrenChanged(primary, primary.children.toMutableList()) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("2 participants").assertExists()
        rule.onNodeWithTag("secondaryMergeAction").assertDoesNotExist()
        capture("87-merge-live-topology")
        // An independent held call must return when Telecom unlinks it.
        rule.runOnIdle {
            ReflectionHelpers.setField(secondary, "mParentId", null)
            callbacks(secondary).forEach { it.onParentChanged(secondary, null) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryMergeAction").assertExists()
    }

    private fun verifyLiveSecondaryTimerUpdates() {
        present(Scenario("71-live-secondary-timer", Call.STATE_RINGING, otherState = Call.STATE_ACTIVE, secondaryDurationMillis = 0))
        rule.onNodeWithTag("secondaryCallTimer").assertTextEquals("—")
        val secondary = repository.allCalls.value.last()
        rule.runOnIdle {
            ReflectionHelpers.setField(secondary.details, "mConnectTimeMillis", System.currentTimeMillis() - 64_000)
            callbacks(secondary).forEach { it.onDetailsChanged(secondary, secondary.details) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryCallTimer").assertTextEquals("01:04")
        rule.runOnIdle {
            ReflectionHelpers.setField(secondary.details, "mState", Call.STATE_HOLDING)
            ReflectionHelpers.setField(secondary, "mState", Call.STATE_HOLDING)
            callbacks(secondary).forEach { it.onStateChanged(secondary, Call.STATE_HOLDING) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryCallTimer").assertTextEquals("01:04")
        rule.runOnIdle {
            ReflectionHelpers.setField(secondary.details, "mConnectTimeMillis", System.currentTimeMillis() - 3_605_000)
            callbacks(secondary).forEach { it.onDetailsChanged(secondary, secondary.details) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("secondaryCallTimer").assertTextEquals("1:00:05")
        capture("71-live-secondary-timer")
    }

    private fun showActivePanel(id: String, label: String) {
        val current = Scenario(id, Call.STATE_ACTIVE, codec = Connection.AUDIO_CODEC_EVS_SWB)
        present(current)
        rule.onNodeWithContentDescription(label).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(650)
        if (id == "26-more") {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            org.junit.Assert.assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold,
                layouts.single().layoutInput.style.fontWeight)
        }
        capture(id)
    }

    private fun verifyCompactGlassKeypad() {
        prefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS, false)
        prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, false)
        prefs.setBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, false)
        prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, false)
        prefs.setInt(PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH, 30)
        val panels = listOf(
            Scenario("d79-keypad-light", Call.STATE_ACTIVE),
            Scenario("d79-keypad-dark150", Call.STATE_ACTIVE, darkTheme = true, fontScale = 1.5f),
            Scenario("d79-keypad-dark200", Call.STATE_ACTIVE, darkTheme = true, fontScale = 2f),
        )
        for (current in panels) {
            present(current)
            val tones = mutableListOf<String>()
            val binderType = Class.forName("com.android.internal.telecom.IInCallAdapter")
            val binder = Proxy.newProxyInstance(binderType.classLoader, arrayOf(binderType)) { _, method, args ->
                when (method.name) {
                    "playDtmfTone" -> tones.add("play:${args?.last()}")
                    "stopDtmfTone" -> tones.add("stop")
                }
                null
            }
            val adapter = Shadow.newInstanceOf(Class.forName("android.telecom.InCallAdapter"))
            ReflectionHelpers.setField(adapter, "mAdapter", binder)
            ReflectionHelpers.setField(primary, "mInCallAdapter", adapter)
            rule.onNodeWithContentDescription("Keypad").performClick()
            prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, true)
            runBlocking { prefs.getPersistedPreferences() }
            rule.mainClock.advanceTimeBy(650)
            rule.waitForIdle()
            rule.waitUntil(5_000) {
                rule.mainClock.advanceTimeByFrame()
                rule.onAllNodesWithTag("inCallKeypadPanel").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("inCallKeypadPanel").assertExists()
            val panel = rule.onNodeWithTag("inCallKeypadContent").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("compact panel: ${panel.height}", panel.height < 440f)
            for (digit in "123456789*0#") {
                val node = rule.onNodeWithTag("inCallKey-$digit").fetchSemanticsNode()
                org.junit.Assert.assertTrue("accessible compact key", node.boundsInRoot.height in 48f..56f)
                org.junit.Assert.assertTrue("key inside panel", panel.contains(node.boundsInRoot.center))
            }
            for (digit in "12*#") rule.onNodeWithTag("inCallKey-$digit").performClick()
            rule.onNodeWithText("12*#").assertExists()
            org.junit.Assert.assertEquals(listOf("play:1", "stop", "play:2", "stop", "play:*", "stop", "play:#", "stop"), tones)
            // Canvas snapshots cannot draw RuntimeShaders. Check the Glass branch above,
            // then capture identical geometry in the normal fallback, not a fake GPU image.
            prefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS, false)
        prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, false)
        prefs.setBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, false)
            runBlocking { prefs.getPersistedPreferences() }
            rule.mainClock.advanceTimeBy(650)
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Cancel").performClick()
            rule.mainClock.advanceTimeBy(650)
            rule.waitForIdle()
            rule.onNodeWithTag("inCallKeypadContent").assertDoesNotExist()
            rule.onNodeWithTag("callHangupSurface").assertExists()
            org.junit.Assert.assertEquals(30, prefs.getInt(PreferenceManager.KEY_DIALPAD_GLASS_STRENGTH, -1))
            // Recreate the panel to discard hardware-only ripple/shader render nodes.
            present(current.copy(id = current.id + "-layout"))
            rule.onNodeWithContentDescription("Keypad").performClick()
            rule.mainClock.advanceTimeBy(650)
            rule.waitForIdle()
            capture(current.id + "-layout")
        }
        prefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS, false)
        prefs.setBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, false)
        prefs.setBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, false)
    }

    private fun verifyLiveParticipantUpdates() {
        val current = Scenario("40-live-participant-added", Call.STATE_ACTIVE, participants = 3)
        present(current)
        rule.runOnIdle {
            children(primary, 4)
            callbacks(primary).forEach { it.onChildrenChanged(primary, primary.children.toMutableList()) }
        }
        rule.mainClock.advanceTimeBy(650)
        rule.waitForIdle()
        rule.onNodeWithText("4 participants").assertExists()
        capture(current.id)
        val first = primary.children.first()
        rule.runOnIdle {
            ReflectionHelpers.setField(first.details, "mCallCapabilities", 0)
            callbacks(first).forEach { it.onDetailsChanged(first, first.details) }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("End call with Radu Popescu").assertIsNotEnabled()
        capture("41-live-capability-removed")
        rule.runOnIdle {
            val remaining = primary.children.drop(1)
            ReflectionHelpers.setField(primary, "mChildren", ArrayList(remaining))
            ReflectionHelpers.setField(primary, "mUnmodifiableChildren", Collections.unmodifiableList(remaining))
            callbacks(primary).forEach { it.onChildrenChanged(primary, remaining.toMutableList()) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("3 participants").assertExists()
        rule.onNodeWithText("Radu Popescu").assertDoesNotExist()
        org.junit.Assert.assertTrue(callbacks(first).isEmpty())
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        capture("42-live-participant-removed")
        rule.runOnIdle {
            ReflectionHelpers.setField(primary.details, "mState", Call.STATE_DISCONNECTING)
            callbacks(primary).forEach { it.onStateChanged(primary, Call.STATE_DISCONNECTING) }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("End call with Mara Ionescu").assertIsNotEnabled()
        capture("43-conference-ending")
        verifyNestedParticipantUpdates()
        verifySingleParticipantPresentation()
    }

    private fun verifySingleParticipantPresentation() {
        present(Scenario("single-conference-two", Call.STATE_ACTIVE, participants = 2, participantPhotos = true, fullPhoto = true))
        rule.runOnIdle { ReflectionHelpers.setField(primary.details, "mCallProperties", Call.Details.PROPERTY_CONFERENCE) }
        val original = primary.children.toList()
        fun report(children: List<Call>) {
            rule.runOnIdle {
                ReflectionHelpers.setField(primary, "mChildren", ArrayList(children))
                ReflectionHelpers.setField(primary, "mUnmodifiableChildren", Collections.unmodifiableList(children))
                callbacks(primary).forEach { it.onChildrenChanged(primary, children.toMutableList()) }
            }
            rule.mainClock.advanceTimeBy(700)
            rule.waitForIdle()
        }
        report(original.take(1))
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodesWithTag("callFullPhotoSharp", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Radu Popescu").assertExists()
        rule.onNodeWithTag("conferenceParticipantList").assertDoesNotExist()
        capture("single-conference-one-full-photo")
        rule.onNodeWithContentDescription("More").performClick()
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithText("Hold").performClick()
        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.runOnIdle { org.junit.Assert.assertEquals(listOf("holdCall:$NUMBER"), telecomCommands.toList()) }
        report(original)
        rule.onNodeWithTag("conferenceParticipantList").assertExists()
        rule.onNodeWithText("2 participants").assertExists()
        capture("single-conference-restored-two")
        report(original.drop(1))
        rule.onNodeWithText("Mara Ionescu").assertExists()
        rule.onNodeWithTag("conferenceParticipantList").assertDoesNotExist()
        capture("single-conference-one-no-photo")
        rule.onNodeWithTag("callHangupSurface").performClick()
        rule.runOnIdle {
            org.junit.Assert.assertEquals(listOf("holdCall:$NUMBER", "disconnectCall:$NUMBER"), telecomCommands.toList())
        }
        report(emptyList())
        val unavailable = rule.activity.getString(dev.goodwy.rphone.R.string.conference_participants_unavailable)
        rule.onNodeWithText(unavailable).assertExists()
        report(original.drop(1))
        rule.runOnIdle {
            ReflectionHelpers.setField(primary.details, "mState", Call.STATE_DISCONNECTING)
            scenario.value = scenario.value.copy(state = Call.STATE_DISCONNECTING)
        }
        report(emptyList())
        rule.onNodeWithText("Mara Ionescu").assertExists()
        rule.onNodeWithText(unavailable).assertDoesNotExist()
        capture("single-conference-ending-retains-caller")
    }

    private fun verifyNestedParticipantUpdates() {
        present(Scenario("70-nested-conference", Call.STATE_ACTIVE, participants = 2))
        val nested = primary.children.first()
        rule.runOnIdle {
            children(nested, 2)
            callbacks(nested).forEach { it.onChildrenChanged(nested, nested.children.toMutableList()) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("3 participants").assertExists()
        rule.runOnIdle {
            children(nested, 3)
            callbacks(nested).forEach { it.onChildrenChanged(nested, nested.children.toMutableList()) }
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithText("4 participants").assertExists()
        capture("71-nested-participant-added")
        val removed = nested.children.first()
        rule.runOnIdle {
            ReflectionHelpers.setField(removed.details, "mState", Call.STATE_DISCONNECTED)
            callbacks(removed).forEach { it.onStateChanged(removed, Call.STATE_DISCONNECTED) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("3 participants").assertExists()
        rule.runOnIdle {
            val remaining = nested.children.drop(1)
            ReflectionHelpers.setField(nested, "mChildren", ArrayList(remaining))
            ReflectionHelpers.setField(nested, "mUnmodifiableChildren", Collections.unmodifiableList(remaining))
            callbacks(nested).forEach { it.onChildrenChanged(nested, remaining.toMutableList()) }
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        org.junit.Assert.assertTrue(callbacks(removed).isEmpty())
        capture("72-nested-participant-removed")
    }

    private fun callbacks(call: Call): List<Call.Callback> =
        ReflectionHelpers.getField<CopyOnWriteArrayList<Any>>(call, "mCallbackRecords").map {
            ReflectionHelpers.getField<Call.Callback>(it, "mCallback")
        }

    private fun showIncomingPanel(id: String, label: String) {
        val current = Scenario(id, Call.STATE_RINGING, incomingMode = 1)
        present(current)
        rule.onNodeWithContentDescription(label).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(650)
        rule.waitForIdle()
        rule.onNodeWithText(if (label == "Message") "Quick Response" else "Call back reminder").assertExists()
        capture(id)
    }

    private fun verifySmallCallPhotoRendering() {
        val original = photoFixture
        val key = dev.goodwy.rphone.controller.util.SmallCallPhotoPreference.KEY
        try {
            for (size in listOf(192, 256, 512)) for (enabled in listOf(false, true)) {
                photoFixture = createPhotoFixture(RuntimeEnvironment.getApplication(), size, size)
                rule.runOnIdle { runBlocking { prefs.restoreAllPreferences(mapOf(key to enabled)) } }
                val current = Scenario("small-photo-$size-$enabled", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true)
                present(current)
                val circle = size <= 256 && enabled
                rule.waitUntil(5_000) {
                    rule.onAllNodesWithTag(if (circle) "callPrimaryAvatar" else "callFullPhotoSharp", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                rule.mainClock.advanceTimeBy(800)
                rule.waitForIdle()
                rule.onNodeWithTag(if (circle) "callFullPhotoSharp" else "callPrimaryAvatar", useUnmergedTree = true).assertDoesNotExist()
                rule.onNodeWithText("Mute").assertExists()
                org.junit.Assert.assertEquals(PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO,
                    prefs.getString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, null))
                capture(current.id)
            }
            val context = RuntimeEnvironment.getApplication()
            val store = dev.goodwy.rphone.controller.util.UnsavedNumberAvatars(context, "avatar-render@example.test")
            val source = createPhotoFixture(context, 512, 512)
            val bytes = File(requireNotNull(Uri.parse(source).path)).readBytes()
            org.junit.Assert.assertTrue(store.store("+40700000992", bytes, dev.goodwy.rphone.controller.util.ContactPhotoAutomaticPolicy.Latest, valid = { true }))
            photoFixture = requireNotNull(store.resolve("+40700000992"))
            runBlocking { prefs.restoreAllPreferences(mapOf(key to false)) }
            present(Scenario("whatsapp-origin-512-toggle-off", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true))
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("callPrimaryAvatar", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            rule.mainClock.advanceTimeBy(800)
            rule.onNodeWithTag("callFullPhotoSharp", useUnmergedTree = true).assertDoesNotExist()
            waitForPhotoFixture()
            capture("whatsapp-origin-512-toggle-off")
        } finally {
            photoFixture = original
            runBlocking { prefs.restoreAllPreferences(mapOf(key to true)) }
        }
    }

    private fun verifyShiftedPhotoAppearanceMenu() {
        present(Scenario("90-full-photo-appearance-menu", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true))
        waitForPhotoFixture()
        rule.waitForIdle()
        rule.onNodeWithTag("callFullPhotoSharp", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick) { action ->
                org.junit.Assert.assertTrue("Shifted photo still opens appearance menu", action())
            }
        rule.waitForIdle()
        rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.call_appearance_full_photo)).assertExists()
        for (labelId in listOf(dev.goodwy.rphone.R.string.call_appearance_circular_avatar,
            dev.goodwy.rphone.R.string.call_appearance_full_photo, dev.goodwy.rphone.R.string.ambient_droplets)) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(rule.activity.getString(labelId), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            org.junit.Assert.assertEquals(androidx.compose.ui.text.font.FontWeight.Medium,
                layouts.single().layoutInput.style.fontWeight)
        }
        val dropletsLabel = rule.activity.getString(dev.goodwy.rphone.R.string.ambient_droplets)
        val savedBrightness = prefs.getInt(AmbientDropletPreference.BRIGHTNESS_KEY, AmbientDropletPreference.DEFAULT_BRIGHTNESS)
        rule.runOnIdle { prefs.setBoolean(AmbientDropletPreference.ENABLED_KEY, true) }
        rule.waitUntil(5_000) { prefs.getBoolean(AmbientDropletPreference.ENABLED_KEY, false) }
        rule.onNodeWithText(dropletsLabel).assertIsOn().performClick()
        rule.waitUntil(5_000) { !prefs.getBoolean(AmbientDropletPreference.ENABLED_KEY, true) }
        rule.onNodeWithText(dropletsLabel).assertIsOff()
        capture("appearance-menu-droplets-off")
        // External Settings writes are reflected by the same open menu.
        rule.runOnIdle { prefs.setBoolean(AmbientDropletPreference.ENABLED_KEY, true) }
        rule.waitUntil(5_000) { prefs.getBoolean(AmbientDropletPreference.ENABLED_KEY, false) }
        rule.onNodeWithText(dropletsLabel).assertIsOn()
        rule.runOnIdle {
            org.junit.Assert.assertEquals(savedBrightness, prefs.getInt(AmbientDropletPreference.BRIGHTNESS_KEY, AmbientDropletPreference.DEFAULT_BRIGHTNESS))
            org.junit.Assert.assertTrue("Appearance must not dispatch Telecom commands", telecomCommands.isEmpty())
        }
        rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.call_appearance_circular_avatar)).performClick()
        rule.waitUntil(5_000) {
            prefs.getString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, null) ==
                PreferenceManager.CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR &&
                rule.onAllNodesWithTag("callPrimaryAvatar", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("callFullPhotoSharp", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("callPrimaryAvatar", useUnmergedTree = true)
            .performTouchInput { longClick() }
        rule.onNodeWithText(dropletsLabel).assertIsOn().performClick()
        rule.waitUntil(5_000) { !prefs.getBoolean(AmbientDropletPreference.ENABLED_KEY, true) }
        rule.onNodeWithText(dropletsLabel).assertIsOff()
        rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.call_appearance_full_photo)).performClick()
        rule.waitUntil(5_000) {
            prefs.getString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, null) == PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO
        }
        rule.onNodeWithText(dropletsLabel).assertDoesNotExist()
        // Recreate the call screen: both choices survive composition disposal.
        rule.runOnIdle {
            scenario.value = Scenario("appearance-menu-persisted", Call.STATE_ACTIVE, withPhoto = true, fullPhoto = true)
        }
        rule.waitForIdle()
        waitForPhotoFixture()
        rule.onNodeWithTag("callFullPhotoSharp", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick) { action -> org.junit.Assert.assertTrue(action()) }
        rule.onNodeWithText(dropletsLabel).assertIsOff()
        rule.onNodeWithText(dropletsLabel).performClick()
        rule.waitUntil(5_000) { prefs.getBoolean(AmbientDropletPreference.ENABLED_KEY, false) }
        rule.onNodeWithText(rule.activity.getString(dev.goodwy.rphone.R.string.call_appearance_full_photo)).performClick()
    }

    private fun appearance(current: Scenario) = if (current.fullPhoto) {
            PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO
        } else PreferenceManager.CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR

    private fun verifyResumeControls() {
        for (dark in listOf(false, true)) for (conference in listOf(false, true)) {
            val current = Scenario("resume-${if (conference) "conference" else "single"}-${if (dark) "dark" else "light"}",
                Call.STATE_HOLDING, participants = if (conference) 3 else 0,
                darkTheme = dark, fontScale = if (dark) 1.5f else 1f)
            present(current)
            capture(current.id)
            rule.onNodeWithContentDescription("Resume").performClick()
            rule.runOnIdle { org.junit.Assert.assertEquals(listOf("unholdCall:$NUMBER"), telecomCommands.toList()) }
            present(current.copy(id = "${current.id}-more"))
            rule.onNodeWithContentDescription("More").performClick()
            rule.mainClock.advanceTimeBy(650)
            capture("${current.id}-more")
            rule.onNodeWithText("Resume").performClick()
            rule.runOnIdle { org.junit.Assert.assertEquals(listOf("unholdCall:$NUMBER"), telecomCommands.toList()) }
        }
    }

    private fun verifyIncomingModeConsistency() {
        for (mode in listOf(0, 1, 2, 3, 4, 10)) for (onStart in listOf(true, false)) {
            val base = Scenario("incoming-mode-$mode-$onStart", Call.STATE_RINGING,
                incomingMode = mode, answerOnStart = onStart,
                darkTheme = !onStart, fontScale = if (onStart) 1f else 1.5f,
                withPhoto = true, fullPhoto = onStart)
            present(base)
            val normal = rule.onNodeWithTag("incomingCallMode-$mode").fetchSemanticsNode().boundsInRoot
            for ((context, state, conference) in listOf(
                Triple("active", Call.STATE_ACTIVE, false),
                Triple("held", Call.STATE_HOLDING, false),
                Triple("conference", Call.STATE_ACTIVE, true),
            )) {
                val current = base.copy(id = "${base.id}-$context", otherState = state, otherConference = conference)
                for (answer in listOf(true, false)) {
                    present(current.copy(id = "${current.id}-$answer"))
                    val waiting = rule.onNodeWithTag("incomingCallMode-$mode").fetchSemanticsNode().boundsInRoot
                    org.junit.Assert.assertEquals("Normal bottom anchor: ${current.id}", normal.bottom, waiting.bottom, 1f)
                    org.junit.Assert.assertEquals("Same response geometry: ${current.id}", normal.top, waiting.top, 1f)
                    assertSquareActionGrid(current.id)
                    if (answer && context == "active") capture(current.id)
                    val gesture = mode != 1 && (mode != 10 || answer)
                    val node = if (mode in listOf(0, 3) || (mode == 10 && answer)) {
                        rule.onNodeWithTag("callAnswerHandle")
                    } else rule.onNodeWithContentDescription(if (answer) "Answer" else "Decline", useUnmergedTree = true)
                    if (gesture) {
                        node.performTouchInput { down(center); moveBy(Offset(2f, 2f), delayMillis = 100); up() }
                        rule.mainClock.advanceTimeBy(700)
                        org.junit.Assert.assertTrue("Cancelled gesture must do nothing: ${current.id}", telecomCommands.isEmpty())
                    }
                    node.performTouchInput { down(center) }
                    if (gesture) node.performTouchInput {
                        val side = if (onStart) 1f else -1f
                        val delta = when (mode) {
                            0 -> Offset((if (answer) -240f else 240f) * side, 0f)
                            3 -> Offset(0f, (if (answer) -160f else 160f) * side)
                            2 -> Offset((if (answer) 240f else -240f) * side, 0f)
                            10 -> Offset(260f * side, 0f)
                            else -> Offset(260f, 0f)
                        }
                        moveBy(delta / 2f, delayMillis = 100)
                        moveBy(delta / 2f, delayMillis = 100)
                    }
                    rule.mainClock.advanceTimeBy(600)
                    node.performTouchInput { up() }
                    rule.mainClock.advanceTimeBy(700)
                    val expected = if (answer) {
                        if (state == Call.STATE_ACTIVE) listOf("holdCall:0700000001", "answerCall:$NUMBER")
                        else listOf("answerCall:$NUMBER")
                    } else {
                        if (state == Call.STATE_HOLDING) listOf("rejectCall:$NUMBER", "unholdCall:0700000001")
                        else listOf("rejectCall:$NUMBER")
                    }
                    org.junit.Assert.assertEquals("Exactly one contextual commit: ${current.id} answer=$answer", expected, telecomCommands.toList())
                }
            }
        }
    }

    private fun verifyAccessibleIncomingResponses() {
        for (mode in listOf(0, 3, 10)) for (onStart in listOf(true, false)) {
            for (held in listOf<Int?>(null, Call.STATE_ACTIVE, Call.STATE_HOLDING)) {
                val current = Scenario("accessible-answer-$mode-$onStart-$held", Call.STATE_RINGING,
                    incomingMode = mode, answerOnStart = onStart, otherState = held)
                present(current)
                val handle = rule.onNodeWithTag("callAnswerHandle")
                // Ordinary tap retains the slider's accidental-touch protection.
                handle.performTouchInput { down(center); up() }
                rule.mainClock.advanceTimeBy(650)
                rule.runOnIdle { org.junit.Assert.assertTrue(telecomCommands.isEmpty()) }
                handle.performSemanticsAction(SemanticsActions.OnClick) { it() }
                rule.runOnIdle {
                    val expected = if (held == Call.STATE_ACTIVE) {
                        listOf("holdCall:0700000001", "answerCall:$NUMBER")
                    } else listOf("answerCall:$NUMBER")
                    org.junit.Assert.assertEquals(expected, telecomCommands.toList())
                }
                if (mode != 10) {
                    present(current.copy(id = current.id + "-decline"))
                    val actions = rule.onNodeWithTag("callAnswerHandle").fetchSemanticsNode().config[SemanticsActions.CustomActions]
                    rule.runOnIdle {
                        org.junit.Assert.assertEquals("Decline", actions.single().label)
                        org.junit.Assert.assertTrue(actions.single().action())
                    }
                    rule.runOnIdle {
                        val expected = if (held == null) {
                            listOf("disconnectCall:$NUMBER")
                        } else if (held == Call.STATE_HOLDING) {
                            listOf("rejectCall:$NUMBER", "unholdCall:0700000001")
                        } else listOf("rejectCall:$NUMBER")
                        org.junit.Assert.assertEquals(expected, telecomCommands.toList())
                    }
                }
            }
        }
    }

    private fun present(current: Scenario) {
        rule.runOnIdle { setup(current) }
        rule.waitUntil(5_000) {
            prefs.getString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, null) == appearance(current) &&
                prefs.getInt(PreferenceManager.KEY_INCOMING_CALL_UI_MODE, -1) == current.incomingMode &&
                prefs.getString(PreferenceManager.KEY_INCOMING_ANSWER_PLACEMENT, null) == placement(current) &&
                prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, false) &&
                prefs.getBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, false)
        }
        rule.runOnIdle { scenario.value = current }
        rule.waitForIdle()
    }

    private fun setup(current: Scenario) {
        telecomCommands.clear()
        // These writes must finish before the next scene mounts; fire-and-forget setters can
        // reorder a late full-photo write after the following circular-avatar scene.
        runBlocking { prefs.restoreAllPreferences(mapOf(
            PreferenceManager.KEY_INCOMING_CALL_UI_MODE to current.incomingMode,
            PreferenceManager.KEY_INCOMING_ANSWER_PLACEMENT to placement(current),
            PreferenceManager.KEY_CALL_SCREEN_APPEARANCE to appearance(current),
            PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR to true,
            PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND to false,
            PreferenceManager.KEY_SHOW_PICTURE to true,
            PreferenceManager.KEY_MODERN_PASTEL_AVATARS to true,
        )) }
        primary = newCall(current.state, if (current.unknown) "0700000099" else NUMBER, current.codec)
        val secondary = current.otherState?.let { newCall(it, "0700000001") }
        secondary?.let {
            ReflectionHelpers.setField(it.details, "mConnectTimeMillis", if (current.secondaryDurationMillis > 0) System.currentTimeMillis() - current.secondaryDurationMillis else 0L)
        }
        if (current.participants > 0) children(primary, current.participants)
        if (current.conference) ReflectionHelpers.setField(primary.details, "mCallProperties", Call.Details.PROPERTY_CONFERENCE)
        primary.children.forEach { participant ->
            if (!current.participantDisconnect) ReflectionHelpers.setField(participant.details, "mCallCapabilities", 0)
        }
        if (current.otherConference && secondary != null) children(secondary, 2)
        if (current.merge && secondary != null) {
            ReflectionHelpers.setField(primary, "mConferenceableCalls", arrayListOf(secondary))
            ReflectionHelpers.setField(primary, "mUnmodifiableConferenceableCalls", listOf(secondary))
            ReflectionHelpers.setField(secondary, "mConferenceableCalls", arrayListOf(primary))
            ReflectionHelpers.setField(secondary, "mUnmodifiableConferenceableCalls", listOf(primary))
        }
        repository.updateAllCalls(listOfNotNull(primary, secondary))
        repository.updateCurrentCallSession(CallSession(primary, current.state, connectTimeMillis = System.currentTimeMillis() - if (current.referenceComposition) 442_000 else 94_000))
        @Suppress("DEPRECATION")
        repository.updateAudioState(CallAudioState(current.muted, current.route, CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER))
    }

    private fun newCall(state: Int, number: String, codec: Int? = null): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        ReflectionHelpers.setField(call, "mCallbackRecords", CopyOnWriteArrayList<Any>())
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mState", state)
        ReflectionHelpers.setField(details, "mHandle", Uri.fromParts("tel", number, null))
        ReflectionHelpers.setField(details, "mHandlePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mCallerDisplayNamePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mAccountHandle", accountHandle)
        ReflectionHelpers.setField(details, "mCallCapabilities", Call.Details.CAPABILITY_HOLD or Call.Details.CAPABILITY_MUTE or Call.Details.CAPABILITY_RESPOND_VIA_TEXT)
        ReflectionHelpers.setField(details, "mExtras", Bundle().apply { codec?.let { putInt(Connection.EXTRA_AUDIO_CODEC, it) } })
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", state)
        ReflectionHelpers.setField(call, "mChildrenIds", arrayListOf<String>())
        val children = arrayListOf<Call>()
        ReflectionHelpers.setField(call, "mChildren", children)
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", Collections.unmodifiableList(children))
        ReflectionHelpers.setField(call, "mChildrenCached", true)
        val mergeable = arrayListOf<Call>()
        ReflectionHelpers.setField(call, "mConferenceableCalls", mergeable)
        ReflectionHelpers.setField(call, "mUnmodifiableConferenceableCalls", Collections.unmodifiableList(mergeable))
        ReflectionHelpers.setField(call, "mTelecomCallId", number)
        val binderType = Class.forName("com.android.internal.telecom.IInCallAdapter")
        val binder = Proxy.newProxyInstance(binderType.classLoader, arrayOf(binderType)) { _, method, args ->
            if (method.name in listOf("holdCall", "answerCall", "rejectCall", "rejectCallWithReason", "unholdCall", "disconnectCall", "conference")) {
                val command = if (method.name == "rejectCallWithReason") "rejectCall" else method.name
                telecomCommands.add("$command:${args!![0]}")
            }
            null
        }
        val adapterType = Class.forName("android.telecom.InCallAdapter")
        val constructor = adapterType.getDeclaredConstructor(binderType).apply { isAccessible = true }
        ReflectionHelpers.setField(call, "mInCallAdapter", constructor.newInstance(binder))
        return call
    }

    private fun placement(current: Scenario) = if (current.answerOnStart) {
        PreferenceManager.INCOMING_ANSWER_PLACEMENT_LEFT_TOP
    } else PreferenceManager.INCOMING_ANSWER_PLACEMENT_RIGHT_BOTTOM

    private fun children(call: Call, count: Int) {
        val children = ArrayList((1..count).map { newCall(Call.STATE_ACTIVE, "070000001$it") })
        val names = listOf("Radu Popescu", "Mara Ionescu", "Elena Marinescu", "Andrei Dumitrescu", "Ioana Georgescu", "Vlad Ionescu")
        children.forEachIndexed { index, child ->
            ReflectionHelpers.setField(child.details, "mCallerDisplayName", names[index % names.size])
            ReflectionHelpers.setField(child.details, "mCallCapabilities", Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
            val duration = if (index == 2) 3_605_000L else 94_000L + index * 20_000L
            ReflectionHelpers.setField(child.details, "mConnectTimeMillis", if (index == 3) 0L else System.currentTimeMillis() - duration)
        }
        ReflectionHelpers.setField(call, "mChildren", children)
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", Collections.unmodifiableList(children))
        ReflectionHelpers.setField(call, "mChildrenIds", ArrayList((1..count).map { "participant-$it" }))
    }

    private fun capture(id: String) {
        output.mkdirs()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.let { window ->
                val dialogView = window.decorView
                val canvas = Canvas(bitmap)
                // Full-screen sheets render their own scrim. Windowed AlertDialogs need
                // the window manager's centered position and dimming represented here.
                if (dialogView.height < bitmap.height) {
                    if (window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                        canvas.drawColor(Color.argb((window.attributes.dimAmount * 255).toInt(), 0, 0, 0))
                    }
                    canvas.translate((bitmap.width - dialogView.width) / 2f, (bitmap.height - dialogView.height) / 2f)
                }
                dialogView.draw(canvas)
            }
            File(output, "$id.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        val roots = rule.onAllNodes(isRoot(), useUnmergedTree = true)
        val dumps = roots.fetchSemanticsNodes().indices.joinToString("\n\n") { roots[it].printToString() }
        File(output, "$id-semantics.txt").writeText(dumps)
    }

    private fun waitForPhotoFixture() {
        // A Compose idle frame alone does not wait for Coil's file decoder. Check the
        // distinctive green fixture in the rendered middle zone before taking the shot.
        rule.waitUntil(timeoutMillis = 5_000) {
            var loaded = false
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val pixel = bitmap.getPixel(view.width / 2, (view.height * 0.45f).toInt())
                loaded = Color.green(pixel) - Color.red(pixel) > 12
                bitmap.recycle()
            }
            loaded
        }
    }

    private fun createNeutralPhotoFixture(context: Context): String {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val file = File(context.cacheDir, "neutral-portrait.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun createPhotoFixture(context: Context, width: Int = 480, height: Int = 640): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Keep the loading marker in the same relative place for all source shapes.
        canvas.scale(width / 480f, height / 640f)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 480f, 640f, Color.rgb(40, 100, 140), Color.rgb(200, 140, 70), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 480f, 640f, paint)
        paint.shader = null
        paint.color = Color.rgb(35, 65, 55)
        canvas.drawCircle(240f, 300f, 120f, paint)
        val file = File(context.cacheDir, "call-photo-fixture-${width}x$height.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun createReferencePhotoFixture(context: Context): String? {
        val relative = "evidence/user-provided/approved-call-backgrounds-20261002/approved-light-119458.jpg"
        val source = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .map { File(it, relative) }.firstOrNull { it.isFile } ?: return null
        val reference = requireNotNull(BitmapFactory.decodeFile(source.absolutePath))
        // Only the supplied portrait, not mockup artwork, is input to the production algorithm.
        val diameter = (reference.width * .439f).toInt()
        val portrait = Bitmap.createBitmap(reference, (reference.width * .281f).toInt(), (reference.height * .279f).toInt(), diameter, diameter)
            .copy(Bitmap.Config.ARGB_8888, true)
        // Mockup background in the square's corners is NOT part of the photographed avatar.
        // Exclude it from colour extraction as well as from the visible circular crop.
        val radius = diameter / 2f
        for (y in 0 until diameter) for (x in 0 until diameter) {
            if ((x + .5f - radius) * (x + .5f - radius) + (y + .5f - radius) * (y + .5f - radius) > radius * radius) {
                portrait.setPixel(x, y, Color.TRANSPARENT)
            }
        }
        val file = File(context.cacheDir, "approved-portrait-fixture.png")
        file.outputStream().use { check(portrait.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        portrait.recycle()
        reference.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun recordReferencePalette(uri: String) {
        // Native Android bitmap sampling; no synchronous network/image-loader work on
        // the test looper. The normal screen still loads and renders through Coil.
        val source = requireNotNull(BitmapFactory.decodeFile(Uri.parse(uri).path))
        val bitmap = Bitmap.createScaledBitmap(source, 64, 64, true)
        val pixels = IntArray(4096)
        bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        val palette = requireNotNull(extractCallAvatarPalette(pixels))
        val report = JSONObject()
        for ((name, color) in listOf("upper" to palette.upper, "left" to palette.left, "right" to palette.right)) {
            fun describe(value: Int): JSONObject {
                val hsv = FloatArray(3).also { Color.colorToHSV(value, it) }
                return JSONObject().put("rgb", JSONArray(listOf(Color.red(value), Color.green(value), Color.blue(value))))
                    .put("hue", hsv[0]).put("saturation", hsv[1]).put("value", hsv[2])
            }
            report.put(name, JSONObject().put("source", describe(color)).put("light", describe(callMistColor(color, false).toArgb())).put("dark", describe(callMistColor(color, true).toArgb())))
        }
        output.mkdirs()
        File(output, "reference-photo-palette.json").writeText(report.toString(2))
        bitmap.recycle()
        source.recycle()
    }

    private fun waitForReferencePhoto() {
        rule.waitUntil(timeoutMillis = 5_000) {
            val bounds = rule.onNodeWithTag("callPrimaryAvatar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            var loaded = false
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val pixel = bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())
                loaded = kotlin.math.abs(Color.green(pixel) - Color.blue(pixel)) > 8
                bitmap.recycle()
            }
            loaded
        }
    }

    private data class Scenario(
        val id: String, val state: Int, val codec: Int? = null,
        val otherState: Int? = null, val merge: Boolean = false,
        val participants: Int = 0, val otherConference: Boolean = false,
        val incomingMode: Int = PreferenceManager.DEFAULT_INCOMING_CALL_UI_MODE,
        val answerOnStart: Boolean = true,
        val muted: Boolean = false, val route: Int = CallAudioState.ROUTE_EARPIECE,
        val unknown: Boolean = false,
        val conference: Boolean = false, val participantDisconnect: Boolean = true, val fontScale: Float = 1f,
        val withPhoto: Boolean = false, val fullPhoto: Boolean = false, val darkTheme: Boolean = false,
        val photoShape: String? = null,
        val referencePhoto: Boolean = false,
        val neutralPhoto: Boolean = false,
        val participantPhotos: Boolean = false,
        val referenceComposition: Boolean = false,
        val secondaryDurationMillis: Long = 114_000,
    )

    private companion object { const val NUMBER = "0700000000" }
}

