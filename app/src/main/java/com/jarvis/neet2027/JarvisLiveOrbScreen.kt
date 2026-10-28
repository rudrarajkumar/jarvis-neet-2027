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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class OrbChromaMode(val label: String) {
    GREEN_AND_YELLOW("Green + Yellow"),
    SOLAR_YELLOW("Solar Yellow"),
    EMERALD_GREEN("Emerald Green")
}

// 1. Inner Core Tangled Great-Circle Wireframe Loops (matches the dense woven core in the video)
private data class CoreWireLoop3D(
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val radiusScale: Float,
    val harmonicFreq: Int,
    val phaseOffset: Float,
    val isYellowSeed: Boolean
)

// 2. Steeply Tilted Gyroscopic Saturn-like Particle Ring Particle
private data class GyroRingParticle3D(
    val ringIndex: Int, // 0 = Steeply tilted ring (~62 deg), 1 = Cross-equatorial ring (~-22 deg)
    val angleRad: Float,
    val radialSpread: Float,
    val zThickness: Float,
    val dotSize: Float,
    val harmonicSeed: Float,
    val isYellowSeed: Boolean
)

// 3. Outer Broken Tectonic Circuit-Trace Shell Segment
private data class TectonicCircuitSegment3D(
    val theta1: Float,
    val phi1: Float,
    val theta2: Float,
    val phi2: Float,
    val radiusFactor: Float,
    val dotSize: Float,
    val harmonicSeed: Float,
    val hasBranchStub: Boolean,
    val thetaStub: Float,
    val phiStub: Float,
    val isYellowSeed: Boolean
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
    var chromaMode by remember { mutableStateOf(OrbChromaMode.GREEN_AND_YELLOW) }
    var liveSoundLevel by remember { mutableFloatStateOf(0.08f) }
    var liveTranscript by remember { mutableStateOf("") }
    var lastJarvisSpoken by remember {
        mutableStateOf(
            messages.lastOrNull { it.role == "jarvis" }?.visibleText
                ?: "At your service, sir. Tap the 3D hologram orb or the microphone button to converse live, even in split-screen."
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

    // Dynamic multi-frequency acoustic pulsation when Jarvis speaks or thinks
    LaunchedEffect(orbMode) {
        while (orbMode == VoiceOrbState.SPEAKING || orbMode == VoiceOrbState.THINKING) {
            liveSoundLevel = if (orbMode == VoiceOrbState.SPEAKING) {
                0.38f + Random.nextFloat() * 0.62f
            } else {
                0.22f + Random.nextFloat() * 0.28f
            }
            delay(55L)
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
                val normalized = ((rmsdB + 2f) / 11f).coerceIn(0.08f, 1.0f)
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

    val primaryAccent = when (chromaMode) {
        OrbChromaMode.GREEN_AND_YELLOW -> Color(0xFF10B981)
        OrbChromaMode.SOLAR_YELLOW -> Color(0xFFFACC15)
        OrbChromaMode.EMERALD_GREEN -> Color(0xFF10B981)
    }
    val secondaryAccent = when (chromaMode) {
        OrbChromaMode.GREEN_AND_YELLOW -> Color(0xFFFACC15)
        OrbChromaMode.SOLAR_YELLOW -> Color(0xFFF59E0B)
        OrbChromaMode.EMERALD_GREEN -> Color(0xFF34D399)
    }

    // Split-Screen Adaptive Container: automatically scales height & layout for Xiaomi Pad 6 Split-Screen
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompactSplitScreen = maxWidth < 560.dp || maxHeight < 520.dp
        val containerHeight = if (isCompactSplitScreen) 520.dp else 680.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF060A09),
                            Color(0xFF020304),
                            Color(0xFF000000)
                        )
                    )
                )
                .border(
                    width = 1.4.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.75f),
                            secondaryAccent.copy(alpha = 0.75f),
                            Color(0xFF18181B)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            // 1. Exact 3D Hologram Orb from Video (Tangled Core + 2 Precessing Rings + Broken Circuit Shell)
            VideoHologram3DOrbCanvas(
                orbState = orbMode,
                chromaMode = chromaMode,
                soundLevel = liveSoundLevel,
                tilt = tilt,
                isCompact = isCompactSplitScreen,
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

            // 2. Top Adaptive Header Bar (Scrollable in Split-Screen so buttons never clip)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(primaryAccent))
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(secondaryAccent))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "J.A.R.V.I.S. 3D HOLOGRAM ORB // ${chromaMode.label.uppercase()}",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "State: ${orbMode.name} • Prosody: ${lastEmotion.uppercase()} • Sound Reactive",
                        color = secondaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1-Tap Colour Mode Toggle (Green+Yellow / Solar Yellow / Emerald Green)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xCC121417))
                            .border(1.dp, secondaryAccent, RoundedCornerShape(14.dp))
                            .clickable {
                                val modes = OrbChromaMode.values()
                                chromaMode = modes[(chromaMode.ordinal + 1) % modes.size]
                            }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = secondaryAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(chromaMode.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Live Mic Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryAccent.copy(alpha = 0.25f))
                            .border(1.2.dp, primaryAccent, RoundedCornerShape(14.dp))
                            .clickable {
                                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) triggerVoiceListening()
                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (orbMode == VoiceOrbState.LISTENING) "Listening..." else "Speak",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Replay Butler Voice Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xCC121417))
                            .border(1.dp, secondaryAccent.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                            .clickable {
                                ttsEngine?.stop()
                                orbMode = VoiceOrbState.SPEAKING
                                ttsEngine?.speak(lastJarvisSpoken.take(320), TextToSpeech.QUEUE_FLUSH, null, "replay_utt")
                            }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = secondaryAccent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Replay", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Bottom Adaptive Transcript & Floating Glass Input Dock
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (liveTranscript.isNotBlank()) {
                    Text(
                        text = "YOU: \"$liveTranscript\"",
                        color = secondaryAccent,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xD9090B0E))
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(listOf(primaryAccent, secondaryAccent)),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = lastJarvisSpoken,
                        color = Color(0xFFFAFAFA),
                        fontSize = if (isCompactSplitScreen) 12.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        maxLines = if (isCompactSplitScreen) 3 else 4
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xEE111318))
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(listOf(primaryAccent, secondaryAccent)),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = secondaryAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (textDraft.isEmpty()) {
                            Text(
                                text = "Tap the 3D Orb to talk, or type to Jarvis...",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        BasicTextField(
                            value = textDraft,
                            onValueChange = { textDraft = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(primaryAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(listOf(primaryAccent, secondaryAccent))
                            )
                            .clickable {
                                if (textDraft.isNotBlank()) {
                                    val q = textDraft
                                    textDraft = ""
                                    speakAndSendQuery(q)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoHologram3DOrbCanvas(
    orbState: VoiceOrbState,
    chromaMode: OrbChromaMode,
    soundLevel: Float,
    tilt: Offset,
    isCompact: Boolean,
    onTapOrb: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Pre-compute 28 tangled 3D wireframe great-circle loops for the dense inner sphere (matches video center)
    val coreLoops = remember {
        val rng = Random(101)
        List(28) { idx ->
            CoreWireLoop3D(
                rotX = rng.nextFloat() * PI.toFloat(),
                rotY = rng.nextFloat() * PI.toFloat(),
                rotZ = rng.nextFloat() * PI.toFloat(),
                radiusScale = 0.28f + (idx % 5) * 0.032f,
                harmonicFreq = 3 + (idx % 6),
                phaseOffset = rng.nextFloat() * 6.28f,
                isYellowSeed = idx % 2 == 0
            )
        }
    }

    // 2. Pre-compute 460 particles for the two steeply tilted gyroscopic Saturn-like rings in the video
    val gyroRingParticles = remember {
        val rng = Random(202)
        List(460) { idx ->
            val rIdx = if (idx < 250) 0 else 1
            GyroRingParticle3D(
                ringIndex = rIdx,
                angleRad = (idx.toFloat() / if (rIdx == 0) 250f else 210f) * (2f * PI.toFloat()) + rng.nextFloat() * 0.05f,
                radialSpread = (rng.nextFloat() - 0.5f) * 0.08f,
                zThickness = (rng.nextFloat() - 0.5f) * 0.04f,
                dotSize = 1.4f + rng.nextFloat() * 2.0f,
                harmonicSeed = rng.nextFloat() * 6.28f,
                isYellowSeed = (idx % 3 != 0)
            )
        }
    }

    // 3. Pre-compute 380 outer broken tectonic circuit-trace shell segments (with natural hemisphere gaps like the video)
    val outerShellSegments = remember {
        val rng = Random(303)
        val list = mutableListOf<TectonicCircuitSegment3D>()
        var attempts = 0
        while (list.size < 380 && attempts < 1400) {
            attempts++
            val theta = rng.nextFloat() * 2f * PI.toFloat()
            val phi = (rng.nextFloat() - 0.5f) * PI.toFloat()

            // Tectonic patch mask: creates the exact crescent/polar shell openings seen in the uploaded video
            val patchWave = sin(theta * 2f + 0.5f) * cos(phi * 2.2f) + sin(phi * 1.6f)
            if (patchWave > -0.22f) {
                // Orthogonal circuit-style step along theta or phi
                val stepTheta = if (rng.nextBoolean()) (0.045f + rng.nextFloat() * 0.06f) else 0f
                val stepPhi = if (stepTheta == 0f) (0.045f + rng.nextFloat() * 0.06f) else 0f
                val t2 = theta + stepTheta
                val p2 = (phi + stepPhi).coerceIn(-1.5f, 1.5f)

                val hasStub = rng.nextFloat() > 0.45f
                val tStub = t2 + (if (stepTheta == 0f) 0.04f else 0f)
                val pStub = p2 + (if (stepPhi == 0f) 0.04f else 0f)

                list.add(
                    TectonicCircuitSegment3D(
                        theta1 = theta,
                        phi1 = phi,
                        theta2 = t2,
                        phi2 = p2,
                        radiusFactor = 0.88f + (rng.nextInt(3) * 0.055f),
                        dotSize = 1.3f + rng.nextFloat() * 1.9f,
                        harmonicSeed = rng.nextFloat() * 6.28f,
                        hasBranchStub = hasStub,
                        thetaStub = tStub,
                        phiStub = pStub,
                        isYellowSeed = rng.nextBoolean()
                    )
                )
            }
        }
        list
    }

    val infinite = rememberInfiniteTransition(label = "video_hologram_orb")
    val spinPrimary by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_primary"
    )
    val spinPrecession by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_precession"
    )

    val animatedAmp by animateFloatAsState(
        targetValue = when (orbState) {
            VoiceOrbState.IDLE -> 0.08f
            VoiceOrbState.LISTENING -> soundLevel.coerceAtLeast(0.30f)
            VoiceOrbState.THINKING -> 0.44f
            VoiceOrbState.SPEAKING -> soundLevel.coerceAtLeast(0.52f)
        },
        animationSpec = spring(dampingRatio = 0.50f, stiffness = 400f),
        label = "orb_sound_amp"
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
        val cx = w * 0.5f + tilt.x * 2.0f
        val cy = h * (if (isCompact) 0.43f else 0.45f) + tilt.y * 2.0f
        val baseR = min(w, h) * (if (isCompact) 0.30f else 0.33f) * (1f + animatedAmp * 0.16f)

        val emeraldBright = Color(0xFF6EE7B7)
        val emeraldCore = Color(0xFF10B981)
        val emeraldDeep = Color(0xFF047857)

        val yellowBright = Color(0xFFFEF08A)
        val yellowCore = Color(0xFFFACC15)
        val yellowDeep = Color(0xFFD97706)

        fun pickColor(isYellowSeed: Boolean, depthNorm: Float, boost: Float = 1f): Color {
            val useYellow = when (chromaMode) {
                OrbChromaMode.SOLAR_YELLOW -> true
                OrbChromaMode.EMERALD_GREEN -> false
                OrbChromaMode.GREEN_AND_YELLOW -> isYellowSeed
            }
            val base = if (useYellow) {
                when {
                    depthNorm > 0.72f -> yellowBright
                    depthNorm > 0.42f -> yellowCore
                    else -> yellowDeep
                }
            } else {
                when {
                    depthNorm > 0.72f -> emeraldBright
                    depthNorm > 0.42f -> emeraldCore
                    else -> emeraldDeep
                }
            }
            val alpha = ((0.22f + depthNorm * 0.72f) * boost).coerceIn(0.10f, 1.0f)
            return base.copy(alpha = alpha)
        }

        // Helper 3D Euler rotation + perspective projection
        fun project3D(x: Float, y: Float, z: Float, rx: Float, ry: Float, rz: Float): Triple<Float, Float, Float> {
            // Rotate Z
            val x1 = x * cos(rz) - y * sin(rz)
            val y1 = x * sin(rz) + y * cos(rz)
            // Rotate X
            val y2 = y1 * cos(rx) - z * sin(rx)
            val z2 = y1 * sin(rx) + z * cos(rx)
            // Rotate Y
            val x3 = x1 * cos(ry) + z2 * sin(ry)
            val z3 = -x1 * sin(ry) + z2 * cos(ry)

            val depthNorm = ((z3 / (baseR * 1.15f)) + 1f) * 0.5f
            val perspective = 0.62f + depthNorm.coerceIn(0f, 1.2f) * 0.56f
            return Triple(cx + x3 * perspective, cy + y2 * perspective, depthNorm.coerceIn(0f, 1f))
        }

        // Subtle atmospheric hologram glow behind the sphere on pitch-black space
        val glowHue = if (chromaMode == OrbChromaMode.SOLAR_YELLOW) yellowCore else emeraldCore
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowHue.copy(alpha = 0.20f + animatedAmp * 0.18f),
                    yellowCore.copy(alpha = 0.08f + animatedAmp * 0.08f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = baseR * 1.45f
            ),
            radius = baseR * 1.45f,
            center = Offset(cx, cy)
        )

        val globalRx = sin(spinPrecession) * 0.32f + (tilt.y * 0.03f)
        val globalRy = spinPrimary + (tilt.x * 0.03f)

        // ====================================================================
        // LAYER 1: DENSE TANGLED WIREFRAME INNER CORE SPHERE (Video Center)
        // ====================================================================
        val coreSteps = 28
        coreLoops.forEach { loop ->
            var prevPt: Offset? = null
            val loopRx = loop.rotX + globalRx * 1.3f
            val loopRy = loop.rotY + globalRy * 1.4f
            val loopRz = loop.rotZ + spinPrecession * 0.6f

            for (step in 0..coreSteps) {
                val u = (step.toFloat() / coreSteps) * (2f * PI.toFloat())
                // Sound-reactive radial ripple along each core filament
                val acousticWave = 1f + sin(u * loop.harmonicFreq + spinPrecession * 4f + loop.phaseOffset) * animatedAmp * 0.34f
                val rCore = baseR * loop.radiusScale * acousticWave

                val lx = rCore * cos(u)
                val ly = rCore * sin(u)
                val (sx, sy, depth) = project3D(lx, ly, 0f, loopRx, loopRy, loopRz)
                val currPt = Offset(sx, sy)

                if (prevPt != null) {
                    drawLine(
                        color = pickColor(loop.isYellowSeed, depth, boost = 0.78f + animatedAmp * 0.22f),
                        start = prevPt!!,
                        end = currPt,
                        strokeWidth = (1.1f + depth * 0.9f).dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                if (step % 4 == 0) {
                    drawCircle(
                        color = pickColor(!loop.isYellowSeed, depth, boost = 0.95f),
                        radius = (1.2f + animatedAmp * 1.6f).dp.toPx() * (0.6f + depth * 0.5f),
                        center = currPt
                    )
                }
                prevPt = currPt
            }
        }

        // ====================================================================
        // LAYER 2: TWO STEEPLY TILTED GYROSCOPIC PARTICLE RINGS (Video Rings)
        // ====================================================================
        var prevRing0: Offset? = null
        var prevRing1: Offset? = null

        gyroRingParticles.forEachIndexed { idx, gp ->
            val isSteepRing = gp.ringIndex == 0
            // Ring 0 tilts at ~62 degrees and precesses; Ring 1 tilts at ~-20 degrees and counter-precesses
            val ringTiltX = if (isSteepRing) {
                1.08f + 0.22f * sin(spinPrecession)
            } else {
                -0.36f + 0.18f * cos(spinPrecession * 0.8f)
            }
            val ringTiltZ = if (isSteepRing) {
                0.48f * cos(spinPrimary)
            } else {
                -0.32f * sin(spinPrimary)
            }
            val ringSpinY = if (isSteepRing) spinPrimary * 1.5f else -spinPrimary * 1.2f

            val soundJitter = sin(gp.angleRad * 8f + spinPrecession * 5f + gp.harmonicSeed) * animatedAmp * 0.26f
            val rRing = baseR * (if (isSteepRing) 0.62f else 0.70f) * (1f + gp.radialSpread + soundJitter * 0.5f)
            val angle = gp.angleRad + ringSpinY

            val rx0 = rRing * cos(angle)
            val ry0 = rRing * sin(angle)
            val rz0 = baseR * (gp.zThickness + soundJitter * 0.12f)

            val (sx, sy, depth) = project3D(rx0, ry0, rz0, ringTiltX + globalRx * 0.5f, globalRy * 0.4f, ringTiltZ)
            val pt = Offset(sx, sy)
            val col = pickColor(gp.isYellowSeed, depth, boost = 0.95f)

            // Connect consecutive ring particles into a glowing braided particle belt
            if (isSteepRing) {
                if (prevRing0 != null && idx % 2 == 0) {
                    drawLine(
                        color = col.copy(alpha = (col.alpha * 0.55f).coerceIn(0.08f, 0.75f)),
                        start = prevRing0!!,
                        end = pt,
                        strokeWidth = 1.1.dp.toPx()
                    )
                }
                prevRing0 = pt
            } else {
                if (prevRing1 != null && idx % 2 == 0) {
                    drawLine(
                        color = col.copy(alpha = (col.alpha * 0.55f).coerceIn(0.08f, 0.75f)),
                        start = prevRing1!!,
                        end = pt,
                        strokeWidth = 1.1.dp.toPx()
                    )
                }
                prevRing1 = pt
            }

            val dotR = (gp.dotSize * (0.65f + depth * 0.65f) * (1f + animatedAmp * 0.45f)).dp.toPx()
            drawCircle(
                color = col,
                radius = dotR,
                center = pt
            )
        }

        // ====================================================================
        // LAYER 3: OUTER BROKEN TECTONIC CIRCUIT-TRACE SHELL (Video Crust)
        // ====================================================================
        val shellRx = globalRx * 0.85f
        val shellRy = -globalRy * 0.75f
        val shellRz = sin(spinPrimary * 0.5f) * 0.25f

        outerShellSegments.forEach { seg ->
            // Each outer shell particle & circuit trace reacts to voice/sound amplitude
            val pulse = 1f + sin(seg.harmonicSeed + spinPrecession * 6f) * animatedAmp * 0.28f
            val rShell = baseR * seg.radiusFactor * pulse

            val x1 = rShell * cos(seg.phi1) * cos(seg.theta1)
            val y1 = rShell * sin(seg.phi1)
            val z1 = rShell * cos(seg.phi1) * sin(seg.theta1)

            val x2 = rShell * cos(seg.phi2) * cos(seg.theta2)
            val y2 = rShell * sin(seg.phi2)
            val z2 = rShell * cos(seg.phi2) * sin(seg.theta2)

            val (sx1, sy1, depth1) = project3D(x1, y1, z1, shellRx, shellRy, shellRz)
            val (sx2, sy2, _) = project3D(x2, y2, z2, shellRx, shellRy, shellRz)

            val p1 = Offset(sx1, sy1)
            val p2 = Offset(sx2, sy2)
            val traceColor = pickColor(seg.isYellowSeed, depth1, boost = 0.90f)

            // Draw circuit-trace segment on the spherical crust
            drawLine(
                color = traceColor,
                start = p1,
                end = p2,
                strokeWidth = (1.1f + depth1 * 1.0f).dp.toPx(),
                cap = StrokeCap.Square
            )

            // Optional 90-degree circuit branch stub (gives the intricate sci-fi glyph texture in the video)
            if (seg.hasBranchStub) {
                val x3 = rShell * cos(seg.phiStub) * cos(seg.thetaStub)
                val y3 = rShell * sin(seg.phiStub)
                val z3 = rShell * cos(seg.phiStub) * sin(seg.thetaStub)
                val (sx3, sy3, _) = project3D(x3, y3, z3, shellRx, shellRy, shellRz)
                drawLine(
                    color = traceColor.copy(alpha = (traceColor.alpha * 0.75f).coerceIn(0.1f, 0.9f)),
                    start = p2,
                    end = Offset(sx3, sy3),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Glowing node particle at circuit vertex
            val nodeRadius = (seg.dotSize * (0.60f + depth1 * 0.65f) * (1f + animatedAmp * 0.42f)).dp.toPx()
            if (depth1 > 0.68f && animatedAmp > 0.20f) {
                drawCircle(
                    color = traceColor.copy(alpha = 0.26f),
                    radius = nodeRadius * 2.2f,
                    center = p1
                )
            }
            drawCircle(
                color = traceColor,
                radius = nodeRadius,
                center = p1
            )
        }
    }
}
