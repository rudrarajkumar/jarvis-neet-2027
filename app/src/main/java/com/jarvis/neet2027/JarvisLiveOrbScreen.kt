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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class OrbChromaMode(val label: String) {
    GREEN_AND_YELLOW("Green + Yellow"),
    SOLAR_YELLOW("Solar Yellow"),
    EMERALD_GREEN("Emerald Green")
}

private data class CoreWireLoop3D(
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val radiusScale: Float,
    val harmonicFreq: Int,
    val phaseOffset: Float,
    val isYellowSeed: Boolean
)

private data class GyroRingParticle3D(
    val ringIndex: Int,
    val angleRad: Float,
    val radialSpread: Float,
    val zThickness: Float,
    val dotSize: Float,
    val harmonicSeed: Float,
    val isYellowSeed: Boolean
)

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
                ?: "Double-clap anywhere (or tap the 3D Orb) to start Gemini Live. Speak naturally and interrupt me anytime, sir."
        )
    }
    var lastEmotion by remember { mutableStateOf("calm") }
    var textDraft by remember { mutableStateOf("") }

    // Tracks whether we are in an active hands-free Gemini Live continuous conversation
    var isLiveSessionActive by remember { mutableStateOf(false) }
    var consecutiveSilenceCount by remember { mutableIntStateOf(0) }
    var lastHandledWakeTick by remember { mutableIntStateOf(0) }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.UK
                engine?.setPitch(0.88f)
                engine?.setSpeechRate(1.05f)
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    // Dynamic acoustic pulsation for the 3D hologram particles
    LaunchedEffect(orbMode, SharedMicAndWakeManager.livePeakNormalized) {
        if (orbMode == VoiceOrbState.IDLE) {
            liveSoundLevel = SharedMicAndWakeManager.livePeakNormalized.coerceAtLeast(0.08f)
        }
    }

    LaunchedEffect(orbMode) {
        while (orbMode == VoiceOrbState.SPEAKING || orbMode == VoiceOrbState.THINKING) {
            liveSoundLevel = if (orbMode == VoiceOrbState.SPEAKING) {
                0.38f + Random.nextFloat() * 0.62f
            } else {
                0.22f + Random.nextFloat() * 0.28f
            }
            delay(55L)
        }
    }

    fun stopGeminiLiveAndRearmClap(statusNote: String = "Gemini Live paused — Double-Clap anytime to wake!") {
        isLiveSessionActive = false
        consecutiveSilenceCount = 0
        SharedMicAndWakeManager.stopBargeInMonitor()
        runCatching { speechRecognizer?.cancel() }
        ttsEngine?.stop()
        orbMode = VoiceOrbState.IDLE
        SharedMicAndWakeManager.lastTriggerLog = statusNote
        SharedMicAndWakeManager.endGeminiLiveAndRearmClapGuard(context)
    }

    fun startGeminiLiveListeningTurn(sourceTag: String = "LIVE") {
        scope.launch {
            isLiveSessionActive = true
            SharedMicAndWakeManager.isGeminiLiveSessionActive = true
            SharedMicAndWakeManager.stopBargeInMonitor()
            ttsEngine?.stop()

            // Release AudioRecord hardware lock first so SpeechRecognizer never conflicts
            SharedMicAndWakeManager.pauseStreamForSpeechTurn()

            orbMode = VoiceOrbState.LISTENING
            liveTranscript = "🎙️ Listening ($sourceTag)... Speak now, sir"

            val preferOffline = when (JarvisSttRouter.selectedMode) {
                SttEngineMode.OFFLINE_ON_DEVICE -> true
                SttEngineMode.ONLINE_GOOGLE_NEET -> false
                SttEngineMode.AUTO_HYBRID -> !JarvisSttRouter.isNetworkReachable || forceOffline
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, JarvisSttRouter.selectedLanguage.code)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(intent)
            }.onFailure {
                stopGeminiLiveAndRearmClap("Mic hand-off retry — Double-Clap to wake.")
            }
        }
    }

    fun speakWithBargeInAndAutoLoop(replyText: String, emotionTag: String, isDetailedRequested: Boolean) {
        val tts = ttsEngine
        if (tts == null) {
            if (isLiveSessionActive) startGeminiLiveListeningTurn("AUTO_LOOP")
            else stopGeminiLiveAndRearmClap()
            return
        }
        tts.stop()

        when (emotionTag) {
            "urgent", "focused" -> {
                tts.setPitch(0.92f)
                tts.setSpeechRate(1.08f)
            }
            "concerned" -> {
                tts.setPitch(0.85f)
                tts.setSpeechRate(0.98f)
            }
            else -> {
                tts.setPitch(0.88f)
                tts.setSpeechRate(1.05f)
            }
        }

        // Format for natural Gemini Live conversation: 1-3 crisp sentences unless user asked for detail
        val clean = replyText.replace("*", "").replace("#", "").trim()
        val allSentences = clean.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val spokenSentences = if (isDetailedRequested) {
            allSentences.take(8)
        } else {
            allSentences.take(3)
        }

        if (spokenSentences.isEmpty()) {
            if (isLiveSessionActive) startGeminiLiveListeningTurn("AUTO_LOOP")
            else stopGeminiLiveAndRearmClap()
            return
        }

        orbMode = VoiceOrbState.SPEAKING

        // Start Real-Time Voice & Clap Barge-In Monitor while Jarvis speaks!
        SharedMicAndWakeManager.startBargeInMonitorDuringTts(context) { interruptReason ->
            // User spoke over Jarvis or double-clapped mid-sentence -> cut TTS immediately & listen!
            tts.stop()
            SharedMicAndWakeManager.playWakeActivationChime()
            startGeminiLiveListeningTurn(interruptReason)
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                orbMode = VoiceOrbState.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "live_utt_${spokenSentences.lastIndex}") {
                    SharedMicAndWakeManager.stopBargeInMonitor()
                    scope.launch(Dispatchers.Main) {
                        if (isLiveSessionActive) {
                            // Gemini Live continuous turn-taking: immediately listen for user's next reply!
                            delay(220L)
                            startGeminiLiveListeningTurn("GEMINI_LIVE_LOOP")
                        } else {
                            stopGeminiLiveAndRearmClap()
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                SharedMicAndWakeManager.stopBargeInMonitor()
                scope.launch(Dispatchers.Main) {
                    if (isLiveSessionActive) {
                        delay(300L)
                        startGeminiLiveListeningTurn("RETRY")
                    } else {
                        stopGeminiLiveAndRearmClap()
                    }
                }
            }
        })

        spokenSentences.forEachIndexed { idx, sentence ->
            val mode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(sentence, mode, null, "live_utt_$idx")
        }
    }

    fun processUserQueryInGeminiLive(rawQuery: String) {
        val clean = rawQuery.trim()
        if (clean.isEmpty()) return

        val lower = clean.lowercase()
        // Voice command to exit Gemini Live continuous loop
        if (lower == "stop" || lower == "stop listening" || lower == "exit" || lower == "pause" || lower == "goodbye" || lower == "bye jarvis") {
            lastJarvisSpoken = "Standing by, sir. Double-clap anytime you need me."
            ttsEngine?.speak(lastJarvisSpoken, TextToSpeech.QUEUE_FLUSH, null, "goodbye_utt")
            stopGeminiLiveAndRearmClap("Standing by — Double-Clap anytime to wake!")
            return
        }

        val wantsDetailed = lower.contains("detail") || lower.contains("elaborate") ||
            lower.contains("in depth") || lower.contains("step by step") || lower.contains("derive")

        // Prompt instruction so Jarvis speaks like Gemini Live (short, punchy, conversational) unless asked for detail
        val liveConversationalQuery = if (wantsDetailed) {
            clean
        } else {
            "$clean (Reply in 1 to 2 natural, conversational spoken sentences like Gemini Live so we can chat back and forth quickly.)"
        }

        liveTranscript = clean
        orbMode = VoiceOrbState.THINKING
        SharedMicAndWakeManager.stopBargeInMonitor()

        scope.launch {
            val reply = JarvisBrainEngine.generateJarvisReply(
                context = context,
                plan = plan,
                masterPrompt = masterPrompt,
                modelId = committedModelId,
                temperature = committedTemperature,
                forceOffline = forceOffline,
                conversation = messages.toList(),
                userQuery = liveConversationalQuery
            )
            messages.add(
                ChatMessageItem(
                    id = System.currentTimeMillis() - 1,
                    role = "user",
                    visibleText = clean,
                    emotionTag = "focused",
                    engineBadge = "GEMINI LIVE"
                )
            )
            messages.add(reply)
            EncryptedKeyStoreVault.saveChatHistory(context, messages)
            lastJarvisSpoken = reply.visibleText
            lastEmotion = reply.emotionTag

            speakWithBargeInAndAutoLoop(
                replyText = reply.visibleText,
                emotionTag = reply.emotionTag,
                isDetailedRequested = wantsDetailed
            )
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                orbMode = VoiceOrbState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                orbMode = VoiceOrbState.LISTENING
                consecutiveSilenceCount = 0
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 11f).coerceIn(0.08f, 1.0f)
                liveSoundLevel = normalized
                SharedMicAndWakeManager.livePeakNormalized = normalized
                SharedMicAndWakeManager.liveMicDb = (-50f + normalized * 45f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (orbMode == VoiceOrbState.LISTENING) {
                    orbMode = VoiceOrbState.THINKING
                }
            }

            override fun onError(error: Int) {
                if (isLiveSessionActive) {
                    consecutiveSilenceCount++
                    // If user stays silent for 2 consecutive turns, gracefully pause Gemini Live and re-arm Double-Clap!
                    if (consecutiveSilenceCount >= 2) {
                        stopGeminiLiveAndRearmClap("No speech detected — Re-armed Double-Clap Guardian.")
                    } else {
                        scope.launch {
                            delay(350L)
                            if (isLiveSessionActive) {
                                startGeminiLiveListeningTurn("CONTINUE_LISTEN")
                            }
                        }
                    }
                } else {
                    stopGeminiLiveAndRearmClap()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (part.isNotBlank()) {
                    liveTranscript = part
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val heard = matches?.firstOrNull().orEmpty().trim()
                if (heard.isNotBlank()) {
                    consecutiveSilenceCount = 0
                    processUserQueryInGeminiLive(heard)
                } else if (isLiveSessionActive) {
                    consecutiveSilenceCount++
                    if (consecutiveSilenceCount >= 2) {
                        stopGeminiLiveAndRearmClap("Paused after silence — Double-Clap anytime to wake!")
                    } else {
                        startGeminiLiveListeningTurn("LISTEN_AGAIN")
                    }
                } else {
                    stopGeminiLiveAndRearmClap()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            SharedMicAndWakeManager.stopBargeInMonitor()
            speechRecognizer?.destroy()
            // Ensure Double-Clap Guardian is re-armed if user navigates away from Live Orb
            SharedMicAndWakeManager.endGeminiLiveAndRearmClapGuard(context)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startGeminiLiveListeningTurn("MIC_GRANTED")
        }
    }

    // Ensure Double-Clap Guardian is armed when sitting idle on Live Orb
    LaunchedEffect(Unit) {
        if (SharedMicAndWakeManager.userWantsStreamArmed && !SharedMicAndWakeManager.isGeminiLiveSessionActive) {
            SharedMicAndWakeManager.startSharedMicStream(context)
        }
    }

    // OBSERVE UNIVERSAL DOUBLE-CLAP EVENT TICK:
    // Whenever you Double-Clap on ANY tab (or on Live Orb itself), this fires immediately!
    val currentWakeTick = SharedMicAndWakeManager.liveOrbWakeEventTick
    LaunchedEffect(currentWakeTick) {
        if (currentWakeTick > 0 && currentWakeTick != lastHandledWakeTick) {
            lastHandledWakeTick = currentWakeTick
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                startGeminiLiveListeningTurn("DOUBLE_CLAP_WAKE")
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
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
            // 1. Exact 3D Hologram Orb from Video (Green & Yellow Sound-Reactive Particles)
            VideoHologram3DOrbCanvas(
                orbState = orbMode,
                chromaMode = chromaMode,
                soundLevel = liveSoundLevel,
                tilt = tilt,
                isCompact = isCompactSplitScreen,
                onTapOrb = {
                    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (!hasMic) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (orbMode == VoiceOrbState.SPEAKING) {
                        // Tapping orb while Jarvis speaks barges in immediately!
                        startGeminiLiveListeningTurn("ORB_BARGE_IN")
                    } else if (isLiveSessionActive || orbMode == VoiceOrbState.LISTENING) {
                        stopGeminiLiveAndRearmClap("Stopped by tap — Double-Clap anytime to wake!")
                    } else {
                        startGeminiLiveListeningTurn("ORB_TAP")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Top Adaptive Header Bar with Live dBFS, Clap Counter & Barge-In Status
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
                            text = "J.A.R.V.I.S. GEMINI LIVE ORB // ${if (isLiveSessionActive) "LIVE AUTO-LOOP + BARGE-IN ON" else "DOUBLE-CLAP GUARD ARMED"}",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "State: ${orbMode.name} • Mic: ${"%.1f".format(SharedMicAndWakeManager.liveMicDb)} dBFS • Claps: ${SharedMicAndWakeManager.totalClapTriggers} • Barge-Ins: ${SharedMicAndWakeManager.totalBargeInTriggers}",
                        color = secondaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1-Tap Colour Mode Toggle
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

                    // Start / End Gemini Live Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isLiveSessionActive) Color(0xFFF43F5E).copy(alpha = 0.28f)
                                else primaryAccent.copy(alpha = 0.25f)
                            )
                            .border(
                                width = 1.2.dp,
                                color = if (isLiveSessionActive) Color(0xFFF43F5E) else primaryAccent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!hasMic) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (isLiveSessionActive) {
                                    stopGeminiLiveAndRearmClap("Ended Live Chat — Double-Clap anytime to wake!")
                                } else {
                                    startGeminiLiveListeningTurn("START_LIVE")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isLiveSessionActive) Color(0xFFF43F5E) else primaryAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isLiveSessionActive) "End Live" else "Start Live",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Barge-In / Replay Voice Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xCC121417))
                            .border(1.dp, secondaryAccent.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                            .clickable {
                                if (orbMode == VoiceOrbState.SPEAKING) {
                                    startGeminiLiveListeningTurn("BARGE_IN_BUTTON")
                                } else {
                                    isLiveSessionActive = true
                                    speakWithBargeInAndAutoLoop(lastJarvisSpoken, lastEmotion, isDetailedRequested = false)
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = secondaryAccent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (orbMode == VoiceOrbState.SPEAKING) "Interrupt" else "Replay",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                text = "Double-clap anywhere to talk live, speak over Jarvis to interrupt, or type here...",
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
                                    isLiveSessionActive = true
                                    processUserQueryInGeminiLive(q)
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

    val outerShellSegments = remember {
        val rng = Random(303)
        val list = mutableListOf<TectonicCircuitSegment3D>()
        var attempts = 0
        while (list.size < 380 && attempts < 1400) {
            attempts++
            val theta = rng.nextFloat() * 2f * PI.toFloat()
            val phi = (rng.nextFloat() - 0.5f) * PI.toFloat()

            val patchWave = sin(theta * 2f + 0.5f) * cos(phi * 2.2f) + sin(phi * 1.6f)
            if (patchWave > -0.22f) {
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
            VoiceOrbState.IDLE -> soundLevel.coerceIn(0.06f, 0.25f)
            VoiceOrbState.LISTENING -> soundLevel.coerceAtLeast(0.32f)
            VoiceOrbState.THINKING -> 0.45f
            VoiceOrbState.SPEAKING -> soundLevel.coerceAtLeast(0.54f)
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

        fun project3D(x: Float, y: Float, z: Float, rx: Float, ry: Float, rz: Float): Triple<Float, Float, Float> {
            val x1 = x * cos(rz) - y * sin(rz)
            val y1 = x * sin(rz) + y * cos(rz)
            val y2 = y1 * cos(rx) - z * sin(rx)
            val z2 = y1 * sin(rx) + z * cos(rx)
            val x3 = x1 * cos(ry) + z2 * sin(ry)
            val z3 = -x1 * sin(ry) + z2 * cos(ry)

            val depthNorm = ((z3 / (baseR * 1.15f)) + 1f) * 0.5f
            val perspective = 0.62f + depthNorm.coerceIn(0f, 1.2f) * 0.56f
            return Triple(cx + x3 * perspective, cy + y2 * perspective, depthNorm.coerceIn(0f, 1f))
        }

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

        // LAYER 1: DENSE TANGLED WIREFRAME INNER CORE SPHERE
        val coreSteps = 28
        coreLoops.forEach { loop ->
            var prevPt: Offset? = null
            val loopRx = loop.rotX + globalRx * 1.3f
            val loopRy = loop.rotY + globalRy * 1.4f
            val loopRz = loop.rotZ + spinPrecession * 0.6f

            for (step in 0..coreSteps) {
                val u = (step.toFloat() / coreSteps) * (2f * PI.toFloat())
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

        // LAYER 2: TWO STEEPLY TILTED GYROSCOPIC PARTICLE RINGS
        var prevRing0: Offset? = null
        var prevRing1: Offset? = null

        gyroRingParticles.forEachIndexed { idx, gp ->
            val isSteepRing = gp.ringIndex == 0
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

        // LAYER 3: OUTER BROKEN TECTONIC CIRCUIT-TRACE SHELL
        val shellRx = globalRx * 0.85f
        val shellRy = -globalRy * 0.75f
        val shellRz = sin(spinPrimary * 0.5f) * 0.25f

        outerShellSegments.forEach { seg ->
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

            drawLine(
                color = traceColor,
                start = p1,
                end = p2,
                strokeWidth = (1.1f + depth1 * 1.0f).dp.toPx(),
                cap = StrokeCap.Square
            )

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
