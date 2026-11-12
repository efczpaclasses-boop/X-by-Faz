package com.faz.xbyfaz.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

object XColors {
    val Bg = androidx.compose.ui.graphics.Color(0xFF0B0D10)
    val Surface1 = androidx.compose.ui.graphics.Color(0xFF14171C)
    val Surface2 = androidx.compose.ui.graphics.Color(0xFF1C2027)
    val Surface3 = androidx.compose.ui.graphics.Color(0xFF262B33)
    val Hairline = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)
    val Text = androidx.compose.ui.graphics.Color(0xFFF5F7FA)
    val Text2 = androidx.compose.ui.graphics.Color(0xFFA9B2BE)
    val Text3 = androidx.compose.ui.graphics.Color(0xFF6E7784)
    val Accent = androidx.compose.ui.graphics.Color(0xFF4DA3FF)
    val Accent2 = androidx.compose.ui.graphics.Color(0xFF8CC4FF)
    val AccentTint = androidx.compose.ui.graphics.Color(0x294DA3FF)
    val White = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val Black = androidx.compose.ui.graphics.Color(0xFF0B0D10)
}

@Composable
fun ProvideXTheme(textScale: Float = 1f, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    // Keep the display's real density so text is rasterized 1:1 on the TV panel.
    // Changing density here used to scale the whole window and blur every glyph.
    CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * textScale)) {
        TvMaterialTheme(content = content)
    }
}
