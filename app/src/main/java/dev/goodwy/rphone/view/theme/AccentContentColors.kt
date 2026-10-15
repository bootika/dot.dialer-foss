package dev.goodwy.rphone.view.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal val DotBlueStrong = Color(0xFF204DA9)
internal val DotRubySoft = Color(0xFFFFE1E5)
internal val DotRubyStrong = Color(0xFFA7293E)
internal val DotRubyInk = Color(0xFF240C11)

/** Content on neutral/light surfaces; brand fills and other accents remain intact. */
internal val ColorScheme.accentText: Color
    get() = when (primary) {
        DotBlue -> DotBlueStrong
        DotRuby -> DotRubyStrong
        else -> primary // Adaptive dark tones and unchanged Jade/Iris.
    }
