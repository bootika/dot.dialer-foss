package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CallAvatarPresentationContractTest {
    @Test
    fun `calm call avatar is circular and fills the mask`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreenComponents.kt",
        ).readText()
        val avatar = source.substringAfter("fun CalmCallAvatar(")
            .substringBefore("@Composable\nfun PulsingAvatar")

        assertTrue(avatar.contains(".aspectRatio(1f)"))
        assertTrue(avatar.contains("shape = CircleShape"))
        assertTrue(avatar.contains("photoContentScale = ContentScale.Crop"))
    }

    @Test
    fun `call photo appearance changes only through the long press menu`() {
        val callScreen = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreen.kt",
        ).readText()
        val background = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/screen/CallScreenBackground.kt",
        ).readText()

        assertTrue(callScreen.contains("onLongPress = { localPosition ->"))
        assertTrue(
            callScreen.contains(
                "openCallAppearanceMenu(avatarPositionInRoot + localPosition)",
            ),
        )
        assertTrue(callScreen.contains("showCallAppearanceMenu = true"))
        assertTrue(callScreen.contains("PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, appearance"))
        assertTrue(background.contains("onPhotoLongClick"))
    }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
            ?: error("Unable to find repository file: $relativePath")
    }
}
