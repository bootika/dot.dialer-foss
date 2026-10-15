package dev.goodwy.rphone.view.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesLayoutTest {
    @Test
    fun `medium avatars show four favorites and a discoverability peek on S25 width`() {
        val favoriteAvatarSize = 46 + FAVORITES_AVATAR_BOOST_DP
        val visibleItems = estimatedVisibleFavoriteItems(
            viewportWidthDp = 360,
            avatarSizeDp = favoriteAvatarSize,
        )

        assertEquals(56, favoriteAvatarSize)
        assertTrue(visibleItems in 4.2f..4.6f)
    }

    @Test
    fun `large avatar preference remains usable in the favorites rail`() {
        val visibleItems = estimatedVisibleFavoriteItems(
            viewportWidthDp = 360,
            avatarSizeDp = 52 + FAVORITES_AVATAR_BOOST_DP,
        )

        assertTrue(visibleItems >= 4f)
    }

    @Test
    fun `favorite labels gain width at large Android font scales`() {
        assertEquals(68.6f, favoriteItemWidthDp(56, fontScale = 1f), 0.001f)
        assertEquals(92f, favoriteItemWidthDp(56, fontScale = 1.5f), 0.001f)
        assertTrue(
            estimatedVisibleFavoriteItems(360, 56, fontScale = 1.5f) <
                estimatedVisibleFavoriteItems(360, 56),
        )
    }

    @Test
    fun `circle gaps are ten percent smaller for every avatar preference and Android font scale`() {
        listOf(50, 56, 62).forEach { avatar ->
            listOf(0.85f, 1f, 1.15f, 1.5f, 2f).forEach { fontScale ->
                val oldWidth = maxOf(70, avatar + 14, kotlin.math.ceil(64f * fontScale).toInt())
                val oldGap = oldWidth + 6f - avatar
                val newGap = favoriteItemWidthDp(avatar, fontScale) + FAVORITE_ITEM_SPACING_DP - avatar
                assertEquals(oldGap * 0.9f, newGap, 0.001f)
                assertTrue(favoriteItemWidthDp(avatar, fontScale) >= avatar + 4f)
                assertTrue(favoriteItemWidthDp(avatar, fontScale) >= 48f)
            }
        }
    }

    @Test
    fun `neutral add favorite control is always the final rail item`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/IPhoneFavoritesRow.kt",
        ).readText()
        val favoritesStart = source.indexOf("itemsIndexed(")
        val addControlStart = source.indexOf("item(key = \"add-favorite\")")

        assertTrue(favoritesStart >= 0)
        assertTrue(addControlStart > favoritesStart)
        assertFalse(source.contains("if (favoritesList.isEmpty())"))
        assertTrue(source.contains("color = MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertTrue(source.contains("tint = MaterialTheme.colorScheme.onSurfaceVariant"))
        assertTrue(source.contains("text = stringResource(R.string.add_favorite_short)"))
    }

    @Test
    fun `favorite rail aligns to its start when contacts first arrive`() {
        val source = repoFile(
            "app/src/main/java/dev/goodwy/rphone/view/components/IPhoneFavoritesRow.kt",
        ).readText()

        assertTrue(source.contains("LaunchedEffect(favoriteIds)"))
        assertTrue(source.contains("!hasAlignedInitialFavorites && favoriteIds.isNotEmpty()"))
        assertTrue(source.contains("scrollToItem(index = 0, scrollOffset = 0)"))
    }

    private fun repoFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("Cannot locate $relativePath from $start")
    }
}
