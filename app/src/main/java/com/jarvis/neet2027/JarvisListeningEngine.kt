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
// 1. GEMINI LIVE-STYLE AUTOMATED HAND-OFF & UNIVERSAL DOUBLE-CLAP ENGINE
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

    var userWantsStreamArmed by mutableStateOf(true)
    var isSharedStreamRunning by mutableStateOf(false)
    var isHandedOffToStt by mutableStateOf(false)

    var liveMicDb by mutableFloatStateOf(-55f)
    var livePeakNormalized by mutableFloatStateOf(0.05f)
    var doubleClapEnabled by mutableStateOf(true)
    var wakeChimeEnabled by mutableStateOf(true)
    var falseTriggerTestMode by mutableStateOf(false)

    var clapSensitivity by mutableFloatStateOf(0.70f)
    var cooldownSeconds by mutableFloatStateOf(1.8f)

    var totalClapTriggers by mutableIntStateOf(0)
    var falseTriggerCount by mutableIntStateOf(0)
    var lastTriggerLog by mutableStateOf("Double-Clap anywhere to launch Gemini Live mode instantly.")
    var aecAvailable by mutableStateOf(false)
    var nsAvailable by mutableStateOf(false)

    private var audioJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastTriggerMillis = 0L
    private var firstClapMillis = 0L

    var onUniversalDoubleClapCallback: (() -> Unit)? = null

    fun playWakeActivationChime() {
        if (!wakeChimeEnabled) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
        }
    }

    suspend fun pauseStreamForSpeechTurn() {
        isHandedOffToStt = true
        isSharedStreamRunning = false
        audioJob?.cancelAndJoin()
        audioJob = null
        delay(130L)
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
            lastTriggerLog = "Grant Microphone permission to enable universal double-clap wake."
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

            val frameBuffer = ShortArray(1024)
            var ambientRmsFloor = 180f
            var triggered = false

            try {
                recorder.startRecording()
                while (isActive && isSharedStreamRunning && !triggered) {
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

                        // Universal Double-Clap Detector
                        val clapThreshold = (24000f * (1.10f - clapSensitivity)).coerceIn(3500f, 21000f)
                        if (doubleClapEnabled && maxAbs > clapThreshold && rms > ambientRmsFloor * 2.3f && zeroCrossings > 70) {
                            if (firstClapMillis == 0L || (now - firstClapMillis) > 580L) {
                                firstClapMillis = now
                            } else if ((now - firstClapMillis) in 100L..540L) {
                                firstClapMillis = 0L
                                lastTriggerMillis = now
                                val stamp = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(now))
                                if (falseTriggerTestMode) {
                                    falseTriggerCount++
                                    lastTriggerLog = "[$stamp] False-Trigger Test logged double clap."
                                } else {
                                    totalClapTriggers++
                                    lastTriggerLog = "[$stamp] ✓ UNIVERSAL DOUBLE-CLAP! Launching Gemini Live..."
                                    playWakeActivationChime()
                                    triggered = true
                                    break
                                }
                            }
                        }
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching { agc?.release() }
                isSharedStreamRunning = false
            }

            if (triggered) {
                isHandedOffToStt = true
                delay(130L)
                withContext(Dispatchers.Main) {
                    onUniversalDoubleClapCallback?.invoke()
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
    }

    fun getBufferedSecondsAvailable(): Int = RING_BUFFER_SECONDS
}

// ============================================================================
// 2. FOREGROUND SERVICE & REBOOT SURVIVAL
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
                "J.A.R.V.I.S. Gemini Live Continuous Guard",
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
            .setContentTitle("J.A.R.V.I.S. Gemini Live Guard Active")
            .setContentText("Double-clap anywhere to trigger live conversation")
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
// 3. STT ROUTER (ONLINE vs OFFLINE + HYSTERESIS REACHABILITY)
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
                "AUTO -> OFFLINE FALLBACK"
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
            if (consecutiveSuccesses >= 2) isNetworkReachable = true
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
// 4. GEMINI LIVE UI WITH BARGE-IN, AUTO-LOOP & UNIVERSAL DOUBLE-CLAP ROUTER
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

    var isSttActivelyListening by remember { mutableStateOf(false) }
    var isForegroundServiceArmed by remember { mutableStateOf(false) }
    var livePartialTranscript by remember { mutableStateOf("") }
    var lastSpokenReply by remember {
        mutableStateOf("Gemini Live mode is active. Double-clap anywhere on your tablet to talk instantly, or tap 'Start Live Conversation'. You can interrupt me anytime.")
    }
    var firstWordLatencyMs by remember { mutableLongStateOf(0L) }
    var showBenchmarkList by remember { mutableStateOf(false) }
    var activeBenchmarkIdx by remember { mutableIntStateOf(0) }
    val benchmarkPhrases = remember { mutableStateListOf<NeetBenchmarkPhrase>().apply { addAll(JarvisSttRouter.build20NeetBenchmarkPhrases()) } }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isJarvisSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.UK
                engine?.setPitch(0.88f)
                engine?.setSpeechRate(1.05f) // Natural conversational speed
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

    fun launchCleanSttTurn(triggerSource: String = "MANUAL") {
        scope.launch {
            if (isJarvisSpeaking) {
                ttsEngine?.stop()
                isJarvisSpeaking = false
            }
            SharedMicAndWakeManager.pauseStreamForSpeechTurn()

            isSttActivelyListening = true
            livePartialTranscript = "🎙️ Listening now ($triggerSource)..."

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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            }
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(intent)
            }.onFailure { err ->
                isSttActivelyListening = false
                SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
            }
        }
    }

    fun speakShortAndAutoLoop(fullText: String) {
        val tts = ttsEngine
        if (tts == null) {
            SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
            return
        }
        tts.stop()
        // Keep conversational responses short and crisp like Gemini Live unless requested otherwise
        val clean = fullText.replace("*", "").trim()
        val shortResponse = if (clean.length > 280 && !clean.contains("detailed", true)) {
            clean.take(280) + "..."
        } else clean

        val sentences = shortResponse.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }.take(3)
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
                        // INFINITE AUTO-LOOP: Automatically listen again for conversational continuity!
                        delay(250L)
                        launchCleanSttTurn("GEMINI_LIVE_AUTO_LOOP")
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
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isSttActivelyListening = true
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
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                isSttActivelyListening = false
            }
            override fun onError(error: Int) {
                isSttActivelyListening = false
                scope.launch {
                    delay(400L)
                    launchCleanSttTurn("AUTO_RETRY")
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
                    launchCleanSttTurn("EMPTY_RETRY")
                    return
                }
                val boosted = addAutoPunctuationAndNeetBoost(heard)
                livePartialTranscript = boosted

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

                val t0 = System.currentTimeMillis()
                scope.launch {
                    val reply = JarvisBrainEngine.generateJarvisReply(
                        context = context,
                        plan = plan,
                        masterPrompt = masterPrompt,
                        modelId = committedModelId,
                        temperature = committedTemperature,
                        forceOffline = forceOffline,
                        conversation = messages.toList(),
                        userQuery = boosted
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
                    speakShortAndAutoLoop(reply.visibleText)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) SharedMicAndWakeManager.startSharedMicStream(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Gemini Live Controller Card
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
                            text = "GEMINI LIVE MODE // CONTINUOUS BARGE-IN CONVERSATION",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isSttActivelyListening) "🎙️ Listening to you now..." else if (isJarvisSpeaking) "🗣️ Jarvis speaking (Barge-In anytime)..." else "Double-clap anywhere to talk instantly.",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lastSpokenReply,
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                LiquidGlassButton(
                    text = if (isSttActivelyListening || isJarvisSpeaking) "Stop Live Chat" else "Start Live Conversation",
                    icon = Icons.Default.Mic,
                    lab = lab,
                    isActive = isSttActivelyListening || isJarvisSpeaking,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = {
                        if (isSttActivelyListening || isJarvisSpeaking) {
                            speechRecognizer?.cancel()
                            ttsEngine?.stop()
                            isSttActivelyListening = false
                            isJarvisSpeaking = false
                            SharedMicAndWakeManager.resumeStreamAfterSpeechTurn(context)
                        } else {
                            launchCleanSttTurn("START_BUTTON")
                        }
                    }
                )
            }
        }

        // 2. Universal Double-Clap & Background Guard Console
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "UNIVERSAL DOUBLE-CLAP WAKE GUARDIAN",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SharedMicAndWakeManager.lastTriggerLog,
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "Clap Triggers: ${SharedMicAndWakeManager.totalClapTriggers}",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassButton(
                    text = if (SharedMicAndWakeManager.userWantsStreamArmed) "Global Double-Clap: ON ✓" else "1. Enable Global Double-Clap",
                    icon = Icons.Default.Mic,
                    lab = lab,
                    isActive = SharedMicAndWakeManager.userWantsStreamArmed,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
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
                    text = if (isForegroundServiceArmed) "BG Service Armed ✓" else "2. Lock Background Guard Service",
                    icon = Icons.Default.Security,
                    lab = lab,
                    isActive = isForegroundServiceArmed,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
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
            }
        }
    }
}

private fun addAutoPunctuationAndNeetBoost(raw: String): String {
    var s = raw.trim()
        .replace(Regex("\\bepsilon not\\b", RegexOption.IGNORE_CASE), "epsilon naught (ε₀)")
        .replace(Regex("\\bk m\\b", RegexOption.IGNORE_CASE), "Km")
        .replace(Regex("\\bg o c\\b", RegexOption.IGNORE_CASE), "GOC")
        .replace(Regex("\\bs h m\\b", RegexOption.IGNORE_CASE), "SHM")
    if (s.isNotEmpty()) {
        s = s.replaceFirstChar { it.uppercase() }
        if (!s.endsWith(".") && !s.endsWith("?") && !s.endsWith("!")) s += "."
    }
    return s
}
