package com.jarvis.neet2027.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.neet2027.ui.theme.GlassLabController
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class VoiceOrbState {
    IDLE, LISTENING, THINKING, SPEAKING
}

@Composable
fun rememberGyroscopeTilt(strength: Float): Offset {
    val context = LocalContext.current
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(strength) {
        if (strength <= 0.01f) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val values = event?.values ?: return
                    if (values.size >= 2) {
                        val rawX = (values[1].coerceIn(-3f, 3f)) * 4f * strength
                        val rawY = (values[0].coerceIn(-3f, 3f)) * 4f * strength
                        tiltX = tiltX * 0.85f + rawX * 0.15f
                        tiltY = tiltY * 0.85f + rawY * 0.15f
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (sensor != null) {
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }
    return Offset(tiltX, tiltY)
}

@Composable
fun LivingAuroraBackground(
    lab: GlassLabController,
    orbState: VoiceOrbState = VoiceOrbState.IDLE,
    tilt: Offset = Offset.Zero,
    modifier: Modifier = Modifier
) {
    val palette = lab.currentPalette
    val infinite = rememberInfiniteTransition(label = "aurora_bg")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (lab.performance144HzMode) 18000 else 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase"
    )

    val voiceBoost = when (orbState) {
        VoiceOrbState.IDLE -> 1.0f
        VoiceOrbState.LISTENING -> 1.35f
        VoiceOrbState.THINKING -> 1.50f
        VoiceOrbState.SPEAKING -> 1.65f
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Deep spatial Apple Pro background gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.deepCanvasTop, palette.deepCanvasBottom)
            )
        )

        val glowAlpha = (lab.auroraGlowIntensity * 0.42f * voiceBoost).coerceIn(0.05f, 0.78f)
        val richness = lab.colorRichness.coerceIn(0.2f, 1f)

        // Multi-colour Siri / visionOS floating liquid light orbs
        val orb1Center = Offset(
            x = w * (0.22f + 0.10f * cos(phase)) + tilt.x * 3f,
            y = h * (0.28f + 0.08f * sin(phase)) + tilt.y * 3f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.orbPrimary.copy(alpha = glowAlpha), Color.Transparent),
                center = orb1Center,
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = orb1Center
        )

        val orb2Center = Offset(
            x = w * (0.78f + 0.09f * sin(phase * 0.8f)) - tilt.x * 2.5f,
            y = h * (0.32f + 0.10f * cos(phase * 0.8f)) - tilt.y * 2.5f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.orbSecondary.copy(alpha = glowAlpha * richness), Color.Transparent),
                center = orb2Center,
                radius = w * 0.42f
            ),
            radius = w * 0.42f,
            center = orb2Center
        )

        val orb3Center = Offset(
            x = w * (0.52f + 0.12f * cos(phase + 2.1f)) + tilt.x * 2f,
            y = h * (0.78f + 0.07f * sin(phase + 2.1f)) + tilt.y * 2f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.orbTertiary.copy(alpha = glowAlpha * richness * 0.9f), Color.Transparent),
                center = orb3Center,
                radius = w * 0.46f
            ),
            radius = w * 0.46f,
            center = orb3Center
        )

        // Subtle spatial dot-matrix grid for sci-fi HUD precision
        if (!lab.performance144HzMode) {
            val step = 64.dp.toPx()
            val dotColor = palette.glassHighlight.copy(alpha = 0.07f)
            var x = step
            while (x < w) {
                var y = step
                while (y < h) {
                    drawCircle(
                        color = dotColor,
                        radius = 1.3f,
                        center = Offset(x + tilt.x * 0.5f, y + tilt.y * 0.5f)
                    )
                    y += step
                }
                x += step
            }
        }
    }
}

@Composable
fun LiquidGlassCard(
    lab: GlassLabController,
    modifier: Modifier = Modifier,
    accentTint: Color? = null,
    tilt: Offset = Offset.Zero,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = lab.currentPalette
    val cornerRadius = lab.cornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    val effectiveAlpha = if (lab.reduceTransparency) 0.96f else lab.surfaceAlpha.coerceIn(0.35f, 0.95f)
    val baseSurface = palette.glassSurface.copy(alpha = effectiveAlpha)
    val primaryRim = accentTint ?: palette.accentPrimary

    val rimBrush = Brush.linearGradient(
        colors = if (lab.highContrastMode) {
            listOf(Color.White, primaryRim, Color.White)
        } else {
            listOf(
                Color.White.copy(alpha = (0.48f * lab.specularGloss).coerceIn(0.15f, 0.85f)),
                primaryRim.copy(alpha = 0.55f),
                palette.accentSecondary.copy(alpha = 0.45f),
                palette.accentTertiary.copy(alpha = 0.35f)
            )
        }
    )

    Box(
        modifier = modifier
            .offset { IntOffset(tilt.x.roundToInt(), tilt.y.roundToInt()) }
            .shadow(
                elevation = (14f * lab.bevelDepth).dp,
                shape = shape,
                ambientColor = primaryRim.copy(alpha = 0.35f),
                spotColor = palette.accentSecondary.copy(alpha = 0.40f)
            )
            .clip(shape)
            .background(baseSurface)
            .drawWithContent {
                // Top curved Apple-style liquid glass specular sheen
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f * lab.specularGloss),
                            primaryRim.copy(alpha = 0.06f * lab.auroraGlowIntensity),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.55f
                    )
                )
                drawContent()
                // Inner 3D bevel highlight along top edge
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f * lab.bevelDepth),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.3f
                    ),
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            .border(width = 1.2.dp, brush = rimBrush, shape = shape)
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

@Composable
fun LiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    lab: GlassLabController,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color? = null,
    isActive: Boolean = false
) {
    val view = LocalView.current
    val palette = lab.currentPalette
    val activeHue = accentColor ?: if (isActive) JarvisSpatialPalettes.StatusSuccess else palette.accentPrimary
    var isPressed by remember { mutableStateOf(false) }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btn_scale"
    )
    val bloomAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.48f else 0.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "btn_bloom"
    )

    val pillShape = RoundedCornerShape((lab.cornerRadiusDp * 0.72f).dp)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 4.dp else 10.dp,
                shape = pillShape,
                ambientColor = activeHue.copy(alpha = 0.45f),
                spotColor = activeHue.copy(alpha = 0.55f)
            )
            .clip(pillShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        activeHue.copy(alpha = if (isActive) 0.36f else 0.24f),
                        palette.accentSecondary.copy(alpha = 0.18f),
                        palette.glassSurface.copy(alpha = 0.75f)
                    )
                )
            )
            .drawWithContent {
                // Top curved specular reflection
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f * lab.specularGloss),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.55f
                    )
                )
                // Fingertip light bloom on press
                if (bloomAlpha > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = bloomAlpha),
                                activeHue.copy(alpha = bloomAlpha * 0.7f),
                                Color.Transparent
                            ),
                            center = if (tapOffset == Offset.Zero) center else tapOffset,
                            radius = size.maxDimension * 0.75f
                        ),
                        radius = size.maxDimension * 0.75f,
                        center = if (tapOffset == Offset.Zero) center else tapOffset
                    )
                }
                drawContent()
            }
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f * lab.specularGloss),
                        activeHue.copy(alpha = 0.85f),
                        palette.accentSecondary.copy(alpha = 0.55f)
                    )
                ),
                shape = pillShape
            )
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { offset ->
                        tapOffset = offset
                        isPressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun LiquidGlassPillInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    lab: GlassLabController,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    val palette = lab.currentPalette
    val shape = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .shadow(12.dp, shape, spotColor = palette.accentPrimary.copy(alpha = 0.35f))
            .clip(shape)
            .background(palette.glassSurface.copy(alpha = 0.82f))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        palette.accentPrimary.copy(alpha = 0.65f),
                        palette.accentSecondary.copy(alpha = 0.55f)
                    )
                ),
                shape = shape
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = palette.accentPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = palette.textSecondary.copy(alpha = 0.65f),
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                cursorBrush = SolidColor(palette.accentPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
fun ArcReactorVoiceOrb(
    state: VoiceOrbState,
    lab: GlassLabController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 84.dp
) {
    val palette = lab.currentPalette
    val infinite = rememberInfiniteTransition(label = "siri_orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = when (state) {
            VoiceOrbState.IDLE -> 1.04f
            VoiceOrbState.LISTENING -> 1.18f
            VoiceOrbState.THINKING -> 1.12f
            VoiceOrbState.SPEAKING -> 1.24f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    VoiceOrbState.IDLE -> 2200
                    VoiceOrbState.LISTENING -> 750
                    VoiceOrbState.THINKING -> 950
                    VoiceOrbState.SPEAKING -> 520
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val spinAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_spin"
    )

    Box(
        modifier = modifier
            .size(sizeDp)
            .scale(pulse)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            // Outer iridescent Siri / Arc-Reactor aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.orbPrimary.copy(alpha = 0.75f),
                        palette.orbSecondary.copy(alpha = 0.55f),
                        palette.orbTertiary.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                radius = r
            )
            // Orbiting energy lobe
            val lobeOffset = Offset(
                x = center.x + r * 0.25f * cos(spinAngle),
                y = center.y + r * 0.25f * sin(spinAngle)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.9f), palette.accentPrimary, Color.Transparent),
                    center = lobeOffset,
                    radius = r * 0.65f
                ),
                radius = r * 0.65f
            )
            // Glass specular rim
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), palette.accentPrimary, palette.accentSecondary)
                ),
                radius = r * 0.86f,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
