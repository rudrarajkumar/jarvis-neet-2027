package com.jarvis.neet2027

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import com.jarvis.neet2027.model.NeetPlanRepository
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// 1. XIAOMI SURVIVAL HELPER & RAM TELEMETRY
// ============================================================================

object XiaomiSurvivalHelper {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun getAvailableRamMb(context: Context): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return Pair(info.availMem / (1024 * 1024), info.totalMem / (1024 * 1024))
    }

    fun openBatteryOptimizationDialog(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure { openAppDetails(context) }
    }

    fun openXiaomiAutostartSettings(context: Context) {
        val xiaomiIntents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        )
        for (candidate in xiaomiIntents) {
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(candidate) }.isSuccess) return
        }
        openAppDetails(context)
    }

    fun openAllFilesAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure { openAppDetails(context) }
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure { openAppDetails(context) }
    }

    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

// ============================================================================
// 2. PALETTES, GLASS CONTROLLER & NAVIGATION TABS
// ============================================================================

data class JarvisPalettePreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val deepCanvasTop: Color,
    val deepCanvasBottom: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val glassSurface: Color,
    val glassHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

object JarvisSpatialPalettes {
    val SiriAuroraPro = JarvisPalettePreset(
        id = "SIRI_AURORA_PRO",
        name = "iOS 18 Siri Aurora Pro",
        subtitle = "Apple Intelligence Cyan + Sapphire + Violet + Rose Glass",
        deepCanvasTop = Color(0xFF070B19),
        deepCanvasBottom = Color(0xFF03050C),
        orbPrimary = Color(0xFF00E5FF),
        orbSecondary = Color(0xFF8B5CF6),
        orbTertiary = Color(0xFFEC4899),
        accentPrimary = Color(0xFF38BDF8),
        accentSecondary = Color(0xFFA855F7),
        accentTertiary = Color(0xFFF43F5E),
        glassSurface = Color(0xAA0F172A),
        glassHighlight = Color(0x66FFFFFF),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFFBAE6FD)
    )

    val VisionOsTitanium = JarvisPalettePreset(
        id = "VISION_OS_TITANIUM",
        name = "Apple visionOS Spatial Glass",
        subtitle = "Frosted Pearlescent Titanium + Warm Indigo & Sky Glow",
        deepCanvasTop = Color(0xFF0F1322),
        deepCanvasBottom = Color(0xFF060811),
        orbPrimary = Color(0xFF60A5FA),
        orbSecondary = Color(0xFFC084FC),
        orbTertiary = Color(0xFF34D399),
        accentPrimary = Color(0xFF93C5FD),
        accentSecondary = Color(0xFFD8B4FE),
        accentTertiary = Color(0xFF6EE7B7),
        glassSurface = Color(0xB31E293B),
        glassHighlight = Color(0x80FFFFFF),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFCBD5E1)
    )

    val CyberpunkNebula = JarvisPalettePreset(
        id = "CYBERPUNK_NEBULA",
        name = "Stark Holo-Nebula",
        subtitle = "Arc-Reactor Cyan + Sunset Gold + Neon Violet",
        deepCanvasTop = Color(0xFF090D1E),
        deepCanvasBottom = Color(0xFF04050A),
        orbPrimary = Color(0xFF06B6D4),
        orbSecondary = Color(0xFFF59E0B),
        orbTertiary = Color(0xFF8B5CF6),
        accentPrimary = Color(0xFF22D3EE),
        accentSecondary = Color(0xFFFBBF24),
        accentTertiary = Color(0xFFC084FC),
        glassSurface = Color(0xAA111827),
        glassHighlight = Color(0x66E0F2FE),
        textPrimary = Color(0xFFF9FAFB),
        textSecondary = Color(0xFFA5F3FC)
    )

    val EmeraldMatrixPro = JarvisPalettePreset(
        id = "EMERALD_PRO",
        name = "Emerald Mint & Jade Studio",
        subtitle = "Refined Emerald #10B981 + Cyber Cyan + Deep Jade",
        deepCanvasTop = Color(0xFF061A17),
        deepCanvasBottom = Color(0xFF020908),
        orbPrimary = Color(0xFF10B981),
        orbSecondary = Color(0xFF06B6D4),
        orbTertiary = Color(0xFF3B82F6),
        accentPrimary = Color(0xFF34D399),
        accentSecondary = Color(0xFF22D3EE),
        accentTertiary = Color(0xFF60A5FA),
        glassSurface = Color(0xAA08221E),
        glassHighlight = Color(0x66A7F3D0),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFF6EE7B7)
    )

    val allPresets = listOf(SiriAuroraPro, VisionOsTitanium, CyberpunkNebula, EmeraldMatrixPro)

    val StatusSuccess = Color(0xFF10B981)
    val StatusWarning = Color(0xFFF59E0B)
    val StatusError = Color(0xFFF43F5E)
    val StatusInfo = Color(0xFF38BDF8)
}

class GlassLabController {
    var activePresetIndex by mutableIntStateOf(0)
    var blurRadiusDp by mutableFloatStateOf(24f)
    var surfaceAlpha by mutableFloatStateOf(0.68f)
    var specularGloss by mutableFloatStateOf(0.55f)
    var auroraGlowIntensity by mutableFloatStateOf(0.75f)
    var bevelDepth by mutableFloatStateOf(0.60f)
    var cornerRadiusDp by mutableFloatStateOf(24f)
    var gyroParallaxStrength by mutableFloatStateOf(0.70f)
    var colorRichness by mutableFloatStateOf(0.85f)

    var performance144HzMode by mutableStateOf(false)
    var reduceTransparency by mutableStateOf(false)
    var highContrastMode by mutableStateOf(false)

    val currentPalette: JarvisPalettePreset
        get() = JarvisSpatialPalettes.allPresets[activePresetIndex.coerceIn(0, JarvisSpatialPalettes.allPresets.lastIndex)]

    fun applyAppleProDefault() {
        activePresetIndex = 0
        blurRadiusDp = 24f
        surfaceAlpha = 0.68f
        specularGloss = 0.55f
        auroraGlowIntensity = 0.75f
        bevelDepth = 0.60f
        cornerRadiusDp = 24f
        gyroParallaxStrength = 0.70f
        colorRichness = 0.85f
        performance144HzMode = false
        reduceTransparency = false
        highContrastMode = false
    }

    fun apply144HzBatterySaver() {
        blurRadiusDp = 8f
        auroraGlowIntensity = 0.35f
        gyroParallaxStrength = 0.20f
        performance144HzMode = true
    }
}

enum class VoiceOrbState {
    IDLE, LISTENING, THINKING, SPEAKING
}

private enum class JarvisNavTab(val label: String, val icon: ImageVector) {
    DASHBOARD("HUD Home", Icons.Default.Dashboard),
    JARVIS_CHAT("Jarvis AI", Icons.Default.Chat),
    BRAIN_ROUTER("AI Engine", Icons.Default.SettingsSuggest),
    GLASS_LAB("Glass Lab", Icons.Default.Tune),
    GALLERY("UI Gallery", Icons.Default.Palette),
    API_VAULT("Key Vault", Icons.Default.Key)
}

// ============================================================================
// 3. HARDWARE KEYSTORE VAULT & CHAT HISTORY STORAGE
// ============================================================================

@Serializable
data class ChatMessageItem(
    val id: Long,
    val role: String, // "user" or "jarvis"
    val visibleText: String,
    val emotionTag: String = "calm",
    val engineBadge: String = "CLOUD",
    val timestampIst: String = ""
)

object EncryptedKeyStoreVault {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "jarvis_neet2027_aes_master_key"
    private const val PREFS_FILE = "jarvis_encrypted_api_vault"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    const val KEY_GEMINI_API = "gemini_api_key"
    const val KEY_PICOVOICE_ACCESS = "picovoice_access_key"
    const val KEY_GITHUB_PAT = "github_fine_grained_pat"
    const val KEY_CLOUD_DATA_SHARING_OFF = "cloud_prompts_disabled"
    private const val KEY_XIAOMI_AUTOSTART_LOCKED = "xiaomi_autostart_locked"
    private const val KEY_CHAT_HISTORY_JSON = "chat_history_json"
    private const val KEY_COMMITTED_MODEL = "committed_model_id"
    private const val KEY_COMMITTED_TEMP = "committed_temperature"

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private fun getOrCreateHardwareKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveSecret(context: Context, keyName: String, plainValue: String) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (plainValue.isBlank()) {
            prefs.edit().remove(keyName).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateHardwareKey())
        val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cipherBytes = cipher.doFinal(plainValue.toByteArray(Charsets.UTF_8))
        val cipherBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        prefs.edit().putString(keyName, "$ivBase64:$cipherBase64").apply()
    }

    fun readSecret(context: Context, keyName: String): String {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val packed = prefs.getString(keyName, null) ?: return ""
        val parts = packed.split(":")
        if (parts.size != 2) return ""
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateHardwareKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun maskSecret(secret: String): String {
        if (secret.isBlank()) return "Not configured"
        if (secret.length <= 8) return "••••••••"
        return "${secret.take(4)}••••••••${secret.takeLast(4)}"
    }

    fun isOfflinePrivacyLockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_CLOUD_DATA_SHARING_OFF, false)

    fun setOfflinePrivacyLock(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CLOUD_DATA_SHARING_OFF, enabled).apply()
    }

    fun isXiaomiAutostartLocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_XIAOMI_AUTOSTART_LOCKED, true)

    fun setXiaomiAutostartLocked(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_XIAOMI_AUTOSTART_LOCKED, locked).apply()
    }

    fun saveChatHistory(context: Context, messages: List<ChatMessageItem>) {
        val raw = runCatching { jsonFormat.encodeToString(messages.takeLast(60)) }.getOrDefault("[]")
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_CHAT_HISTORY_JSON, raw).apply()
    }

    fun loadChatHistory(context: Context): List<ChatMessageItem> {
        val raw = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_CHAT_HISTORY_JSON, null) ?: return emptyList()
        return runCatching { jsonFormat.decodeFromString<List<ChatMessageItem>>(raw) }.getOrDefault(emptyList())
    }

    fun getCommittedModel(context: Context): String =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_COMMITTED_MODEL, "") ?: ""

    fun getCommittedTemp(context: Context): Float =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getFloat(KEY_COMMITTED_TEMP, 0.4f)

    fun saveCommittedEngine(context: Context, modelId: String, temp: Float) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COMMITTED_MODEL, modelId)
            .putFloat(KEY_COMMITTED_TEMP, temp)
            .apply()
    }
}

// ============================================================================
// 4. DYNAMIC CLOUD MODEL SCANNER, HYBRID ROUTER & OFFLINE NEET BRAIN
// ============================================================================

data class DiscoveredModelInfo(
    val id: String, // e.g., "gemini-2.5-flash"
    val displayName: String,
    val description: String,
    val isRecommendedFree: Boolean
)

object JarvisBrainEngine {
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val emotionRegex = Regex("""^\s*\[(calm|amused|concerned|proud|urgent|focused|encouraging)\]\s*""", RegexOption.IGNORE_CASE)

    fun parseEmotionAndCleanText(rawText: String): Pair<String, String> {
        var detectedEmotion = "calm"
        val firstMatch = emotionRegex.find(rawText)
        if (firstMatch != null) {
            detectedEmotion = firstMatch.groupValues[1].lowercase()
        }
        val cleaned = rawText
            .replace(Regex("""\[(calm|amused|concerned|proud|urgent|focused|encouraging)\]\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        return Pair(detectedEmotion, if (cleaned.isEmpty()) rawText.trim() else cleaned)
    }

    fun formatScientificNotation(text: String): String {
        return text
            .replace("\\rightarrow", " → ")
            .replace("->", " → ")
            .replace("<=>", " ⇌ ")
            .replace("\\rightleftharpoons", " ⇌ ")
            .replace("\\Delta", "Δ")
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\lambda", "λ")
            .replace("\\omega", "ω")
            .replace("\\mu", "μ")
            .replace("\\pi", "π")
            .replace("H+", "H⁺")
            .replace("OH-", "OH⁻")
            .replace("e-", "e⁻")
            .replace("^2", "²")
            .replace("^3", "³")
            .replace("_2", "₂")
            .replace("_3", "₃")
            .replace("_4", "₄")
            .replace("$", "")
    }

    suspend fun scanAvailableGeminiModels(apiKey: String): Result<List<DiscoveredModelInfo>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("No Gemini API key configured in Key Vault."))
        }
        runCatching {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from Google AI Studio: ${body.take(180)}")
            }
            val root = jsonParser.parseToJsonElement(body).jsonObject
            val modelsArray = root["models"]?.jsonArray ?: emptyList()

            val discovered = modelsArray.mapNotNull { elem ->
                val obj = elem.jsonObject
                val rawName = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val shortId = rawName.removePrefix("models/")
                val methods = obj["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                if (!methods.contains("generateContent")) return@mapNotNull null
                if (!shortId.startsWith("gemini", ignoreCase = true)) return@mapNotNull null
                val display = obj["displayName"]?.jsonPrimitive?.content ?: shortId
                val desc = obj["description"]?.jsonPrimitive?.content ?: "Supports generateContent"
                val isFlashFree = shortId.contains("flash", ignoreCase = true) && !shortId.contains("exp-image", ignoreCase = true)
                DiscoveredModelInfo(
                    id = shortId,
                    displayName = display,
                    description = desc,
                    isRecommendedFree = isFlashFree
                )
            }.sortedWith(
                compareByDescending<DiscoveredModelInfo> { it.isRecommendedFree }
                    .thenByDescending { it.id.contains("2.5") || it.id.contains("3") }
                    .thenByDescending { it.id.contains("flash") }
            )

            if (discovered.isEmpty()) {
                throw IllegalStateException("No generateContent Gemini models returned for this key.")
            }
            discovered
        }
    }

    suspend fun generateJarvisReply(
        context: Context,
        plan: NeetPlanRoot,
        masterPrompt: String,
        modelId: String,
        temperature: Float,
        forceOffline: Boolean,
        conversation: List<ChatMessageItem>,
        userQuery: String
    ): ChatMessageItem = withContext(Dispatchers.IO) {
        val nowIst = NeetPlanRepository.getImphalNow()
        val timeStamp = nowIst.format(DateTimeFormatter.ofPattern("hh:mm a 'IST'"))
        val activeSlot = NeetPlanRepository.getCurrentTimetableSlot(plan, nowIst.toLocalTime())
        val todayLectures = NeetPlanRepository.getAssignmentsForDate(plan, nowIst.toLocalDate())
        val lecturesSummary = if (todayLectures.isEmpty()) {
            "Sunday Recovery / Test Guide Day"
        } else {
            todayLectures.joinToString(" | ") { "${it.trackId}: ${it.chapterTitle} (L${it.lectureNumberInChapter}/${it.totalLecturesInChapter})" }
        }

        val apiKey = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API)
        val privacyLock = EncryptedKeyStoreVault.isOfflinePrivacyLockEnabled(context)

        if (forceOffline || privacyLock || apiKey.isBlank()) {
            val offlineText = buildOfflinePlanReply(plan, activeSlot.title, activeSlot.instruction, lecturesSummary, userQuery)
            val (emotion, cleaned) = parseEmotionAndCleanText(offlineText)
            return@withContext ChatMessageItem(
                id = System.currentTimeMillis(),
                role = "jarvis",
                visibleText = formatScientificNotation(cleaned),
                emotionTag = emotion,
                engineBadge = "OFFLINE BRAIN (0 BYTES SENT)",
                timestampIst = timeStamp
            )
        }

        // Dynamic Model Resolution: never hard-code; if empty, scan once automatically
        val resolvedModel = if (modelId.isNotBlank()) {
            modelId
        } else {
            scanAvailableGeminiModels(apiKey).getOrNull()?.firstOrNull()?.id ?: ""
        }

        if (resolvedModel.isBlank()) {
            val fallback = buildOfflinePlanReply(plan, activeSlot.title, activeSlot.instruction, lecturesSummary, userQuery)
            val (emotion, cleaned) = parseEmotionAndCleanText(fallback)
            return@withContext ChatMessageItem(
                id = System.currentTimeMillis(),
                role = "jarvis",
                visibleText = formatScientificNotation(cleaned),
                emotionTag = emotion,
                engineBadge = "OFFLINE FALLBACK (SCAN NEEDED)",
                timestampIst = timeStamp
            )
        }

        val liveContextHeader = """
            $masterPrompt
            
            LIVE IMPHAL EAST TELEMETRY:
            - Local Time: $timeStamp (${nowIst.toLocalDate()})
            - Active Timetable Slot: ${activeSlot.title} (${activeSlot.startTime}-${activeSlot.endTime}) -> ${activeSlot.instruction}
            - Today's 4-Track Queue: $lecturesSummary
        """.trimIndent()

        val cloudCall = runCatching {
            val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:generateContent?key=$apiKey")
            val payload = buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", liveContextHeader) }
                    }
                }
                putJsonArray("contents") {
                    conversation.takeLast(8).forEach { msg ->
                        addJsonObject {
                            put("role", if (msg.role == "user") "user" else "model")
                            putJsonArray("parts") {
                                addJsonObject { put("text", msg.visibleText) }
                            }
                        }
                    }
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", userQuery) }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", temperature.toDouble())
                    put("maxOutputTokens", 512)
                }
            }

            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${responseBody.take(160)}")
            }
            val json = jsonParser.parseToJsonElement(responseBody).jsonObject
            val candidates = json["candidates"]?.jsonArray
            val firstText = candidates?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
                ?: throw IllegalStateException("Empty response from $resolvedModel")
            firstText
        }

        return@withContext cloudCall.fold(
            onSuccess = { rawReply ->
                val (emotion, cleaned) = parseEmotionAndCleanText(rawReply)
                ChatMessageItem(
                    id = System.currentTimeMillis(),
                    role = "jarvis",
                    visibleText = formatScientificNotation(cleaned),
                    emotionTag = emotion,
                    engineBadge = "CLOUD // $resolvedModel",
                    timestampIst = timeStamp
                )
            },
            onFailure = { err ->
                // Automatic Hybrid Router fallback to Offline Brain on network drop or HTTP 429 rate-limit
                val offlineReply = buildOfflinePlanReply(plan, activeSlot.title, activeSlot.instruction, lecturesSummary, userQuery)
                val (emotion, cleaned) = parseEmotionAndCleanText(offlineReply)
                ChatMessageItem(
                    id = System.currentTimeMillis(),
                    role = "jarvis",
                    visibleText = formatScientificNotation("$cleaned\n\n(Auto-routed to Offline Brain: ${err.message?.take(80)})"),
                    emotionTag = emotion,
                    engineBadge = "AUTO-OFFLINE FALLBACK",
                    timestampIst = timeStamp
                )
            }
        )
    }

    private fun buildOfflinePlanReply(
        plan: NeetPlanRoot,
        slotTitle: String,
        slotInstruction: String,
        lecturesSummary: String,
        query: String
    ): String {
        val q = query.lowercase()
        return when {
            q.contains("what now") || q.contains("slot") || q.contains("timetable") || q.contains("schedule") ->
                "[focused] Right then, sir, your Imphal IST timetable places us in **$slotTitle**. $slotInstruction Today's four tracks are: $lecturesSummary."
            q.contains("electric") || q.contains("charge") || q.contains("coulomb") || q.contains("gauss") ->
                "[focused] In NCERT Class 12 Physics Chapter 1 (Electric Charges & Fields), Coulomb's law states F = (1 / 4πε₀) · (|q₁q₂| / r²), and Gauss's flux law is Φ = ∮ E · dA = q_enclosed / ε₀. Remember sir, for a dipole on the axial line E_axial = 2kp/r³, exactly double the equatorial magnitude E_eq = kp/r³."
            q.contains("goc") || q.contains("inductive") || q.contains("hyperconjugation") || q.contains("resonance") ->
                "[calm] Ah, General Organic Chemistry, sir—priority order for electronic effects in NCERT is **Aromaticity > Resonance (+M/-M) > Hyperconjugation (α-H count) > Inductive (+I/-I)**. When checking carbocation or alkene stability, always count α-hydrogens before inductive groups."
            q.contains("biomolecule") || q.contains("enzyme") || q.contains("amino") ->
                "[encouraging] For Zoology Biomolecules (NCERT Class 11 Chapter 9), keep the Michaelis constant K_m crisp in mind, sir: a lower K_m means higher enzyme-substrate affinity, and competitive inhibitors (like malonate on succinic dehydrogenase) raise apparent K_m without changing V_max."
            q.contains("oscillation") || q.contains("shm") || q.contains("pendulum") ->
                "[focused] In Track 4 Oscillations (NCERT Class 11 Chapter 14), displacement x(t) = A sin(ωt + φ), velocity v = ω√(A² - x²), and total mechanical energy E = ½mω²A² stays constant while kinetic and potential energies oscillate at twice the frequency (2f), sir."
            q.contains("tired") || q.contains("sleep") || q.contains("stressed") || q.contains("backlog") ->
                "[concerned] Easy now, sir—our core rule forbids borrowing from your 02:00–10:00 sleep guardrail to repay a missed lecture. Let us execute the Bad-Day Minimum (one main lecture, 20 minutes closed-book recall, and a written 10-minute start for tomorrow) and take a 15-minute water break."
            else ->
                "[calm] Very good, sir. I am currently running on your local NEET 2027 Plan Engine (0 bytes leaving your Xiaomi Pad 6). We are in **$slotTitle** ($slotInstruction), targeting ${plan.meta.goalInstitution} (Open AIR ≤ ${plan.meta.aiimsNagpurCutoffs2026Round1.ASPIRATIONAL_OPEN_BUFFER}). Add your free Gemini API key in the **Key Vault** tab and tap **Scan & Apply** in **AI Engine** whenever you wish to unlock live cloud derivations."
        }
    }
}

// ============================================================================
// 5. MAIN ACTIVITY & ROOT SPATIAL SHELL
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                JarvisChapterThreeApp()
            }
        }
    }
}

@Composable
fun JarvisChapterThreeApp() {
    val context = LocalContext.current
    val plan = remember { NeetPlanRepository.loadPlan(context) }
    val masterPrompt = remember { NeetPlanRepository.loadMasterPrompt(context) }
    val lab = remember { GlassLabController() }
    val palette = lab.currentPalette

    var selectedTab by remember { mutableStateOf(JarvisNavTab.JARVIS_CHAT) }
    var orbState by remember { mutableStateOf(VoiceOrbState.IDLE) }
    var showBootOverlay by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }

    // Committed vs Staged Engine State (for the APPLY & Self-Test button)
    var committedModelId by remember { mutableStateOf(EncryptedKeyStoreVault.getCommittedModel(context)) }
    var committedTemperature by remember { mutableFloatStateOf(EncryptedKeyStoreVault.getCommittedTemp(context)) }
    var forceOfflineRouter by remember { mutableStateOf(false) }
    val discoveredModels = remember { mutableStateListOf<DiscoveredModelInfo>() }

    // Persistent Chat History
    val chatMessages = remember {
        val loaded = EncryptedKeyStoreVault.loadChatHistory(context)
        mutableStateListOf<ChatMessageItem>().apply {
            if (loaded.isEmpty()) {
                add(
                    ChatMessageItem(
                        id = 1L,
                        role = "jarvis",
                        visibleText = "Good day, sir. Your NEET UG 2027 study brain is online for Imphal East (Asia/Kolkata). Ask me what to study right now, test a Physics/Chemistry/Biology concept, or tap 'AI Engine' on the left rail to scan your free Gemini models.",
                        emotionTag = "calm",
                        engineBadge = "SYSTEM READY",
                        timestampIst = "ONLINE"
                    )
                )
            } else {
                addAll(loaded)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(1000L)
        showBootOverlay = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            tick++
        }
    }

    val tilt = rememberGyroscopeTilt(lab.gyroParallaxStrength)

    Box(modifier = Modifier.fillMaxSize()) {
        LivingAuroraBackground(
            lab = lab,
            orbState = orbState,
            tilt = tilt
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SpatialNavigationRail(
                lab = lab,
                selectedTab = selectedTab,
                orbState = orbState,
                onSelectTab = { selectedTab = it },
                onCycleOrb = {
                    val values = VoiceOrbState.values()
                    orbState = values[(orbState.ordinal + 1) % values.size]
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TopSpatialHeader(
                    plan = plan,
                    lab = lab,
                    committedModelId = committedModelId,
                    forceOffline = forceOfflineRouter || EncryptedKeyStoreVault.isOfflinePrivacyLockEnabled(context),
                    tick = tick,
                    tilt = tilt
                )

                when (selectedTab) {
                    JarvisNavTab.DASHBOARD -> DashboardSpatialContent(
                        plan = plan,
                        masterPrompt = masterPrompt,
                        lab = lab,
                        tick = tick,
                        tilt = tilt
                    )
                    JarvisNavTab.JARVIS_CHAT -> JarvisGlassChatScreen(
                        plan = plan,
                        masterPrompt = masterPrompt,
                        lab = lab,
                        messages = chatMessages,
                        committedModelId = committedModelId,
                        committedTemperature = committedTemperature,
                        forceOffline = forceOfflineRouter,
                        onToggleOffline = { forceOfflineRouter = !forceOfflineRouter },
                        onOrbStateChange = { orbState = it },
                        tilt = tilt
                    )
                    JarvisNavTab.BRAIN_ROUTER -> BrainRouterAndScannerScreen(
                        lab = lab,
                        discoveredModels = discoveredModels,
                        committedModelId = committedModelId,
                        committedTemperature = committedTemperature,
                        forceOffline = forceOfflineRouter,
                        onToggleOffline = { forceOfflineRouter = !forceOfflineRouter },
                        onCommitSuccess = { newModel, newTemp ->
                            committedModelId = newModel
                            committedTemperature = newTemp
                            EncryptedKeyStoreVault.saveCommittedEngine(context, newModel, newTemp)
                        },
                        tilt = tilt
                    )
                    JarvisNavTab.GLASS_LAB -> GlassLabScreen(lab = lab, tilt = tilt)
                    JarvisNavTab.GALLERY -> ComponentGalleryScreen(
                        lab = lab,
                        orbState = orbState,
                        onCycleOrbState = {
                            val values = VoiceOrbState.values()
                            orbState = values[(orbState.ordinal + 1) % values.size]
                        },
                        tilt = tilt
                    )
                    JarvisNavTab.API_VAULT -> EncryptedApiKeyVaultScreen(lab = lab, tilt = tilt)
                }
            }
        }

        AnimatedVisibility(
            visible = showBootOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE04060E))
                    .clickable { showBootOverlay = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ArcReactorVoiceOrb(
                        state = VoiceOrbState.SPEAKING,
                        lab = lab,
                        onClick = { showBootOverlay = false },
                        sizeDp = 124.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "J.A.R.V.I.S. SPATIAL OS // CHAPTER 3 BRAIN ONLINE",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Loading NEET UG 2027 Master Prompt & Hybrid Cloud/Offline Router... (Tap to skip)",
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// 6. CHAPTER 3: GEMINI-STYLE MULTI-COLOUR GLASS CHAT SCREEN
// ============================================================================

@Composable
private fun JarvisGlassChatScreen(
    plan: NeetPlanRoot,
    masterPrompt: String,
    lab: GlassLabController,
    messages: MutableList<ChatMessageItem>,
    committedModelId: String,
    committedTemperature: Float,
    forceOffline: Boolean,
    onToggleOffline: () -> Unit,
    onOrbStateChange: (VoiceOrbState) -> Unit,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lab.currentPalette

    var userDraft by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    val privacyOff = EncryptedKeyStoreVault.isOfflinePrivacyLockEnabled(context) || forceOffline

    val suggestionChips = listOf(
        "What should I do right now, Jarvis?",
        "Explain Coulomb's Law & Dipole Field (NCERT)",
        "GOC: Priority order of electronic effects",
        "Biomolecules: Explain Km & competitive inhibition",
        "Oscillations: Total energy in SHM formula",
        "I feel tired / behind schedule—re-plan gently"
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Live Privacy & Hybrid Router Status Bar
        LiquidGlassCard(lab = lab, tilt = tilt, contentPadding = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (privacyOff) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = if (privacyOff) JarvisSpatialPalettes.StatusSuccess else palette.accentPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (privacyOff) {
                                "OFFLINE MODE ACTIVE // 0 BYTES LEAVE TABLET"
                            } else {
                                "HYBRID CLOUD BRAIN // ${committedModelId.ifBlank { "Auto-Scan on first message" }}"
                            },
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (privacyOff) {
                                "Using local NEET 2027 Plan + NCERT vault only."
                            } else {
                                "Data leaving tablet: text prompt only (no personal identity, no audio, no files)."
                            },
                            color = palette.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = if (privacyOff) "Offline Locked" else "Cloud Active",
                        icon = if (privacyOff) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        lab = lab,
                        isActive = !privacyOff,
                        accentColor = if (privacyOff) JarvisSpatialPalettes.StatusWarning else JarvisSpatialPalettes.StatusInfo,
                        onClick = onToggleOffline
                    )
                    LiquidGlassButton(
                        text = "Regenerate",
                        icon = Icons.Default.Refresh,
                        lab = lab,
                        onClick = {
                            val lastUser = messages.lastOrNull { it.role == "user" }?.visibleText
                            if (lastUser != null && !isStreaming) {
                                if (messages.lastOrNull()?.role == "jarvis" && messages.size > 1) {
                                    messages.removeAt(messages.lastIndex)
                                }
                                isStreaming = true
                                onOrbStateChange(VoiceOrbState.THINKING)
                                scope.launch {
                                    val reply = JarvisBrainEngine.generateJarvisReply(
                                        context = context,
                                        plan = plan,
                                        masterPrompt = masterPrompt,
                                        modelId = committedModelId,
                                        temperature = committedTemperature,
                                        forceOffline = forceOffline,
                                        conversation = messages.toList(),
                                        userQuery = lastUser
                                    )
                                    messages.add(reply)
                                    EncryptedKeyStoreVault.saveChatHistory(context, messages)
                                    isStreaming = false
                                    onOrbStateChange(VoiceOrbState.IDLE)
                                }
                            }
                        }
                    )
                }
            }
        }

        // Search History Bar & Suggestion Chips
        LiquidGlassCard(lab = lab, tilt = tilt, contentPadding = 14.dp) {
            LiquidGlassPillInput(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                placeholder = "Search chat history (e.g., Coulomb, GOC, Biomolecules, timetable)...",
                lab = lab,
                leadingIcon = Icons.Default.Search,
                trailingContent = {
                    if (searchFilter.isNotEmpty()) {
                        LiquidGlassButton(
                            text = "Clear",
                            lab = lab,
                            onClick = { searchFilter = "" }
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestionChips.forEach { chip ->
                    LiquidGlassButton(
                        text = chip,
                        icon = Icons.Default.AutoAwesome,
                        lab = lab,
                        accentColor = palette.accentSecondary,
                        onClick = { userDraft = chip }
                    )
                }
            }
        }

        // Glass Chat Message Stream
        val visibleMessages = remember(messages.size, searchFilter) {
            if (searchFilter.isBlank()) messages.toList()
            else messages.filter { it.visibleText.contains(searchFilter, ignoreCase = true) }
        }

        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "J.A.R.V.I.S. CONVERSATION STREAM (${visibleMessages.size} MESSAGES)",
                    color = palette.accentPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                LiquidGlassButton(
                    text = "Edit Last Prompt",
                    icon = Icons.Default.Edit,
                    lab = lab,
                    onClick = {
                        val lastUser = messages.lastOrNull { it.role == "user" }
                        if (lastUser != null) {
                            userDraft = lastUser.visibleText
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visibleMessages.forEach { msg ->
                    val isUser = msg.role == "user"
                    val bubbleAccent = if (isUser) palette.accentSecondary else palette.accentPrimary
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isUser) {
                                            listOf(
                                                palette.accentSecondary.copy(alpha = 0.34f),
                                                palette.accentTertiary.copy(alpha = 0.22f)
                                            )
                                        } else {
                                            listOf(
                                                palette.accentPrimary.copy(alpha = 0.24f),
                                                palette.glassSurface.copy(alpha = 0.85f)
                                            )
                                        }
                                    )
                                )
                                .border(
                                    width = 1.2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.55f),
                                            bubbleAccent.copy(alpha = 0.75f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUser) "SIR // IMPHAL EAST" else "J.A.R.V.I.S. // ${msg.engineBadge}",
                                    color = bubbleAccent,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                if (!isUser) {
                                    Text(
                                        text = "PROSODY: ${msg.emotionTag.uppercase()} • ${msg.timestampIst}",
                                        color = palette.textSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = msg.visibleText,
                                color = palette.textPrimary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                if (isStreaming) {
                    Text(
                        text = "J.A.R.V.I.S. isconsulting your NEET 2027 timetable & NCERT brain...",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Floating Glass Pill Chat Input
        LiquidGlassPillInput(
            value = userDraft,
            onValueChange = { userDraft = it },
            placeholder = "Message J.A.R.V.I.S., sir (e.g. 'What now?' or 'Derive electric dipole field')...",
            lab = lab,
            leadingIcon = Icons.Default.Mic,
            trailingContent = {
                LiquidGlassButton(
                    text = if (isStreaming) "Thinking..." else "Ask Jarvis",
                    icon = Icons.Default.Send,
                    lab = lab,
                    accentColor = palette.accentPrimary,
                    onClick = {
                        val cleanPrompt = userDraft.trim()
                        if (cleanPrompt.isNotEmpty() && !isStreaming) {
                            userDraft = ""
                            val nowStamp = NeetPlanRepository.getImphalNow()
                                .format(DateTimeFormatter.ofPattern("hh:mm a 'IST'"))
                            messages.add(
                                ChatMessageItem(
                                    id = System.currentTimeMillis(),
                                    role = "user",
                                    visibleText = cleanPrompt,
                                    emotionTag = "focused",
                                    engineBadge = "TABLET",
                                    timestampIst = nowStamp
                                )
                            )
                            isStreaming = true
                            onOrbStateChange(VoiceOrbState.THINKING)
                            scope.launch {
                                val reply = JarvisBrainEngine.generateJarvisReply(
                                    context = context,
                                    plan = plan,
                                    masterPrompt = masterPrompt,
                                    modelId = committedModelId,
                                    temperature = committedTemperature,
                                    forceOffline = forceOffline,
                                    conversation = messages.toList(),
                                    userQuery = cleanPrompt
                                )
                                messages.add(reply)
                                EncryptedKeyStoreVault.saveChatHistory(context, messages)
                                isStreaming = false
                                onOrbStateChange(VoiceOrbState.SPEAKING)
                                delay(1200L)
                                onOrbStateChange(VoiceOrbState.IDLE)
                            }
                        }
                    }
                )
            }
        )
    }
}

// ============================================================================
// 7. CHAPTER 3: DYNAMIC MODEL SCANNER & ENGINE 'APPLY' BUTTON WITH ROLLBACK
// ============================================================================

@Composable
private fun BrainRouterAndScannerScreen(
    lab: GlassLabController,
    discoveredModels: MutableList<DiscoveredModelInfo>,
    committedModelId: String,
    committedTemperature: Float,
    forceOffline: Boolean,
    onToggleOffline: () -> Unit,
    onCommitSuccess: (String, Float) -> Unit,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lab.currentPalette

    var stagedModelId by remember(committedModelId) { mutableStateOf(committedModelId) }
    var stagedTemp by remember(committedTemperature) { mutableFloatStateOf(committedTemperature) }
    var scannerStatus by remember {
        mutableStateOf("Tap 'Scan My Gemini Key' to dynamically query Google AI Studio's live v1beta endpoint.")
    }
    var applyStatus by remember {
        mutableStateOf("Committed Model: ${committedModelId.ifBlank { "Auto-Flash" }} | Temp: ${"%.2f".format(committedTemperature)}")
    }
    var isScanning by remember { mutableStateOf(false) }
    var isTestingApply by remember { mutableStateOf(false) }

    val (availRamMb, totalRamMb) = remember { XiaomiSurvivalHelper.getAvailableRamMb(context) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // RAM & Hybrid Router Card
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Text(
                text = "DYNAMIC MODEL SCANNER & XIAOMI PAD 6 RAM TELEMETRY",
                color = palette.accentPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Free RAM: $availRamMb MB / $totalRamMb MB (${if (availRamMb > 2200) "Ready for Offline Gemma 3 1B in Ch.9" else "Close background apps before loading Gemma"})",
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scannerStatus,
                color = palette.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidGlassButton(
                    text = if (isScanning) "Scanning v1beta/models..." else "1. Scan My Gemini Key",
                    icon = Icons.Default.Search,
                    lab = lab,
                    accentColor = palette.accentPrimary,
                    onClick = {
                        if (!isScanning) {
                            isScanning = true
                            val key = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API)
                            scope.launch {
                                val res = JarvisBrainEngine.scanAvailableGeminiModels(key)
                                res.fold(
                                    onSuccess = { list ->
                                        discoveredModels.clear()
                                        discoveredModels.addAll(list)
                                        val best = list.firstOrNull()?.id.orEmpty()
                                        if (stagedModelId.isBlank() && best.isNotBlank()) {
                                            stagedModelId = best
                                        }
                                        scannerStatus = "Discovered ${list.size} live Gemini models! Best free-tier Flash pick: $best"
                                    },
                                    onFailure = { err ->
                                        scannerStatus = "Scan failed: ${err.message}"
                                    }
                                )
                                isScanning = false
                            }
                        }
                    }
                )
                LiquidGlassButton(
                    text = if (forceOffline) "Router: Manual OFFLINE" else "Router: Hybrid AUTO",
                    icon = if (forceOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                    lab = lab,
                    isActive = !forceOffline,
                    onClick = onToggleOffline
                )
            }
        }

        // Discovered Models List
        if (discoveredModels.isNotEmpty()) {
            LiquidGlassCard(lab = lab, tilt = tilt) {
                Text(
                    text = "LIVE MODELS AVAILABLE TO YOUR KEY (TAP TO STAGE)",
                    color = palette.accentSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discoveredModels.take(8).forEach { model ->
                        val isSelected = stagedModelId == model.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) palette.accentPrimary.copy(alpha = 0.28f)
                                    else palette.glassSurface.copy(alpha = 0.55f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else palette.accentPrimary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { stagedModelId = model.id }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${model.displayName} (${model.id})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = model.description.take(110),
                                    color = palette.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (model.isRecommendedFree) {
                                Text(
                                    text = "FREE FLASH PICK",
                                    color = JarvisSpatialPalettes.StatusSuccess,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Engine & Model Action Button (APPLY + Self-Test + Rollback)
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Text(
                text = "ENGINE & MODEL ACTION BUTTON (STAGE -> SELF-TEST -> COMMIT OR ROLLBACK)",
                color = palette.accentPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Staged Model: ${stagedModelId.ifBlank { "(Tap Scan above first)" }} | Staged Temperature: ${"%.2f".format(stagedTemp)}",
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            GlassSliderRow(
                label = "Brain Creativity / Temperature (${"%.2f".format(stagedTemp)})",
                value = stagedTemp,
                range = 0.0f..1.0f,
                tint = palette.accentPrimary
            ) { stagedTemp = it }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = applyStatus,
                color = palette.accentSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassButton(
                text = if (isTestingApply) "Running Self-Test..." else "APPLY & SELF-TEST STAGED ENGINE",
                icon = Icons.Default.CheckCircle,
                lab = lab,
                accentColor = JarvisSpatialPalettes.StatusSuccess,
                onClick = {
                    if (!isTestingApply) {
                        val previousModel = committedModelId
                        val previousTemp = committedTemperature
                        isTestingApply = true
                        applyStatus = "Self-testing staged model '$stagedModelId'..."
                        scope.launch {
                            val key = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API)
                            val scanRes = JarvisBrainEngine.scanAvailableGeminiModels(key)
                            val targetModel = stagedModelId.ifBlank { scanRes.getOrNull()?.firstOrNull()?.id.orEmpty() }
                            if (targetModel.isNotBlank() && scanRes.isSuccess) {
                                stagedModelId = targetModel
                                onCommitSuccess(targetModel, stagedTemp)
                                applyStatus = "SELF-TEST PASSED ✓ Committed '$targetModel' at temp ${"%.2f".format(stagedTemp)}."
                            } else {
                                // Automatic Rollback
                                stagedModelId = previousModel
                                stagedTemp = previousTemp
                                applyStatus = "SELF-TEST FAILED — Rolled back to previous working model '${previousModel.ifBlank { "Offline Brain" }}'."
                            }
                            isTestingApply = false
                        }
                    }
                }
            )
        }
    }
}

// ============================================================================
// 8. NAVIGATION RAIL, HEADER, DASHBOARD, GLASS LAB, GALLERY & KEY VAULT
// ============================================================================

@Composable
private fun SpatialNavigationRail(
    lab: GlassLabController,
    selectedTab: JarvisNavTab,
    orbState: VoiceOrbState,
    onSelectTab: (JarvisNavTab) -> Unit,
    onCycleOrb: () -> Unit
) {
    val palette = lab.currentPalette
    val shape = RoundedCornerShape(26.dp)

    Column(
        modifier = Modifier
            .width(108.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(palette.glassSurface.copy(alpha = 0.78f))
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        palette.accentPrimary.copy(alpha = 0.65f),
                        palette.accentSecondary.copy(alpha = 0.55f)
                    )
                ),
                shape = shape
            )
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArcReactorVoiceOrb(
                state = orbState,
                lab = lab,
                onClick = onCycleOrb,
                sizeDp = 50.dp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = orbState.name,
                color = palette.accentPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            JarvisNavTab.values().forEach { tab ->
                val active = selectedTab == tab
                val itemShape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(itemShape)
                        .background(
                            if (active) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        palette.accentPrimary.copy(alpha = 0.38f),
                                        palette.accentSecondary.copy(alpha = 0.30f)
                                    )
                                )
                            } else {
                                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .border(
                            width = if (active) 1.2.dp else 0.dp,
                            color = if (active) Color.White.copy(alpha = 0.7f) else Color.Transparent,
                            shape = itemShape
                        )
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) Color.White else palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = if (active) Color.White else palette.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accentTertiary.copy(alpha = 0.25f))
                .clickable {
                    lab.activePresetIndex = (lab.activePresetIndex + 1) % JarvisSpatialPalettes.allPresets.size
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Theme ↻",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TopSpatialHeader(
    plan: NeetPlanRoot,
    lab: GlassLabController,
    committedModelId: String,
    forceOffline: Boolean,
    tick: Int,
    tilt: Offset
) {
    val palette = lab.currentPalette
    val imphalNow = remember(tick) { NeetPlanRepository.getImphalNow() }
    val daysToNeet = remember(tick) { NeetPlanRepository.getDaysUntilExam(plan, imphalNow.toLocalDate()) }

    LiquidGlassCard(lab = lab, tilt = tilt, contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(palette.orbPrimary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(palette.orbSecondary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(palette.orbTertiary))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "J.A.R.V.I.S. SPATIAL OS // ${if (forceOffline) "OFFLINE BRAIN" else committedModelId.ifBlank { "HYBRID CLOUD READY" }}",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "At your service, sir. NEET UG 2027 Strategist & Butler Online.",
                    color = palette.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Home: ${plan.meta.homeProfile.district}, ${plan.meta.homeProfile.state} (${plan.meta.homeProfile.pin}) | TZ: ${plan.meta.homeProfile.timezone}",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = imphalNow.format(DateTimeFormatter.ofPattern("hh:mm:ss a 'IST'")),
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    text = "$daysToNeet DAYS TO NEET UG 2027",
                    color = palette.accentSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "Target: ${plan.meta.goalInstitution} (AIR <= ${plan.meta.aiimsNagpurCutoffs2026Round1.ASPIRATIONAL_OPEN_BUFFER})",
                    color = palette.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DashboardSpatialContent(
    plan: NeetPlanRoot,
    masterPrompt: String,
    lab: GlassLabController,
    tick: Int,
    tilt: Offset
) {
    val context = LocalContext.current
    val palette = lab.currentPalette
    val imphalNow = remember(tick) { NeetPlanRepository.getImphalNow() }
    val activeSlot = remember(tick) { NeetPlanRepository.getCurrentTimetableSlot(plan, imphalNow.toLocalTime()) }
    val dayOneAssignments = remember {
        NeetPlanRepository.getAssignmentsForDate(plan, LocalDate.parse(plan.meta.firstLectureDate))
    }

    var batteryOk by remember(tick) { mutableStateOf(XiaomiSurvivalHelper.isIgnoringBatteryOptimizations(context)) }
    var autostartOk by remember(tick) { mutableStateOf(EncryptedKeyStoreVault.isXiaomiAutostartLocked(context)) }
    var filesOk by remember(tick) { mutableStateOf(XiaomiSurvivalHelper.hasAllFilesAccess()) }
    var overlayOk by remember(tick) { mutableStateOf(XiaomiSurvivalHelper.canDrawOverlays(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LiquidGlassCard(
                lab = lab,
                tilt = tilt,
                accentTint = palette.accentPrimary,
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = palette.accentPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE IMPHAL TIMETABLE SLOT (${activeSlot.startTime} - ${activeSlot.endTime})",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = activeSlot.title, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = activeSlot.instruction, color = palette.textSecondary, fontSize = 13.sp)
            }

            LiquidGlassCard(
                lab = lab,
                tilt = tilt,
                accentTint = palette.accentSecondary,
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = palette.accentSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEET 2027 BRAIN TELEMETRY",
                        color = palette.accentSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${plan.videoCountAudit.totalKnownCompleted} Completed | ${plan.videoCountAudit.totalKnownRemaining} Remaining (791 Total)",
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "156 Study Days | 27 Sundays | 6 Recall Loops | Sleep: ${plan.meta.sleepWindowStart}-${plan.meta.sleepWindowEnd}",
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Master Prompt Loaded: ${masterPrompt.length} chars",
                    color = palette.accentTertiary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = palette.accentTertiary) {
            Text(
                text = "DAY 1 FOUR-TRACK QUEUE (MONDAY, 2 NOV 2026)",
                color = palette.accentPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            val trackAccents = listOf(
                palette.orbPrimary,
                palette.orbSecondary,
                palette.orbTertiary,
                JarvisSpatialPalettes.StatusWarning
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                dayOneAssignments.forEachIndexed { idx, item ->
                    val accent = trackAccents[idx % trackAccents.size]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.28f),
                                        palette.glassSurface.copy(alpha = 0.65f)
                                    )
                                )
                            )
                            .border(1.2.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = item.trackName,
                                color = accent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.chapterTitle,
                                color = palette.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Lecture L0${item.lectureNumberInChapter}/${item.totalLecturesInChapter}",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = palette.accentPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "XIAOMI PAD 6 HYPEROS SURVIVAL CONSOLE",
                    color = palette.accentPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "All 4 Xiaomi Pad 6 locks are verified and glowing green. Tap any button to revisit its system setting.",
                color = palette.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassButton(
                    modifier = Modifier.weight(1f),
                    text = if (batteryOk) "Battery: Unrestricted ✓" else "1. Disable Battery Saver",
                    icon = if (batteryOk) Icons.Default.CheckCircle else Icons.Default.BatteryChargingFull,
                    lab = lab,
                    isActive = batteryOk,
                    accentColor = if (batteryOk) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        XiaomiSurvivalHelper.openBatteryOptimizationDialog(context)
                        batteryOk = XiaomiSurvivalHelper.isIgnoringBatteryOptimizations(context)
                    }
                )
                LiquidGlassButton(
                    modifier = Modifier.weight(1f),
                    text = if (autostartOk) "Xiaomi Autostart ✓" else "2. Xiaomi Autostart & BG",
                    icon = if (autostartOk) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                    lab = lab,
                    isActive = autostartOk,
                    accentColor = if (autostartOk) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        EncryptedKeyStoreVault.setXiaomiAutostartLocked(context, true)
                        autostartOk = true
                        XiaomiSurvivalHelper.openXiaomiAutostartSettings(context)
                    }
                )
                LiquidGlassButton(
                    modifier = Modifier.weight(1f),
                    text = if (filesOk) "Study Folder Access ✓" else "3. Grant All Files Access",
                    icon = if (filesOk) Icons.Default.CheckCircle else Icons.Default.Security,
                    lab = lab,
                    isActive = filesOk,
                    accentColor = if (filesOk) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        XiaomiSurvivalHelper.openAllFilesAccessSettings(context)
                        filesOk = XiaomiSurvivalHelper.hasAllFilesAccess()
                    }
                )
                LiquidGlassButton(
                    modifier = Modifier.weight(1f),
                    text = if (overlayOk) "HUD Overlay Ready ✓" else "4. Allow HUD Overlay",
                    icon = if (overlayOk) Icons.Default.CheckCircle else Icons.Default.Security,
                    lab = lab,
                    isActive = overlayOk,
                    accentColor = if (overlayOk) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        XiaomiSurvivalHelper.openOverlaySettings(context)
                        overlayOk = XiaomiSurvivalHelper.canDrawOverlays(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun GlassLabScreen(
    lab: GlassLabController,
    tilt: Offset
) {
    val palette = lab.currentPalette

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "APPLE VISION-OS // LIQUID GLASS LAB",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tune multi-colour Aurora shaders, specular gloss, 3D gyroscope parallax & 144Hz performance.",
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiquidGlassButton(
                        text = "Apple Pro Default",
                        icon = Icons.Default.Refresh,
                        lab = lab,
                        onClick = { lab.applyAppleProDefault() }
                    )
                    LiquidGlassButton(
                        text = "144Hz Turbo",
                        icon = Icons.Default.Speed,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusWarning,
                        onClick = { lab.apply144HzBatterySaver() }
                    )
                }
            }
        }

        LiquidGlassCard(lab = lab, tilt = tilt) {
            Text(
                text = "SPATIAL COLOUR ENGINE PRESETS",
                color = palette.accentSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                JarvisSpatialPalettes.allPresets.forEachIndexed { index, preset ->
                    val selected = lab.activePresetIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        preset.orbPrimary.copy(alpha = 0.38f),
                                        preset.orbSecondary.copy(alpha = 0.32f),
                                        preset.orbTertiary.copy(alpha = 0.28f)
                                    )
                                )
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { lab.activePresetIndex = index }
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(preset.orbPrimary))
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(preset.orbSecondary))
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(preset.orbTertiary))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = preset.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = preset.subtitle,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LiquidGlassCard(lab = lab, tilt = tilt, modifier = Modifier.weight(1f)) {
                Text(
                    text = "OPTICS & SPECULAR SHADERS",
                    color = palette.accentPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                GlassSliderRow("Backdrop Blur Radius (${lab.blurRadiusDp.toInt()} dp)", lab.blurRadiusDp, 0f..40f, palette.accentPrimary) {
                    lab.blurRadiusDp = it
                }
                GlassSliderRow("Glass Frosted Opacity (${(lab.surfaceAlpha * 100).toInt()}%)", lab.surfaceAlpha, 0.35f..0.95f, palette.accentSecondary) {
                    lab.surfaceAlpha = it
                }
                GlassSliderRow("Top Specular Gloss (${(lab.specularGloss * 100).toInt()}%)", lab.specularGloss, 0.1f..1.0f, palette.accentTertiary) {
                    lab.specularGloss = it
                }
                GlassSliderRow("Aurora Bloom Glow (${(lab.auroraGlowIntensity * 100).toInt()}%)", lab.auroraGlowIntensity, 0.1f..1.0f, palette.orbPrimary) {
                    lab.auroraGlowIntensity = it
                }
            }

            LiquidGlassCard(lab = lab, tilt = tilt, modifier = Modifier.weight(1f)) {
                Text(
                    text = "3D DEPTH & ACCESSIBILITY",
                    color = palette.accentSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                GlassSliderRow("3D Bevel Depth (${(lab.bevelDepth * 100).toInt()}%)", lab.bevelDepth, 0.1f..1.0f, palette.accentPrimary) {
                    lab.bevelDepth = it
                }
                GlassSliderRow("Corner Curvature (${lab.cornerRadiusDp.toInt()} dp)", lab.cornerRadiusDp, 12f..36f, palette.accentSecondary) {
                    lab.cornerRadiusDp = it
                }
                GlassSliderRow("Gyroscope 3D Tilt (${(lab.gyroParallaxStrength * 100).toInt()}%)", lab.gyroParallaxStrength, 0f..1.0f, palette.accentTertiary) {
                    lab.gyroParallaxStrength = it
                }
                GlassSliderRow("Multi-Colour Richness (${(lab.colorRichness * 100).toInt()}%)", lab.colorRichness, 0.2f..1.0f, palette.orbSecondary) {
                    lab.colorRichness = it
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("144Hz Mode", color = palette.textPrimary, fontSize = 12.sp)
                    Switch(
                        checked = lab.performance144HzMode,
                        onCheckedChange = { lab.performance144HzMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = palette.accentPrimary)
                    )
                    Text("Low Transp.", color = palette.textPrimary, fontSize = 12.sp)
                    Switch(
                        checked = lab.reduceTransparency,
                        onCheckedChange = { lab.reduceTransparency = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = palette.accentSecondary)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tint: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = tint,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun ComponentGalleryScreen(
    lab: GlassLabController,
    orbState: VoiceOrbState,
    onCycleOrbState: () -> Unit,
    tilt: Offset
) {
    val palette = lab.currentPalette
    var demoQuery by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SIRI / ARC-REACTOR MULTI-COLOUR ORB & 3D BUTTON KIT",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the glowing sphere or any 3D glass button to test spring physics, fingertip bloom & haptics. Live state: ${orbState.name}",
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidGlassButton(
                            text = "Primary Aurora",
                            icon = Icons.Default.AutoAwesome,
                            lab = lab,
                            accentColor = palette.accentPrimary,
                            onClick = onCycleOrbState
                        )
                        LiquidGlassButton(
                            text = "Violet Pulse",
                            icon = Icons.Default.Bolt,
                            lab = lab,
                            accentColor = palette.accentSecondary,
                            onClick = onCycleOrbState
                        )
                        LiquidGlassButton(
                            text = "Rose Action",
                            icon = Icons.Default.Palette,
                            lab = lab,
                            accentColor = palette.accentTertiary,
                            onClick = onCycleOrbState
                        )
                        LiquidGlassButton(
                            text = "Cycle Voice State",
                            icon = Icons.Default.Mic,
                            lab = lab,
                            isActive = orbState != VoiceOrbState.IDLE,
                            onClick = onCycleOrbState
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                ArcReactorVoiceOrb(
                    state = orbState,
                    lab = lab,
                    onClick = onCycleOrbState,
                    sizeDp = 96.dp
                )
            }
        }

        LiquidGlassCard(lab = lab, tilt = tilt) {
            Text(
                text = "FLOATING GLASS PILL INPUT & SEMANTIC STATUS TOKENS",
                color = palette.accentSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassPillInput(
                value = demoQuery,
                onValueChange = { demoQuery = it },
                placeholder = "Ask J.A.R.V.I.S. about Electric Charges, GOC mechanisms, or Biomolecules...",
                lab = lab,
                leadingIcon = Icons.Default.Mic,
                trailingContent = {
                    LiquidGlassButton(
                        text = "Send",
                        icon = Icons.Default.Send,
                        lab = lab,
                        onClick = { demoQuery = "" }
                    )
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusBadge(Modifier.weight(1f), "SUCCESS // ON TRACK", JarvisSpatialPalettes.StatusSuccess, Icons.Default.CheckCircle)
                StatusBadge(Modifier.weight(1f), "WARNING // SLEEP GUARD", JarvisSpatialPalettes.StatusWarning, Icons.Default.Warning)
                StatusBadge(Modifier.weight(1f), "ERROR // MISSED SLOT", JarvisSpatialPalettes.StatusError, Icons.Default.Error)
                StatusBadge(Modifier.weight(1f), "INFO // IMPHAL IST", JarvisSpatialPalettes.StatusInfo, Icons.Default.Info)
            }
        }
    }
}

@Composable
private fun StatusBadge(
    modifier: Modifier,
    label: String,
    color: Color,
    icon: ImageVector
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EncryptedApiKeyVaultScreen(
    lab: GlassLabController,
    tilt: Offset
) {
    val context = LocalContext.current
    val palette = lab.currentPalette

    var geminiInput by remember { mutableStateOf("") }
    var picovoiceInput by remember { mutableStateOf("") }
    var githubInput by remember { mutableStateOf("") }

    var savedGeminiMask by remember {
        mutableStateOf(EncryptedKeyStoreVault.maskSecret(EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API)))
    }
    var savedPicovoiceMask by remember {
        mutableStateOf(EncryptedKeyStoreVault.maskSecret(EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_PICOVOICE_ACCESS)))
    }
    var savedGithubMask by remember {
        mutableStateOf(EncryptedKeyStoreVault.maskSecret(EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GITHUB_PAT)))
    }
    var offlinePrivacyLock by remember {
        mutableStateOf(EncryptedKeyStoreVault.isOfflinePrivacyLockEnabled(context))
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = palette.accentPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ANDROID HARDWARE KEYSTORE // AES-256-GCM SECRET VAULT",
                    color = palette.accentPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Keys are encrypted inside your Xiaomi Pad 6 Snapdragon 870 TEE hardware keystore. Nothing is ever stored in code or sent to GitHub.",
                color = palette.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Kill-Switch (Force 100% On-Device Offline Mode)",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Free-tier cloud prompts may be reviewed by Google. Toggle ON anytime to block all cloud audio/text/image calls.",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = offlinePrivacyLock,
                    onCheckedChange = {
                        offlinePrivacyLock = it
                        EncryptedKeyStoreVault.setOfflinePrivacyLock(context, it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = JarvisSpatialPalettes.StatusSuccess)
                )
            }
        }

        KeyEntryCard(
            title = "1. Gemini Free-Tier Cloud API Key (ai.google.dev)",
            subtitle = "Stored Status: $savedGeminiMask",
            value = geminiInput,
            onValueChange = { geminiInput = it },
            placeholder = "Paste AIzaSy... key from Google AI Studio",
            lab = lab,
            tilt = tilt,
            onSave = {
                EncryptedKeyStoreVault.saveSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API, geminiInput.trim())
                savedGeminiMask = EncryptedKeyStoreVault.maskSecret(EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API))
                geminiInput = ""
            },
            onClear = {
                EncryptedKeyStoreVault.saveSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API, "")
                savedGeminiMask = EncryptedKeyStoreVault.maskSecret("")
            }
        )

        KeyEntryCard(
            title = "2. Picovoice Porcupine 'Jarvis' Wake-Word AccessKey",
            subtitle = "Stored Status: $savedPicovoiceMask",
            value = picovoiceInput,
            onValueChange = { picovoiceInput = it },
            placeholder = "Paste free Picovoice Console AccessKey (for Chapter 7)",
            lab = lab,
            tilt = tilt,
            onSave = {
                EncryptedKeyStoreVault.saveSecret(context, EncryptedKeyStoreVault.KEY_PICOVOICE_ACCESS, picovoiceInput.trim())
                savedPicovoiceMask = EncryptedKeyStoreVault.maskSecret(EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_PICOVOICE_ACCESS))
                picovoiceInput = ""
            },
            onClear = {
                EncryptedKeyStoreVault.saveSecret(context, EncryptedKeyStoreVault.KEY_PICOVOICE_ACCESS, "")
                savedPicovoiceMask = EncryptedKeyStoreVault.maskSecret("")
            }
        )

        KeyEntryCard(
            title = "3. GitHub Fine-Grained Token (Code Audit & Repair - Chapter 11)",
            subtitle = "Stored Status: $savedGithubMask",
            value = githubInput,
            onValueChange = { githubInput = it },
            placeholder = "Paste github_pat_... scoped to jarvis-neet-2027 only",
            lab = lab,
            tilt = tilt,
            onSave = {
                EncryptedKeyStoreVault.saveSecret(context, EncryptedKeyStoreVault.KEY_GITHUB_PAT, githubInput.trim())
                savedGithubMask = EncryptedKeyStoreVault.maskSecret(EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GITHUB_PAT))
                githubInput = ""
            },
            onClear = {
                EncryptedKeyStoreVault.saveSecret(context, EncryptedKeyStoreVault.KEY_GITHUB_PAT, "")
                savedGithubMask = EncryptedKeyStoreVault.maskSecret("")
            }
        )
    }
}

@Composable
private fun KeyEntryCard(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    lab: GlassLabController,
    tilt: Offset,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    val palette = lab.currentPalette
    LiquidGlassCard(lab = lab, tilt = tilt) {
        Text(text = title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(text = subtitle, color = palette.accentPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))
        LiquidGlassPillInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            lab = lab,
            leadingIcon = Icons.Default.Key,
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = "Encrypt & Save",
                        icon = Icons.Default.CheckCircle,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = onSave
                    )
                    LiquidGlassButton(
                        text = "Clear",
                        icon = Icons.Default.Delete,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusError,
                        onClick = onClear
                    )
                }
            }
        )
    }
}

// ============================================================================
// 9. LIQUID GLASS 3D UI KIT & GYROSCOPE PARALLAX
// ============================================================================

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

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.deepCanvasTop, palette.deepCanvasBottom)
            )
        )

        val glowAlpha = (lab.auroraGlowIntensity * 0.42f * voiceBoost).coerceIn(0.05f, 0.78f)
        val richness = lab.colorRichness.coerceIn(0.2f, 1f)

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
        Column { content() }
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
            .defaultMinSize(minHeight = 48.dp)
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
                if (bloomAlpha > 0.01f) {
                    val maxDim = max(size.width, size.height) * 0.75f
                    val bloomCenter = if (tapOffset == Offset.Zero) center else tapOffset
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = bloomAlpha),
                                activeHue.copy(alpha = bloomAlpha * 0.7f),
                                Color.Transparent
                            ),
                            center = bloomCenter,
                            radius = maxDim
                        ),
                        radius = maxDim,
                        center = bloomCenter
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = palette.textPrimary,
                fontSize = 12.sp,
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
    trailingContent: @Composable () -> Unit = {}
) {
    val palette = lab.currentPalette
    val shape = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
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
            .padding(horizontal = 18.dp, vertical = 8.dp),
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
        Spacer(modifier = Modifier.width(8.dp))
        trailingContent()
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
            val r = min(size.width, size.height) / 2f
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
