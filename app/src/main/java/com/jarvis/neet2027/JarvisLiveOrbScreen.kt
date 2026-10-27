package com.jarvis.neet2027

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Subtle High-End Tri-Chroma Accents (Emerald Green, Solar Amber-Yellow, Crimson Red)
private val AccentEmerald = Color(0xFF10B981)
private val AccentAmber = Color(0xFFF59E0B)
private val AccentCrimson = Color(0xFFF43F5E)
private val ObsidianBlack = Color(0xFF09090B)
private val CharcoalSlate = Color(0xFF18181B)

private data class OrbParticle3D(
    val theta: Float,
    val phi: Float,
    val shellLayer: Int,
    val baseRadiusFactor: Float,
    val dotSize: Float,
    val speedMultiplier: Float,
    val shadeAlpha: Float,
    val accentType: Int // 0 = Obsidian Black (76%), 1 = Emerald (8%), 2 = Solar Amber (9%), 3 = Crimson Red (7%)
)

@Composable
fun JarvisLiveParticleOrbPage(
    plan: NeetPlanRoot,
    masterPrompt: String,
    messages: MutableList<ChatMessageItem>,
    committedModelId: String,
    committedTemperature: Float,
    forceOffline: Boolean,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var orbMode by remember { mutableStateOf(VoiceOrbState.IDLE) }
    var liveSoundLevel by remember { mutableFloatStateOf(0.08f) }
    var liveTranscript by remember { mutableStateOf("") }
    var lastJarvisSpoken by remember {
        mutableStateOf(
            messages.lastOrNull { it.role == "jarvis" }?.visibleText
                ?: "At your service, sir. Tap the 3D black & tri-chroma particle core or the microphone button to speak live."
        )
    }
    var lastEmotion by remember { mutableStateOf("calm") }
    var textDraft by remember { mutableStateOf("") }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.UK
                engine?.setPitch(0.88f)
                engine?.setSpeechRate(1.02f)
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    LaunchedEffect(orbMode) {
        while (orbMode == VoiceOrbState.SPEAKING || orbMode == VoiceOrbState.THINKING) {
            liveSoundLevel = if (orbMode == VoiceOrbState.SPEAKING) {
                0.35f + Random.nextFloat() * 0.65f
            } else {
                0.20f + Random.nextFloat() * 0.25f
            }
            delay(65L)
        }
        if (orbMode == VoiceOrbState.IDLE) {
            liveSoundLevel = 0.08f
        }
    }

    fun speakAndSendQuery(queryText: String) {
        val clean = queryText.trim()
        if (clean.isEmpty()) return
        liveTranscript = clean
        orbMode = VoiceOrbState.THINKING
        scope.launch {
            val reply = JarvisBrainEngine.generateJarvisReply(
                context = context,
                plan = plan,
                masterPrompt = masterPrompt,
                modelId = committedModelId,
                temperature = committedTemperature,
                forceOffline = forceOffline,
                conversation = messages.toList(),
                userQuery = clean
            )
            messages.add(
                ChatMessageItem(
                    id = System.currentTimeMillis() - 1,
                    role = "user",
                    visibleText = clean,
                    emotionTag = "focused",
                    engineBadge = "LIVE VOICE"
                )
            )
            messages.add(reply)
            EncryptedKeyStoreVault.saveChatHistory(context, messages)
            lastJarvisSpoken = reply.visibleText
            lastEmotion = reply.emotionTag
            orbMode = VoiceOrbState.SPEAKING

            val tts = ttsEngine
            if (tts != null) {
                when (reply.emotionTag) {
                    "urgent", "focused" -> {
                        tts.setPitch(0.92f)
                        tts.setSpeechRate(1.08f)
                    }
                    "concerned" -> {
                        tts.setPitch(0.84f)
                        tts.setSpeechRate(0.95f)
                    }
                    else -> {
                        tts.setPitch(0.88f)
                        tts.setSpeechRate(1.02f)
                    }
                }
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        orbMode = VoiceOrbState.SPEAKING
                    }
                    override fun onDone(utteranceId: String?) {
                        orbMode = VoiceOrbState.IDLE
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        orbMode = VoiceOrbState.IDLE
                    }
                })
                val spokenShort = reply.visibleText.replace("*", "").take(360)
                tts.speak(spokenShort, TextToSpeech.QUEUE_FLUSH, null, "jarvis_live_utt")
            } else {
                delay(2800L)
                orbMode = VoiceOrbState.IDLE
            }
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                orbMode = VoiceOrbState.LISTENING
            }
            override fun onBeginningOfSpeech() {
                orbMode = VoiceOrbState.LISTENING
            }
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                liveSoundLevel = normalized
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                if (orbMode == VoiceOrbState.LISTENING) {
                    orbMode = VoiceOrbState.THINKING
                }
            }
            override fun onError(error: Int) {
                orbMode = VoiceOrbState.IDLE
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val heard = matches?.firstOrNull().orEmpty()
                if (heard.isNotBlank()) {
                    speakAndSendQuery(heard)
                } else {
                    orbMode = VoiceOrbState.IDLE
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (part.isNotBlank()) {
                    liveTranscript = part
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun triggerVoiceListening() {
        ttsEngine?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching {
            orbMode = VoiceOrbState.LISTENING
            speechRecognizer?.startListening(intent)
        }.onFailure {
            orbMode = VoiceOrbState.IDLE
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) triggerVoiceListening()
    }

    val stateAccentColor = when (orbMode) {
        VoiceOrbState.IDLE -> AccentEmerald
        VoiceOrbState.LISTENING -> AccentCrimson
        VoiceOrbState.THINKING -> AccentAmber
        VoiceOrbState.SPEAKING -> AccentAmber
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(690.dp)
            .shadow(20.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8FAFC),
                        Color(0xFFE2E8F0),
                        Color(0xFFCBD5E1)
                    )
                )
            )
            .border(
                width = 1.6.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        AccentEmerald.copy(alpha = 0.55f),
                        AccentAmber.copy(alpha = 0.55f),
                        AccentCrimson.copy(alpha = 0.55f),
                        Color(0xFF475569)
                    )
                ),
                shape = RoundedCornerShape(30.dp)
            )
    ) {
        BlackJarvis3DParticleCanvas(
            orbState = orbMode,
            soundLevel = liveSoundLevel,
            tilt = tilt,
            onTapOrb = {
                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (orbMode == VoiceOrbState.LISTENING) {
                    speechRecognizer?.stopListening()
                    orbMode = VoiceOrbState.IDLE
                } else if (hasMic) {
                    triggerVoiceListening()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Frosted Header with Tri-Colour Telemetry Dots (Emerald, Amber, Crimson)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(ObsidianBlack))
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentEmerald))
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentAmber))
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentCrimson))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "J.A.R.V.I.S. 3D BLACK PARTICLE MATRIX // TRI-CHROMA EDITION",
                        color = ObsidianBlack,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "State: ${orbMode.name} • Prosody: ${lastEmotion.uppercase()} • Tap the 3D Orb to speak",
                    color = Color(0xFF334155),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(ObsidianBlack)
                        .border(
                            width = 1.2.dp,
                            color = if (orbMode == VoiceOrbState.LISTENING) AccentCrimson else AccentEmerald,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) triggerVoiceListening()
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (orbMode == VoiceOrbState.LISTENING) AccentCrimson else AccentEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (orbMode == VoiceOrbState.LISTENING) "Listening..." else "Speak Live",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xD9FFFFFF))
                        .border(1.2.dp, AccentAmber, RoundedCornerShape(16.dp))
                        .clickable {
                            ttsEngine?.stop()
                            orbMode = VoiceOrbState.SPEAKING
                            ttsEngine?.speak(lastJarvisSpoken.take(320), TextToSpeech.QUEUE_FLUSH, null, "replay_utt")
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Replay Voice", color = ObsidianBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bottom Frosted Dock with subtle Emerald, Amber & Crimson Glass Rim
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (liveTranscript.isNotBlank()) {
                Text(
                    text = "YOU: \"$liveTranscript\"",
                    color = ObsidianBlack,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xE6FFFFFF))
                    .border(
                        width = 1.3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                ObsidianBlack,
                                stateAccentColor,
                                ObsidianBlack
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = lastJarvisSpoken,
                    color = ObsidianBlack,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(CharcoalSlate)
                    .border(
                        width = 1.3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                AccentEmerald.copy(alpha = 0.8f),
                                AccentAmber.copy(alpha = 0.8f),
                                AccentCrimson.copy(alpha = 0.8f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (textDraft.isEmpty()) {
                        Text(
                            text = "Tap the 3D Black & Tri-Chroma Orb to talk, or type to Jarvis...",
                            color = Color(0xFFA1A1AA),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = textDraft,
                        onValueChange = { textDraft = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentEmerald),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AccentEmerald, AccentAmber)
                            )
                        )
                        .clickable {
                            if (textDraft.isNotBlank()) {
                                val q = textDraft
                                textDraft = ""
                                speakAndSendQuery(q)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send", color = ObsidianBlack, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlackJarvis3DParticleCanvas(
    orbState: VoiceOrbState,
    soundLevel: Float,
    tilt: Offset,
    onTapOrb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val particles = remember {
        val rng = Random(2027)
        List(330) { idx ->
            val layer = when {
                idx < 75 -> 0
                idx < 215 -> 1
                else -> 2
            }
            val radiusFactor = when (layer) {
                0 -> 0.28f + rng.nextFloat() * 0.14f
                1 -> 0.56f + rng.nextFloat() * 0.20f
                else -> 0.84f + rng.nextFloat() * 0.22f
            }
            // ~76% pure Obsidian Black/Charcoal, ~24% split across Emerald Green, Solar Amber & Crimson Red
            val roll = rng.nextInt(100)
            val chroma = when {
                roll < 76 -> 0 // Obsidian Black
                roll < 84 -> 1 // Emerald Green (#10B981)
                roll < 93 -> 2 // Solar Amber-Yellow (#F59E0B)
                else -> 3      // Crimson Red (#F43F5E)
            }
            OrbParticle3D(
                theta = rng.nextFloat() * 2f * PI.toFloat(),
                phi = (rng.nextFloat() - 0.5f) * PI.toFloat(),
                shellLayer = layer,
                baseRadiusFactor = radiusFactor,
                dotSize = when (chroma) {
                    0 -> 1.8f + rng.nextFloat() * 2.2f
                    else -> 2.4f + rng.nextFloat() * 2.6f
                },
                speedMultiplier = when (layer) {
                    0 -> 1.6f
                    1 -> 1.0f
                    else -> -0.75f
                },
                shadeAlpha = 0.50f + rng.nextFloat() * 0.50f,
                accentType = chroma
            )
        }
    }

    val infinite = rememberInfiniteTransition(label = "black_jarvis_orb_3d")
    val masterSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "master_spin"
    )
    val secondarySpin by infinite.animateFloat(
        initialValue = (2.0 * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondary_spin"
    )

    val animatedAmplitude by animateFloatAsState(
        targetValue = when (orbState) {
            VoiceOrbState.IDLE -> 0.08f
            VoiceOrbState.LISTENING -> soundLevel.coerceAtLeast(0.28f)
            VoiceOrbState.THINKING -> 0.42f
            VoiceOrbState.SPEAKING -> soundLevel.coerceAtLeast(0.48f)
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "voice_amp"
    )

    Canvas(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTapOrb
        )
    ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f + tilt.x * 2.2f
        val cy = h * 0.44f + tilt.y * 2.2f
        val baseOrbRadius = min(w, h) * 0.27f * (1f + animatedAmplitude * 0.22f)

        // Living White & Grey Background with ultra-subtle Emerald, Amber & Crimson ambient aura
        val bgOrbEmerald = Offset(
            x = w * (0.24f + 0.08f * cos(masterSpin)),
            y = h * (0.30f + 0.06f * sin(masterSpin))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentEmerald.copy(alpha = 0.11f), Color(0xFFE2E8F0).copy(alpha = 0.5f), Color.Transparent),
                center = bgOrbEmerald,
                radius = w * 0.38f
            ),
            radius = w * 0.38f,
            center = bgOrbEmerald
        )
        val bgOrbAmber = Offset(
            x = w * (0.76f + 0.08f * sin(secondarySpin)),
            y = h * (0.36f + 0.06f * cos(secondarySpin))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentAmber.copy(alpha = 0.12f), Color(0xFFCBD5E1).copy(alpha = 0.5f), Color.Transparent),
                center = bgOrbAmber,
                radius = w * 0.38f
            ),
            radius = w * 0.38f,
            center = bgOrbAmber
        )
        val bgOrbCrimson = Offset(
            x = w * (0.50f + 0.07f * cos(secondarySpin)),
            y = h * (0.64f + 0.05f * sin(masterSpin))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentCrimson.copy(alpha = 0.09f), Color.Transparent),
                center = bgOrbCrimson,
                radius = w * 0.34f
            ),
            radius = w * 0.34f,
            center = bgOrbCrimson
        )

        // Concentric Dashed Acoustic Ripples (Slate Grey + subtle tri-colour outer ring)
        val rippleColors = listOf(Color(0xFF334155), AccentEmerald, AccentAmber, AccentCrimson)
        for (rIdx in 1..4) {
            val rippleRadius = baseOrbRadius * (1.08f + rIdx * 0.15f + animatedAmplitude * 0.12f * rIdx)
            val rColor = rippleColors[(rIdx - 1) % rippleColors.size]
            drawCircle(
                color = rColor.copy(alpha = (0.28f / rIdx).coerceIn(0.05f, 0.24f)),
                radius = rippleRadius,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1.3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), phase = masterSpin * 40f)
                )
            )
        }

        // 3D Shadow Halo under the Black Jarvis Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ObsidianBlack.copy(alpha = 0.30f + animatedAmplitude * 0.16f),
                    AccentAmber.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = baseOrbRadius * 1.38f
            ),
            radius = baseOrbRadius * 1.38f,
            center = Offset(cx, cy)
        )

        // Outer Segmented Mechanical HUD Arcs (Obsidian Black alternating with Emerald, Amber & Crimson ticks)
        val arcRectSize = Size(baseOrbRadius * 2.15f, baseOrbRadius * 2.15f)
        val arcTopLeft = Offset(cx - arcRectSize.width / 2f, cy - arcRectSize.height / 2f)
        for (i in 0 until 6) {
            val startDeg = (i * 60f) + (masterSpin * 180f / PI.toFloat()) * (if (i % 2 == 0) 0.7f else -0.5f)
            val segColor = when (i) {
                1 -> AccentEmerald.copy(alpha = 0.85f)
                3 -> AccentAmber.copy(alpha = 0.90f)
                5 -> AccentCrimson.copy(alpha = 0.85f)
                else -> ObsidianBlack.copy(alpha = 0.82f)
            }
            drawArc(
                color = segColor,
                startAngle = startDeg,
                sweepAngle = 32f + animatedAmplitude * 18f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRectSize,
                style = Stroke(width = (2.4f + (i % 2) * 1.4f).dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Inner Geodesic Rings (Obsidian + Solar Amber Hologram Filaments)
        val innerArcSize = Size(baseOrbRadius * 1.48f, baseOrbRadius * 1.48f)
        val innerTopLeft = Offset(cx - innerArcSize.width / 2f, cy - innerArcSize.height / 2f)
        for (i in 0 until 4) {
            val startDeg = (i * 90f) + (secondarySpin * 180f / PI.toFloat())
            val ringColor = if (i % 2 == 0) CharcoalSlate.copy(alpha = 0.85f) else AccentAmber.copy(alpha = 0.75f)
            drawArc(
                color = ringColor,
                startAngle = startDeg,
                sweepAngle = 54f,
                useCenter = false,
                topLeft = innerTopLeft,
                size = innerArcSize,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt)
            )
        }

        // Radial Neural Data Spokes (Obsidian with occasional Emerald/Amber/Crimson laser spokes)
        val spokeCount = 18
        for (s in 0 until spokeCount) {
            val angle = (s.toFloat() / spokeCount) * 2f * PI.toFloat() + masterSpin * 0.4f
            val wave = sin(angle * 4f + secondarySpin * 2f) * animatedAmplitude * 0.25f
            val innerR = baseOrbRadius * (0.22f + wave * 0.2f)
            val outerR = baseOrbRadius * (0.88f + wave)
            val p1 = Offset(cx + innerR * cos(angle), cy + innerR * sin(angle))
            val p2 = Offset(cx + outerR * cos(angle), cy + outerR * sin(angle))
            val spokeColor = when {
                s == 0 || s == 9 -> AccentAmber.copy(alpha = 0.65f)
                s == 4 || s == 13 -> AccentEmerald.copy(alpha = 0.60f)
                s == 7 || s == 16 -> AccentCrimson.copy(alpha = 0.60f)
                else -> ObsidianBlack.copy(alpha = 0.35f + animatedAmplitude * 0.30f)
            }
            drawLine(
                color = spokeColor,
                start = p1,
                end = p2,
                strokeWidth = 1.4.dp.toPx()
            )
        }

        // 3D Projected Particle Cloud (Black/Charcoal + Emerald, Amber & Crimson Sparks)
        var prevProjected: Offset? = null
        val rotY = masterSpin + (tilt.x * 0.04f)
        val rotX = sin(secondarySpin) * 0.45f + (tilt.y * 0.04f)

        particles.forEachIndexed { idx, pt ->
            val currentTheta = pt.theta + rotY * pt.speedMultiplier
            val currentPhi = pt.phi + sin(masterSpin + idx * 0.1f) * (0.08f + animatedAmplitude * 0.24f)

            val jitter = 1f + sin(secondarySpin * 3f + idx) * animatedAmplitude * 0.28f
            val r3D = baseOrbRadius * pt.baseRadiusFactor * jitter

            val x0 = r3D * cos(currentPhi) * cos(currentTheta)
            val y0 = r3D * sin(currentPhi)
            val z0 = r3D * cos(currentPhi) * sin(currentTheta)

            val y1 = y0 * cos(rotX) - z0 * sin(rotX)
            val z1 = y0 * sin(rotX) + z0 * cos(rotX)

            val depthNorm = ((z1 / (baseOrbRadius * 1.2f)) + 1f) * 0.5f
            val perspective = 0.55f + depthNorm * 0.70f

            val screenX = cx + x0 * perspective
            val screenY = cy + y1 * perspective
            val currentOffset = Offset(screenX, screenY)

            if (idx % 5 == 0 && prevProjected != null && depthNorm > 0.45f) {
                val dx = screenX - prevProjected!!.x
                val dy = screenY - prevProjected!!.y
                if (sqrt(dx * dx + dy * dy) < baseOrbRadius * 0.42f) {
                    val lineCol = when (pt.accentType) {
                        1 -> AccentEmerald
                        2 -> AccentAmber
                        3 -> AccentCrimson
                        else -> CharcoalSlate
                    }
                    drawLine(
                        color = lineCol.copy(alpha = (0.26f * depthNorm).coerceIn(0.06f, 0.40f)),
                        start = prevProjected!!,
                        end = currentOffset,
                        strokeWidth = 1.1.dp.toPx()
                    )
                }
            }
            if (depthNorm > 0.45f) {
                prevProjected = currentOffset
            }

            val particleColor = when (pt.accentType) {
                1 -> AccentEmerald
                2 -> AccentAmber
                3 -> AccentCrimson
                else -> when (pt.shellLayer) {
                    0 -> Color(0xFF000000)
                    1 -> ObsidianBlack
                    else -> Color(0xFF1E293B)
                }
            }
            val alpha = (pt.shadeAlpha * (0.32f + depthNorm * 0.68f)).coerceIn(0.18f, 1.0f)
            val radiusPx = (pt.dotSize * perspective * (1f + animatedAmplitude * 0.35f)).dp.toPx()

            // Subtle glow halo behind coloured accent particles
            if (pt.accentType != 0 && depthNorm > 0.5f) {
                drawCircle(
                    color = particleColor.copy(alpha = 0.25f),
                    radius = radiusPx * 2.1f,
                    center = currentOffset
                )
            }

            drawCircle(
                color = particleColor.copy(alpha = alpha),
                radius = radiusPx,
                center = currentOffset
            )
        }

        // Dense 3D Obsidian Singularity Core with Tri-Chroma (Emerald + Amber + Crimson) Precision Ring
        val coreRadius = baseOrbRadius * (0.21f + animatedAmplitude * 0.10f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF000000),
                    ObsidianBlack,
                    AccentAmber.copy(alpha = 0.45f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = coreRadius * 1.55f
            ),
            radius = coreRadius * 1.55f,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    AccentEmerald,
                    AccentAmber,
                    AccentCrimson,
                    Color.White,
                    AccentEmerald
                ),
                center = Offset(cx, cy)
            ),
            radius = coreRadius * 0.64f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.2.dp.toPx())
        )
    }
}
