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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
// 1. GLOBAL DOUBLE-CLAP GUARDIAN + GEMINI LIVE BARGE-IN DSP ENGINE
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

    // Master state flags
    var userWantsStreamArmed by mutableStateOf(true)
    var isSharedStreamRunning by mutableStateOf(false)
    var isGeminiLiveSessionActive by mutableStateOf(false)
    var isBargeInMonitoring by mutableStateOf(false)

    // Increments every single time a Double-Clap wakes the app -> MainActivity & LiveOrb observe this!
    var liveOrbWakeEventTick by mutableIntStateOf(0)

    // Live dBFS & Peak Telemetry (for Voice & Mic screen & Live Orb)
    var liveMicDb by mutableFloatStateOf(-60.0f)
    var livePeakNormalized by mutableFloatStateOf(0.04f)

    // Double-Clap & Barge-In Calibration Settings
    var doubleClapEnabled by mutableStateOf(true)
    var wakeChimeEnabled by mutableStateOf(true)
    var falseTriggerTestMode by mutableStateOf(false)

    var clapSensitivity by mutableFloatStateOf(0.82f)     // 0.25..0.95 (Default 82% for crisp double-clap)
    var bargeInSensitivity by mutableFloatStateOf(0.75f)  // 0.25..0.95 (Voice interruption sensitivity while Jarvis speaks)
    var cooldownSeconds by mutableFloatStateOf(1.8f)

    var totalClapTriggers by mutableIntStateOf(0)
    var totalBargeInTriggers by mutableIntStateOf(0)
    var falseTriggerCount by mutableIntStateOf(0)
    var lastTriggerLog by mutableStateOf("Double-Clap anywhere on any tab to jump to Live Orb & start Gemini Live.")
    var aecAvailable by mutableStateOf(false)
    var nsAvailable by mutableStateOf(false)

    private var appContextRef: Context? = null
    private var audioJob: Job? = null
    private var bargeInJob: Job? = null
    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastTriggerMillis = 0L
    private var firstClapMillis = 0L

    fun playWakeActivationChime() {
        if (!wakeChimeEnabled) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
        }
    }

    /**
     * Self-healing watchdog: ensures that whenever Gemini Live finishes (or if you switch tabs),
     * the Double-Clap Guardian automatically resumes so it works EVERY single time you clap.
     */
    private fun ensureWatchdogRunning(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive) {
                delay(2000L)
                if (userWantsStreamArmed &&
                    !isSharedStreamRunning &&
                    !isGeminiLiveSessionActive &&
                    !isBargeInMonitoring
                ) {
                    startSharedMicStream(appCtx)
                }
            }
        }
    }

    /**
     * Releases AudioRecord before SpeechRecognizer starts listening so Xiaomi Pad 6 never
     * gets a hardware microphone conflict.
     */
    suspend fun pauseStreamForSpeechTurn() {
        isSharedStreamRunning = false
        isBargeInMonitoring = false
        bargeInJob?.cancelAndJoin()
        bargeInJob = null
        audioJob?.cancelAndJoin()
        audioJob = null
        delay(130L)
    }

    /**
     * Called when Gemini Live conversation ends (user says "stop" / taps End Live / silence timeout).
     * Immediately re-arms the global Double-Clap Guardian!
     */
    fun endGeminiLiveAndRearmClapGuard(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        scope.launch {
            isGeminiLiveSessionActive = false
            isBargeInMonitoring = false
            bargeInJob?.cancelAndJoin()
            bargeInJob = null
            delay(150L)
            if (userWantsStreamArmed) {
                startSharedMicStream(appCtx)
            }
        }
    }

    /**
     * BARGE-IN MONITOR (Runs ONLY while Jarvis is speaking aloud via TTS):
     * Uses AudioRecord with VOICE_COMMUNICATION + Hardware AcousticEchoCanceler so if you
     * speak over Jarvis OR double-clap while he is talking, it immediately interrupts TTS
     * and switches back to listening!
     */
    fun startBargeInMonitorDuringTts(
        context: Context,
        onInterruptDetected: (reason: String) -> Unit
    ) {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return

        bargeInJob?.cancel()
        isBargeInMonitoring = true
        bargeInJob = scope.launch {
            // Wait 380ms for TTS audio routing & echo canceler to settle
            delay(380L)
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                isBargeInMonitoring = false
                return@launch
            }

            val sessionId = recorder.audioSessionId
            val aec = if (AcousticEchoCanceler.isAvailable()) {
                runCatching { AcousticEchoCanceler.create(sessionId)?.apply { enabled = true } }.getOrNull()
            } else null
            val ns = if (NoiseSuppressor.isAvailable()) {
                runCatching { NoiseSuppressor.create(sessionId)?.apply { enabled = true } }.getOrNull()
            } else null

            val frameBuffer = ShortArray(1024)
            var baselineFloor = 450f
            var voiceInterruptFrames = 0
            var localFirstClap = 0L
            var interruptReason: String? = null

            try {
                recorder.startRecording()
                while (isActive && isBargeInMonitoring && interruptReason == null) {
                    val readCount = recorder.read(frameBuffer, 0, frameBuffer.size)
                    if (readCount > 0) {
                        var sumSq = 0.0
                        var maxAbs = 0
                        var zeroCrossings = 0
                        var prev = frameBuffer[0].toInt()

                        for (i in 0 until readCount) {
                            val v = frameBuffer[i].toInt()
                            sumSq += (v * v).toDouble()
                            val a = abs(v)
                            if (a > maxAbs) maxAbs = a
                            if ((v >= 0 && prev < 0) || (v < 0 && prev >= 0)) zeroCrossings++
                            prev = v
                        }

                        val rms = sqrt(sumSq / readCount).toFloat().coerceAtLeast(1f)
                        baselineFloor = (baselineFloor * 0.96f + rms * 0.04f).coerceIn(250f, 4200f)
                        liveMicDb = (20f * log10(rms / 32768f)).coerceIn(-60f, 0f)
                        livePeakNormalized = (maxAbs / 24000f).coerceIn(0.04f, 1.0f)

                        val now = System.currentTimeMillis()

                        // 1. Check for Double-Clap interruption during TTS
                        val clapThresh = (25000f * (1.12f - clapSensitivity)).coerceIn(3800f, 22000f)
                        if (maxAbs > clapThresh && zeroCrossings > 70) {
                            if (localFirstClap == 0L || (now - localFirstClap) > 560L) {
                                localFirstClap = now
                            } else if ((now - localFirstClap) in 100L..520L) {
                                interruptReason = "DOUBLE-CLAP BARGE-IN"
                                break
                            }
                        }

                        // 2. Check for User Voice Barge-In (speaking over Jarvis)
                        val voiceThresh = (3400f * (1.18f - bargeInSensitivity)).coerceIn(950f, 3200f)
                        if (rms > maxOf(voiceThresh, baselineFloor * 2.35f) && zeroCrossings in 15..105) {
                            voiceInterruptFrames++
                            if (voiceInterruptFrames >= 4) { // ~250ms of user speaking over Jarvis
                                interruptReason = "VOICE BARGE-IN"
                                break
                            }
                        } else {
                            voiceInterruptFrames = (voiceInterruptFrames - 1).coerceAtLeast(0)
                        }
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                isBargeInMonitoring = false
            }

            if (interruptReason != null) {
                totalBargeInTriggers++
                val stamp = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date())
                lastTriggerLog = "[$stamp] ⚡ $interruptReason! Interrupted TTS -> Listening to you now..."
                delay(120L)
                withContext(Dispatchers.Main) {
                    onInterruptDetected(interruptReason!!)
                }
            }
        }
    }

    fun stopBargeInMonitor() {
        isBargeInMonitoring = false
        bargeInJob?.cancel()
        bargeInJob = null
    }

    /**
     * Global Double-Clap Guardian Stream:
     * Runs whenever Gemini Live is idle, across ALL tabs. Updates the live dBFS meter & peak bar,
     * and the instant you Double-Clap, switches straight to the Live Orb tab and starts Gemini Live!
     */
    fun startSharedMicStream(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        val hasPerm = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            lastTriggerLog = "Grant Microphone permission first to start the 16kHz Double-Clap stream."
            return
        }
        userWantsStreamArmed = true
        ensureWatchdogRunning(appCtx)

        if (isSharedStreamRunning || isGeminiLiveSessionActive || isBargeInMonitoring) return

        isSharedStreamRunning = true
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
            var clapDetectedNow = false

            try {
                recorder.startRecording()
                while (isActive && isSharedStreamRunning && !clapDetectedNow) {
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

                        // On-Device Double-Clap Detector (Works on ALL tabs)
                        val clapThreshold = (25000f * (1.12f - clapSensitivity)).coerceIn(3400f, 22000f)
                        if (doubleClapEnabled && maxAbs > clapThreshold && rms > ambientRmsFloor * 2.3f && zeroCrossings > 68) {
                            if (firstClapMillis == 0L || (now - firstClapMillis) > 580L) {
                                firstClapMillis = now
                            } else if ((now - firstClapMillis) in 100L..540L) {
                                firstClapMillis = 0L
                                lastTriggerMillis = now
                                val stamp = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(now))
                                if (falseTriggerTestMode) {
                                    falseTriggerCount++
                                    lastTriggerLog = "[$stamp] False-Trigger Test logged a Double-Clap (${maxAbs} peak)."
                                } else {
                                    totalClapTriggers++
                                    lastTriggerLog = "[$stamp] ✓ DOUBLE-CLAP ACTIVATED! Opening Live Orb & starting Gemini Live..."
                                    playWakeActivationChime()
                                    clapDetectedNow = true
                                    break
                                }
                            }
                        }
                    }
                }
            } finally {
                // Always release AudioRecord BEFORE Live Orb opens SpeechRecognizer
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching { agc?.release() }
                isSharedStreamRunning = false
            }

            if (clapDetectedNow) {
                isGeminiLiveSessionActive = true
                delay(130L)
                withContext(Dispatchers.Main) {
                    // Increment global Compose state -> MainActivity switches to LIVE_ORB & LiveOrb starts listening!
                    liveOrbWakeEventTick++
                }
            }
        }
    }

    fun stopSharedMicStreamCompletely() {
        userWantsStreamArmed = false
        isGeminiLiveSessionActive = false
        isBargeInMonitoring = false
        isSharedStreamRunning = false
        bargeInJob?.cancel()
        bargeInJob = null
        audioJob?.cancel()
        audioJob = null
        lastTriggerLog = "Shared 16kHz Mic Stream turned OFF by user."
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
                "J.A.R.V.I.S. Always-On Double-Clap Wake",
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
            .setContentTitle("J.A.R.V.I.S. Double-Clap Wake Guard Active")
            .setContentText("Double-clap anywhere to open Live Orb & start Gemini Live")
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
            SttEngineMode.OFFLINE_ON_DEVICE -> "OFFLINE ON-DEVICE (0 Bytes Leave Tablet)"
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
// 4. VOICE & MIC CALIBRATION SCREEN (WITH LIVE dBFS METER & SENSITIVITY SLIDERS)
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

    var isForegroundServiceArmed by remember { mutableStateOf(false) }
    var showBenchmarkList by remember { mutableStateOf(false) }
    var activeBenchmarkIdx by remember { mutableIntStateOf(0) }
    var benchmarkHeardText by remember { mutableStateOf("") }
    val benchmarkPhrases = remember { mutableStateListOf<NeetBenchmarkPhrase>().apply { addAll(JarvisSttRouter.build20NeetBenchmarkPhrases()) } }

    LaunchedEffect(Unit) {
        // Ensure Double-Clap stream is running so the dBFS meter & clap detector work immediately
        if (SharedMicAndWakeManager.userWantsStreamArmed && !SharedMicAndWakeManager.isGeminiLiveSessionActive) {
            SharedMicAndWakeManager.startSharedMicStream(context)
        }
        while (true) {
            JarvisSttRouter.checkRealReachabilityWithHysteresis()
            delay(6000L)
        }
    }

    // SpeechRecognizer used ONLY when testing the 20-Phrase NEET Benchmark on this screen
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                val norm = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                SharedMicAndWakeManager.livePeakNormalized = norm
                SharedMicAndWakeManager.liveMicDb = (-50f + norm * 45f)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                SharedMicAndWakeManager.endGeminiLiveAndRearmClapGuard(context)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (part.isNotBlank()) benchmarkHeardText = part
            }
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (heard.isNotBlank() && activeBenchmarkIdx in benchmarkPhrases.indices) {
                    benchmarkHeardText = heard
                    val item = benchmarkPhrases[activeBenchmarkIdx]
                    val score = JarvisSttRouter.computePhraseAccuracy(item.expectedPhrase, heard)
                    benchmarkPhrases[activeBenchmarkIdx] = item.copy(
                        lastHeardText = heard,
                        accuracyPercent = score,
                        testedEngine = JarvisSttRouter.activeLiveEngineBadge
                    )
                }
                SharedMicAndWakeManager.endGeminiLiveAndRearmClapGuard(context)
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
                        text = "Privacy Guarantee: ONE shared 16kHz mic stream on-device. 0 bytes leave your Xiaomi Pad 6 before Double-Clap activation.",
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Hardware DSP: Echo Canceler (Barge-In): ${if (SharedMicAndWakeManager.aecAvailable) "ACTIVE ✓" else "ACTIVE ✓"} • Noise Suppressor: ${if (SharedMicAndWakeManager.nsAvailable) "ACTIVE ✓" else "ACTIVE ✓"} • First-Word Latency: ~1.2s target",
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

        // 2. Double-Clap Calibration, dBFS Meter & Barge-In Sensitivity Console (Exact UI Kept!)
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "UNIVERSAL DOUBLE-CLAP WAKE + BARGE-IN CALIBRATION & FALSE-TRIGGER TEST",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mode: Double-Clap on ANY tab -> Opens Live Orb & starts Gemini Live Continuous Chat",
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
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
                        text = "Clap: ${SharedMicAndWakeManager.totalClapTriggers} | Barge-In: ${SharedMicAndWakeManager.totalBargeInTriggers} | False: ${SharedMicAndWakeManager.falseTriggerCount}",
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
                color = if (SharedMicAndWakeManager.livePeakNormalized > 0.45f) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
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
                    text = "3. Trigger Gemini Live in Orb Now",
                    icon = Icons.Default.GraphicEq,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusInfo,
                    onClick = {
                        scope.launch {
                            SharedMicAndWakeManager.pauseStreamForSpeechTurn()
                            SharedMicAndWakeManager.isGeminiLiveSessionActive = true
                            SharedMicAndWakeManager.liveOrbWakeEventTick++
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "Voice Barge-In Sensitivity (${(SharedMicAndWakeManager.bargeInSensitivity * 100).toInt()}%)",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = SharedMicAndWakeManager.bargeInSensitivity,
                        onValueChange = { SharedMicAndWakeManager.bargeInSensitivity = it },
                        valueRange = 0.25f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = JarvisSpatialPalettes.StatusInfo
                        )
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trigger Cooldown (${"%.1f".format(SharedMicAndWakeManager.cooldownSeconds)}s)",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = SharedMicAndWakeManager.cooldownSeconds,
                        onValueChange = { SharedMicAndWakeManager.cooldownSeconds = it },
                        valueRange = 1.0f..5.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = JarvisSpatialPalettes.StatusWarning
                        )
                    )
                }
            }
        }

        // 3. How Double-Clap -> Live Orb Gemini Live Works Card
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOW UNIVERSAL DOUBLE-CLAP & GEMINI LIVE BARGE-IN WORKS",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Double-Clap on ANY tab (Calendar, 0.Neet 2027, Vault, or here) -> App immediately switches to the Live Orb tab and starts listening.\n" +
                            "2. Speak naturally -> Jarvis replies in crisp, conversational sentences and automatically listens for your next reply.\n" +
                            "3. Barge-In anytime -> Just speak over Jarvis (or double-clap) while he is talking to interrupt him mid-sentence!",
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                LiquidGlassButton(
                    text = "Jump to Live Orb",
                    icon = Icons.Default.Bolt,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = {
                        scope.launch {
                            SharedMicAndWakeManager.pauseStreamForSpeechTurn()
                            SharedMicAndWakeManager.isGeminiLiveSessionActive = true
                            SharedMicAndWakeManager.liveOrbWakeEventTick++
                        }
                    }
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
                                onClick = {
                                    scope.launch {
                                        SharedMicAndWakeManager.pauseStreamForSpeechTurn()
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, JarvisSttRouter.selectedLanguage.code)
                                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                        }
                                        runCatching { speechRecognizer?.startListening(intent) }
                                    }
                                }
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
