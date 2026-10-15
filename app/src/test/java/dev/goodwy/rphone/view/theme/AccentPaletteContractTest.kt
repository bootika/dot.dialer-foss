package dev.goodwy.rphone.view.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AccentPaletteContractTest {
    @Test fun `approved base palette and Ruby roles use canonical values`() {
        assertEquals(Color(0xFF00BB77), DotJade)
        assertEquals(Color(0xFF6857E5), DotElectricIris)
        assertEquals(Color(0xFF4285F4), DotBlue)
        assertEquals(Color(0xFFE5485B), DotRuby)
        assertEquals(DotRuby, DotCoral)
        assertEquals(Color(0xFFFFE1E5), DotRubySoft)
        assertEquals(Color(0xFFA7293E), DotRubyStrong)
        assertEquals(Color(0xFF240C11), DotRubyInk)
        assertEquals(DotAccent.Coral, resolveAccent("coral"))
        assertEquals(DotAccent.Jade, resolveAccent(null))
    }
    @Test fun `content tones are separate from fills and don't change other accents or dark tones`() {
        assertEquals(DotBlueStrong, lightColorScheme(primary=DotBlue).accentText)
        assertEquals(DotRubyStrong, lightColorScheme(primary=DotRuby).accentText)
        for (unchanged in listOf(DotJade, DotElectricIris, Color(0xFFAFC6FF), Color(0xFFFFB4BC)))
            assertEquals(unchanged, lightColorScheme(primary=unchanged).accentText)
        for ((fg,bg) in listOf(DotRubyInk to DotRuby, DotRubyStrong to Color.White,
            DotRubyStrong to DotRubySoft, DotInk to DotBlue, DotBlueStrong to Color.White))
            assertTrue("Small-text contrast", (maxOf(fg.luminance(),bg.luminance())+.05f)/(minOf(fg.luminance(),bg.luminance())+.05f)>=4.5f)
        assertEquals(DotJade, CallActionColors.answerCommitted)
        assertEquals(Color(0xFFC83E4D), CallActionColors.rejectCommitted)
    }
    @Test fun `Ruby is invariant and no localized Coral override remains`() {
        val root=File(requireNotNull(System.getProperty("user.dir"))).let { if(File(it,"src/main/res").isDirectory) it else File(it,"app") }
        val base=File(root,"src/main/res/values/strings.xml").readText()
        assertTrue(base.contains("<string name=\"accent_coral\" translatable=\"false\">Ruby</string>"))
        File(root,"src/main/res").listFiles().orEmpty().filter { it.name.startsWith("values-") }.forEach {
            val strings=File(it,"strings.xml")
            assertFalse(it.name,strings.isFile && strings.readText().contains("name=\"accent_coral\""))
        }
    }
}
