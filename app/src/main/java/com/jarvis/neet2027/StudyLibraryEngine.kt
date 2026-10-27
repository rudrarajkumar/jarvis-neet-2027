package com.jarvis.neet2027

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.Dispatchers
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Inflater

@Serializable
data class PermanentPdfMemory(
    val relativePath: String,
    val fileName: String,
    val fileSizeKb: Long,
    val lastModifiedMillis: Long,
    val pageCount: Int,
    val subjectTrack: String,
    val subCategory: String, // e.g., "OC / Ch 09 Biomolecules / DppNotes" or "notes"
    val matchedChapter: String,
    val extractedMemoryText: String,
    val deepVisionMemorized: Boolean = false,
    val memorizedAtIst: String
)

data class IndexedStudyFile(
    val file: File,
    val relativePath: String,
    val name: String,
    val extension: String,
    val sizeKb: Long,
    val lastModifiedIst: String,
    val extractedSnippet: String,
    val matchedChapter: String,
    val subjectTrack: String = "",
    val folderCategory: String = "",
    val isVideoMetadataOnly: Boolean = false,
    val isPdfMemorizedPermanently: Boolean = false,
    val pdfPageCount: Int = 0
)

object StudyLibraryManager {
    private const val EXACT_NEET_FOLDER_NAME = "0.Neet 2027"
    private const val MEMORY_DB_FILENAME = "jarvis_permanent_pdf_memories.json"
    private val jsonFormat = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // In-RAM mirror of the permanent on-disk PDF memory database so PDFs are never re-read
    private val memoryCache = mutableMapOf<String, PermanentPdfMemory>()
    private var memoryLoadedFromDisk = false

    fun getDedicatedRootFolder(context: Context): File {
        val extRoot = Environment.getExternalStorageDirectory()
        val exactFolder = File(extRoot, EXACT_NEET_FOLDER_NAME)
        if (exactFolder.exists()) return exactFolder

        // Fallback case-insensitive check in /storage/emulated/0/ if needed
        val matched = extRoot.listFiles()?.firstOrNull {
            it.isDirectory && it.name.equals(EXACT_NEET_FOLDER_NAME, ignoreCase = true)
        }
        return matched ?: exactFolder
    }

    private fun getPermanentMemoryFile(context: Context, rootFolder: File): File {
        // Store in app-private internal storage AND mirror inside 0.Neet 2027/.jarvis_pdf_memory.json
        val mirror = File(rootFolder, ".jarvis_pdf_memory.json")
        return if (rootFolder.exists() && rootFolder.canWrite()) mirror
        else File(context.filesDir, MEMORY_DB_FILENAME)
    }

    @Synchronized
    fun ensurePermanentMemoriesLoaded(context: Context, rootFolder: File) {
        if (memoryLoadedFromDisk) return
        val candidates = listOf(
            File(rootFolder, ".jarvis_pdf_memory.json"),
            File(context.filesDir, MEMORY_DB_FILENAME)
        )
        for (dbFile in candidates) {
            if (dbFile.exists()) {
                runCatching {
                    val raw = dbFile.readText()
                    val list = jsonFormat.decodeFromString<List<PermanentPdfMemory>>(raw)
                    list.forEach { memoryCache[it.relativePath] = it }
                }
                if (memoryCache.isNotEmpty()) break
            }
        }
        memoryLoadedFromDisk = true
    }

    @Synchronized
    fun savePermanentMemoriesToDisk(context: Context, rootFolder: File) {
        val list = memoryCache.values.toList()
        val raw = runCatching { jsonFormat.encodeToString(list) }.getOrDefault("[]")
        runCatching {
            File(context.filesDir, MEMORY_DB_FILENAME).writeText(raw)
        }
        runCatching {
            if (rootFolder.exists()) {
                File(rootFolder, ".jarvis_pdf_memory.json").writeText(raw)
            }
        }
    }

    fun getAllPermanentMemories(): List<PermanentPdfMemory> = memoryCache.values.toList()

    fun getPdfPageCount(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrDefault(0)
    }

    fun renderPdfPageToBitmap(file: File, pageIndex: Int, maxDim: Int = 1400): Bitmap? {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    val safeIdx = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(safeIdx).use { page ->
                        val width = (page.width * 2).coerceIn(500, maxDim)
                        val height = (page.height * 2).coerceIn(700, maxDim + 400)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * Reads a PDF ONCE and extracts embedded text streams + structural syllabus metadata
     * without ever needing to re-read the PDF on future launches.
     */
    private fun readPdfOnceAndBuildPermanentMemory(
        file: File,
        relativePath: String,
        subjectTrack: String,
        subCategory: String,
        matchedChapter: String
    ): PermanentPdfMemory {
        val pages = getPdfPageCount(file)
        val embeddedText = extractEmbeddedPdfTextOnce(file)
        val teacherMatch = Regex("""By\s+([A-Za-z\s]+Sir)""", RegexOption.IGNORE_CASE)
            .find(file.name)?.groupValues?.getOrNull(1)?.trim() ?: "PW / NEET Faculty"

        val summaryBuilder = StringBuilder()
        summaryBuilder.append("PERMANENT PDF MEMORY // Path: $relativePath\n")
        summaryBuilder.append("Track: $subjectTrack | Folder: $subCategory | Chapter: $matchedChapter | Pages: $pages | Faculty: $teacherMatch\n")
        if (embeddedText.isNotBlank()) {
            summaryBuilder.append("Extracted PDF Content:\n").append(embeddedText.take(3500))
        } else {
            summaryBuilder.append(
                "Slide/Handwritten PDF ($pages pages, ${file.length() / 1024} KB). " +
                    "Indexed permanently under $matchedChapter ($subCategory). " +
                    "Tap 'Deep Vision Memorize' in Library to OCR slide diagrams & handwritten reactions into permanent memory."
            )
        }

        val stamp = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US).format(Date())
        return PermanentPdfMemory(
            relativePath = relativePath,
            fileName = file.name,
            fileSizeKb = (file.length() / 1024L).coerceAtLeast(1L),
            lastModifiedMillis = file.lastModified(),
            pageCount = pages,
            subjectTrack = subjectTrack,
            subCategory = subCategory,
            matchedChapter = matchedChapter,
            extractedMemoryText = summaryBuilder.toString(),
            deepVisionMemorized = embeddedText.length > 180,
            memorizedAtIst = stamp
        )
    }

    /**
     * Pure-Kotlin one-time PDF text stream extractor (decompresses FlateDecode streams
     * and reads PDF text blocks `( ... ) Tj` / `[ ... ] TJ` up to 3.5 MB).
     */
    private fun extractEmbeddedPdfTextOnce(file: File): String {
        return runCatching {
            val maxBytes = minOf(file.length(), 3_500_000L).toInt()
            val rawBytes = ByteArray(maxBytes)
            file.inputStream().use { it.read(rawBytes, 0, maxBytes) }
            val rawLatin = String(rawBytes, Charsets.ISO_8859_1)

            val extractedChunks = mutableListOf<String>()
            val streamRegex = Regex("""stream\r?\n([\s\S]*?)\r?\nendstream""")
            val textLiteralRegex = Regex("""\(([^()\\]{2,120})\)""")

            for (match in streamRegex.findAll(rawLatin)) {
                val content = match.groupValues[1]
                val streamBytes = content.toByteArray(Charsets.ISO_8859_1)
                val decompressed = runCatching {
                    val inflater = Inflater()
                    inflater.setInput(streamBytes)
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    while (!inflater.finished() && out.size() < 65536) {
                        val count = inflater.inflate(buf)
                        if (count <= 0) break
                        out.write(buf, 0, count)
                    }
                    inflater.end()
                    out.toString("ISO-8859-1")
                }.getOrDefault(content)

                for (lit in textLiteralRegex.findAll(decompressed)) {
                    val token = lit.groupValues[1]
                        .replace("\\n", " ")
                        .replace("\\r", " ")
                        .trim()
                    if (token.count { it.isLetterOrDigit() } >= 3) {
                        extractedChunks.add(token)
                    }
                    if (extractedChunks.size >= 350) break
                }
                if (extractedChunks.size >= 350) break
            }
            extractedChunks.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(3500)
        }.getOrDefault("")
    }

    /**
     * Deep Vision OCR / Slide Memory Extractor: renders pages 1..3 of a handwritten/slide PDF
     * once, sends them to Gemini Flash, and saves the extracted formulas/reactions permanently.
     */
    suspend fun deepMemorizePdfWithVision(
        context: Context,
        rootFolder: File,
        target: IndexedStudyFile,
        modelId: String
    ): Result<PermanentPdfMemory> = withContext(Dispatchers.IO) {
        val apiKey = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Add your Gemini API key in Key Vault first."))
        }
        runCatching {
            val pagesToScan = minOf(target.pdfPageCount.coerceAtLeast(1), 3)
            val base64Pages = mutableListOf<String>()
            for (p in 0 until pagesToScan) {
                val bmp = renderPdfPageToBitmap(target.file, p, maxDim = 1024)
                if (bmp != null) {
                    val bos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 72, bos)
                    base64Pages.add(Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
                }
            }

            val chosenModel = modelId.ifBlank { "gemini-2.5-flash" }
            val candidates = listOf(chosenModel, "gemini-2.5-flash", "gemini-3-flash-preview").distinct()
            var visionSummary = ""

            for (mod in candidates) {
                val attempt = runCatching {
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$mod:generateContent?key=$apiKey")
                    val payload = buildJsonObject {
                        putJsonArray("contents") {
                            addJsonObject {
                                put("role", "user")
                                putJsonArray("parts") {
                                    addJsonObject {
                                        put(
                                            "text",
                                            "You are J.A.R.V.I.S. Read these pages from '${target.relativePath}' " +
                                                "(Chapter: ${target.matchedChapter}) and create a permanent, dense NEET UG 2027 " +
                                                "memory sheet containing: 1) Every formula/reaction/mechanism shown, " +
                                                "2) Key NCERT definitions & exceptions, 3) Any DPP/question concepts."
                                        )
                                    }
                                    base64Pages.forEach { b64 ->
                                        addJsonObject {
                                            putJsonObject("inlineData") {
                                                put("mimeType", "image/jpeg")
                                                put("data", b64)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        doOutput = true
                        connectTimeout = 20000
                        readTimeout = 30000
                    }
                    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    if (code !in 200..299) throw IllegalStateException("HTTP $code")
                    val root = jsonFormat.parseToJsonElement(body).jsonObject
                    root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content.orEmpty()
                }
                if (attempt.isSuccess && attempt.getOrThrow().isNotBlank()) {
                    visionSummary = attempt.getOrThrow()
                    break
                }
            }

            if (visionSummary.isBlank()) {
                throw IllegalStateException("Vision OCR returned empty text.")
            }

            val stamp = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US).format(Date())
            val updated = PermanentPdfMemory(
                relativePath = target.relativePath,
                fileName = target.name,
                fileSizeKb = target.sizeKb,
                lastModifiedMillis = target.file.lastModified(),
                pageCount = target.pdfPageCount,
                subjectTrack = target.subjectTrack,
                subCategory = target.folderCategory,
                matchedChapter = target.matchedChapter,
                extractedMemoryText = "DEEP PERMANENT PDF MEMORY (${target.relativePath}):\n$visionSummary",
                deepVisionMemorized = true,
                memorizedAtIst = stamp
            )
            synchronized(this) {
                memoryCache[target.relativePath] = updated
                savePermanentMemoriesToDisk(context, rootFolder)
            }
            updated
        }
    }

    private fun parseFolderHierarchy(relativePath: String, plan: NeetPlanRoot): Triple<String, String, String> {
        val segments = relativePath.split("/").filter { it.isNotBlank() }
        val topFolder = segments.firstOrNull().orEmpty()

        val subjectTrack = when {
            topFolder.contains("Biology", ignoreCase = true) -> "Biology_360p (Biology Track)"
            topFolder.contains("Chemistry", ignoreCase = true) -> {
                val sub = segments.getOrNull(1).orEmpty()
                if (sub.equals("OC", ignoreCase = true)) "Chemistry_360p • OC (Organic)"
                else if (sub.equals("IOC", ignoreCase = true)) "Chemistry_360p • IOC (Inorganic)"
                else "Chemistry_360p"
            }
            topFolder.contains("Physics", ignoreCase = true) -> "Physics_360p (Physics Track)"
            topFolder.contains("MATERIALS", ignoreCase = true) -> "MATERIALS NEEDED_360p"
            else -> "0.Neet 2027 Root"
        }

        val parentFolder = segments.dropLast(1).lastOrNull().orEmpty()
        val folderCategory = when {
            parentFolder.equals("DppNotes", ignoreCase = true) -> "DppNotes (DPP PDF)"
            parentFolder.equals("notes", ignoreCase = true) -> "notes (Class Notes PDF)"
            parentFolder.equals("videos", ignoreCase = true) -> "videos (Lecture Video Metadata Only)"
            else -> parentFolder.ifBlank { "Root Study Plan" }
        }

        // Detect Chapter folder (e.g. "Ch 09 Biomolecules", "Ch-03 Organic Chemistry ... GOC_360p")
        val chapterSegment = segments.firstOrNull {
            it.startsWith("Ch", ignoreCase = true) ||
                it.contains("Biomolecules", ignoreCase = true) ||
                it.contains("Hydrocarbon", ignoreCase = true) ||
                it.contains("GOC", ignoreCase = true) ||
                it.contains("Aldehyde", ignoreCase = true) ||
                it.contains("Amine", ignoreCase = true) ||
                it.contains("Haloalkane", ignoreCase = true) ||
                it.contains("Alcohol", ignoreCase = true)
        } ?: segments.dropLast(1).lastOrNull() ?: "NEET 2027 Master Plan"

        return Triple(subjectTrack, folderCategory, chapterSegment)
    }

    /**
     * Scans `/storage/emulated/0/0.Neet 2027` and all subfolders recursively.
     * - Videos (.mp4/.mkv/.webm): maps path, chapter, lecture number & size WITHOUT reading or playing video bytes.
     * - PDFs (.pdf): checks Permanent Memory Bank first; if not yet memorized, reads the PDF ONCE and stores it permanently!
     */
    suspend fun indexAllStudyFiles(root: File, plan: NeetPlanRoot): List<IndexedStudyFile> = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext emptyList()

        // Load existing permanent PDF memories from disk if not loaded yet
        val existingDbFile = File(root, ".jarvis_pdf_memory.json")
        if (!memoryLoadedFromDisk && existingDbFile.exists()) {
            runCatching {
                val list = jsonFormat.decodeFromString<List<PermanentPdfMemory>>(existingDbFile.readText())
                synchronized(this@StudyLibraryManager) {
                    list.forEach { memoryCache[it.relativePath] = it }
                    memoryLoadedFromDisk = true
                }
            }
        }

        val fmt = SimpleDateFormat("dd/MM | HH:mm", Locale.US)
        var newMemoriesAdded = false

        val results = root.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { file ->
                val rel = file.absolutePath.removePrefix(root.absolutePath).removePrefix("/")
                val ext = file.extension.lowercase()
                val sizeKb = (file.length() / 1024L).coerceAtLeast(1L)
                val (subjectTrack, folderCategory, matchedChapter) = parseFolderHierarchy(rel, plan)

                val isVideo = ext in listOf("mp4", "mkv", "webm", "avi", "mov")

                if (isVideo) {
                    // STRICT RULE: Never read video file bytes or play video files directly! Only map metadata.
                    val sizeMb = "%.2f MB".format(file.length().toDouble() / (1024.0 * 1024.0))
                    IndexedStudyFile(
                        file = file,
                        relativePath = rel,
                        name = file.name,
                        extension = ext,
                        sizeKb = sizeKb,
                        lastModifiedIst = fmt.format(Date(file.lastModified())),
                        extractedSnippet = "VIDEO CATALOGUE MAP ONLY (Not opened/played directly) // Track: $subjectTrack | Chapter: $matchedChapter | Lecture File: ${file.name} ($sizeMb)",
                        matchedChapter = matchedChapter,
                        subjectTrack = subjectTrack,
                        folderCategory = folderCategory,
                        isVideoMetadataOnly = true,
                        isPdfMemorizedPermanently = false,
                        pdfPageCount = 0
                    )
                } else if (ext == "pdf") {
                    // Check if this PDF is already in our Permanent Memory Bank
                    val cached = synchronized(this@StudyLibraryManager) { memoryCache[rel] }
                    val memory = if (cached != null && cached.lastModifiedMillis == file.lastModified()) {
                        cached
                    } else {
                        val fresh = readPdfOnceAndBuildPermanentMemory(
                            file = file,
                            relativePath = rel,
                            subjectTrack = subjectTrack,
                            subCategory = folderCategory,
                            matchedChapter = matchedChapter
                        )
                        synchronized(this@StudyLibraryManager) {
                            memoryCache[rel] = fresh
                        }
                        newMemoriesAdded = true
                        fresh
                    }

                    IndexedStudyFile(
                        file = file,
                        relativePath = rel,
                        name = file.name,
                        extension = ext,
                        sizeKb = sizeKb,
                        lastModifiedIst = fmt.format(Date(file.lastModified())),
                        extractedSnippet = memory.extractedMemoryText,
                        matchedChapter = memory.matchedChapter,
                        subjectTrack = memory.subjectTrack,
                        folderCategory = memory.subCategory,
                        isVideoMetadataOnly = false,
                        isPdfMemorizedPermanently = true,
                        pdfPageCount = memory.pageCount
                    )
                } else {
                    val textSnippet = runCatching { file.readText().take(3000) }.getOrDefault("File: $rel")
                    IndexedStudyFile(
                        file = file,
                        relativePath = rel,
                        name = file.name,
                        extension = ext,
                        sizeKb = sizeKb,
                        lastModifiedIst = fmt.format(Date(file.lastModified())),
                        extractedSnippet = textSnippet,
                        matchedChapter = matchedChapter,
                        subjectTrack = subjectTrack,
                        folderCategory = folderCategory,
                        isVideoMetadataOnly = false,
                        isPdfMemorizedPermanently = true,
                        pdfPageCount = 0
                    )
                }
            }
            .sortedWith(compareBy<IndexedStudyFile> { it.subjectTrack }.thenBy { it.matchedChapter }.thenBy { it.name })
            .toList()

        if (newMemoriesAdded) {
            runCatching {
                val raw = jsonFormat.encodeToString(memoryCache.values.toList())
                File(root, ".jarvis_pdf_memory.json").writeText(raw)
            }
        }

        results
    }
}

@Composable
fun StudyLibraryScreen(
    plan: NeetPlanRoot,
    masterPrompt: String,
    lab: GlassLabController,
    committedModelId: String,
    committedTemperature: Float,
    forceOffline: Boolean,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lab.currentPalette

    var hasStoragePerm by remember { mutableStateOf(XiaomiSurvivalHelper.hasAllFilesAccess()) }
    val rootFolder = remember(hasStoragePerm) { StudyLibraryManager.getDedicatedRootFolder(context) }

    val indexedFiles = remember { mutableStateListOf<IndexedStudyFile>() }
    var selectedTrackFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<IndexedStudyFile?>(null) }

    var statusBanner by remember {
        mutableStateOf("Scanning /storage/emulated/0/0.Neet 2027 (Biology_360p, Chemistry_360p, MATERIALS NEEDED_360p, Physics_360p)...")
    }
    var isScanning by remember { mutableStateOf(false) }
    var isDeepMemorizing by remember { mutableStateOf(false) }

    // PDF Preview Page State (for inspecting pages when desired)
    var currentPdfPage by remember { mutableIntStateOf(0) }
    var renderedPdfBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun scanAndMemorizeAllOnce() {
        if (isScanning) return
        isScanning = true
        scope.launch {
            StudyLibraryManager.ensurePermanentMemoriesLoaded(context, rootFolder)
            val list = StudyLibraryManager.indexAllStudyFiles(rootFolder, plan)
            StudyLibraryManager.savePermanentMemoriesToDisk(context, rootFolder)
            indexedFiles.clear()
            indexedFiles.addAll(list)
            val pdfCount = list.count { it.extension == "pdf" }
            val videoCount = list.count { it.isVideoMetadataOnly }
            val chaptersCount = list.map { it.matchedChapter }.distinct().size
            statusBanner = "✓ Mapped ${list.size} items in '0.Neet 2027' across $chaptersCount folders: " +
                "$pdfCount PDFs permanently memorized (read-once) • $videoCount videos catalogued (metadata only)."
            if (selectedFile == null && list.isNotEmpty()) {
                val firstPdf = list.firstOrNull { it.extension == "pdf" } ?: list.first()
                selectedFile = firstPdf
                if (firstPdf.extension == "pdf") {
                    currentPdfPage = 0
                    renderedPdfBitmap = StudyLibraryManager.renderPdfPageToBitmap(firstPdf.file, 0)
                }
            }
            isScanning = false
        }
    }

    LaunchedEffect(hasStoragePerm) {
        if (hasStoragePerm) {
            scanAndMemorizeAllOnce()
        }
    }

    val trackFilters = listOf("ALL", "Biology_360p", "Chemistry_360p", "Physics_360p", "MATERIALS", "PDFs ONLY", "VIDEOS MAP")

    val filteredFiles = remember(indexedFiles.size, selectedTrackFilter, searchQuery) {
        indexedFiles.filter { item ->
            val matchesTrack = when (selectedTrackFilter) {
                "ALL" -> true
                "PDFs ONLY" -> item.extension == "pdf"
                "VIDEOS MAP" -> item.isVideoMetadataOnly
                else -> item.relativePath.contains(selectedTrackFilter, ignoreCase = true) ||
                    item.subjectTrack.contains(selectedTrackFilter, ignoreCase = true)
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                item.relativePath.contains(searchQuery, ignoreCase = true) ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.matchedChapter.contains(searchQuery, ignoreCase = true) ||
                    item.extractedSnippet.contains(searchQuery, ignoreCase = true)
            }
            matchesTrack && matchesQuery
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Top Status & Permanent PDF Memory Engine Card
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = JarvisSpatialPalettes.StatusSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EXACT FOLDER LOCKED // Internal shared storage > 0.Neet 2027",
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
                        text = "Rule Active: All PDFs (DppNotes, notes, Study Plan) are read ONCE and stored in permanent memory (.jarvis_pdf_memory.json). Videos are mapped by name/chapter without reading or playing video files.",
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                }
                if (!hasStoragePerm) {
                    LiquidGlassButton(
                        text = "Grant All Files Access",
                        icon = Icons.Default.Security,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusWarning,
                        onClick = {
                            XiaomiSurvivalHelper.openAllFilesAccessSettings(context)
                            hasStoragePerm = XiaomiSurvivalHelper.hasAllFilesAccess()
                        }
                    )
                }
            }

            if (isScanning || isDeepMemorizing) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = JarvisSpatialPalettes.StatusWarning,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassButton(
                    text = if (isScanning) "Scanning 0.Neet 2027..." else "Scan & Memorize New Files (${indexedFiles.size})",
                    icon = Icons.Default.Refresh,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = { scanAndMemorizeAllOnce() }
                )
                LiquidGlassButton(
                    text = if (isDeepMemorizing) "Memorizing Slides..." else "Batch Vision-Memorize PDFs",
                    icon = Icons.Default.Memory,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        if (!isDeepMemorizing) {
                            isDeepMemorizing = true
                            scope.launch {
                                val pdfTargets = indexedFiles.filter { it.extension == "pdf" }.take(5)
                                var done = 0
                                for (pdf in pdfTargets) {
                                    val res = StudyLibraryManager.deepMemorizePdfWithVision(
                                        context = context,
                                        rootFolder = rootFolder,
                                        target = pdf,
                                        modelId = committedModelId
                                    )
                                    if (res.isSuccess) done++
                                }
                                scanAndMemorizeAllOnce()
                                statusBanner = "✓ Deep Vision-Memorized $done PDF(s) permanently into .jarvis_pdf_memory.json!"
                                isDeepMemorizing = false
                            }
                        }
                    }
                )
            }
        }

        // 2. Folder Tree Filter Chips & Search Bar
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trackFilters.forEach { filter ->
                    LiquidGlassButton(
                        text = filter,
                        lab = lab,
                        isActive = selectedTrackFilter == filter,
                        accentColor = if (selectedTrackFilter == filter) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                        onClick = { selectedTrackFilter = filter }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassPillInput(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search 0.Neet 2027 (e.g. 'Biomolecules', 'Pankaj Sijariya', 'DppNotes', 'GOC', 'Hydrocarbon')...",
                lab = lab,
                leadingIcon = Icons.Default.Search,
                trailingContent = {
                    if (searchQuery.isNotEmpty()) {
                        LiquidGlassButton(
                            text = "Clear",
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusError,
                            onClick = { searchQuery = "" }
                        )
                    }
                }
            )
        }

        // 3. Two-Pane Map: Full Recursive Subfolder List (Left) + Permanent PDF Memory & Page Inspector (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Mapped Files & Subfolders inside 0.Neet 2027
            LiquidGlassCard(lab = lab, tilt = tilt, modifier = Modifier.weight(0.48f)) {
                Text(
                    text = "MAPPED ITEMS IN 0.Neet 2027 (${filteredFiles.size})",
                    color = JarvisSpatialPalettes.StatusSuccess,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (filteredFiles.isEmpty()) {
                    Text(
                        text = if (!rootFolder.exists()) {
                            "Folder '/storage/emulated/0/0.Neet 2027' not detected yet—ensure '3. Grant All Files Access' is enabled."
                        } else {
                            "No files match filter '$selectedTrackFilter / $searchQuery'."
                        },
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredFiles.take(80).forEach { item ->
                            val isSelected = selectedFile?.relativePath == item.relativePath
                            val badgeColor = when {
                                item.isVideoMetadataOnly -> JarvisSpatialPalettes.StatusError
                                item.extension == "pdf" -> JarvisSpatialPalettes.StatusSuccess
                                else -> JarvisSpatialPalettes.StatusWarning
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0xFF27272A) else Color(0xFF121216))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) badgeColor else Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        selectedFile = item
                                        if (item.extension == "pdf") {
                                            currentPdfPage = 0
                                            renderedPdfBitmap = StudyLibraryManager.renderPdfPageToBitmap(item.file, 0)
                                        } else {
                                            renderedPdfBitmap = null
                                        }
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (item.isVideoMetadataOnly) "VIDEO MAP ONLY"
                                        else if (item.extension == "pdf") "PDF MEMORIZED ✓"
                                        else item.extension.uppercase(),
                                        color = badgeColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.relativePath,
                                    color = JarvisSpatialPalettes.StatusWarning,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "${item.subjectTrack} • ${item.matchedChapter} • ${item.folderCategory}",
                                    color = palette.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Right Column: Permanent Memory Inspector & PDF Page Viewer (Never plays videos)
            LiquidGlassCard(lab = lab, tilt = tilt, modifier = Modifier.weight(0.52f)) {
                val active = selectedFile
                if (active == null) {
                    Text(
                        text = "Tap any mapped PDF or video item on the left to inspect its permanent memory.",
                        color = palette.textSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = "PERMANENT MEMORY RECORD // ${active.folderCategory.uppercase()}",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = active.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Path: 0.Neet 2027/${active.relativePath}",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (active.isVideoMetadataOnly) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF18181B))
                                .border(1.dp, JarvisSpatialPalettes.StatusError, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "VIDEO CATALOGUE ENTRY (DIRECT READING & PLAYBACK DISABLED BY RULE)",
                                    color = JarvisSpatialPalettes.StatusError,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Track: ${active.subjectTrack}\n" +
                                        "• Chapter Folder: ${active.matchedChapter}\n" +
                                        "• Lecture File: ${active.name}\n" +
                                        "• Size: ${"%.2f MB".format(active.sizeKb / 1024.0)} (${active.lastModifiedIst})",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    } else if (active.extension == "pdf") {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassButton(
                                text = if (isDeepMemorizing) "Extracting Slide Memory..." else "Deep Vision Memorize PDF (Once)",
                                icon = Icons.Default.AutoAwesome,
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusSuccess,
                                onClick = {
                                    if (!isDeepMemorizing) {
                                        isDeepMemorizing = true
                                        scope.launch {
                                            val res = StudyLibraryManager.deepMemorizePdfWithVision(
                                                context = context,
                                                rootFolder = rootFolder,
                                                target = active,
                                                modelId = committedModelId
                                            )
                                            res.fold(
                                                onSuccess = { mem ->
                                                    val updatedItem = active.copy(extractedSnippet = mem.extractedMemoryText)
                                                    selectedFile = updatedItem
                                                    val idx = indexedFiles.indexOfFirst { it.relativePath == active.relativePath }
                                                    if (idx >= 0) indexedFiles[idx] = updatedItem
                                                    statusBanner = "✓ Permanently stored deep slide memory for '${active.name}'!"
                                                },
                                                onFailure = { err ->
                                                    statusBanner = "Vision memory note: ${err.message}"
                                                }
                                            )
                                            isDeepMemorizing = false
                                        }
                                    }
                                }
                            )
                            LiquidGlassButton(
                                text = "◀ Page",
                                lab = lab,
                                onClick = {
                                    if (currentPdfPage > 0) {
                                        currentPdfPage--
                                        renderedPdfBitmap = StudyLibraryManager.renderPdfPageToBitmap(active.file, currentPdfPage)
                                    }
                                }
                            )
                            LiquidGlassButton(
                                text = "Page ${currentPdfPage + 1}/${active.pdfPageCount.coerceAtLeast(1)} ▶",
                                lab = lab,
                                onClick = {
                                    if (currentPdfPage + 1 < active.pdfPageCount) {
                                        currentPdfPage++
                                        renderedPdfBitmap = StudyLibraryManager.renderPdfPageToBitmap(active.file, currentPdfPage)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        val bmp = renderedPdfBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF Page ${currentPdfPage + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF09090B))
                                .border(1.dp, JarvisSpatialPalettes.StatusSuccess, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisSpatialPalettes.StatusSuccess, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "STORED PERMANENT MEMORY (NEVER NEEDS RE-READING)",
                                        color = JarvisSpatialPalettes.StatusSuccess,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = active.extractedSnippet,
                                    color = Color(0xFFFAFAFA),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
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
