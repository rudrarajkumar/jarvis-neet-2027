package com.jarvis.neet2027.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class JarvisPalettePreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val deepCanvasTop: Color,
    val deepCanvasBottom: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val glassSurface: Color,
    val glassHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

object JarvisSpatialPalettes {
    val SiriAuroraPro = JarvisPalettePreset(
        id = "SIRI_AURORA_PRO",
        name = "iOS 18 Siri Aurora Pro",
        subtitle = "Apple Intelligence Cyan + Sapphire + Violet + Rose Glass",
        deepCanvasTop = Color(0xFF070B19),
        deepCanvasBottom = Color(0xFF03050C),
        orbPrimary = Color(0xFF00E5FF),
        orbSecondary = Color(0xFF8B5CF6),
        orbTertiary = Color(0xFFEC4899),
        accentPrimary = Color(0xFF38BDF8),
        accentSecondary = Color(0xFFA855F7),
        accentTertiary = Color(0xFFF43F5E),
        glassSurface = Color(0xAA0F172A),
        glassHighlight = Color(0x66FFFFFF),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFFBAE6FD)
    )

    val VisionOsTitanium = JarvisPalettePreset(
        id = "VISION_OS_TITANIUM",
        name = "Apple visionOS Spatial Glass",
        subtitle = "Frosted Pearlescent Titanium + Warm Indigo & Sky Glow",
        deepCanvasTop = Color(0xFF0F1322),
        deepCanvasBottom = Color(0xFF060811),
        orbPrimary = Color(0xFF60A5FA),
        orbSecondary = Color(0xFFC084FC),
        orbTertiary = Color(0xFF34D399),
        accentPrimary = Color(0xFF93C5FD),
        accentSecondary = Color(0xFFD8B4FE),
        accentTertiary = Color(0xFF6EE7B7),
        glassSurface = Color(0xB31E293B),
        glassHighlight = Color(0x80FFFFFF),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFCBD5E1)
    )

    val CyberpunkNebula = JarvisPalettePreset(
        id = "CYBERPUNK_NEBULA",
        name = "Stark Holo-Nebula",
        subtitle = "Arc-Reactor Cyan + Sunset Gold + Neon Violet",
        deepCanvasTop = Color(0xFF090D1E),
        deepCanvasBottom = Color(0xFF04050A),
        orbPrimary = Color(0xFF06B6D4),
        orbSecondary = Color(0xFFF59E0B),
        orbTertiary = Color(0xFF8B5CF6),
        accentPrimary = Color(0xFF22D3EE),
        accentSecondary = Color(0xFFFBBF24),
        accentTertiary = Color(0xFFC084FC),
        glassSurface = Color(0xAA111827),
        glassHighlight = Color(0x66E0F2FE),
        textPrimary = Color(0xFFF9FAFB),
        textSecondary = Color(0xFFA5F3FC)
    )

    val EmeraldMatrixPro = JarvisPalettePreset(
        id = "EMERALD_PRO",
        name = "Emerald Mint & Jade Studio",
        subtitle = "Refined Emerald #10B981 + Cyber Cyan + Deep Jade",
        deepCanvasTop = Color(0xFF061A17),
        deepCanvasBottom = Color(0xFF020908),
        orbPrimary = Color(0xFF10B981),
        orbSecondary = Color(0xFF06B6D4),
        orbTertiary = Color(0xFF3B82F6),
        accentPrimary = Color(0xFF34D399),
        accentSecondary = Color(0xFF22D3EE),
        accentTertiary = Color(0xFF60A5FA),
        glassSurface = Color(0xAA08221E),
        glassHighlight = Color(0x66A7F3D0),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFF6EE7B7)
    )

    val allPresets = listOf(SiriAuroraPro, VisionOsTitanium, CyberpunkNebula, EmeraldMatrixPro)

    // Semantic Status Tokens (Always distinct + paired with icon & label)
    val StatusSuccess = Color(0xFF10B981)
    val StatusWarning = Color(0xFFF59E0B)
    val StatusError = Color(0xFFF43F5E)
    val StatusInfo = Color(0xFF38BDF8)
}

class GlassLabController {
    var activePresetIndex by mutableIntStateOf(0)
    var blurRadiusDp by mutableFloatStateOf(24f)
    var surfaceAlpha by mutableFloatStateOf(0.68f)
    var specularGloss by mutableFloatStateOf(0.55f)
    var auroraGlowIntensity by mutableFloatStateOf(0.75f)
    var bevelDepth by mutableFloatStateOf(0.60f)
    var cornerRadiusDp by mutableFloatStateOf(24f)
    var gyroParallaxStrength by mutableFloatStateOf(0.70f)
    var colorRichness by mutableFloatStateOf(0.85f)

    var performance144HzMode by mutableStateOf(false)
    var reduceTransparency by mutableStateOf(false)
    var highContrastMode by mutableStateOf(false)
    var skipBootAnimation by mutableStateOf(false)

    val currentPalette: JarvisPalettePreset
        get() = JarvisSpatialPalettes.allPresets[activePresetIndex.coerceIn(0, JarvisSpatialPalettes.allPresets.lastIndex)]

    fun applyAppleProDefault() {
        activePresetIndex = 0
        blurRadiusDp = 24f
        surfaceAlpha = 0.68f
        specularGloss = 0.55f
        auroraGlowIntensity = 0.75f
        bevelDepth = 0.60f
        cornerRadiusDp = 24f
        gyroParallaxStrength = 0.70f
        colorRichness = 0.85f
        performance144HzMode = false
        reduceTransparency = false
        highContrastMode = false
    }

    fun apply144HzBatterySaver() {
        blurRadiusDp = 8f
        auroraGlowIntensity = 0.35f
        gyroParallaxStrength = 0.20f
        performance144HzMode = true
    }
}
