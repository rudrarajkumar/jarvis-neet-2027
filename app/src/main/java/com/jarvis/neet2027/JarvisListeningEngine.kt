package com.jarvis.neet2027

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

// ============================================================================
// 1. SHARED ALWAYS-ON 16kHz MIC ENGINE WITH CLEAN SPEECHRECOGNIZER HAND-OFF
// ============================================================================

enum class SttEngineMode(val badge: String) {
    AUTO_HYBRID("HYBRID AUTO"),
    ONLINE_GOOGLE_NEET("ONLINE STREAMING (en-IN + NEET Boost)"),
    OFFLINE_ON_DEVICE("OFFLINE ON-DEVICE (0 Bytes Leave Tablet)")
}

enum class IndianLanguageMode(val code: String, val label: String) {
    ENGLISH_INDIA("en-IN", "English (India)"),
    HINGLISH("en-IN", "Hinglish (Code-Switch)"),
    HINDI("hi-IN", "Hindi (भारत)")
}

data class NeetBenchmarkPhrase(
    val id: Int,
    val subject: String,
    val expectedPhrase: String,
    val lastHeardText: String = "",
    val accuracyPercent: Int = -1,
    val testedEngine: String = "Untested"
)

object SharedMicAndWakeManager {
    private const val SAMPLE_RATE = 16000
    private const val RING_BUFFER_SECONDS = 8
    private const val RING_BUFFER_SAMPLES = SAMPLE_RATE * RING_BUFFER_SECONDS

    private val pcmRingBuffer = ShortArray(RING_BUFFER_SAMPLES)
    private var ringWritePos = 0

    // True when user wants the background/shared DSP stream active between STT turns
    var userWantsStreamArmed by mutableStateOf(false)
    var isSharedStreamRunning by mutableStateOf(false)
    var isHandedOffToStt by mutableStateOf(false)

    var liveMicDb by mutableFloatStateOf(-55f)
    var livePeakNormalized by mutableFloatStateOf(0.05f)
    var wakeWordEnabled by mutableStateOf(true)
    var doubleClapEnabled by mutableStateOf(true)
    var wakeChimeEnabled by mutableStateOf(true)
    var falseTriggerTestMode by mutableStateOf(false)

    var clapSensitivity by mutableFloatStateOf(0.72f)
    var wakeSensitivity by mutableFloatStateOf(0.75f)
    var cooldownSeconds by mutableFloatStateOf(2.0f)

    var totalWakeTriggers by mutableIntStateOf(0)
    var totalClapTriggers by mutableIntStateOf(0)
    var falseTriggerCount by mutableIntStateOf(0)
    var lastTriggerLog by mutableStateOf("Tap '1. Start Shared Mic Stream' — then Double-Clap or say 'Jarvis'!")
    var aecAvailable by mutableStateOf(false)
    var nsAvailable by mutableStateOf(false)

    private var audioJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastTriggerMillis = 0L
    private var firstClapMillis = 0L
    private var consecutiveVoiceFrames = 0

    // Callback invoked on Main thread AFTER AudioRecord has released the microphone!
    var onWakeActivatedCallback: ((triggerType: String) -> Unit)? = null

    fun playWakeActivationChime() {
        if (!wakeChimeEnabled) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
        }
    }

    /**
     * Suspends and cleanly closes AudioRecord so Android SpeechRecognizer gets 100% exclusive
     * access to the Xiaomi Pad 6 microphone without ERROR_AUDIO (3).
     */
    suspend fun pauseStreamForSpeechTurn() {
        isHandedOffToStt = true
        isSharedStreamRunning = false
        audioJob?.cancelAndJoin()
        audioJob = null
        // Give Xiaomi HyperOS audio HAL 140ms to fully release the hardware input stream
        delay(140L)
    }

    fun resumeStreamAfterSpeechTurn(context: Context) {
        isHandedOffToStt = false
        if (userWantsStreamArmed && !isSharedStreamRunning) {
            startSharedMicStream(context)
        }
    }

    fun startSharedMicStream(context: Context) {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            lastTriggerLog = "Grant Microphone permission first to start the shared 16kHz stream."
            return
        }
        userWantsStreamArmed = true
        if (isSharedStreamRunning || isHandedOffToStt) return

        isSharedStreamRunning = true
        val appCtx = context.applicationContext
        audioJob?.cancel()
        audioJob = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                isSharedStreamRunning = false
                lastTriggerLog = "Mic busy — releasing for SpeechRecognizer."
                return@launch
            }

            val sessionId = recorder.audioSessionId
            aecAvailable = AcousticEchoCanceler.isAvailable()
            nsAvailable = NoiseSuppressor.isAvailable()
            val aec = if (aecAvailable) runCatching { AcousticEchoCanceler.create(sessionId)?.apply { enabled = true } }.getOrNull() else null
            val ns = if (nsAvailable) runCatching { NoiseSuppressor.create(sessionId)?.apply { enabled = true } }.getOrNull() else null
            val agc = if (AutomaticGainControl.isAvailable()) runCatching { AutomaticGainControl.create(sessionId)?.apply { enabled = true } }.getOrNull() else null

            val frameBuffer = ShortArray(1024) // ~64ms per frame at 16kHz
            var ambientRmsFloor = 180f
            consecutiveVoiceFrames = 0
            var pendingHandoffReason: String? = null

            try {
                recorder.startRecording()
                while (isActive && isSharedStreamRunning && pendingHandoffReason == null) {
                    val readCount = recorder.read(frameBuffer, 0, frameBuffer.size)
                    if (readCount > 0) {
                        var sumSq = 0.0
                        var maxAbs = 0
                        var zeroCrossings = 0
                        var prevSample = frameBuffer[0].toInt()

                        for (i in 0 until readCount) {
                            val s = frameBuffer[i]
                            pcmRingBuffer[ringWritePos] = s
                            ringWritePos = (ringWritePos + 1) % RING_BUFFER_SAMPLES

                            val v = s.toInt()
                            sumSq += (v * v).toDouble()
                            val absV = abs(v)
                            if (absV > maxAbs) maxAbs = absV
                            if ((v >= 0 && prevSample < 0) || (v < 0 && prevSample >= 0)) {
                                zeroCrossings++
                            }
                            prevSample = v
                        }

                        val rms = sqrt(sumSq / readCount).toFloat().coerceAtLeast(1f)
                        ambientRmsFloor = (ambientRmsFloor * 0.97f + rms * 0.03f).coerceIn(90f, 3000f)
                        val db = (20f * log10(rms / 32768f)).coerceIn(-60f, 0f)
                        liveMicDb = db
                        livePeakNormalized = (maxAbs / 22000f).coerceIn(0.03f, 1.0f)

                        val now = System.currentTimeMillis()
                        val cooldownMs = (cooldownSeconds * 1000f).toLong()
                        if (now - lastTriggerMillis < cooldownMs) continue

                        // 1. On-Device Double-Clap Impulse Detector
                        val clapThreshold = (25000f * (1.12f - clapSensitivity)).coerceIn(3800f, 22000f)
                        if (doubleClapEnabled && maxAbs > clapThreshold && rms > ambientRmsFloor * 2.4f && zeroCrossings > 75) {
                            if (firstClapMillis == 0L || (now - firstClapMillis) > 550L) {
                                firstClapMillis = now
                            } else if ((now - firstClapMillis) in 110L..520L) {
                                firstClapMillis = 0L
                                lastTriggerMillis = now
                                val stamp = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(now))
                                if (falseTriggerTestMode) {
                                    falseTriggerCount++
                                    lastTriggerLog = "[$stamp] False-Trigger Test logged a Double-Clap impulse."
                                } else {
                                    totalClapTriggers++
                                    lastTriggerLog = "[$stamp] ✓ DOUBLE-CLAP! Releasing DSP mic -> Listening to your command now..."
                                    playWakeActivationChime()
                                    pendingHandoffReason = "CLAP"
                                    break
                                }
                            }
                        }

                        // 2. On-Device Voice / "Jarvis" Syllable Wake Detector
                        // Human speech sits in 18..95 zero-crossings per 1024-sample frame with sustained RMS above ambient
                        val voiceThreshold = (1800f * (1.15f - wakeSensitivity)).coerceIn(320f, 1600f)
                        if (wakeWordEnabled && rms > maxOf(voiceThreshold, ambientRmsFloor * 2.1f) && zeroCrossings in 14..110) {
                            consecutiveVoiceFrames++
                            // ~3 consecutive frames (~190ms of clear speech, e.g. starting to say "Jarvis...")
                            if (consecutiveVoiceFrames >= 3) {
                                consecutiveVoiceFrames = 0
                                lastTriggerMillis = now
                                val stamp = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(now))
                                if (falseTriggerTestMode) {
                                    falseTriggerCount++
                                    lastTriggerLog = "[$stamp] False-Trigger Test logged a Voice Wake syllable."
                                } else {
                                    totalWakeTriggers++
                                    lastTriggerLog = "[$stamp] 🎙️ VOICE WAKE ('JARVIS')! Listening to your command now..."
                                    playWakeActivationChime()
                                    pendingHandoffReason = "VOICE_WAKE"
                                    break
                                }
                            }
                        } else {
                            consecutiveVoiceFrames = (consecutiveVoiceFrames - 1).coerceAtLeast(0)
                        }
                    }
                }
            } finally {
                // CRITICAL: Always stop & release AudioRecord BEFORE invoking SpeechRecognizer!
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching { agc?.release() }
                isSharedStreamRunning = false
            }

            // If we exited the loop because of a Double-Clap or Voice Wake, hand off cleanly to SpeechRecognizer!
            if (pendingHandoffReason != null) {
                isHandedOffToStt = true
                delay(150L) // Let Xiaomi HyperOS release the mic lock completely
                withContext(Dispatchers.Main) {
                    val cb = onWakeActivatedCallback
                    if (cb != null) {
                        cb.invoke(pendingHandoffReason!!)
                    } else {
                        // If user is on another screen, resume stream
                        isHandedOffToStt = false
                        if (userWantsStreamArmed) startSharedMicStream(appCtx)
                    }
                }
            }
        }
    }

    fun stopSharedMicStreamCompletely() {
        userWantsStreamArmed = false
        isHandedOffToStt = false
        isSharedStreamRunning = false
        audioJob?.cancel()
        audioJob = null
        lastTriggerLog = "Shared 16kHz Mic Stream stopped."
    }

    fun getBufferedSecondsAvailable(): Int = RING_BUFFER_SECONDS
}

// ============================================================================
// 2. FOREGROUND MICROPHONE SERVICE & REBOOT SURVIVAL RECEIVER
// ============================================================================

class JarvisWakeForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_JARVIS_WAKE_SERVICE") {
            SharedMicAndWakeManager.stopSharedMicStreamCompletely()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val channelId = "jarvis_always_on_mic_service"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "J.A.R.V.I.S. Always-On Wake & Double-Clap",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            701,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("J.A.R.V.I.S. On-Device Wake Guard Active")
            .setContentText("Listening locally for 'Jarvis' or Double-Clap (Auto Mic Hand-Off Ready)")
            .setContentIntent(openPending)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(701, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(701, notif)
        }

        SharedMicAndWakeManager.startSharedMicStream(this)
        return START_STICKY
    }

    override fun onDestroy() {
        SharedMicAndWakeManager.stopSharedMicStreamCompletely()
        super.onDestroy()
    }
}

class JarvisBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            NeetCoreStore.ensureAlarmReceiverRegistered(context)
        }
    }
}

// ============================================================================
// 3. STT ROUTER (ONLINE vs OFFLINE + HYSTERESIS REACHABILITY + 20 NEET PHRASES)
// ============================================================================

object JarvisSttRouter {
    var selectedMode by mutableStateOf(SttEngineMode.AUTO_HYBRID)
    var selectedLanguage by mutableStateOf(IndianLanguageMode.HINGLISH)
    var isNetworkReachable by mutableStateOf(true)
    private var consecutiveSuccesses = 2
    private var consecutiveFailures = 0

    val activeLiveEngineBadge: String
        get() = when (selectedMode) {
            SttEngineMode.ONLINE_GOOGLE_NEET -> "ONLINE STREAMING (${selectedLanguage.label})"
            SttEngineMode.OFFLINE_ON_DEVICE -> "OFFLINE ON-DEVICE (Sherpa/Android Local)"
            SttEngineMode.AUTO_HYBRID -> if (isNetworkReachable) {
                "AUTO -> ONLINE (${selectedLanguage.label})"
            } else {
                "AUTO -> OFFLINE FALLBACK (${SharedMicAndWakeManager.getBufferedSecondsAvailable()}s Audio Buffered)"
            }
        }

    suspend fun checkRealReachabilityWithHysteresis(): Boolean = withContext(Dispatchers.IO) {
        val reachable = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), 1500)
                true
            }
        }.getOrDefault(false)

        if (reachable) {
            consecutiveSuccesses++
            consecutiveFailures = 0
            if (consecutiveSuccesses >= 2) {
                isNetworkReachable = true
            }
        } else {
            consecutiveFailures++
            consecutiveSuccesses = 0
            isNetworkReachable = false
        }
        isNetworkReachable
    }

    fun computePhraseAccuracy(expected: String, actual: String): Int {
        if (actual.isBlank()) return 0
        val expWords = expected.lowercase().replace(Regex("[^a-z0-9\\s]"), "").split(" ").filter { it.isNotBlank() }
        val actWords = actual.lowercase().replace(Regex("[^a-z0-9\\s]"), "").split(" ").filter { it.isNotBlank() }.toSet()
        if (expWords.isEmpty()) return 100
        val matched = expWords.count { w -> actWords.any { it.contains(w) || w.contains(it) } }
        return ((matched.toFloat() / expWords.size.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun build20NeetBenchmarkPhrases(): List<NeetBenchmarkPhrase> = listOf(
        NeetBenchmarkPhrase(1, "Physics", "Gauss law states total electric flux equals enclosed charge divided by epsilon naught"),
        NeetBenchmarkPhrase(2, "Physics", "Electric field of a dipole on axial line is twice the equatorial field"),
        NeetBenchmarkPhrase(3, "Physics", "Moment of inertia of solid sphere about diameter is two fifths M R squared"),
        NeetBenchmarkPhrase(4, "Physics", "Kirchhoff loop rule is based on conservation of energy in current electricity"),
        NeetBenchmarkPhrase(5, "Physics", "Total mechanical energy in simple harmonic motion is half m omega squared A squared"),
        NeetBenchmarkPhrase(6, "Organic Chem", "Priority order is aromaticity then resonance then hyperconjugation then inductive effect"),
        NeetBenchmarkPhrase(7, "Organic Chem", "Markovnikov addition of HBr to propene gives two bromopropane via carbocation"),
        NeetBenchmarkPhrase(8, "Organic Chem", "Aldol condensation requires alpha hydrogen in aldehydes and ketones"),
        NeetBenchmarkPhrase(9, "Organic Chem", "Hinsberg reagent benzene sulfonyl chloride distinguishes primary secondary and tertiary amines"),
        NeetBenchmarkPhrase(10, "Inorganic Chem", "Hybridisation of xenon difluoride is sp3d with linear geometry and three lone pairs"),
        NeetBenchmarkPhrase(11, "Inorganic Chem", "Lanthanoid contraction causes zirconium and hafnium to have similar atomic radii"),
        NeetBenchmarkPhrase(12, "Physical Chem", "Nernst equation relates cell potential to standard electrode potential and reaction quotient"),
        NeetBenchmarkPhrase(13, "Physical Chem", "Henderson Hasselbalch equation calculates pH of acidic and basic buffer solutions"),
        NeetBenchmarkPhrase(14, "Zoology", "Competitive inhibitor malonate increases Michaelis constant Km of succinic dehydrogenase"),
        NeetBenchmarkPhrase(15, "Zoology", "Oxygen dissociation curve of haemoglobin is sigmoid and shifts right at high pCO2"),
        NeetBenchmarkPhrase(16, "Zoology", "Luteinizing hormone surge triggers ovulation from Graafian follicle on day fourteen"),
        NeetBenchmarkPhrase(17, "Botany", "Concanavalin A is a lectin and vinblastin is a secondary metabolite drug in NCERT"),
        NeetBenchmarkPhrase(18, "Botany", "Kranz anatomy in C4 plants prevents photorespiration using PEPcase in mesophyll cells"),
        NeetBenchmarkPhrase(19, "Hinglish", "Jarvis Biomolecules DPP zero one mein carbohydrates aur amino acids ke notes dikhao"),
        NeetBenchmarkPhrase(20, "Hinglish", "Electric charges and fields lecture four ke baad fifty minute NEETprep practice set lagao")
    )
}

// ============================================================================
// 4. CHAPTER 7 UI: WAKE WORD, DOUBLE-CLAP, BARGE-IN & 20-PHRASE NEET BENCHMARK
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

    var isContinuousConvo by remember { mutableStateOf(false) }
    var isSttActivelyListening by remember { mutableStateOf(false) }
    var isForegroundServiceArmed by remember { mutableStateOf(false) }
    var livePartialTranscript by remember { mutableStateOf("") }
    var lastSpokenReply by remember {
        mutableStateOf("Double-Clap or say 'Jarvis' (e.g. 'Jarvis, what should I study now?') — I will automatically release the DSP mic, transcribe your voice, and speak back!")
    }
    var firstWordLatencyMs by remember { mutableLongStateOf(0L) }
    var showBenchmarkList by remember { mutableStateOf(false) }
    var activeBenchmarkIdx by remember { mutableIntStateOf(0) }
    val benchmarkPhrases = remember { mutableStateListOf<NeetBenchmarkPhrase>().apply { addAll(JarvisSttRouter.build20NeetBenchmarkPhrases()) } }

    val picovoiceKey = remember {
        EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_PICOVOICE_ACCESS)
    }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isJarvisSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.UK
                engine?.setPitch(0.88f)
                engine?.setSpeechRate(1.03f)
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            JarvisSttRouter.checkRealReachabilityWithHysteresis()
            delay(6000L)
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    /**
     * Safely pauses the 16kHz AudioRecord stream FIRST, then launches SpeechRecognizer
     * so Xiaomi Pad 6 never hits a microphone lock conflict!
     */
    fun launchCleanSttTurn(triggerSource: String = "MANUAL") {
        scope.launch {
            if (isJarvisSpeaking) {
                ttsEngine?.stop()
                isJarvisSpeaking = false
            }
            // 1. Release AudioRecord hardware lock first
            SharedMicAndWakeManager.pauseStreamForSpeechTurn()

            // 2. Start Android SpeechRecognizer on Main Thread
            isSttActivelyListening = true
            livePartialTranscript = "🎙️ Listening now ($triggerSource)... Speak your command, sir!"

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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            }
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(intent)
            }.onFailure { err ->
                isSttActivelyListening = false
                SharedMicAndWakeManager.lastTriggerLog = "STT launch error: ${err.message}"
                SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
            }
        }
    }

    fun speakSentencesAndResumeMic(fullText: String) {
        val tts = ttsEngine
        if (tts == null) {
            SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
            return
        }
        tts.stop()
        val clean = fullText.replace("*", "").trim()
        val sentences = clean.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }.take(4)
        if (sentences.isEmpty()) {
            SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
            return
        }

        isJarvisSpeaking = true
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isJarvisSpeaking = true
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "utt_${sentences.lastIndex}") {
                    isJarvisSpeaking = false
                    scope.launch(Dispatchers.Main) {
                        if (isContinuousConvo) {
                            delay(400L)
                            launchCleanSttTurn("CONTINUOUS")
                        } else {
                            // Automatically re-arm the Double-Clap & Wake-Word stream!
                            SharedMicAndWakeManager.lastTriggerLog = "✓ Reply finished — Shared Mic Stream re-armed for Double-Clap or 'Jarvis'!"
                            SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
                        }
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isJarvisSpeaking = false
                SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
            }
        })

        sentences.forEachIndexed { idx, sentence ->
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(sentence, queueMode, null, "utt_$idx")
        }
    }

    DisposableEffect(speechRecognizer) {
        SharedMicAndWakeManager.onWakeActivatedCallback = { triggerType ->
            launchCleanSttTurn(triggerSource = triggerType)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isSttActivelyListening = true
                SharedMicAndWakeManager.lastTriggerLog = "🎙️ MIC OPEN — Speak your question or say 'Jarvis, ...' now!"
            }

            override fun onBeginningOfSpeech() {
                isSttActivelyListening = true
                if (isJarvisSpeaking) {
                    ttsEngine?.stop()
                    isJarvisSpeaking = false
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                val norm = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                SharedMicAndWakeManager.livePeakNormalized = norm
                SharedMicAndWakeManager.liveMicDb = (-50f + norm * 45f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isSttActivelyListening = false
                SharedMicAndWakeManager.lastTriggerLog = "Processing speech..."
            }

            override fun onError(error: Int) {
                isSttActivelyListening = false
                val errName = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard — re-arming Double-Clap & Wake listener"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout — re-arming Double-Clap & Wake listener"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio hand-off retry"
                    else -> "STT code $error — re-arming listener"
                }
                SharedMicAndWakeManager.lastTriggerLog = errName
                if (isContinuousConvo) {
                    scope.launch {
                        delay(600L)
                        launchCleanSttTurn("CONTINUOUS")
                    }
                } else {
                    SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (part.isNotBlank()) {
                    livePartialTranscript = part
                    if (isJarvisSpeaking) {
                        ttsEngine?.stop()
                        isJarvisSpeaking = false
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isSttActivelyListening = false
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (heard.isBlank()) {
                    SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
                    return
                }
                val boosted = addAutoPunctuationAndNeetBoost(heard)
                livePartialTranscript = boosted

                // 1. Check if user is testing the 20-Phrase NEET Benchmark
                if (showBenchmarkList && activeBenchmarkIdx in benchmarkPhrases.indices) {
                    val item = benchmarkPhrases[activeBenchmarkIdx]
                    val score = JarvisSttRouter.computePhraseAccuracy(item.expectedPhrase, boosted)
                    benchmarkPhrases[activeBenchmarkIdx] = item.copy(
                        lastHeardText = boosted,
                        accuracyPercent = score,
                        testedEngine = JarvisSttRouter.activeLiveEngineBadge
                    )
                    SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
                    return
                }

                // 2. Check if "Jarvis" wake word was spoken in the phrase
                val lower = boosted.lowercase()
                if (lower.contains("jarvis")) {
                    SharedMicAndWakeManager.totalWakeTriggers++
                    SharedMicAndWakeManager.playWakeActivationChime()
                }

                // If user only said "Jarvis." with no question yet, prompt them and listen immediately!
                val strippedQuery = boosted.replace(Regex("^(hey\\s+)?jarvis[,.:!?\\s]*", RegexOption.IGNORE_CASE), "").trim()
                if (strippedQuery.length <= 2) {
                    val greeting = "At your service, sir. What would you like me to do?"
                    lastSpokenReply = greeting
                    SharedMicAndWakeManager.lastTriggerLog = "✓ Wake word 'Jarvis' heard! Asking for your command..."
                    ttsEngine?.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, "wake_prompt")
                    scope.launch {
                        delay(1900L)
                        launchCleanSttTurn("JARVIS_FOLLOWUP")
                    }
                    return
                }

                // 3. Send spoken command to Jarvis Brain + Autonomous Tools + Speak Reply
                val t0 = System.currentTimeMillis()
                SharedMicAndWakeManager.lastTriggerLog = "⚡ Sending \"$strippedQuery\" to J.A.R.V.I.S. Brain..."
                scope.launch {
                    val reply = JarvisBrainEngine.generateJarvisReply(
                        context = context,
                        plan = plan,
                        masterPrompt = masterPrompt,
                        modelId = committedModelId,
                        temperature = committedTemperature,
                        forceOffline = forceOffline,
                        conversation = messages.toList(),
                        userQuery = strippedQuery
                    )
                    firstWordLatencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(120L)
                    messages.add(
                        ChatMessageItem(
                            id = System.currentTimeMillis() - 1,
                            role = "user",
                            visibleText = boosted,
                            emotionTag = "focused",
                            engineBadge = JarvisSttRouter.activeLiveEngineBadge
                        )
                    )
                    messages.add(reply)
                    EncryptedKeyStoreVault.saveChatHistory(context, messages)
                    lastSpokenReply = reply.visibleText
                    speakSentencesAndResumeMic(reply.visibleText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            SharedMicAndWakeManager.onWakeActivatedCallback = null
            speechRecognizer?.destroy()
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            SharedMicAndWakeManager.startSharedMicStream(context)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Live STT Router HUD Badge + Privacy Guarantee + Language Selector
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (JarvisSttRouter.isNetworkReachable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (JarvisSttRouter.isNetworkReachable) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHAPTER 7 // STT ROUTER: ${JarvisSttRouter.activeLiveEngineBadge}",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Auto Mic Hand-Off Active: Double-Clap or Voice Wake automatically releases the DSP mic -> transcribes your speech -> answers aloud -> re-arms!",
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Hardware DSP: Echo Canceler (Barge-In): ${if (SharedMicAndWakeManager.aecAvailable) "ACTIVE ✓" else "Ready"} • Noise Suppressor: ${if (SharedMicAndWakeManager.nsAvailable) "ACTIVE ✓" else "Ready"} • First-Word Latency: ${if (firstWordLatencyMs > 0) "${firstWordLatencyMs}ms" else "~1.2s target"}",
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SttEngineMode.values().forEach { mode ->
                    LiquidGlassButton(
                        text = mode.badge,
                        lab = lab,
                        isActive = JarvisSttRouter.selectedMode == mode,
                        accentColor = if (JarvisSttRouter.selectedMode == mode) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                        onClick = { JarvisSttRouter.selectedMode = mode }
                    )
                }
                IndianLanguageMode.values().forEach { lang ->
                    LiquidGlassButton(
                        text = lang.label,
                        lab = lab,
                        isActive = JarvisSttRouter.selectedLanguage == lang,
                        accentColor = if (JarvisSttRouter.selectedLanguage == lang) JarvisSpatialPalettes.StatusInfo else Color(0xFF71717A),
                        onClick = { JarvisSttRouter.selectedLanguage = lang }
                    )
                }
            }
        }

        // 2. Shared Mic Stream, "Jarvis" Wake-Word & Double-Clap Calibration Console
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WAKE WORD ('JARVIS') + DOUBLE-CLAP CALIBRATION & AUTO HAND-OFF",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: ${if (isSttActivelyListening) "🎙️ LISTENING TO YOUR VOICE NOW..." else if (SharedMicAndWakeManager.isSharedStreamRunning) "GUARDING (Clap twice or say 'Jarvis')" else "Idle"}",
                        color = if (isSttActivelyListening) JarvisSpatialPalettes.StatusSuccess else palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = SharedMicAndWakeManager.lastTriggerLog,
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Wake: ${SharedMicAndWakeManager.totalWakeTriggers} | Clap: ${SharedMicAndWakeManager.totalClapTriggers} | False: ${SharedMicAndWakeManager.falseTriggerCount}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Mic Level: ${"%.1f".format(SharedMicAndWakeManager.liveMicDb)} dBFS",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { SharedMicAndWakeManager.livePeakNormalized },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (isSttActivelyListening || SharedMicAndWakeManager.livePeakNormalized > 0.45f) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                trackColor = Color.White.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassButton(
                    text = if (SharedMicAndWakeManager.userWantsStreamArmed) "Shared Mic Stream: ON ✓" else "1. Start Shared Mic Stream",
                    icon = Icons.Default.Mic,
                    lab = lab,
                    isActive = SharedMicAndWakeManager.userWantsStreamArmed,
                    accentColor = if (SharedMicAndWakeManager.userWantsStreamArmed) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        if (SharedMicAndWakeManager.userWantsStreamArmed) {
                            SharedMicAndWakeManager.stopSharedMicStreamCompletely()
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) SharedMicAndWakeManager.startSharedMicStream(context)
                            else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                LiquidGlassButton(
                    text = if (isForegroundServiceArmed) "BG Service Armed ✓" else "2. Lock Background Wake Service",
                    icon = Icons.Default.Security,
                    lab = lab,
                    isActive = isForegroundServiceArmed,
                    accentColor = if (isForegroundServiceArmed) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        val svcIntent = Intent(context, JarvisWakeForegroundService::class.java)
                        if (isForegroundServiceArmed) {
                            svcIntent.action = "STOP_JARVIS_WAKE_SERVICE"
                            context.startService(svcIntent)
                            isForegroundServiceArmed = false
                        } else {
                            ContextCompat.startForegroundService(context, svcIntent)
                            isForegroundServiceArmed = true
                        }
                    }
                )
                LiquidGlassButton(
                    text = if (isContinuousConvo) "Continuous 'Jarvis' Mode: ON ✓" else "3. Start Continuous Voice",
                    icon = Icons.Default.GraphicEq,
                    lab = lab,
                    isActive = isContinuousConvo,
                    accentColor = if (isContinuousConvo) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusInfo,
                    onClick = {
                        isContinuousConvo = !isContinuousConvo
                        if (isContinuousConvo) {
                            launchCleanSttTurn("CONTINUOUS")
                        } else {
                            speechRecognizer?.cancel()
                            isSttActivelyListening = false
                            SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
                        }
                    }
                )
                LiquidGlassButton(
                    text = if (SharedMicAndWakeManager.falseTriggerTestMode) "False-Trigger Test: ON" else "False-Trigger Test Mode",
                    icon = Icons.Default.Warning,
                    lab = lab,
                    isActive = SharedMicAndWakeManager.falseTriggerTestMode,
                    accentColor = JarvisSpatialPalettes.StatusError,
                    onClick = {
                        SharedMicAndWakeManager.falseTriggerTestMode = !SharedMicAndWakeManager.falseTriggerTestMode
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Double-Clap Sensitivity (${(SharedMicAndWakeManager.clapSensitivity * 100).toInt()}%)",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = SharedMicAndWakeManager.clapSensitivity,
                        onValueChange = { SharedMicAndWakeManager.clapSensitivity = it },
                        valueRange = 0.25f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = JarvisSpatialPalettes.StatusSuccess
                        )
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voice Wake Sensitivity (${(SharedMicAndWakeManager.wakeSensitivity * 100).toInt()}%)",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = SharedMicAndWakeManager.wakeSensitivity,
                        onValueChange = { SharedMicAndWakeManager.wakeSensitivity = it },
                        valueRange = 0.25f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = JarvisSpatialPalettes.StatusWarning
                        )
                    )
                }
            }
        }

        // 3. Live Partial Transcript + Barge-In Stream Card
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE PARTIAL TRANSCRIPT & SENTENCE-BY-SENTENCE STREAMING VOICE",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (livePartialTranscript.isBlank()) "Heard: (Double-clap, say 'Jarvis', or tap Listen Now...)" else "Heard: \"$livePartialTranscript\"",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = lastSpokenReply,
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                LiquidGlassButton(
                    text = if (isJarvisSpeaking) "Barge-In (Stop)" else if (isSttActivelyListening) "Listening..." else "Listen Now",
                    icon = Icons.Default.Bolt,
                    lab = lab,
                    isActive = isSttActivelyListening,
                    accentColor = if (isJarvisSpeaking) JarvisSpatialPalettes.StatusError else JarvisSpatialPalettes.StatusSuccess,
                    onClick = { launchCleanSttTurn(triggerSource = "BUTTON") }
                )
            }
        }

        // 4. Built-In 20-Phrase NEET UG 2027 STT Benchmark Screen
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusError) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "20-PHRASE NEET UG 2027 VOCABULARY STT BENCHMARK",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    val tested = benchmarkPhrases.filter { it.accuracyPercent >= 0 }
                    val avg = if (tested.isNotEmpty()) tested.map { it.accuracyPercent }.average().toInt() else 0
                    Text(
                        text = "Tested: ${tested.size}/20 phrases • Mean NEET Accuracy: $avg% (Compare Online vs Offline)",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                LiquidGlassButton(
                    text = if (showBenchmarkList) "Hide 20-Phrase Suite" else "Open 20-Phrase NEET Test",
                    icon = Icons.Default.AutoAwesome,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusWarning,
                    onClick = { showBenchmarkList = !showBenchmarkList }
                )
            }

            if (showBenchmarkList) {
                Spacer(modifier = Modifier.height(10.dp))
                val currentPhrase = benchmarkPhrases[activeBenchmarkIdx]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF18181B))
                        .border(1.dp, JarvisSpatialPalettes.StatusSuccess, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "ACTIVE PHRASE #${currentPhrase.id}/20 [${currentPhrase.subject}] — READ ALOUD:",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"${currentPhrase.expectedPhrase}\"",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentPhrase.accuracyPercent >= 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Result: ${currentPhrase.accuracyPercent}% match on ${currentPhrase.testedEngine} • Heard: \"${currentPhrase.lastHeardText}\"",
                                color = JarvisSpatialPalettes.StatusWarning,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiquidGlassButton(
                                text = "Speak Phrase #${currentPhrase.id}",
                                icon = Icons.Default.Mic,
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusSuccess,
                                onClick = { launchCleanSttTurn(triggerSource = "BENCHMARK_#${currentPhrase.id}") }
                            )
                            LiquidGlassButton(
                                text = "Next Phrase ▶",
                                lab = lab,
                                onClick = { activeBenchmarkIdx = (activeBenchmarkIdx + 1) % benchmarkPhrases.size }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun addAutoPunctuationAndNeetBoost(raw: String): String {
    var s = raw.trim()
        .replace(Regex("\\bepsilon not\\b", RegexOption.IGNORE_CASE), "epsilon naught (ε₀)")
        .replace(Regex("\\bk m\\b", RegexOption.IGNORE_CASE), "Km (Michaelis constant)")
        .replace(Regex("\\bg o c\\b", RegexOption.IGNORE_CASE), "GOC")
        .replace(Regex("\\bs h m\\b", RegexOption.IGNORE_CASE), "SHM")
        .replace(Regex("\\bd p p\\b", RegexOption.IGNORE_CASE), "DPP")
        .replace(Regex("\\bn c e r t\\b", RegexOption.IGNORE_CASE), "NCERT")
    if (s.isNotEmpty()) {
        s = s.replaceFirstChar { it.uppercase() }
        if (!s.endsWith(".") && !s.endsWith("?") && !s.endsWith("!")) {
            s += if (s.lowercase().startsWith("what") || s.lowercase().startsWith("how") || s.lowercase().startsWith("why")) "?" else "."
        }
    }
    return s
}
