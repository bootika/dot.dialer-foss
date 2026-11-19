package dev.goodwy.rphone.localization

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards direct UI sinks, not arbitrary strings such as URLs, logs, provider names or backend codes. */
class HardcodedUiTextTest {
    private data class Literal(val path: String, val sink: String, val text: String)

    @Test
    fun `main and Play sources cannot introduce ordinary UI literals`() {
        val root = repoRoot()
        val actual = listOf("app/src/main/java", "app/src/gplay/java").flatMap { directory ->
            File(root, directory).walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }
                .flatMap { file -> literals(file.relativeTo(root).invariantSeparatorsPath, file.readText()).asSequence() }.toList()
        }.toSet()
        val exemptions = documentedExemptions()
        assertTrue("New ordinary UI literals need string resources: ${actual - exemptions.keys}", (actual - exemptions.keys).isEmpty())
        assertEquals("Remove obsolete exemptions when retiring legacy literals", exemptions.keys, actual)
    }

    @Test
    fun `scanner catches new UI text including named and multiline arguments`() {
        val code = """
            Text("New English text")
            Title(text = "Another title")
            context.toast(
                "A new error message"
            )
            Row(headline = "New headline", supporting = "New supporting text", contentDescription = "New accessibility label")
            Toast.makeText(context, "A native toast", Toast.LENGTH_SHORT)
            Text("${'$'}{count} selected")
            Row(title = "New title argument", text = "New text argument", placeholder = "New placeholder argument")
        """.trimIndent()
        assertEquals(11, literals("fixture.kt", code).size)
    }

    @Test
    fun `scanner ignores comments brands dynamic values and technical measurements`() {
        val code = """
            // Text("Comment only")
            /* Title("Comment only too") */
            Text("dot.dialer")
            Text("Google Play")
            Text("24 fps")
            Text("${'$'}contactName")
            Text("${'$'}{count}")
            Log.w("Screen", "Technical failure")
            val endpoint = "https://example.invalid/Text(\"Not UI\")"
        """.trimIndent()
        assertTrue(literals("fixture.kt", code).isEmpty())
    }

    @Test
    fun `large source strings and comments do not recurse through the scanner`() {
        val source = "/*" + "legacy comment ".repeat(30000) + "*/\nval blob = \"" + "opaque ".repeat(30000) + "\"\nText(\"New visible prose\")"
        assertEquals(listOf(Literal("large.kt", "Text", "New visible prose")), literals("large.kt", source))
    }

    private data class Token(val value: String, val literal: Boolean = false)
    private data class Frame(val quote: Int, var braces: Int = 1)

    private fun literals(path: String, source: String): List<Literal> {
        val tokens = tokenize(source)
        val found = linkedMapOf<Int, Literal>()
        fun take(index: Int, sink: String) {
            tokens.getOrNull(index)?.takeIf { it.literal }?.let { found.putIfAbsent(index, Literal(path, sink, it.value)) }
        }
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token.literal) continue
            if (token.value in setOf("Text", "Title", "toast") && tokens.getOrNull(index + 1)?.value == "(") {
                val argument = if (tokens.getOrNull(index + 2)?.value == "text" && tokens.getOrNull(index + 3)?.value == "=") index + 4 else index + 2
                take(argument, token.value)
            }
            if (token.value in setOf("headline", "supporting", "contentDescription", "title", "text", "placeholder") &&
                tokens.getOrNull(index + 1)?.value == "=") take(index + 2, token.value)
            if (token.value == "Toast" && tokens.getOrNull(index + 1)?.value == "." &&
                tokens.getOrNull(index + 2)?.value == "makeText" && tokens.getOrNull(index + 3)?.value == "(") {
                var depth = 0
                for (argument in index + 4 until tokens.size) {
                    if (tokens[argument].literal) continue
                    when (tokens[argument].value) {
                        "(", "[", "{" -> depth++
                        ")", "]", "}" -> if (depth == 0) break else depth--
                        "," -> if (depth == 0) { take(argument + 1, "makeText"); break }
                    }
                }
            }
        }
        return found.values.filter { literal ->
            val text = withoutInterpolation(literal.text).trim()
            // Pure identities/counts have no remaining prose; English around interpolation still needs resources.
            Regex("[A-Za-z]{2}").containsMatchIn(text) && text !in BRANDS &&
                !Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(text) && !MEASUREMENT.matches(text)
        }
    }

    private fun tokenize(source: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < source.length) {
            val character = source[index]
            when {
                character.isWhitespace() -> index++
                source.startsWith("//", index) || source.startsWith("/*", index) -> index = commentEnd(source, index)
                character == '"' || character == '\'' -> {
                    val width = if (source.startsWith("\"\"\"", index)) 3 else 1
                    val end = delimitedEnd(source, index + width, Frame(if (character == '\'') -1 else width))
                    if (character == '"') tokens += Token(source.substring(index + width, (end - width).coerceAtLeast(index + width)), true)
                    index = end
                }
                Character.isJavaIdentifierStart(character) -> {
                    val start = index++
                    while (index < source.length && Character.isJavaIdentifierPart(source[index])) index++
                    tokens += Token(source.substring(start, index))
                }
                else -> { tokens += Token(character.toString()); index++ }
            }
        }
        return tokens
    }

    /** Explicit state stack handles quoted/interpolated strings without recursive regular expressions. */
    private fun delimitedEnd(source: String, start: Int, initial: Frame): Int {
        val frames = mutableListOf(initial)
        var index = start
        while (index < source.length && frames.isNotEmpty()) {
            val frame = frames.last()
            val character = source[index]
            if (frame.quote == 0) {
                when {
                    source.startsWith("//", index) || source.startsWith("/*", index) -> index = commentEnd(source, index)
                    character == '"' || character == '\'' -> {
                        val width = if (source.startsWith("\"\"\"", index)) 3 else 1
                        frames += Frame(if (character == '\'') -1 else width)
                        index += width
                    }
                    character == '{' -> { frame.braces++; index++ }
                    character == '}' -> { if (--frame.braces == 0) frames.removeAt(frames.lastIndex); index++ }
                    else -> index++
                }
            } else {
                when {
                    frame.quote != 3 && character == '\\' -> index = (index + 2).coerceAtMost(source.length)
                    frame.quote == 3 && source.startsWith("\"\"\"", index) -> { frames.removeAt(frames.lastIndex); index += 3 }
                    frame.quote == 1 && character == '"' || frame.quote == -1 && character == '\'' -> { frames.removeAt(frames.lastIndex); index++ }
                    frame.quote != -1 && source.startsWith("${'$'}{", index) -> { frames += Frame(0); index += 2 }
                    else -> index++
                }
            }
        }
        return index
    }

    private fun commentEnd(source: String, start: Int): Int {
        if (source.startsWith("//", start)) return source.indexOf('\n', start).takeIf { it >= 0 } ?: source.length
        var depth = 1
        var index = start + 2
        while (index < source.length && depth > 0) {
            when {
                source.startsWith("/*", index) -> { depth++; index += 2 }
                source.startsWith("*/", index) -> { depth--; index += 2 }
                else -> index++
            }
        }
        return index
    }

    private fun withoutInterpolation(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            when {
                value.startsWith("${'$'}{", index) -> index = delimitedEnd(value, index + 2, Frame(0))
                value[index] == '$' && value.getOrNull(index + 1)?.let(Character::isJavaIdentifierStart) == true -> {
                    index += 2
                    while (index < value.length && Character.isJavaIdentifierPart(value[index])) index++
                }
                else -> append(value[index++])
            }
        }
    }

    private fun documentedExemptions(): Map<Literal, String> = buildMap {
        fun allow(file: String, sink: String, text: String, reason: String) {
            put(Literal("app/src/main/java/dev/goodwy/rphone/view/screen/settings/$file", sink, text), reason)
        }
        // Retired screen: current blocked-number routes use BlockedNumbersScreen, not this legacy destination.
        listOf("Block List", "No numbers blocked", "Close", "Blocked Numbers", "Search…", "Enter number to block", "Done")
            .forEach { allow("SpamScreen.kt", "Text", it, "Retired SpamScreen implementation") }
        allow("SpamScreen.kt", "Title", "Spam", "Retired SpamScreen implementation")
        allow("SpamScreen.kt", "supporting", "${'$'}{blockedContactsList.size} number(s) blocked", "Retired SpamScreen implementation")
        allow("SpamScreen.kt", "Text", """Block \"${'$'}{searchQuery.trim()}\"""", "Retired SpamScreen implementation")
        listOf("Silence Unknown Callers", "Blocked Numbers", "Tap to see the Block list")
            .forEach { allow("SpamScreen.kt", "headline", it, "Retired SpamScreen implementation") }
        allow("SpamScreen.kt", "supporting", "Automatically decline calls from unknown numbers", "Retired SpamScreen implementation")
        // About retains this old theme dialog, but no action sets its showDialog state true.
        allow("About.kt", "Text", "Current theme colors", "Unreachable legacy theme palette dialog")
        allow("About.kt", "Text", "Close", "Unreachable legacy theme palette dialog")
        put(Literal("app/src/main/java/dev/goodwy/rphone/view/components/VideoLauncher.kt", "title", "Video Call"),
            "Unreachable legacy video launcher; no call site references this dialog")
        put(Literal("app/src/main/java/dev/goodwy/rphone/view/screen/CallActionGrid.kt", "text", "Ag"),
            "Font ascent/descent measurement sample, never a rendered UI label")
    }

    private fun repoRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .take(6).firstOrNull { File(it, "app/src/main/java").isDirectory } ?: error("Cannot find application sources")

    companion object {
        private val BRANDS = setOf("dot.dialer", "Google Play", "WhatsApp", "Telegram", "Signal", "Bluetooth", "Android Auto")
        private val MEASUREMENT = Regex("(?:[0-9.]+\\s*)?(?:ms|fps|Hz|dp|sp|px|KB|MB|GB|%)", RegexOption.IGNORE_CASE)
    }
}
