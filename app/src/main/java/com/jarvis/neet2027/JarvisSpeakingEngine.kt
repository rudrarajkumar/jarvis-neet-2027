package com.jarvis.neet2027

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

// ============================================================================
// 1. BRITISH BUTLER PROSODY, VOICE PACK DISCOVERY & NEET PRONUNCIATION LEXICON
// ============================================================================

@Serializable
data class PronunciationLexiconRule(
    val id: Long,
    val writtenToken: String,
    val spokenPhonetic: String,
    val category: String
)

data class ProsodyProfile(
    val emotionTag: String,
    val pitchMultiplier: Float,
    val rateMultiplier: Float,
    val sentencePauseMs: Long,
    val sampleLine: String
)

data class DiscoveredTtsVoice(
    val name: String,
    val localeTag: String,
    val isOfflineInstalled: Boolean,
    val isBritish: Boolean,
    val qualityScore: Int
)

object JarvisVoiceOutputController {
    private const val PREFS_NAME = "jarvis_speaking_chapter8_prefs"
    private const val KEY_SELECTED_VOICE = "selected_tts_voice_name"
    private const val KEY_BASE_PITCH = "base_butler_pitch"
    private const val KEY_BASE_RATE = "base_butler_rate"
    private const val KEY_LEXICON_JSON = "custom_pronunciation_lexicon_json"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val prosodyPresets = listOf(
        ProsodyProfile(
            emotionTag = "calm",
            pitchMultiplier = 0.88f,
            rateMultiplier = 1.04f,
            sentencePauseMs = 180L,
            sampleLine = "Good morning, sir. All four NEET 2027 lecture tracks and your Imphal timetable are standing by."
        ),
        ProsodyProfile(
            emotionTag = "focused",
            pitchMultiplier = 0.90f,
            rateMultiplier = 1.08f,
            sentencePauseMs = 140L,
            sampleLine = "Right then, sir. In Gauss's law, total electric flux equals enclosed charge divided by epsilon naught."
        ),
        ProsodyProfile(
            emotionTag = "urgent",
            pitchMultiplier = 0.94f,
            rateMultiplier = 1.14f,
            sentencePauseMs = 100L,
            sampleLine = "Attention, sir—our ten-thirty Physics slot begins in five minutes. Let us open the ten-minute starter note now."
        ),
        ProsodyProfile(
            emotionTag = "encouraging",
            pitchMultiplier = 0.92f,
            rateMultiplier = 1.05f,
            sentencePauseMs = 170L,
            sampleLine = "Steady on, sir. You have already completed one hundred and ten lectures; ten focused minutes will put us back in command."
        ),
        ProsodyProfile(
            emotionTag = "concerned",
            pitchMultiplier = 0.84f,
            rateMultiplier = 0.96f,
            sentencePauseMs = 240L,
            sampleLine = "Easy now, sir. It is approaching two A.M. in Imphal—our sleep guardrail forbids borrowing from rest to repay backlog."
        ),
        ProsodyProfile(
            emotionTag = "proud",
            pitchMultiplier = 0.91f,
            rateMultiplier = 1.04f,
            sentencePauseMs = 180L,
            sampleLine = "Splendid execution, sir. All four tracks and your fifty-minute active recall block are logged complete."
        ),
        ProsodyProfile(
            emotionTag = "amused",
            pitchMultiplier = 0.93f,
            rateMultiplier = 1.06f,
            sentencePauseMs = 160L,
            sampleLine = "I daresay even a tertiary carbocation is less stable than our study streak today, sir."
        )
    )

    fun getBasePitch(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_BASE_PITCH, 0.88f)

    fun getBaseRate(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_BASE_RATE, 1.05f)

    fun saveBaseVoiceTuning(context: Context, pitch: Float, rate: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_BASE_PITCH, pitch.coerceIn(0.6f, 1.4f))
            .putFloat(KEY_BASE_RATE, rate.coerceIn(0.7f, 1.6f))
            .apply()
    }

    fun getPreferredVoiceName(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SELECTED_VOICE, "") ?: ""

    fun savePreferredVoiceName(context: Context, voiceName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_VOICE, voiceName)
            .apply()
    }

    fun loadLexiconRules(context: Context): List<PronunciationLexiconRule> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LEXICON_JSON, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultNeetLexicon()
            saveLexiconRules(context, defaults)
            return defaults
        }
        return runCatching { json.decodeFromString<List<PronunciationLexiconRule>>(raw) }
            .getOrElse { defaultNeetLexicon() }
    }

    fun saveLexiconRules(context: Context, rules: List<PronunciationLexiconRule>) {
        val raw = runCatching { json.encodeToString(rules) }.getOrDefault("[]")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LEXICON_JSON, raw)
            .apply()
    }

    fun addLexiconRule(context: Context, token: String, phonetic: String, category: String): List<PronunciationLexiconRule> {
        val current = loadLexiconRules(context).toMutableList()
        current.removeAll { it.writtenToken.equals(token.trim(), ignoreCase = true) }
        current.add(
            0,
            PronunciationLexiconRule(
                id = System.currentTimeMillis(),
                writtenToken = token.trim(),
                spokenPhonetic = phonetic.trim(),
                category = category.ifBlank { "Custom" }
            )
        )
        saveLexiconRules(context, current)
        return current
    }

    fun deleteLexiconRule(context: Context, ruleId: Long): List<PronunciationLexiconRule> {
        val updated = loadLexiconRules(context).filterNot { it.id == ruleId }
        saveLexiconRules(context, updated)
        return updated
    }

    /**
     * Converts scientific notation, chemical formulas, and NEET abbreviations into
     * clean phonetic text so TTS speaks them like a real British tutor.
     */
    fun applyLexiconForSpeech(context: Context, rawText: String): String {
        var result = rawText
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .replace("→", " gives ")
            .replace("⇌", " is in equilibrium with ")
            .replace("≤", " less than or equal to ")
            .replace("≥", " greater than or equal to ")
            .replace("×", " times ")
            .replace("÷", " divided by ")
            .replace("∞", " infinity ")
            .replace("∮", " closed surface integral of ")
            .replace("Δ", " delta ")
            .replace("α", " alpha ")
            .replace("β", " beta ")
            .replace("γ", " gamma ")
            .replace("λ", " lambda ")
            .replace("ω", " omega ")
            .replace("μ", " mu ")
            .replace("π", " pi ")
            .replace("θ", " theta ")
            .replace("Φ", " flux phi ")
            .replace("²", " squared ")
            .replace("³", " cubed ")

        val rules = loadLexiconRules(context)
        for (rule in rules) {
            val escaped = Regex.escape(rule.writtenToken)
            val pattern = if (rule.writtenToken.all { it.isLetterOrDigit() }) {
                Regex("\\b$escaped\\b", RegexOption.IGNORE_CASE)
            } else {
                Regex(escaped, RegexOption.IGNORE_CASE)
            }
            result = result.replace(pattern, " ${rule.spokenPhonetic} ")
        }

        return result.replace(Regex("\\s+"), " ").trim()
    }

    fun applyConfiguredVoiceAndProsody(
        context: Context,
        tts: TextToSpeech,
        emotionTag: String
    ): ProsodyProfile {
        val savedVoiceName = getPreferredVoiceName(context)
        val voices = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())

        val chosenVoice: Voice? = if (savedVoiceName.isNotBlank()) {
            voices.firstOrNull { it.name == savedVoiceName }
        } else {
            // Prioritize offline British male neural voices (e.g. rjs, gbd, fis)
            voices.filter { it.locale.language == "en" && it.locale.country.equals("GB", ignoreCase = true) }
                .sortedWith(
                    compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                        .thenByDescending { it.name.contains("rjs", true) || it.name.contains("gbd", true) || it.name.contains("male", true) }
                        .thenByDescending { it.quality }
                )
                .firstOrNull()
        }

        if (chosenVoice != null) {
            runCatching { tts.voice = chosenVoice }
        } else {
            runCatching { tts.language = Locale.UK }
        }

        val profile = prosodyPresets.firstOrNull { it.emotionTag.equals(emotionTag, ignoreCase = true) }
            ?: prosodyPresets.first()
        val userBasePitch = getBasePitch(context)
        val userBaseRate = getBaseRate(context)

        val finalPitch = (userBasePitch * (profile.pitchMultiplier / 0.88f)).coerceIn(0.65f, 1.35f)
        val finalRate = (userBaseRate * (profile.rateMultiplier / 1.04f)).coerceIn(0.75f, 1.50f)

        tts.setPitch(finalPitch)
        tts.setSpeechRate(finalRate)
        return profile
    }

    fun scanInstalledVoices(tts: TextToSpeech?): List<DiscoveredTtsVoice> {
        if (tts == null) return emptyList()
        val rawVoices = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        return rawVoices
            .filter {
                it.locale.language.equals("en", ignoreCase = true) &&
                    (it.locale.country.equals("GB", ignoreCase = true) ||
                        it.locale.country.equals("IN", ignoreCase = true) ||
                        it.locale.country.equals("US", ignoreCase = true))
            }
            .map { v ->
                val features = v.features ?: emptySet()
                val offlineInstalled = !v.isNetworkConnectionRequired && !features.contains("notInstalled")
                DiscoveredTtsVoice(
                    name = v.name,
                    localeTag = "${v.locale.language}-${v.locale.country}",
                    isOfflineInstalled = offlineInstalled,
                    isBritish = v.locale.country.equals("GB", ignoreCase = true),
                    qualityScore = v.quality
                )
            }
            .sortedWith(
                compareByDescending<DiscoveredTtsVoice> { it.isBritish }
                    .thenByDescending { it.isOfflineInstalled }
                    .thenByDescending { it.name.contains("rjs", true) || it.name.contains("gbd", true) }
                    .thenBy { it.name }
            )
            .take(18)
    }

    fun openSystemOfflineTtsInstaller(context: Context) {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    private fun defaultNeetLexicon(): List<PronunciationLexiconRule> = listOf(
        PronunciationLexiconRule(1L, "ε₀", "epsilon naught", "Physics"),
        PronunciationLexiconRule(2L, "ε0", "epsilon naught", "Physics"),
        PronunciationLexiconRule(3L, "K_m", "Michaelis constant K m", "Biology"),
        PronunciationLexiconRule(4L, "V_max", "V max", "Biology"),
        PronunciationLexiconRule(5L, "q_enclosed", "enclosed charge", "Physics"),
        PronunciationLexiconRule(6L, "I_cm", "moment of inertia about center of mass", "Physics"),
        PronunciationLexiconRule(7L, "v_cm", "velocity of center of mass", "Physics"),
        PronunciationLexiconRule(8L, "SN1", "S N 1 unimolecular nucleophilic substitution", "Organic Chem"),
        PronunciationLexiconRule(9L, "SN2", "S N 2 bimolecular nucleophilic substitution", "Organic Chem"),
        PronunciationLexiconRule(10L, "sp3d2", "s p 3 d 2", "Inorganic Chem"),
        PronunciationLexiconRule(11L, "sp3d", "s p 3 d", "Inorganic Chem"),
        PronunciationLexiconRule(12L, "sp3", "s p 3", "Chemistry"),
        PronunciationLexiconRule(13L, "sp2", "s p 2", "Chemistry"),
        PronunciationLexiconRule(14L, "pKa", "p K a", "Organic Chem"),
        PronunciationLexiconRule(15L, "pKb", "p K b", "Chemistry"),
        PronunciationLexiconRule(16L, "pCO2", "partial pressure of C O 2", "Zoology"),
        PronunciationLexiconRule(17L, "pO2", "partial pressure of oxygen", "Zoology"),
        PronunciationLexiconRule(18L, "GOC", "General Organic Chemistry", "Organic Chem"),
        PronunciationLexiconRule(19L, "SHM", "Simple Harmonic Motion", "Physics"),
        PronunciationLexiconRule(20L, "AIIMS", "Aims", "NEET Meta"),
        PronunciationLexiconRule(21L, "PW", "Physics Wallah", "NEET Meta"),
        PronunciationLexiconRule(22L, "NCERT", "N C E R T", "NEET Meta")
    )
}

// ============================================================================
// 2. CHAPTER 8 UI: BRITISH BUTLER VOICE STUDIO, PROSODY & LEXICON EDITOR
// ============================================================================

@Composable
fun JarvisSpeakingScreen(
    lab: GlassLabController,
    tilt: Offset
) {
    val context = LocalContext.current
    val palette = lab.currentPalette

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    val discoveredVoices = remember { mutableStateListOf<DiscoveredTtsVoice>() }
    var selectedVoiceName by remember { mutableStateOf(JarvisVoiceOutputController.getPreferredVoiceName(context)) }
    var basePitch by remember { mutableFloatStateOf(JarvisVoiceOutputController.getBasePitch(context)) }
    var baseRate by remember { mutableFloatStateOf(JarvisVoiceOutputController.getBaseRate(context)) }
    var activeEmotion by remember { mutableStateOf("calm") }
    var statusBanner by remember {
        mutableStateOf("British Butler Voice Engine Ready — Select any offline en-GB voice, tune prosody, or edit NEET pronunciation rules.")
    }

    val lexiconRules = remember {
        mutableStateListOf<PronunciationLexiconRule>().apply {
            addAll(JarvisVoiceOutputController.loadLexiconRules(context))
        }
    }
    var newRuleToken by remember { mutableStateOf("") }
    var newRulePhonetic by remember { mutableStateOf("") }
    var newRuleCategory by remember { mutableStateOf("Physics") }
    var testEquationInput by remember {
        mutableStateOf("In NCERT & PW GOC, Φ = q_enclosed / ε₀, K_m vs V_max in SN1 and sp3d2 hybridisation for AIIMS Nagpur.")
    }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.UK
                val scanned = JarvisVoiceOutputController.scanInstalledVoices(engine)
                discoveredVoices.clear()
                discoveredVoices.addAll(scanned)
                if (selectedVoiceName.isBlank() && scanned.isNotEmpty()) {
                    selectedVoiceName = scanned.first().name
                    JarvisVoiceOutputController.savePreferredVoiceName(context, selectedVoiceName)
                }
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    fun speakAuditionText(rawText: String, emotion: String) {
        val tts = ttsEngine ?: return
        tts.stop()
        JarvisVoiceOutputController.applyConfiguredVoiceAndProsody(context, tts, emotion)
        val phoneticText = JarvisVoiceOutputController.applyLexiconForSpeech(context, rawText)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = Unit
        })
        tts.speak(phoneticText, TextToSpeech.QUEUE_FLUSH, null, "audition_utt")
        statusBanner = "🔊 Speaking [$emotion]: \"$phoneticText\""
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. British Butler Neural Voice Pack Manager & Base Timbre Controls
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = JarvisSpatialPalettes.StatusSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHAPTER 8 // BRITISH BUTLER VOICE PACKS & OFFLINE NEURAL TTS",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusBanner,
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Active Voice: ${selectedVoiceName.ifBlank { "en-GB Default Butler" }} • Pitch: ${"%.2f".format(basePitch)}x • Rate: ${"%.2f".format(baseRate)}x",
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = "Install Offline Voices",
                        icon = Icons.Default.AutoAwesome,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusWarning,
                        onClick = { JarvisVoiceOutputController.openSystemOfflineTtsInstaller(context) }
                    )
                    LiquidGlassButton(
                        text = "Re-Scan Voices",
                        icon = Icons.Default.Refresh,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            val list = JarvisVoiceOutputController.scanInstalledVoices(ttsEngine)
                            discoveredVoices.clear()
                            discoveredVoices.addAll(list)
                            statusBanner = "Scanned ${list.size} English (UK/IN/US) neural voices on your Xiaomi Pad 6."
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Butler Baritone Pitch (${"%.2f".format(basePitch)}x)", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = basePitch,
                        onValueChange = {
                            basePitch = it
                            JarvisVoiceOutputController.saveBaseVoiceTuning(context, basePitch, baseRate)
                        },
                        valueRange = 0.65f..1.25f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = JarvisSpatialPalettes.StatusSuccess
                        )
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Conversational Speed (${"%.2f".format(baseRate)}x)", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = baseRate,
                        onValueChange = {
                            baseRate = it
                            JarvisVoiceOutputController.saveBaseVoiceTuning(context, basePitch, baseRate)
                        },
                        valueRange = 0.80f..1.45f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = JarvisSpatialPalettes.StatusWarning
                        )
                    )
                }
            }

            if (discoveredVoices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "DISCOVERED ON-DEVICE & NETWORK VOICES (TAP TO SELECT & AUDITION):",
                    color = JarvisSpatialPalettes.StatusWarning,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    discoveredVoices.forEach { v ->
                        val isSelected = selectedVoiceName == v.name
                        Box(
                            modifier = Modifier
                                .width(210.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Color(0xFF27272A) else Color(0xFF121216))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) JarvisSpatialPalettes.StatusSuccess else Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    selectedVoiceName = v.name
                                    JarvisVoiceOutputController.savePreferredVoiceName(context, v.name)
                                    speakAuditionText(
                                        "At your service, sir. Voice profile ${v.localeTag} locked and calibrated.",
                                        activeEmotion
                                    )
                                }
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = v.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${v.localeTag} • ${if (v.isOfflineInstalled) "OFFLINE INSTALLED ✓" else "NETWORK VOICE"}",
                                    color = if (v.isOfflineInstalled) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 7-Emotion Prosody & Cadence Studio
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Text(
                text = "7-EMOTION PROSODY ENGINE (TAP ANY EMOTION TAG TO AUDITION CADENCE)",
                color = JarvisSpatialPalettes.StatusWarning,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JarvisVoiceOutputController.prosodyPresets.forEach { preset ->
                    val selected = activeEmotion == preset.emotionTag
                    LiquidGlassButton(
                        text = "[${preset.emotionTag}]",
                        icon = Icons.Default.PlayArrow,
                        lab = lab,
                        isActive = selected,
                        accentColor = if (selected) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                        onClick = {
                            activeEmotion = preset.emotionTag
                            speakAuditionText(preset.sampleLine, preset.emotionTag)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            val currentPreset = JarvisVoiceOutputController.prosodyPresets.firstOrNull { it.emotionTag == activeEmotion }
                ?: JarvisVoiceOutputController.prosodyPresets.first()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141418))
                    .border(1.dp, JarvisSpatialPalettes.StatusSuccess, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "PROSODY PROFILE [${currentPreset.emotionTag.uppercase()}] // Pitch: ${currentPreset.pitchMultiplier}x • Rate: ${currentPreset.rateMultiplier}x • Sentence Pause: ${currentPreset.sentencePauseMs}ms",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${currentPreset.sampleLine}\"",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // 3. NEET UG 2027 Scientific Pronunciation Lexicon Editor & Live Phonetic Tester
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Text(
                text = "NEET UG 2027 SCIENTIFIC PRONUNCIATION LEXICON EDITOR (${lexiconRules.size} ACTIVE RULES)",
                color = JarvisSpatialPalettes.StatusSuccess,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LiquidGlassPillInput(
                value = testEquationInput,
                onValueChange = { testEquationInput = it },
                placeholder = "Type any formula/sentence to test phonetic pronunciation...",
                lab = lab,
                leadingIcon = Icons.Default.Tune,
                trailingContent = {
                    LiquidGlassButton(
                        text = "Speak Phonetic",
                        icon = Icons.Default.VolumeUp,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = { speakAuditionText(testEquationInput, activeEmotion) }
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            val livePhoneticPreview = remember(testEquationInput, lexiconRules.size) {
                JarvisVoiceOutputController.applyLexiconForSpeech(context, testEquationInput)
            }
            Text(
                text = "Phonetic Output: \"$livePhoneticPreview\"",
                color = JarvisSpatialPalettes.StatusWarning,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(0.35f)) {
                    LiquidGlassPillInput(
                        value = newRuleToken,
                        onValueChange = { newRuleToken = it },
                        placeholder = "Symbol (e.g. ΔH)",
                        lab = lab,
                        leadingIcon = Icons.Default.Edit
                    )
                }
                Box(modifier = Modifier.weight(0.45f)) {
                    LiquidGlassPillInput(
                        value = newRulePhonetic,
                        onValueChange = { newRulePhonetic = it },
                        placeholder = "Spoken as (e.g. enthalpy change delta H)",
                        lab = lab,
                        leadingIcon = Icons.Default.VolumeUp
                    )
                }
                LiquidGlassButton(
                    text = "Add Rule",
                    icon = Icons.Default.Add,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = {
                        if (newRuleToken.isNotBlank() && newRulePhonetic.isNotBlank()) {
                            val updated = JarvisVoiceOutputController.addLexiconRule(
                                context = context,
                                token = newRuleToken,
                                phonetic = newRulePhonetic,
                                category = newRuleCategory
                            )
                            lexiconRules.clear()
                            lexiconRules.addAll(updated)
                            statusBanner = "✓ Added pronunciation rule: '${newRuleToken}' -> '${newRulePhonetic}'"
                            newRuleToken = ""
                            newRulePhonetic = ""
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lexiconRules.take(12).forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141418))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "\"${rule.writtenToken}\"  →  \"${rule.spokenPhonetic}\"",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Category: ${rule.category}",
                                color = palette.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LiquidGlassButton(
                                text = "Test",
                                icon = Icons.Default.PlayArrow,
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusSuccess,
                                onClick = { speakAuditionText(rule.writtenToken, activeEmotion) }
                            )
                            LiquidGlassButton(
                                text = "Delete",
                                icon = Icons.Default.Delete,
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusError,
                                onClick = {
                                    val updated = JarvisVoiceOutputController.deleteLexiconRule(context, rule.id)
                                    lexiconRules.clear()
                                    lexiconRules.addAll(updated)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
