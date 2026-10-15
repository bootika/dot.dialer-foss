package io.github.bootika.dotdialer.feature.diagnostics

internal data class DiagnosticReportPresentation(val fullText: String, val blocks: List<String>)

/** Preserve export bytes; bound each text layout and prefer complete source lines. */
internal fun prepareDiagnosticReport(text: String): DiagnosticReportPresentation {
    val blocks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + 1024, text.length)
        var lastNewline = -1
        var lineCount = 0
        for (index in start until end) {
            if (text[index] == '\n') {
                lastNewline = index
                if (++lineCount == 12) { end = index + 1; break }
            }
        }
        if (end < text.length && lastNewline >= start) end = lastNewline + 1
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        blocks += text.substring(start, end)
        start = end
    }
    return DiagnosticReportPresentation(text, blocks.ifEmpty { listOf("") })
}
