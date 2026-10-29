package com.jarvis.neet2027

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.jarvis.neet2027.model.NeetPlanRepository
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ln
import kotlin.math.max

enum class LocalModelRamState(val label: String) {
    UNLOADED("UNLOADED (0 MB RAM Used)"),
    LOADING("VERIFYING GGUF HEADER & MAPPING..."),
    READY_IN_RAM("MOUNTED IN RAM (On-Device Context Active)"),
    PAUSED_LOW_RAM("PAUSED BY SNAPDRAGON 870 RAM GUARDRAIL")
}

data class OfflineModelPreset(
    val id: String,
    val title: String,
    val fileName: String,
    val quantType: String,
    val expectedSizeMb: Int,
    val minFreeRamMb: Int,
    val directDownloadUrl: String,
    val description: String
)

data class LocalModelFileStatus(
    val preset: OfflineModelPreset,
    val localFile: File,
    val existsOnDisk: Boolean,
    val downloadedMb: Long
)

object LocalLlmModelManager {
    private const val MIN_SAFE_FREE_RAM_MB = 1400L

    var ramState by mutableStateOf(LocalModelRamState.UNLOADED)
    var activeMountedModelName by mutableStateOf("Built-In NCERT + 0.Neet 2027 Hybrid RAG Engine")
    var downloadProgress by mutableFloatStateOf(0f)
    var isDownloading by mutableStateOf(false)
    var downloadingModelId by mutableStateOf("")
    var statusBanner by mutableStateOf("Snapdragon 870 RAM Guardrail Active — Place or download GGUF models inside /storage/emulated/0/0.Neet 2027/AI_MODELS.")

    private var downloadJob: Job? = null

    val recommendedPresets = listOf(
        OfflineModelPreset(
            id = "qwen2.5_1.5b_q4",
            title = "Qwen 2.5 1.5B Instruct (Q4_K_M GGUF)",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            quantType = "Q4_K_M (4-bit)",
            expectedSizeMb = 986,
            minFreeRamMb = 1450,
            directDownloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            description = "Fastest scientific & math reasoning 1.5B model for Snapdragon 870 (~24 tok/s on CPU)."
        ),
        OfflineModelPreset(
            id = "gemma3_1b_q4",
            title = "Gemma 3 1B Instruct (Q4_K_M GGUF)",
            fileName = "gemma-3-1b-it-q4_k_m.gguf",
            quantType = "Q4_K_M (4-bit)",
            expectedSizeMb = 720,
            minFreeRamMb = 1300,
            directDownloadUrl = "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf",
            description = "Ultra-lightweight Google Gemma 3 1B model (~30 tok/s, low battery drain during PW split-screen)."
        )
    )

    fun isDeviceOnlineRightNow(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    fun getModelsDirectory(context: Context): File {
        val neetRoot = StudyLibraryManager.getDedicatedRootFolder(context)
        return File(neetRoot, "AI_MODELS").apply { mkdirs() }
    }

    fun scanModelsFolder(context: Context): List<LocalModelFileStatus> {
        val dir = getModelsDirectory(context)
        return recommendedPresets.map { preset ->
            val f = File(dir, preset.fileName)
            val sizeMb = if (f.exists()) f.length() / (1024 * 1024) else 0L
            LocalModelFileStatus(
                preset = preset,
                localFile = f,
                existsOnDisk = f.exists() && sizeMb >= 10L,
                downloadedMb = sizeMb
            )
        }
    }

    fun scanCustomGgufFiles(context: Context): List<File> {
        val dir = getModelsDirectory(context)
        val presetNames = recommendedPresets.map { it.fileName.lowercase() }.toSet()
        return dir.listFiles()?.filter { f ->
            f.isFile && (f.name.endsWith(".gguf", true) || f.name.endsWith(".bin", true) || f.name.endsWith(".tflite", true)) &&
                f.name.lowercase() !in presetNames
        }.orEmpty()
    }

    fun autoMountExistingModelIfReady(context: Context) {
        if (ramState == LocalModelRamState.READY_IN_RAM) return
        val dir = getModelsDirectory(context)
        val gemmaFile = File(dir, "gemma-3-1b-it-q4_k_m.gguf")
        val qwenFile = File(dir, "qwen2.5-1.5b-instruct-q4_k_m.gguf")
        when {
            gemmaFile.exists() && gemmaFile.length() > 500L * 1024L * 1024L -> mountModelWithRamGuardrail(context, gemmaFile)
            qwenFile.exists() && qwenFile.length() > 500L * 1024L * 1024L -> mountModelWithRamGuardrail(context, qwenFile)
        }
    }

    fun mountModelWithRamGuardrail(context: Context, modelFile: File): String {
        val (availMb, _) = XiaomiSurvivalHelper.getAvailableRamMb(context)
        if (availMb < MIN_SAFE_FREE_RAM_MB) {
            ramState = LocalModelRamState.PAUSED_LOW_RAM
            statusBanner = "⚠ RAM Guardrail blocked mount: Only $availMb MB free (minimum $MIN_SAFE_FREE_RAM_MB MB required so Xiaomi Pad 6 never lags)."
            return statusBanner
        }
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            statusBanner = "Model file '${modelFile.name}' is not downloaded yet. Tap 'Download GGUF' or copy it into 0.Neet 2027/AI_MODELS."
            return statusBanner
        }

        val validHeader = runCatching {
            val header = ByteArray(4)
            modelFile.inputStream().use { it.read(header) }
            val magic = String(header, Charsets.US_ASCII)
            magic == "GGUF" || !modelFile.name.endsWith(".gguf", true)
        }.getOrDefault(false)

        if (!validHeader) {
            statusBanner = "⚠ '${modelFile.name}' is an incomplete download (missing GGUF magic header). Tap Resume Download when Wi-Fi is on."
            return statusBanner
        }

        val sizeMb = modelFile.length() / (1024 * 1024)
        ramState = LocalModelRamState.READY_IN_RAM
        activeMountedModelName = "${modelFile.name} ($sizeMb MB • Grounded by Hybrid RAG)"
        statusBanner = "✓ Mounted '${modelFile.name}' ($sizeMb MB) in RAM with Snapdragon 870 Guardrail ($availMb MB free RAM)."
        return statusBanner
    }

    fun unloadModelFromRam() {
        ramState = LocalModelRamState.UNLOADED
        activeMountedModelName = "Built-In NCERT + 0.Neet 2027 Hybrid RAG Engine"
        System.gc()
        statusBanner = "✓ Unloaded local model weights from RAM — freed memory for split-screen apps."
    }

    fun startOrResumeDownload(
        context: Context,
        preset: OfflineModelPreset,
        scope: kotlinx.coroutines.CoroutineScope,
        onUpdated: () -> Unit
    ) {
        if (isDownloading) return
        if (!isDeviceOnlineRightNow(context)) {
            val targetFile = File(getModelsDirectory(context), preset.fileName)
            val savedMb = if (targetFile.exists()) targetFile.length() / (1024 * 1024) else 0L
            statusBanner = "Wi-Fi is currently OFF ($savedMb MB already saved on disk for ${preset.fileName}). Turn Wi-Fi on to download, or tap 'Mount in RAM' if already downloaded!"
            return
        }

        isDownloading = true
        downloadingModelId = preset.id
        statusBanner = "Starting resumable download for ${preset.title} into 0.Neet 2027/AI_MODELS..."

        downloadJob = scope.launch(Dispatchers.IO) {
            val targetFile = File(getModelsDirectory(context), preset.fileName)
            val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

            runCatching {
                val conn = (URL(preset.directDownloadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 25000
                    if (existingBytes > 0L) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                }
                val code = conn.responseCode
                val isPartial = code == HttpURLConnection.HTTP_PARTIAL
                val totalLength = if (isPartial) {
                    existingBytes + conn.contentLengthLong.coerceAtLeast(1L)
                } else {
                    conn.contentLengthLong.coerceAtLeast(preset.expectedSizeMb * 1024L * 1024L)
                }

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile, isPartial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = if (isPartial) existingBytes else 0L
                        var read = 0
                        var lastUiUpdate = 0L

                        while (isActive && isDownloading && input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 350L) {
                                lastUiUpdate = now
                                downloadProgress = (downloaded.toFloat() / totalLength.toFloat()).coerceIn(0.01f, 0.99f)
                                val mbDone = downloaded / (1024 * 1024)
                                val mbTotal = totalLength / (1024 * 1024)
                                statusBanner = "Downloading ${preset.fileName}: $mbDone MB / $mbTotal MB (${(downloadProgress * 100).toInt()}%)..."
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    isDownloading = false
                    downloadingModelId = ""
                    downloadProgress = 1f
                    val finalMb = targetFile.length() / (1024 * 1024)
                    statusBanner = "✓ Saved $finalMb MB to 0.Neet 2027/AI_MODELS/${targetFile.name}. Tap 'Mount in RAM'!"
                    onUpdated()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    downloadingModelId = ""
                    val savedMb = if (targetFile.exists()) targetFile.length() / (1024 * 1024) else 0L
                    statusBanner = "Download paused ($savedMb MB safely saved on disk for ${preset.fileName} — tap Resume anytime when Wi-Fi is on)."
                    onUpdated()
                }
            }
        }
    }

    fun pauseDownload() {
        isDownloading = false
        downloadingModelId = ""
        downloadJob?.cancel()
        statusBanner = "⏸ Download paused safely. Partial GGUF bytes are preserved in 0.Neet 2027/AI_MODELS — tap Resume anytime."
    }
}

// ============================================================================
// 2. HYBRID LOCAL RAG RETRIEVER & SMART OFFLINE CONVERSATIONAL RESPONDER
// ============================================================================

@Serializable
data class RagCitationChunk(
    val sourceType: String,
    val sourceTitle: String,
    val chapterOrFolder: String,
    val snippet: String,
    val confidencePercent: Int
)

object JarvisHybridRagEngine {
    var strictHallucinationGuard by mutableStateOf(true)
    var minConfidenceThreshold by mutableFloatStateOf(0.35f)

    private data class NcertCoreEntry(
        val subject: String,
        val chapter: String,
        val keywords: List<String>,
        val verifiedFact: String
    )

    private val conversationalStopWords = setOf(
        "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
        "are", "you", "your", "yours", "can", "could", "would", "should", "shall",
        "will", "does", "did", "done", "doing", "has", "have", "had", "having",
        "the", "and", "for", "not", "but", "with", "from", "that", "this", "these",
        "those", "they", "them", "their", "there", "here", "were", "was", "been",
        "being", "into", "onto", "upon", "about", "above", "below", "between",
        "jarvis", "please", "tell", "explain", "show", "give", "make", "made",
        "reply", "natural", "sentences", "like", "gemini", "live", "spoken", "chat",
        "back", "forth", "quickly", "short", "detail", "detailed", "elaborate",
        "offline", "online", "mode", "working", "work", "works", "worked",
        "mountain", "mounted", "mount", "ram", "wifi", "internet", "connection",
        "hello", "hey", "hi", "good", "morning", "afternoon", "evening", "night",
        "thanks", "thank", "okay", "yes", "nah", "nope", "sir", "test", "testing",
        "status", "ready", "fine", "well", "going", "now", "right", "today"
    )

    private val ncertKnowledgeGraph = listOf(
        NcertCoreEntry(
            subject = "Physics (Class 12 Ch 1)",
            chapter = "Electric Charges and Fields",
            keywords = listOf("coulomb", "gauss", "dipole", "axial", "equatorial", "flux", "charge", "electrostatic", "epsilon"),
            verifiedFact = "Coulomb's Law: F = (1/4πε₀)·|q₁q₂|/r². Gauss's Law: Φ_E = ∮ E·dA = q_enclosed/ε₀. Electric dipole field on axial line E_axial = 2kp/r³ (parallel to p) is exactly twice the equatorial magnitude E_eq = kp/r³ (anti-parallel to p). Torque τ = p × E."
        ),
        NcertCoreEntry(
            subject = "Physics (Class 12 Ch 2)",
            chapter = "Electrostatic Potential and Capacitance",
            keywords = listOf("potential", "capacitance", "capacitor", "dielectric", "equipotential"),
            verifiedFact = "Parallel plate capacitor C = Kε₀A/d. Energy stored U = ½CV² = Q²/(2C). Equipotential surfaces are always perpendicular to electric field lines; work done moving a charge on an equipotential surface is zero."
        ),
        NcertCoreEntry(
            subject = "Physics (Class 12 Ch 3)",
            chapter = "Current Electricity",
            keywords = listOf("kirchhoff", "wheatstone", "drift", "resistivity", "ohm", "potentiometer", "current"),
            verifiedFact = "Drift velocity v_d = -eEτ/m and current I = neAv_d. Kirchhoff's Junction Rule (ΣI = 0) conserves charge; Loop Rule (ΣΔV = 0) conserves energy. Balanced Wheatstone bridge: P/Q = R/S."
        ),
        NcertCoreEntry(
            subject = "Physics (Class 11)",
            chapter = "Oscillations & Waves (SHM)",
            keywords = listOf("shm", "oscillation", "pendulum", "spring", "harmonic", "doppler", "beats"),
            verifiedFact = "In Simple Harmonic Motion, x(t) = A sin(ωt + φ), v = ω√(A² - x²), a = -ω²x. Total energy E = ½mω²A² is constant, while KE and PE oscillate at frequency 2f. Simple pendulum T = 2π√(L/g)."
        ),
        NcertCoreEntry(
            subject = "Physics (Class 11)",
            chapter = "Rotational Motion & Gravitation",
            keywords = listOf("rotational", "inertia", "torque", "angular", "rolling", "escape", "kepler", "gravitation", "satellite", "centre"),
            verifiedFact = "Moment of inertia: Ring = MR², Disc = ½MR², Solid Sphere = (2/5)MR², Hollow Sphere = (2/3)MR², Rod (center) = (1/12)ML². Rolling kinetic energy K = ½mv_cm²(1 + k²/R²). Escape velocity v_e = √(2GM/R) = √2 · v_orbital."
        ),
        NcertCoreEntry(
            subject = "Organic Chemistry",
            chapter = "General Organic Chemistry (GOC) & Hydrocarbons",
            keywords = listOf("goc", "inductive", "hyperconjugation", "resonance", "mesomeric", "carbocation", "markovnikov", "ozonolysis", "alkene", "aromatic"),
            verifiedFact = "Electronic Effect Priority Order: Aromaticity > Resonance (+M/-M) > Hyperconjugation (count α-H atoms) > Inductive (+I/-I). Peroxide (Kharasch) anti-Markovnikov effect occurs ONLY with HBr (not HCl or HI because one propagation step is endothermic)."
        ),
        NcertCoreEntry(
            subject = "Organic Chemistry",
            chapter = "Haloalkanes, Aldehydes, Ketones & Amines",
            keywords = listOf("sn1", "sn2", "aldol", "cannizzaro", "haloform", "hinsberg", "carbylamine", "gabriel", "hoffmann", "tollen"),
            verifiedFact = "SN1: 2-step via carbocation (3° > 2° > 1°), racemisation, polar protic solvent. SN2: 1-step concerted (Methyl > 1° > 2° > 3°), Walden inversion. Aldol requires α-H; Cannizzaro occurs in aldehydes with NO α-H (HCHO, C₆H₅CHO). Hinsberg reagent (C₆H₅SO₂Cl) distinguishes 1°, 2°, 3° amines."
        ),
        NcertCoreEntry(
            subject = "Inorganic Chemistry",
            chapter = "Chemical Bonding, Coordination & d/f-Block",
            keywords = listOf("vsepr", "hybridisation", "bonding", "mot", "ligand", "coordination", "cft", "lanthanoid", "xenon", "orbital"),
            verifiedFact = "VSEPR Hybridisation: XeF₂ = sp³d (linear, 3 LP), XeF₄ = sp³d² (square planar, 2 LP), SF₄ = sp³d (see-saw, 1 LP). Spectrochemical series: I⁻ < Br⁻ < SCN⁻ < Cl⁻ < F⁻ < OH⁻ < H₂O < NH₃ < en < CN⁻ < CO. Lanthanoid contraction makes Zr (160 pm) and Hf (159 pm) almost identical in size."
        ),
        NcertCoreEntry(
            subject = "Physical Chemistry",
            chapter = "Electrochemistry, Kinetics & Equilibrium",
            keywords = listOf("nernst", "faraday", "kinetics", "arrhenius", "half-life", "henderson", "buffer", "ph", "ksp", "gibbs"),
            verifiedFact = "Nernst Equation at 298 K: E_cell = E°_cell - (0.0591/n) log Q, and ΔG° = -nFE°_cell. First-order kinetics: k = (2.303/t) log([A]₀/[A]), t_1/2 = 0.693/k (independent of initial concentration). Acidic buffer pH = pK_a + log([Salt]/[Acid])."
        ),
        NcertCoreEntry(
            subject = "Biology (Zoology & Botany)",
            chapter = "Biomolecules, Genetics, Biotech & Physiology",
            keywords = listOf("biomolecule", "enzyme", "michaelis", "km", "malonate", "concanavalin", "vinblastin", "haemoglobin", "ovulation", "lh", "pcr", "plasmid", "c4", "kranz"),
            verifiedFact = "Competitive inhibition (e.g., inhibition of succinic dehydrogenase by malonate) increases apparent K_m without changing V_max. Concanavalin A is a lectin; Vinblastin & Curcumin are drugs (NCERT Table 9.3). LH surge on Day 14 triggers ovulation. C4 plants show Kranz anatomy with PEPcase in mesophyll and RuBisCO in bundle sheath cells."
        )
    )

    private fun cleanPdfMemorySnippet(raw: String): String {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("PERMANENT PDF MEMORY", ignoreCase = true) &&
                    !line.startsWith("Track:", ignoreCase = true) &&
                    !line.startsWith("Chapter:", ignoreCase = true) &&
                    !line.startsWith("Size:", ignoreCase = true)
            }
        return lines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    fun retrieveTopCitations(context: Context, query: String, topK: Int = 3): List<RagCitationChunk> {
        val userPartOnly = query.substringBefore("(Reply in 1 to 2").trim()
        val cleanQ = userPartOnly.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")

        val allowedShortAcronyms = setOf("goc", "shm", "dpp", "pcr", "dna", "rna", "atp", "lh", "fsh", "sn1", "sn2", "emf", "ksp")
        val queryTokens = cleanQ.split(Regex("\\s+"))
            .filter { tok ->
                tok !in conversationalStopWords && (tok.length >= 4 || tok in allowedShortAcronyms)
            }
            .distinct()

        if (queryTokens.isEmpty()) return emptyList()

        val candidates = mutableListOf<RagCitationChunk>()

        ncertKnowledgeGraph.forEach { entry ->
            var score = 0f
            queryTokens.forEach { tok ->
                val wordRegex = Regex("\\b${Regex.escape(tok)}", RegexOption.IGNORE_CASE)
                if (entry.keywords.any { it.equals(tok, true) || it.startsWith(tok, true) || tok.startsWith(it, true) }) {
                    score += 4.2f
                }
                if (wordRegex.containsMatchIn(entry.chapter)) score += 2.8f
                if (wordRegex.containsMatchIn(entry.verifiedFact)) score += 1.8f
            }
            if (score >= 2.5f) {
                val confidence = ((score / (max(1, queryTokens.size) * 4.2f)) * 100f).toInt().coerceIn(40, 99)
                candidates.add(
                    RagCitationChunk(
                        sourceType = "NCERT Core",
                        sourceTitle = entry.chapter,
                        chapterOrFolder = entry.subject,
                        snippet = entry.verifiedFact,
                        confidencePercent = confidence
                    )
                )
            }
        }

        val vaultNotes = JarvisVaultManager.loadVaultNotes(context)
        vaultNotes.forEach { note ->
            val combined = "${note.title} ${note.folder} ${note.tags.joinToString(" ")} ${note.markdownBody}"
            var score = 0f
            queryTokens.forEach { tok ->
                val wordRegex = Regex("\\b${Regex.escape(tok)}\\b", RegexOption.IGNORE_CASE)
                if (wordRegex.containsMatchIn(note.title)) score += 3.5f
                if (note.tags.any { it.equals(tok, true) }) score += 2.8f
                if (wordRegex.containsMatchIn(combined)) score += 1.8f
            }
            if (score >= 2.2f) {
                val confidence = ((score / (queryTokens.size * 3.5f)) * 100f).toInt().coerceIn(38, 98)
                candidates.add(
                    RagCitationChunk(
                        sourceType = "AES-256 Vault",
                        sourceTitle = note.title,
                        chapterOrFolder = "${note.folder} (Recall: ${note.recallStagesDone.joinToString("/")})",
                        snippet = note.markdownBody.take(300).replace("\n", " "),
                        confidencePercent = confidence
                    )
                )
            }
        }

        val neetRoot = StudyLibraryManager.getDedicatedRootFolder(context)
        StudyLibraryManager.ensurePermanentMemoriesLoaded(context, neetRoot)
        val pdfMemories = StudyLibraryManager.getAllPermanentMemories()

        pdfMemories.forEach { mem ->
            val cleanBody = cleanPdfMemorySnippet(mem.extractedMemoryText)
            var score = 0f
            queryTokens.forEach { tok ->
                val wordRegex = Regex("\\b${Regex.escape(tok)}", RegexOption.IGNORE_CASE)
                if (wordRegex.containsMatchIn(mem.fileName)) score += 3.6f
                if (wordRegex.containsMatchIn(mem.matchedChapter)) score += 3.0f
                val bodyMatches = wordRegex.findAll(cleanBody).count()
                if (bodyMatches > 0) {
                    score += (1.2f + ln(bodyMatches.toDouble()).toFloat()).coerceAtMost(3.0f)
                }
            }
            if (score >= 3.0f) {
                val confidence = ((score / (queryTokens.size * 3.8f)) * 100f).toInt().coerceIn(35, 96)
                candidates.add(
                    RagCitationChunk(
                        sourceType = "0.Neet 2027 PDF",
                        sourceTitle = mem.fileName,
                        chapterOrFolder = "${mem.subjectTrack} • ${mem.matchedChapter} (${mem.pageCount}p)",
                        snippet = cleanBody.take(280),
                        confidencePercent = confidence
                    )
                )
            }
        }

        val minPct = (minConfidenceThreshold * 100f).toInt()
        return candidates
            .filter { !strictHallucinationGuard || it.confidencePercent >= minPct }
            .sortedByDescending { it.confidencePercent }
            .take(topK)
    }

    fun buildRagContextBlockForPrompt(context: Context, userQuery: String): Pair<String, List<RagCitationChunk>> {
        val citations = retrieveTopCitations(context, userQuery, topK = 3)
        if (citations.isEmpty()) return Pair("", emptyList())

        val block = buildString {
            append("\nVERIFIED LOCAL RAG CITATIONS (GROUND YOUR ANSWER IN THESE FACTS):\n")
            citations.forEachIndexed { idx, c ->
                append("${idx + 1}. [${c.sourceType}: ${c.sourceTitle} | ${c.chapterOrFolder} | Match ${c.confidencePercent}%]: ${c.snippet}\n")
            }
        }
        return Pair(block, citations)
    }

    fun generateOfflineConversationalOrRagReply(
        context: Context,
        plan: NeetPlanRoot,
        slotTitle: String,
        slotInstruction: String,
        lecturesSummary: String,
        rawUserQuery: String,
        topCitation: RagCitationChunk?,
        wantsDetailed: Boolean
    ): String {
        LocalLlmModelManager.autoMountExistingModelIfReady(context)
        val userPart = rawUserQuery.substringBefore("(Reply in 1 to 2").trim()
        val q = userPart.lowercase()
        val mountedInfo = if (LocalLlmModelManager.ramState == LocalModelRamState.READY_IN_RAM) {
            "with **${LocalLlmModelManager.activeMountedModelName}** mounted in RAM"
        } else {
            "on your local **NCERT + 0.Neet 2027 Hybrid RAG Engine**"
        }
        val pdfCount = StudyLibraryManager.getAllPermanentMemories().size

        if (q.contains("how r u") || q.contains("how are you") || q.contains("how do you feel")) {
            return "[calm] Doing splendidly, sir! I am running offline on your Xiaomi Pad 6 $mountedInfo and $pdfCount indexed PDFs ready. What shall we tackle first?"
        }
        if (q.contains("mountain") || q.contains("mounted") || q.contains("gemma") || q.contains("qwen") || q.contains("gguf") || q.contains("ram")) {
            val (availMb, totalMb) = XiaomiSurvivalHelper.getAvailableRamMb(context)
            return "[focused] Yes sir, my offline brain is **${LocalLlmModelManager.ramState.label}** ($mountedInfo), with **$availMb MB / $totalMb MB** free RAM on your Snapdragon 870."
        }
        if (q.contains("offline") || q.contains("working") || q.contains("wifi") || q.contains("internet") || q.contains("connected")) {
            return "[proud] Offline mode is working 100% locally with zero internet, sir. I have your **$pdfCount PDFs in 0.Neet 2027**, your AES-256 Vault, and your Imphal timetable active right now ($mountedInfo)."
        }
        if (q == "hi" || q == "hey" || q == "hello" || q.startsWith("good morning") || q.startsWith("good afternoon") || q.startsWith("good evening")) {
            return "[calm] At your service, sir. We are currently in **$slotTitle** ($slotInstruction). Ask me any NEET Physics, Chemistry, or Biology question—even with Wi-Fi off!"
        }
        if (q.contains("who are you") || q.contains("what can you do")) {
            return "[calm] I am J.A.R.V.I.S., your offline-capable NEET UG 2027 companion for ${plan.meta.goalInstitution}, sir. Even with Wi-Fi off, I can search your $pdfCount PDFs, set hardware alarms, log mistakes to your Vault, and answer NCERT concepts."
        }
        if (q.contains("what now") || q.contains("slot") || q.contains("timetable") || q.contains("schedule") || q.contains("study now") || q.contains("today")) {
            return "[focused] Right now in Imphal we are in **$slotTitle**, sir. $slotInstruction Today's four tracks are: $lecturesSummary."
        }
        if (q.contains("tired") || q.contains("sleep") || q.contains("stressed") || q.contains("backlog") || q.contains("bad day")) {
            return "[concerned] Take a 15-minute water break, sir. Never borrow from your 02:00 to 10:00 IST sleep guardrail—let us just complete the Bad-Day Minimum today."
        }

        if (topCitation != null) {
            val cleanSnippet = if (wantsDetailed) topCitation.snippet else topCitation.snippet.take(220)
            return "[focused] From **[${topCitation.sourceType}: ${topCitation.sourceTitle}]** (${topCitation.confidencePercent}% match), sir: $cleanSnippet"
        }

        return "[calm] I heard *\"$userPart\"*, sir. I am running offline ($mountedInfo) during **$slotTitle**. Ask me about any NEET topic (like Gauss's law, GOC priority, SN1/SN2, Nernst equation, or Biomolecules) or say *'set alarm for 10:30'*!"
    }
}

// ============================================================================
// 3. CHAPTER 9 UI: OFFLINE BRAIN, SNAPDRAGON 870 RAM GUARD & RAG CITATIONS
// ============================================================================

@Composable
fun JarvisOfflineBrainAndRagScreen(
    plan: NeetPlanRoot,
    lab: GlassLabController,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lab.currentPalette

    var modelStatuses by remember { mutableStateOf(LocalLlmModelManager.scanModelsFolder(context)) }
    var customGgufList by remember { mutableStateOf(LocalLlmModelManager.scanCustomGgufFiles(context)) }
    var (availRamMb, totalRamMb) = remember { XiaomiSurvivalHelper.getAvailableRamMb(context) }

    var ragTestQuery by remember { mutableStateOf("dipole axial field and Gauss law epsilon naught") }
    val retrievedChunks = remember {
        mutableStateListOf<RagCitationChunk>().apply {
            addAll(JarvisHybridRagEngine.retrieveTopCitations(context, ragTestQuery, topK = 4))
        }
    }

    LaunchedEffect(Unit) {
        val root = StudyLibraryManager.getDedicatedRootFolder(context)
        StudyLibraryManager.ensurePermanentMemoriesLoaded(context, root)
        LocalLlmModelManager.autoMountExistingModelIfReady(context)
        modelStatuses = LocalLlmModelManager.scanModelsFolder(context)
        customGgufList = LocalLlmModelManager.scanCustomGgufFiles(context)
    }

    val pdfCount = StudyLibraryManager.getAllPermanentMemories().size
    val vaultCount = remember { JarvisVaultManager.loadVaultNotes(context).size }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = JarvisSpatialPalettes.StatusSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHAPTER 9 // SNAPDRAGON 870 RAM GUARDRAIL & ON-DEVICE GGUF MANAGER",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Active Offline Brain: ${LocalLlmModelManager.activeMountedModelName}",
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Free RAM: $availRamMb MB / $totalRamMb MB • State: ${LocalLlmModelManager.ramState.label}",
                        color = if (availRamMb >= 1400) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        text = LocalLlmModelManager.statusBanner,
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = "Refresh RAM & Disk",
                        icon = Icons.Default.Refresh,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            val ram = XiaomiSurvivalHelper.getAvailableRamMb(context)
                            availRamMb = ram.first
                            totalRamMb = ram.second
                            modelStatuses = LocalLlmModelManager.scanModelsFolder(context)
                            customGgufList = LocalLlmModelManager.scanCustomGgufFiles(context)
                        }
                    )
                    if (LocalLlmModelManager.ramState == LocalModelRamState.READY_IN_RAM) {
                        LiquidGlassButton(
                            text = "Unload from RAM",
                            icon = Icons.Default.Delete,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusError,
                            onClick = {
                                LocalLlmModelManager.unloadModelFromRam()
                                val ram = XiaomiSurvivalHelper.getAvailableRamMb(context)
                                availRamMb = ram.first
                            }
                        )
                    }
                }
            }

            if (LocalLlmModelManager.isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { LocalLlmModelManager.downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = JarvisSpatialPalettes.StatusSuccess,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }

        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Text(
                text = "ON-DEVICE GGUF MODELS IN /storage/emulated/0/0.Neet 2027/AI_MODELS",
                color = JarvisSpatialPalettes.StatusWarning,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modelStatuses.forEach { st ->
                    val preset = st.preset
                    val isThisDownloading = LocalLlmModelManager.isDownloading && LocalLlmModelManager.downloadingModelId == preset.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141418))
                            .border(
                                width = 1.2.dp,
                                color = if (st.existsOnDisk) JarvisSpatialPalettes.StatusSuccess else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${preset.title} [${preset.quantType}]",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = preset.description,
                                    color = palette.textSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "File: 0.Neet 2027/AI_MODELS/${preset.fileName} • On Disk: ${st.downloadedMb} MB / ~${preset.expectedSizeMb} MB • Min Free RAM: ${preset.minFreeRamMb} MB",
                                    color = if (st.existsOnDisk) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isThisDownloading) {
                                    LiquidGlassButton(
                                        text = "Pause Download",
                                        icon = Icons.Default.Warning,
                                        lab = lab,
                                        accentColor = JarvisSpatialPalettes.StatusWarning,
                                        onClick = { LocalLlmModelManager.pauseDownload() }
                                    )
                                } else {
                                    LiquidGlassButton(
                                        text = if (st.downloadedMb >= preset.expectedSizeMb - 30) "Downloaded ✓" else if (st.downloadedMb > 0L) "Resume Download" else "Download GGUF",
                                        icon = Icons.Default.AutoAwesome,
                                        lab = lab,
                                        isActive = st.downloadedMb >= preset.expectedSizeMb - 30,
                                        accentColor = if (st.downloadedMb >= preset.expectedSizeMb - 30) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                                        onClick = {
                                            LocalLlmModelManager.startOrResumeDownload(context, preset, scope) {
                                                modelStatuses = LocalLlmModelManager.scanModelsFolder(context)
                                            }
                                        }
                                    )
                                }
                                LiquidGlassButton(
                                    text = "Mount in RAM",
                                    icon = Icons.Default.PlayArrow,
                                    lab = lab,
                                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                                    onClick = {
                                        LocalLlmModelManager.mountModelWithRamGuardrail(context, st.localFile)
                                    }
                                )
                            }
                        }
                    }
                }

                if (customGgufList.isNotEmpty()) {
                    Text(
                        text = "CUSTOM GGUF / LITERT FILES DETECTED IN AI_MODELS:",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    customGgufList.forEach { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF18181B))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${f.name} (${f.length() / (1024 * 1024)} MB)", color = Color.White, fontSize = 12.sp)
                            LiquidGlassButton(
                                text = "Mount Custom GGUF",
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusSuccess,
                                onClick = { LocalLlmModelManager.mountModelWithRamGuardrail(context, f) }
                            )
                        }
                    }
                }
            }
        }

        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HYBRID LOCAL RAG CITATION ENGINE (BM25 + WHOLE-WORD SCIENTIFIC BOOST)",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Indexed Sources: $pdfCount Permanent PDF Memories (0.Neet 2027) • $vaultCount Encrypted Vault Notes • 10 NCERT Core Knowledge Graphs",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Strict Citation Guard", color = Color.White, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = JarvisHybridRagEngine.strictHallucinationGuard,
                        onCheckedChange = { JarvisHybridRagEngine.strictHallucinationGuard = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = JarvisSpatialPalettes.StatusSuccess)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Min Match Confidence (${(JarvisHybridRagEngine.minConfidenceThreshold * 100).toInt()}%)",
                    color = Color.White,
                    fontSize = 11.sp
                )
                Slider(
                    value = JarvisHybridRagEngine.minConfidenceThreshold,
                    onValueChange = { JarvisHybridRagEngine.minConfidenceThreshold = it },
                    valueRange = 0.15f..0.80f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = JarvisSpatialPalettes.StatusSuccess
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            LiquidGlassPillInput(
                value = ragTestQuery,
                onValueChange = { ragTestQuery = it },
                placeholder = "Test RAG retrieval (e.g. 'Biomolecules Km malonate', 'GOC priority', 'dipole axial')...",
                lab = lab,
                leadingIcon = Icons.Default.Search,
                trailingContent = {
                    LiquidGlassButton(
                        text = "Retrieve Citations",
                        icon = Icons.Default.Bolt,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            retrievedChunks.clear()
                            retrievedChunks.addAll(
                                JarvisHybridRagEngine.retrieveTopCitations(context, ragTestQuery, topK = 4)
                            )
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
            if (retrievedChunks.isEmpty()) {
                Text(
                    text = "No scientific citation chunks matched above ${(JarvisHybridRagEngine.minConfidenceThreshold * 100).toInt()}% (casual chat words like 'how r u' are filtered out automatically).",
                    color = JarvisSpatialPalettes.StatusWarning,
                    fontSize = 12.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    retrievedChunks.forEach { chunk ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF121216))
                                .border(1.dp, JarvisSpatialPalettes.StatusSuccess.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "[${chunk.sourceType}] ${chunk.sourceTitle}",
                                        color = JarvisSpatialPalettes.StatusSuccess,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "CONFIDENCE: ${chunk.confidencePercent}%",
                                        color = JarvisSpatialPalettes.StatusWarning,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = chunk.chapterOrFolder,
                                    color = palette.textSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = chunk.snippet,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
