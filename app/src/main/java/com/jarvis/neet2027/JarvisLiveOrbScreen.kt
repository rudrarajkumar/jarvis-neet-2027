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

private data class OrbParticle3D(
    val theta: Float,
    val phi: Float,
    val shellLayer: Int,
    val baseRadiusFactor: Float,
    val dotSize: Float,
    val speedMultiplier: Float,
    val shadeAlpha: Float
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
                ?: "At your service, sir. Tap the 3D black particle core or the microphone button to speak live."
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
                        Color(0xFFF1F5F9),
                        Color(0xFFE2E8F0),
                        Color(0xFFCBD5E1)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFF94A3B8), Color(0xFF475569))
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF09090B))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "J.A.R.V.I.S. 3D BLACK PARTICLE MATRIX // LIVE VOICE CORE",
                        color = Color(0xFF09090B),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "State: ${orbMode.name} • Prosody: ${lastEmotion.uppercase()} • Tap the 3D Black Orb to speak",
                    color = Color(0xFF334155),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE609090B))
                        .clickable {
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) triggerVoiceListening()
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                        .background(Color(0xCCFFFFFF))
                        .border(1.dp, Color(0xFF09090B), RoundedCornerShape(16.dp))
                        .clickable {
                            ttsEngine?.stop()
                            orbMode = VoiceOrbState.SPEAKING
                            ttsEngine?.speak(lastJarvisSpoken.take(320), TextToSpeech.QUEUE_FLUSH, null, "replay_utt")
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Replay Voice", color = Color(0xFF09090B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

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
                    color = Color(0xFF475569),
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
                    .background(Color(0xD9FFFFFF))
                    .border(1.2.dp, Color(0xFF18181B), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = lastJarvisSpoken,
                    color = Color(0xFF09090B),
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
                    .background(Color(0xF218181B))
                    .border(1.2.dp, Color(0xFFA1A1AA), RoundedCornerShape(28.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (textDraft.isEmpty()) {
                        Text(
                            text = "Speak live by tapping the 3D Black Orb, or type to Jarvis here...",
                            color = Color(0xFFA1A1AA),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = textDraft,
                        onValueChange = { textDraft = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
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
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send", color = Color(0xFF09090B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        List(320) { idx ->
            val layer = when {
                idx < 70 -> 0
                idx < 210 -> 1
                else -> 2
            }
            val radiusFactor = when (layer) {
                0 -> 0.28f + rng.nextFloat() * 0.14f
                1 -> 0.56f + rng.nextFloat() * 0.20f
                else -> 0.84f + rng.nextFloat() * 0.22f
            }
            OrbParticle3D(
                theta = rng.nextFloat() * 2f * PI.toFloat(),
                phi = (rng.nextFloat() - 0.5f) * PI.toFloat(),
                shellLayer = layer,
                baseRadiusFactor = radiusFactor,
                dotSize = when (layer) {
                    0 -> 2.4f + rng.nextFloat() * 2.5f
                    1 -> 1.8f + rng.nextFloat() * 2.2f
                    else -> 1.4f + rng.nextFloat() * 2.6f
                },
                speedMultiplier = when (layer) {
                    0 -> 1.6f
                    1 -> 1.0f
                    else -> -0.75f
                },
                shadeAlpha = 0.45f + rng.nextFloat() * 0.55f
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

        val bgOrb1 = Offset(
            x = w * (0.25f + 0.08f * cos(masterSpin)),
            y = h * (0.30f + 0.06f * sin(masterSpin))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE2E8F0), Color(0x00E2E8F0)),
                center = bgOrb1,
                radius = w * 0.40f
            ),
            radius = w * 0.40f,
            center = bgOrb1
        )
        val bgOrb2 = Offset(
            x = w * (0.75f + 0.08f * sin(secondarySpin)),
            y = h * (0.60f + 0.06f * cos(secondarySpin))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFCBD5E1).copy(alpha = 0.65f), Color.Transparent),
                center = bgOrb2,
                radius = w * 0.42f
            ),
            radius = w * 0.42f,
            center = bgOrb2
        )

        val rippleCount = 4
        for (rIdx in 1..rippleCount) {
            val rippleRadius = baseOrbRadius * (1.08f + rIdx * 0.16f + animatedAmplitude * 0.12f * rIdx)
            drawCircle(
                color = Color(0xFF475569).copy(alpha = (0.20f / rIdx).coerceIn(0.03f, 0.18f)),
                radius = rippleRadius,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), phase = masterSpin * 40f)
                )
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF09090B).copy(alpha = 0.26f + animatedAmplitude * 0.18f),
                    Color(0xFF334155).copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = baseOrbRadius * 1.35f
            ),
            radius = baseOrbRadius * 1.35f,
            center = Offset(cx, cy)
        )

        val arcRectSize = Size(baseOrbRadius * 2.15f, baseOrbRadius * 2.15f)
        val arcTopLeft = Offset(cx - arcRectSize.width / 2f, cy - arcRectSize.height / 2f)
        for (i in 0 until 6) {
            val startDeg = (i * 60f) + (masterSpin * 180f / PI.toFloat()) * (if (i % 2 == 0) 0.7f else -0.5f)
            drawArc(
                color = Color(0xFF09090B).copy(alpha = 0.75f),
                startAngle = startDeg,
                sweepAngle = 32f + animatedAmplitude * 18f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRectSize,
                style = Stroke(width = (2.5f + (i % 2) * 1.5f).dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val innerArcSize = Size(baseOrbRadius * 1.48f, baseOrbRadius * 1.48f)
        val innerTopLeft = Offset(cx - innerArcSize.width / 2f, cy - innerArcSize.height / 2f)
        for (i in 0 until 4) {
            val startDeg = (i * 90f) + (secondarySpin * 180f / PI.toFloat())
            drawArc(
                color = Color(0xFF18181B).copy(alpha = 0.82f),
                startAngle = startDeg,
                sweepAngle = 54f,
                useCenter = false,
                topLeft = innerTopLeft,
                size = innerArcSize,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt)
            )
        }

        val spokeCount = 18
        for (s in 0 until spokeCount) {
            val angle = (s.toFloat() / spokeCount) * 2f * PI.toFloat() + masterSpin * 0.4f
            val wave = sin(angle * 4f + secondarySpin * 2f) * animatedAmplitude * 0.25f
            val innerR = baseOrbRadius * (0.22f + wave * 0.2f)
            val outerR = baseOrbRadius * (0.88f + wave)
            val p1 = Offset(cx + innerR * cos(angle), cy + innerR * sin(angle))
            val p2 = Offset(cx + outerR * cos(angle), cy + outerR * sin(angle))
            drawLine(
                color = Color(0xFF09090B).copy(alpha = 0.35f + animatedAmplitude * 0.35f),
                start = p1,
                end = p2,
                strokeWidth = 1.4.dp.toPx()
            )
        }

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
                    drawLine(
                        color = Color(0xFF18181B).copy(alpha = (0.22f * depthNorm).coerceIn(0.05f, 0.35f)),
                        start = prevProjected!!,
                        end = currentOffset,
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            if (depthNorm > 0.45f) {
                prevProjected = currentOffset
            }

            val particleColor = when (pt.shellLayer) {
                0 -> Color(0xFF000000)
                1 -> Color(0xFF09090B)
                else -> Color(0xFF1E293B)
            }
            val alpha = (pt.shadeAlpha * (0.30f + depthNorm * 0.70f)).coerceIn(0.15f, 1.0f)
            val radiusPx = (pt.dotSize * perspective * (1f + animatedAmplitude * 0.35f)).dp.toPx()

            drawCircle(
                color = particleColor.copy(alpha = alpha),
                radius = radiusPx,
                center = currentOffset
            )
        }

        val coreRadius = baseOrbRadius * (0.21f + animatedAmplitude * 0.10f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF000000),
                    Color(0xFF18181B),
                    Color(0xFF3F3F46).copy(alpha = 0.65f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = coreRadius * 1.45f
            ),
            radius = coreRadius * 1.45f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = coreRadius * 0.52f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.8.dp.toPx())
        )
    }
}
