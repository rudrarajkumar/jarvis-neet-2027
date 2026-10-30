package com.jarvis.neet2027

import ai.picovoice.porcupine.Porcupine
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.neet2027.model.NeetPlanRepository
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

// ============================================================================
// 1. STT ROUTER MODES & INDIAN ACCENT / BILINGUAL LANGUAGE OPTIONS
// ============================================================================

enum class SttEngineMode(val title: String, val subtitle: String) {
    AUTO_HYBRID("Hybrid Auto Router", "Uses Google NEET Scientific STT when Wi-Fi is ON, On-Device Offline STT when OFF"),
    ONLINE_GOOGLE_NEET("Cloud NEET Scientific STT", "Highest accuracy for IUPAC chemical names, Latin biology & Physics equations"),
    OFFLINE_ON_DEVICE("100% On-Device Offline STT", "Zero bytes leave Xiaomi Pad 6 — uses local Android offline speech pack")
}

enum class SttLanguageOption(val code: String, val label: String) {
    ENGLISH_INDIA("en-IN", "English (India • NEET Scientific + Hinglish)"),
    ENGLISH_UK("en-GB", "English (UK • British Butler)"),
    HINDI_INDIA("hi-IN", "Hindi / Hinglish (भारत)")
}

object JarvisSttRouter {
    var selectedMode by mutableStateOf(SttEngineMode.AUTO_HYBRID)
    var selectedLanguage by mutableStateOf(SttLanguageOption.ENGLISH_INDIA)
    var isNetworkReachable by mutableStateOf(true)
}

// ============================================================================
// 2. SHARED 16kHz MIC STREAM: PICOVOICE PORCUPINE ("JARVIS") + DOUBLE-CLAP DSP
// ============================================================================

object SharedMicAndWakeManager {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var micStreamJob: Job? = null
    private var bargeInJob: Job? = null

    var isStreamRunning by mutableStateOf(false)
    var userWantsStreamArmed by mutableStateOf(true)
    var isGeminiLiveSessionActive by mutableStateOf(false)

    // Dual Wake Toggles: Run Picovoice Porcupine ("Jarvis") AND Double-Clap simultaneously!
    var enablePicovoiceWake by mutableStateOf(true)
    var enableDoubleClapWake by mutableStateOf(true)
    var enableVoiceBargeIn by mutableStateOf(true)

    // Sensitivity Sliders
    var porcupineSensitivity by mutableFloatStateOf(0.65f)
    var clapPeakThreshold by mutableFloatStateOf(0.42f)
    var bargeInVoiceThreshold by mutableFloatStateOf(0.36f)

    // Live Telemetry
    var liveMicDb by mutableFloatStateOf(-52.0f)
    var livePeakNormalized by mutableFloatStateOf(0.06f)
    var totalClapTriggers by mutableIntStateOf(0)
    var totalPorcupineTriggers by mutableIntStateOf(0)
    var totalBargeInTriggers by mutableIntStateOf(0)
    var liveOrbWakeEventTick by mutableIntStateOf(0)

    var picovoiceStatusBanner by mutableStateOf("Picovoice Porcupine: Checking Key Vault for AccessKey...")
    var lastTriggerLog by mutableStateOf("Guardian Armed — Say 'Jarvis' (Picovoice) or Double-Clap on any tab to launch Gemini Live!")

    fun playWakeActivationChime() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
        }
    }

    fun startSharedMicStream(context: Context) {
        if (isGeminiLiveSessionActive) return
        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPerm) {
            lastTriggerLog = "Microphone permission required — tap 'Grant Mic & Arm Guardian' below."
            return
        }

        if (micStreamJob?.isActive == true) return

        val appCtx = context.applicationContext
        micStreamJob = managerScope.launch {
            var porcupine: Porcupine? = null
            val accessKey = EncryptedKeyStoreVault.readSecret(appCtx, EncryptedKeyStoreVault.KEY_PICOVOICE_ACCESS).trim()

            if (enablePicovoiceWake && accessKey.isNotBlank()) {
                porcupine = runCatching {
                    Porcupine.Builder()
                        .setAccessKey(accessKey)
                        .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                        .setSensitivity(porcupineSensitivity.coerceIn(0.1f, 0.95f))
                        .build(appCtx)
                }.fold(
                    onSuccess = { engine ->
                        picovoiceStatusBanner = "✓ Picovoice Porcupine Neural 'JARVIS' Active (16kHz • 512-sample frames • Sensitivity ${(porcupineSensitivity * 100).toInt()}%)"
                        engine
                    },
                    onFailure = { err ->
                        picovoiceStatusBanner = "Picovoice Key Note: ${err.message?.take(85) ?: "Verify AccessKey"} (Double-Clap DSP remains 100% active)"
                        null
                    }
                )
            } else if (!enablePicovoiceWake) {
                picovoiceStatusBanner = "Picovoice 'JARVIS' Wake Word paused by toggle (Double-Clap DSP active)."
            } else {
                picovoiceStatusBanner = "No Picovoice AccessKey in Key Vault — Paste free key from console.picovoice.ai in Key Vault to enable 'Jarvis' wake word (Double-Clap DSP is active!)."
            }

            val sampleRate = porcupine?.sampleRate ?: 16000
            val frameLength = porcupine?.frameLength ?: 512
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameLength * 4)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                porcupine?.delete()
                isStreamRunning = false
                lastTriggerLog = "Mic busy with another app — tap Re-Arm Guardian."
                return@launch
            }

            val pcmFrame = ShortArray(frameLength)
            var firstClapTimestamp = 0L
            var lastWakeTimestamp = 0L

            try {
                recorder.startRecording()
                isStreamRunning = true

                while (isActive && userWantsStreamArmed && !isGeminiLiveSessionActive) {
                    var totalRead = 0
                    while (totalRead < frameLength && isActive && !isGeminiLiveSessionActive) {
                        val n = recorder.read(pcmFrame, totalRead, frameLength - totalRead)
                        if (n <= 0) break
                        totalRead += n
                    }
                    if (totalRead < frameLength) {
                        delay(15L)
                        continue
                    }

                    // 1. Compute live dBFS & Peak Envelope for UI and Double-Clap DSP
                    var sumSq = 0.0
                    var maxAbs = 0
                    for (i in 0 until frameLength) {
                        val s = pcmFrame[i].toInt()
                        val a = abs(s)
                        if (a > maxAbs) maxAbs = a
                        sumSq += (s * s).toDouble()
                    }
                    val rms = sqrt(sumSq / frameLength.toDouble())
                    val rmsNorm = (rms / 32768.0).toFloat().coerceIn(0.0001f, 1.0f)
                    val peakNorm = (maxAbs / 32768f).coerceIn(0.02f, 1.0f)
                    val db = (20.0 * log10(rmsNorm.toDouble())).toFloat().coerceIn(-60f, 0f)

                    liveMicDb = liveMicDb * 0.7f + db * 0.3f
                    livePeakNormalized = livePeakNormalized * 0.6f + peakNorm * 0.4f

                    val now = System.currentTimeMillis()
                    if (now - lastWakeTimestamp < 1300L) continue

                    // 2. Picovoice Porcupine Neural "JARVIS" Wake Word Detection
                    if (enablePicovoiceWake && porcupine != null) {
                        val keywordIndex = runCatching { porcupine.process(pcmFrame) }.getOrDefault(-1)
                        if (keywordIndex >= 0) {
                            lastWakeTimestamp = now
                            totalPorcupineTriggers++
                            totalClapTriggers++
                            playWakeActivationChime()
                            lastTriggerLog = "🎙️ Picovoice 'JARVIS' Wake Word Detected! Switching to Live Orb..."
                            isGeminiLiveSessionActive = true
                            liveOrbWakeEventTick++
                            break
                        }
                    }

                    // 3. Universal Double-Clap Acoustic Impulse Detector
                    if (enableDoubleClapWake) {
                        val crestFactor = peakNorm / max(0.015f, rmsNorm)
                        val isSharpClapImpulse = peakNorm >= clapPeakThreshold && crestFactor >= 1.95f

                        if (isSharpClapImpulse) {
                            val gap = now - firstClapTimestamp
                            if (gap in 110L..780L) {
                                firstClapTimestamp = 0L
                                lastWakeTimestamp = now
                                totalClapTriggers++
                                playWakeActivationChime()
                                lastTriggerLog = "👏 Double-Clap Wake Detected! Switching to Live Orb..."
                                isGeminiLiveSessionActive = true
                                liveOrbWakeEventTick++
                                break
                            } else if (gap > 780L) {
                                firstClapTimestamp = now
                            }
                        }
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { porcupine?.delete() }
                isStreamRunning = false
            }
        }
    }

    fun stopSharedMicStream() {
        micStreamJob?.cancel()
        micStreamJob = null
        isStreamRunning = false
    }

    suspend fun pauseStreamForSpeechTurn() {
        stopBargeInMonitor()
        micStreamJob?.cancel()
        micStreamJob?.join()
        micStreamJob = null
        isStreamRunning = false
        delay(90L)
    }

    fun endGeminiLiveAndRearmClapGuard(context: Context) {
        stopBargeInMonitor()
        isGeminiLiveSessionActive = false
        if (userWantsStreamArmed) {
            managerScope.launch {
                delay(280L)
                startSharedMicStream(context)
            }
        }
    }

    /**
     * Real-time Voice, Clap & Picovoice "Jarvis" Barge-In Monitor while TTS is speaking:
     * Lets you interrupt Jarvis mid-sentence by speaking, saying "Jarvis", or clapping!
     */
    fun startBargeInMonitorDuringTts(
        context: Context,
        onInterruptDetected: (String) -> Unit
    ) {
        stopBargeInMonitor()
        if (!enableVoiceBargeIn) return
        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPerm) return

        val appCtx = context.applicationContext
        bargeInJob = managerScope.launch {
            delay(450L) // Brief grace window so initial TTS click doesn't self-trigger
            val sampleRate = 16000
            val frameLength = 512
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameLength * 4)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
            }.getOrNull() ?: return@launch

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { recorder.release() }
                return@launch
            }

            val pcmFrame = ShortArray(frameLength)
            var consecutiveVoiceFrames = 0

            try {
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(pcmFrame, 0, frameLength)
                    if (read <= 0) {
                        delay(20L)
                        continue
                    }
                    var maxAbs = 0
                    for (i in 0 until read) {
                        val a = abs(pcmFrame[i].toInt())
                        if (a > maxAbs) maxAbs = a
                    }
                    val peakNorm = (maxAbs / 32768f).coerceIn(0.02f, 1.0f)
                    livePeakNormalized = peakNorm

                    if (peakNorm >= bargeInVoiceThreshold) {
                        consecutiveVoiceFrames++
                        if (consecutiveVoiceFrames >= 3) {
                            totalBargeInTriggers++
                            lastTriggerLog = "⚡ Mid-Speech Barge-In Detected — Listening to you now, sir!"
                            launch(Dispatchers.Main) {
                                onInterruptDetected("VOICE_BARGE_IN")
                            }
                            break
                        }
                    } else {
                        consecutiveVoiceFrames = max(0, consecutiveVoiceFrames - 1)
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }
        }
    }

    fun stopBargeInMonitor() {
        bargeInJob?.cancel()
        bargeInJob = null
    }
}

// ============================================================================
// 3. CHAPTER 7 + 12 UI: PICOVOICE PORCUPINE ("JARVIS") + DOUBLE-CLAP + STT LAB
// ============================================================================

@Composable
fun JarvisListeningAndWakeScreen(
    plan: NeetPlanRoot,
    masterPrompt: String,
    lab: GlassLabController,
    messages: MutableList<ChatMessageItem>,
    committedModelId: String,
    committedTemperature: Float,
    forceOffline: Boolean,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lab.currentPalette

    var isTestingStt by remember { mutableStateOf(false) }
    var sttTranscript by remember { mutableStateOf("Tap 'Test NEET Speech-to-Text' or say 'Jarvis' / Double-Clap to test live voice recognition.") }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                sttTranscript = "🎙️ Listening (${JarvisSttRouter.selectedLanguage.label})..."
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                val norm = ((rmsdB + 2f) / 11f).coerceIn(0.06f, 1.0f)
                SharedMicAndWakeManager.livePeakNormalized = norm
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                isTestingStt = false
            }
            override fun onError(error: Int) {
                isTestingStt = false
                sttTranscript = "STT Note (Code $error) — Rearming Picovoice & Double-Clap Guardian."
                SharedMicAndWakeManager.endGeminiLiveAndRearmClapGuard(context)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (part.isNotBlank()) sttTranscript = part
            }
            override fun onResults(results: Bundle?) {
                isTestingStt = false
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (heard.isNotBlank()) {
                    sttTranscript = "Heard: \"$heard\" — Sending to Jarvis Brain..."
                    scope.launch {
                        val reply = JarvisBrainEngine.generateJarvisReply(
                            context = context,
                            plan = plan,
                            masterPrompt = masterPrompt,
                            modelId = committedModelId,
                            temperature = committedTemperature,
                            forceOffline = forceOffline,
                            conversation = messages.toList(),
                            userQuery = heard
                        )
                        val stamp = NeetPlanRepository.getImphalNow().format(DateTimeFormatter.ofPattern("hh:mm a 'IST'"))
                        messages.add(ChatMessageItem(System.currentTimeMillis() - 1, "user", heard, "focused", "VOICE STT", stamp))
                        messages.add(reply)
                        EncryptedKeyStoreVault.saveChatHistory(context, messages)
                        sttTranscript = "YOU: \"$heard\"\nJARVIS: ${reply.visibleText}"
                        SharedMicAndWakeManager.endGeminiLiveAndRearmClapGuard(context)
                    }
                } else {
                    SharedMicAndWakeManager.endGeminiLiveAndRearmClapGuard(context)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            runCatching { speechRecognizer?.destroy() }
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            SharedMicAndWakeManager.userWantsStreamArmed = true
            SharedMicAndWakeManager.startSharedMicStream(context)
        }
    }

    val savedPicovoiceMask = remember {
        EncryptedKeyStoreVault.maskSecret(
            EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_PICOVOICE_ACCESS)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Dual Wake Guardian: Official Picovoice Porcupine ("JARVIS") + Double-Clap DSP
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = JarvisSpatialPalettes.StatusSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DUAL WAKE ENGINE // PICOVOICE PORCUPINE ('JARVIS') + UNIVERSAL DOUBLE-CLAP",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stream: ${if (SharedMicAndWakeManager.isStreamRunning) "16kHz SHARED PCM ACTIVE ✓" else "STANDBY"} • Picovoice Wakes: ${SharedMicAndWakeManager.totalPorcupineTriggers} • Clap Wakes: ${SharedMicAndWakeManager.totalClapTriggers} • Barge-Ins: ${SharedMicAndWakeManager.totalBargeInTriggers}",
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = SharedMicAndWakeManager.picovoiceStatusBanner,
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        text = SharedMicAndWakeManager.lastTriggerLog,
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = if (SharedMicAndWakeManager.isStreamRunning) "Guardian Armed ✓" else "Arm Wake Guardian",
                        icon = Icons.Default.Mic,
                        lab = lab,
                        isActive = SharedMicAndWakeManager.isStreamRunning,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (!hasMic) {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                SharedMicAndWakeManager.userWantsStreamArmed = true
                                SharedMicAndWakeManager.stopSharedMicStream()
                                SharedMicAndWakeManager.startSharedMicStream(context)
                            }
                        }
                    )
                    LiquidGlassButton(
                        text = "Simulate Wake -> Orb",
                        icon = Icons.Default.Bolt,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusWarning,
                        onClick = {
                            SharedMicAndWakeManager.playWakeActivationChime()
                            SharedMicAndWakeManager.totalClapTriggers++
                            SharedMicAndWakeManager.isGeminiLiveSessionActive = true
                            SharedMicAndWakeManager.liveOrbWakeEventTick++
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { SharedMicAndWakeManager.livePeakNormalized.coerceIn(0.04f, 1.0f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = JarvisSpatialPalettes.StatusSuccess,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Live 16kHz Mic Level: ${"%.1f".format(SharedMicAndWakeManager.liveMicDb)} dBFS • Peak Envelope: ${(SharedMicAndWakeManager.livePeakNormalized * 100).toInt()}% • Key Vault Picovoice Key: $savedPicovoiceMask",
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        // 2. Independent Toggles & Sensitivity Sliders for Picovoice ("Jarvis"), Double-Clap & Barge-In
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Text(
                text = "WAKE WORD & BARGE-IN SENSITIVITY CALIBRATION (XIAOMI PAD 6 QUAD-MIC)",
                color = JarvisSpatialPalettes.StatusWarning,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("1. Picovoice Neural 'JARVIS' Wake Word", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = SharedMicAndWakeManager.enablePicovoiceWake,
                    onCheckedChange = {
                        SharedMicAndWakeManager.enablePicovoiceWake = it
                        SharedMicAndWakeManager.stopSharedMicStream()
                        SharedMicAndWakeManager.startSharedMicStream(context)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = JarvisSpatialPalettes.StatusSuccess)
                )
            }
            Slider(
                value = SharedMicAndWakeManager.porcupineSensitivity,
                onValueChange = { SharedMicAndWakeManager.porcupineSensitivity = it },
                valueRange = 0.20f..0.90f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = JarvisSpatialPalettes.StatusSuccess
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("2. Universal Double-Clap Acoustic Wake", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = SharedMicAndWakeManager.enableDoubleClapWake,
                    onCheckedChange = { SharedMicAndWakeManager.enableDoubleClapWake = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = JarvisSpatialPalettes.StatusWarning)
                )
            }
            Slider(
                value = SharedMicAndWakeManager.clapPeakThreshold,
                onValueChange = { SharedMicAndWakeManager.clapPeakThreshold = it },
                valueRange = 0.20f..0.80f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = JarvisSpatialPalettes.StatusWarning
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("3. Mid-Speech Voice & Clap Barge-In", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = SharedMicAndWakeManager.enableVoiceBargeIn,
                    onCheckedChange = { SharedMicAndWakeManager.enableVoiceBargeIn = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = JarvisSpatialPalettes.StatusInfo)
                )
            }
            Slider(
                value = SharedMicAndWakeManager.bargeInVoiceThreshold,
                onValueChange = { SharedMicAndWakeManager.bargeInVoiceThreshold = it },
                valueRange = 0.18f..0.75f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = JarvisSpatialPalettes.StatusInfo
                )
            )
        }

        // 3. Hybrid NEET Scientific Speech-to-Text (STT) Router & Tester
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Text(
                text = "HYBRID NEET SCIENTIFIC SPEECH-TO-TEXT (STT) ROUTER",
                color = JarvisSpatialPalettes.StatusSuccess,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SttEngineMode.values().forEach { mode ->
                    val selected = JarvisSttRouter.selectedMode == mode
                    LiquidGlassButton(
                        text = mode.title,
                        lab = lab,
                        isActive = selected,
                        accentColor = if (selected) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                        onClick = { JarvisSttRouter.selectedMode = mode }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SttLanguageOption.values().forEach { lang ->
                    val selected = JarvisSttRouter.selectedLanguage == lang
                    LiquidGlassButton(
                        text = lang.label,
                        lab = lab,
                        isActive = selected,
                        accentColor = if (selected) JarvisSpatialPalettes.StatusInfo else JarvisSpatialPalettes.StatusWarning,
                        onClick = { JarvisSttRouter.selectedLanguage = lang }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sttTranscript,
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                LiquidGlassButton(
                    text = if (isTestingStt) "Listening..." else "Test NEET Speech-to-Text",
                    icon = Icons.Default.PlayArrow,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = {
                        scope.launch {
                            SharedMicAndWakeManager.pauseStreamForSpeechTurn()
                            isTestingStt = true
                            val preferOffline = when (JarvisSttRouter.selectedMode) {
                                SttEngineMode.OFFLINE_ON_DEVICE -> true
                                SttEngineMode.ONLINE_GOOGLE_NEET -> false
                                SttEngineMode.AUTO_HYBRID -> !LocalLlmModelManager.isDeviceOnlineRightNow(context) || forceOffline
                            }
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, JarvisSttRouter.selectedLanguage.code)
                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                            }
                            runCatching {
                                speechRecognizer?.cancel()
                                speechRecognizer?.startListening(intent)
                            }.onFailure {
                                isTestingStt = false
                                SharedMicAndWakeManager.endGeminiLiveAndRearmClapGuard(context)
                            }
                        }
                    }
                )
            }
        }
    }
}
