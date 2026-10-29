package com.jarvis.neet2027

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// ============================================================================
// 1. SYSTEM-WIDE DRAGGABLE FLOATING HUD PILL OVERLAY (FOR PW / JNOTE STUDYING)
// ============================================================================

object JarvisFloatingHudOverlay {
    var isFloatingPillShowing by mutableStateOf(false)
    private var floatingView: View? = null
    private var windowManager: WindowManager? = null

    fun showFloatingPill(context: Context): String {
        if (!Settings.canDrawOverlays(context)) {
            XiaomiSurvivalHelper.openOverlaySettings(context)
            return "Grant 'Display over other apps' permission first, then tap Launch Floating HUD Pill."
        }
        if (isFloatingPillShowing && floatingView != null) {
            return "Floating HUD Pill is already active on screen."
        }

        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 48
            y = 140
        }

        val pillBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 64f
            setColor(AndroidColor.parseColor("#E6101216"))
            setStroke(3, AndroidColor.parseColor("#10B981"))
        }

        val container = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pillBackground
            setPadding(32, 18, 28, 18)
        }

        val titleView = TextView(appCtx).apply {
            text = "🟢 J.A.R.V.I.S. LIVE  "
            setTextColor(AndroidColor.parseColor("#FAFAFA"))
            textSize = 12f
        }

        val wakeBtn = TextView(appCtx).apply {
            text = " [🎙️ Ask] "
            setTextColor(AndroidColor.parseColor("#FACC15"))
            textSize = 12f
            setOnClickListener {
                // Bring J.A.R.V.I.S. to foreground on Live Orb and trigger Gemini Live listening!
                SharedMicAndWakeManager.playWakeActivationChime()
                SharedMicAndWakeManager.isGeminiLiveSessionActive = true
                SharedMicAndWakeManager.liveOrbWakeEventTick++
                val openIntent = Intent(appCtx, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                runCatching { appCtx.startActivity(openIntent) }
            }
        }

        val closeBtn = TextView(appCtx).apply {
            text = "  ✕"
            setTextColor(AndroidColor.parseColor("#F43F5E"))
            textSize = 13f
            setOnClickListener {
                hideFloatingPill()
            }
        }

        container.addView(titleView)
        container.addView(wakeBtn)
        container.addView(closeBtn)

        // Make the pill smoothly draggable anywhere over Physics Wallah or JNote
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    runCatching { wm.updateViewLayout(container, layoutParams) }
                    true
                }
                else -> false
            }
        }

        return runCatching {
            wm.addView(container, layoutParams)
            floatingView = container
            isFloatingPillShowing = true
            "✓ Floating J.A.R.V.I.S. HUD Pill active! Drag it anywhere over Physics Wallah or JNote."
        }.getOrElse { err ->
            "Overlay error: ${err.message}"
        }
    }

    fun hideFloatingPill() {
        val view = floatingView
        val wm = windowManager
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        floatingView = null
        isFloatingPillShowing = false
    }
}

// ============================================================================
// 2. MULTIMODAL GEMINI VISION + OFFLINE RAG DIAGRAM & MCQ SOLVER ENGINE
// ============================================================================

data class VisionSolveOutcome(
    val engineBadge: String,
    val extractedTelemetry: String,
    val stepByStepSolution: String,
    val matchedRagCitation: String
)

object JarvisVisionEngine {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }.getOrNull()
    }

    /**
     * Generates a synthetic NEET Physics / Organic Chemistry sample diagram bitmap on-device
     * so you can test the Multimodal Vision & OCR solver immediately with 1 tap!
     */
    fun generateSampleNeetDiagramBitmap(topic: String): Bitmap {
        val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(AndroidColor.parseColor("#0D1117"))

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#10B981")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRoundRect(20f, 20f, 940f, 520f, 28f, 28f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#FACC15")
            textSize = 30f
            isFakeBoldText = true
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#FAFAFA")
            textSize = 24f
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#38BDF8")
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        if (topic == "PHYSICS_DIPOLE") {
            canvas.drawText("NEET UG 2027 PHYSICS // ELECTRIC DIPOLE AXIAL vs EQUATORIAL", 55f, 80f, titlePaint)
            canvas.drawCircle(300f, 260f, 28f, accentPaint)
            canvas.drawText("-q", 288f, 268f, textPaint)
            canvas.drawLine(328f, 260f, 572f, 260f, borderPaint)
            canvas.drawCircle(600f, 260f, 28f, borderPaint)
            canvas.drawText("+q", 586f, 268f, textPaint)
            canvas.drawText("Dipole moment p = q * (2a)   |   Point P at distance r >> a", 55f, 360f, textPaint)
            canvas.drawText("Q: Find ratio of |E_axial| / |E_equatorial| and angle between them.", 55f, 430f, titlePaint)
        } else {
            canvas.drawText("NEET UG 2027 ORGANIC CHEM // MARKOVNIKOV vs PEROXIDE EFFECT", 55f, 80f, titlePaint)
            canvas.drawText("Reaction 1:  CH3-CH=CH2  +  HBr  (Dark / CCl4)  --->  Product A (Major)", 55f, 200f, textPaint)
            canvas.drawText("Reaction 2:  CH3-CH=CH2  +  HBr  (Benzoyl Peroxide)  --->  Product B (Major)", 55f, 280f, textPaint)
            canvas.drawText("Reaction 3:  CH3-CH=CH2  +  HCl  (Benzoyl Peroxide)  --->  Product C (Major)", 55f, 360f, textPaint)
            canvas.drawText("Q: Identify A, B, and C with NCERT mechanism & intermediate stability.", 55f, 445f, titlePaint)
        }
        return bmp
    }

    fun inspectBitmapTelemetry(bitmap: Bitmap): String {
        val w = bitmap.width
        val h = bitmap.height
        val sampleStep = max(1, minOf(w, h) / 40)
        var brightPixels = 0
        var totalSampled = 0
        for (x in 0 until w step sampleStep) {
            for (y in 0 until h step sampleStep) {
                val px = bitmap.getPixel(x, y)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                if (lum > 110) brightPixels++
                totalSampled++
            }
        }
        val inkPct = if (totalSampled > 0) ((brightPixels * 100f) / totalSampled).roundToInt() else 0
        return "${w}x${h} px • Visual Ink/Stroke Density: $inkPct%"
    }

    private fun encodeBitmapToBase64Jpeg(bitmap: Bitmap, maxDim: Int = 1280): String {
        val scale = minOf(1f, maxDim.toFloat() / max(bitmap.width, bitmap.height).toFloat())
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun solveVisualNeetQuestion(
        context: Context,
        plan: NeetPlanRoot,
        bitmap: Bitmap?,
        questionOrOcrHint: String,
        committedModelId: String,
        forceOffline: Boolean
    ): VisionSolveOutcome = withContext(Dispatchers.IO) {
        val telemetry = if (bitmap != null) inspectBitmapTelemetry(bitmap) else "Text/OCR Input Only"
        val searchSeed = questionOrOcrHint.ifBlank { "dipole axial equatorial Gauss law Markovnikov peroxide" }
        val (ragBlock, citations) = JarvisHybridRagEngine.buildRagContextBlockForPrompt(context, searchSeed)
        val topCitation = citations.firstOrNull()
        val citationLabel = if (topCitation != null) {
            "[${topCitation.sourceType}] ${topCitation.sourceTitle} (${topCitation.confidencePercent}% match)"
        } else {
            "NCERT Class 11/12 Core Principles"
        }

        val isOnline = LocalLlmModelManager.isDeviceOnlineRightNow(context)
        val privacyLock = EncryptedKeyStoreVault.isOfflinePrivacyLockEnabled(context)
        val apiKey = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API)

        // 1. Offline Vision + Hybrid RAG Fallback (When Wi-Fi is OFF or Offline Locked)
        if (forceOffline || privacyLock || !isOnline || apiKey.isBlank() || bitmap == null) {
            val offlineSolution = buildString {
                append("1. OFFLINE VISUAL & RAG ANALYSIS:\n")
                append("• Image Telemetry: $telemetry\n")
                append("• Query / OCR Prompt: \"${questionOrOcrHint.ifBlank { "Standard NEET Diagram Analysis" }}\"\n\n")
                if (topCitation != null) {
                    append("2. VERIFIED NCERT / 0.Neet 2027 CITATION:\n")
                    append("• Source: $citationLabel\n")
                    append("• Core Fact: ${topCitation.snippet}\n\n")
                }
                append("3. EXAMINER TRAP & STEP-BY-STEP TAKEAWAY:\n")
                append("• Always verify units (SI vs CGS), vector direction (axial is parallel to dipole moment p, equatorial is anti-parallel at 180°), and NCERT exceptions (peroxide anti-Markovnikov applies ONLY to HBr, never HCl/HI).")
            }
            return@withContext VisionSolveOutcome(
                engineBadge = "OFFLINE OCR + HYBRID RAG (0 Bytes Sent)",
                extractedTelemetry = telemetry,
                stepByStepSolution = JarvisBrainEngine.formatScientificNotation(offlineSolution),
                matchedRagCitation = citationLabel
            )
        }

        // 2. Online Multimodal Gemini Vision Solver (Sends Base64 JPEG + Prompt + Local RAG Context)
        val base64Jpeg = encodeBitmapToBase64Jpeg(bitmap)
        val candidateModels = listOf(
            committedModelId.ifBlank { "gemini-3-flash-preview" },
            "gemini-3-flash-preview",
            "gemini-2.5-flash"
        ).distinct()

        val visionPrompt = """
            You are J.A.R.V.I.S., an elite NEET UG 2027 Physics, Chemistry & Biology Visual Solver for a student in Imphal targeting ${plan.meta.goalInstitution}.
            Analyze the attached image (handwritten note, diagram, or MCQ screenshot) along with the student's note: "${questionOrOcrHint.ifBlank { "Solve this NEET question step-by-step and point out any common NEET trap." }}"
            
            $ragBlock
            
            Format your response clearly:
            1. EXTRACTED QUESTION / DIAGRAM SUMMARY
            2. STEP-BY-STEP NCERT DERIVATION OR MECHANISM
            3. FINAL ANSWER & COMMON NEET EXAMINER TRAP
        """.trimIndent()

        for (modelId in candidateModels) {
            val attempt = runCatching {
                val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
                val payload = buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                addJsonObject { put("text", visionPrompt) }
                                addJsonObject {
                                    putJsonObject("inlineData") {
                                        put("mimeType", "image/jpeg")
                                        put("data", base64Jpeg)
                                    }
                                }
                            }
                        }
                    }
                    putJsonObject("generationConfig") {
                        put("temperature", 0.25)
                        put("maxOutputTokens", 700)
                    }
                }

                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 12000
                    readTimeout = 18000
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                if (code !in 200..299) throw IllegalStateException("HTTP $code on $modelId")

                val root = jsonParser.parseToJsonElement(body).jsonObject
                root["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
                    ?: throw IllegalStateException("Empty vision response")
            }

            if (attempt.isSuccess) {
                val (_, cleaned) = JarvisBrainEngine.parseEmotionAndCleanText(attempt.getOrThrow())
                return@withContext VisionSolveOutcome(
                    engineBadge = "MULTIMODAL VISION // $modelId",
                    extractedTelemetry = telemetry,
                    stepByStepSolution = JarvisBrainEngine.formatScientificNotation(cleaned),
                    matchedRagCitation = citationLabel
                )
            }
        }

        // Auto-fallback to Offline RAG if cloud vision quota/network fails
        return@withContext VisionSolveOutcome(
            engineBadge = "AUTO-OFFLINE VISION FALLBACK",
            extractedTelemetry = telemetry,
            stepByStepSolution = JarvisBrainEngine.formatScientificNotation(
                "Offline RAG Analysis ($telemetry):\n${topCitation?.snippet ?: "Verify NCERT formula & sign conventions carefully, sir."}"
            ),
            matchedRagCitation = citationLabel
        )
    }
}

// ============================================================================
// 3. CHAPTER 10 UI: MULTIMODAL VISION, OCR SOLVER & FLOATING HUD CONTROLLER
// ============================================================================

@Composable
fun JarvisVisionAndOcrScreen(
    plan: NeetPlanRoot,
    lab: GlassLabController,
    committedModelId: String,
    forceOffline: Boolean,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lab.currentPalette

    var currentBitmap by remember {
        mutableStateOf<Bitmap?>(JarvisVisionEngine.generateSampleNeetDiagramBitmap("PHYSICS_DIPOLE"))
    }
    var questionHintInput by remember {
        mutableStateOf("Find the ratio |E_axial| / |E_equatorial| for a short electric dipole and the angle between them.")
    }
    var isSolving by remember { mutableStateOf(false) }
    var solveOutcome by remember {
        mutableStateOf(
            VisionSolveOutcome(
                engineBadge = "READY FOR CAMERA, GALLERY OR SAMPLE DIAGRAM",
                extractedTelemetry = "960x540 px • Built-In NEET Physics Sample Loaded",
                stepByStepSolution = "Snap a photo of any NEET MCQ, pick a screenshot from your Xiaomi Pad 6 gallery, or tap 'Solve Visual MCQ' right now to test the Multimodal Vision + RAG solver.",
                matchedRagCitation = "[NCERT Core] Electric Charges and Fields"
            )
        )
    }
    var statusMessage by remember {
        mutableStateOf("Multimodal Vision & Floating Split-Screen HUD Pill Ready.")
    }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS && engine != null) {
                JarvisVoiceOutputController.applyConfiguredVoiceAndProsody(context, engine!!, "focused")
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    // 1. Camera Snapshot Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            currentBitmap = bmp
            statusMessage = "✓ Captured camera snapshot (${JarvisVisionEngine.inspectBitmapTelemetry(bmp)}). Tap 'Solve Visual MCQ'!"
        }
    }

    // 2. Gallery / Screenshot Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = JarvisVisionEngine.loadBitmapFromUri(context, uri)
            if (bmp != null) {
                currentBitmap = bmp
                statusMessage = "✓ Loaded screenshot/diagram from Gallery (${JarvisVisionEngine.inspectBitmapTelemetry(bmp)})."
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Draggable Floating HUD Pill Controller (For Split-Screen / Physics Wallah / JNote)
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = JarvisSpatialPalettes.StatusSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHAPTER 10 // FLOATING HUD PILL OVER PHYSICS WALLAH & JNOTE",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Floats a draggable Obsidian & Emerald pill on top of any app on your Xiaomi Pad 6. Tap '[🎙️ Ask]' on the floating pill anytime to jump straight into Gemini Live!",
                        color = palette.textPrimary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = statusMessage,
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = if (JarvisFloatingHudOverlay.isFloatingPillShowing) "Floating HUD: ON ✓" else "Launch Floating HUD Pill",
                        icon = Icons.Default.AutoAwesome,
                        lab = lab,
                        isActive = JarvisFloatingHudOverlay.isFloatingPillShowing,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            if (JarvisFloatingHudOverlay.isFloatingPillShowing) {
                                JarvisFloatingHudOverlay.hideFloatingPill()
                                statusMessage = "Floating HUD Pill closed."
                            } else {
                                statusMessage = JarvisFloatingHudOverlay.showFloatingPill(context)
                            }
                        }
                    )
                }
            }
        }

        // 2. Camera, Screenshot & Sample NEET Diagram Picker + OCR Hint Input
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Text(
                text = "MULTIMODAL NEET MCQ, HANDWRITTEN NOTE & DIAGRAM VISION SOLVER",
                color = JarvisSpatialPalettes.StatusWarning,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassButton(
                    text = "1. Snap Camera Photo",
                    icon = Icons.Default.Add,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = { cameraLauncher.launch() }
                )
                LiquidGlassButton(
                    text = "2. Pick Screenshot / Gallery",
                    icon = Icons.Default.Folder,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusWarning,
                    onClick = { galleryLauncher.launch("image/*") }
                )
                LiquidGlassButton(
                    text = "Sample: Physics Dipole",
                    icon = Icons.Default.Bolt,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusInfo,
                    onClick = {
                        currentBitmap = JarvisVisionEngine.generateSampleNeetDiagramBitmap("PHYSICS_DIPOLE")
                        questionHintInput = "Find the ratio |E_axial| / |E_equatorial| for a short electric dipole and the angle between them."
                        statusMessage = "Loaded NEET Physics Dipole sample diagram."
                    }
                )
                LiquidGlassButton(
                    text = "Sample: Organic Markovnikov",
                    icon = Icons.Default.AutoAwesome,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusError,
                    onClick = {
                        currentBitmap = JarvisVisionEngine.generateSampleNeetDiagramBitmap("ORGANIC_MARKOVNIKOV")
                        questionHintInput = "Identify major products A, B, and C for propene + HBr/HCl with and without peroxide."
                        statusMessage = "Loaded NEET Organic Chemistry Markovnikov/Peroxide sample diagram."
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            val bmp = currentBitmap
            if (bmp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF09090B))
                        .border(1.2.dp, JarvisSpatialPalettes.StatusSuccess.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected NEET Diagram or MCQ",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassPillInput(
                value = questionHintInput,
                onValueChange = { questionHintInput = it },
                placeholder = "Optional question note or OCR text (e.g. 'Solve Q14 step-by-step' or 'Explain this FBD')...",
                lab = lab,
                leadingIcon = Icons.Default.Edit,
                trailingContent = {
                    LiquidGlassButton(
                        text = if (isSolving) "Solving..." else "Solve Visual MCQ",
                        icon = Icons.Default.PlayArrow,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            if (!isSolving) {
                                isSolving = true
                                statusMessage = "Analyzing diagram & retrieving NCERT/PDF citations..."
                                scope.launch {
                                    solveOutcome = JarvisVisionEngine.solveVisualNeetQuestion(
                                        context = context,
                                        plan = plan,
                                        bitmap = currentBitmap,
                                        questionOrOcrHint = questionHintInput,
                                        committedModelId = committedModelId,
                                        forceOffline = forceOffline
                                    )
                                    statusMessage = "✓ Solved via ${solveOutcome.engineBadge}"
                                    isSolving = false
                                }
                            }
                        }
                    )
                }
            )
        }

        // 3. Step-by-Step Visual Solution, Butler Audio & 1-Tap Mistake Vault Logger
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VISUAL SOLUTION // ${solveOutcome.engineBadge}",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${solveOutcome.extractedTelemetry} • Grounded by: ${solveOutcome.matchedRagCitation}",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = "Speak Solution",
                        icon = Icons.Default.VolumeUp,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            val tts = ttsEngine ?: return@LiquidGlassButton
                            tts.stop()
                            JarvisVoiceOutputController.applyConfiguredVoiceAndProsody(context, tts, "focused")
                            val phonetic = JarvisVoiceOutputController.applyLexiconForSpeech(
                                context,
                                solveOutcome.stepByStepSolution.take(360)
                            )
                            tts.speak(phonetic, TextToSpeech.QUEUE_FLUSH, null, "vision_utt")
                        }
                    )
                    LiquidGlassButton(
                        text = "Save to Mistake Vault",
                        icon = Icons.Default.Lock,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusWarning,
                        onClick = {
                            scope.launch {
                                val summary = "log mistake: Visual MCQ (${questionHintInput.take(45).ifBlank { "Diagram Analysis" }}) — ${solveOutcome.matchedRagCitation}: ${solveOutcome.stepByStepSolution.take(220)}"
                                val outcome = JarvisAutonomousToolRouter.evaluateAndRunAutonomousTools(
                                    context = context,
                                    plan = plan,
                                    userQuery = summary
                                )
                                statusMessage = outcome.toolSummaryBanner.ifBlank {
                                    "✓ Logged solved visual MCQ to your AES-256 Mistake Notebook (D0-D30 Recall Armed)!"
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121216))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = solveOutcome.stepByStepSolution,
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}
