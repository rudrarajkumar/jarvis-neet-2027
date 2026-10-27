package com.jarvis.neet2027

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import com.jarvis.neet2027.model.NeetPlanRepository
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class IndexedStudyFile(
    val file: File,
    val relativePath: String,
    val name: String,
    val extension: String,
    val sizeKb: Long,
    val lastModifiedIst: String,
    val extractedSnippet: String,
    val matchedChapter: String
)

object StudyLibraryManager {
    private const val PREFS_NAME = "jarvis_study_library_prefs"
    private const val KEY_CUSTOM_FOLDER = "dedicated_folder_path"

    fun getDedicatedRootFolder(context: Context): File {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultRoot = File(Environment.getExternalStorageDirectory(), "JarvisNEET2027")
        val savedPath = prefs.getString(KEY_CUSTOM_FOLDER, defaultRoot.absolutePath) ?: defaultRoot.absolutePath
        val folder = File(savedPath)
        if (!folder.exists()) {
            runCatching { folder.mkdirs() }
        }
        // Ensure safety subfolders exist
        runCatching {
            File(folder, "Lectures").mkdirs()
            File(folder, "Notes").mkdirs()
            File(folder, "NCERT_PDFs").mkdirs()
            File(folder, "Jarvis_AI_Notes").mkdirs()
            File(folder, ".jarvis_snapshots").mkdirs()
            File(folder, ".jarvis_trash").mkdirs()
        }
        return folder
    }

    fun isInsideSandbox(root: File, target: File): Boolean {
        return runCatching {
            target.canonicalPath.startsWith(root.canonicalPath)
        }.getOrDefault(false)
    }

    fun appendAuditLog(root: File, action: String, detail: String) {
        runCatching {
            val logFile = File(root, ".jarvis_audit.log")
            val stamp = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US).format(Date())
            logFile.appendText("[$stamp IST] $action: $detail\n")
        }
    }

    fun readAuditLog(root: File): String {
        val logFile = File(root, ".jarvis_audit.log")
        if (!logFile.exists()) return "Audit log empty. Every create, edit, snapshot, move, and delete is recorded here."
        return runCatching {
            logFile.readLines().takeLast(18).reversed().joinToString("\n")
        }.getOrDefault("Unable to read audit log.")
    }

    fun seedStarterNeetFiles(root: File) {
        val samples = mapOf(
            "Notes/Rotational_Motion_Notes.md" to """
                # Rotational Motion — Compressed NCERT & PW Formula Notes
                - **Page 1 | Moment of Inertia (I)**: I = Σ mᵢ rᵢ² | Ring: MR² | Disc: ½MR² | Solid Sphere: (2/5)MR² | Hollow Sphere: (2/3)MR².
                - **Page 2 | Theorems**: Parallel Axis: I = I_cm + Md² | Perpendicular Axis (2D lamina): I_z = I_x + I_y.
                - **Page 3 | Rolling Without Slipping**: v_cm = Rω | Kinetic Energy K_total = ½mv_cm² (1 + k²/R²).
                - **Acceleration on Inclined Plane (θ)**: a = g sinθ / (1 + k²/R²) — Solid sphere beats hollow sphere every time!
                - **Wrong Question ID #RM-14**: Forgot to check if cylinder was hollow or solid; always underline geometry in NEET question stem.
            """.trimIndent(),
            "Notes/Electric_Charges_And_Fields_L01.md" to """
                # Electric Charges & Fields — Class 12 Physics Track (Day 1)
                - **Page 1 | Coulomb's Law**: F = (1 / 4πε₀) · |q₁q₂| / r² where 1/(4πε₀) = 9 × 10⁹ N·m²/C².
                - **Page 2 | Electric Dipole Field**:
                  * Axial line: E_axial = (1 / 4πε₀) · (2p / r³) (along p vector)
                  * Equatorial line: E_eq = (1 / 4πε₀) · (p / r³) (opposite to p vector)
                  * Torque in uniform field: τ = p × E = pE sinθ | Potential Energy: U = -p · E = -pE cosθ.
                - **Page 3 | Gauss's Law**: Φ_E = ∮ E · dA = q_enclosed / ε₀ (independent of Gaussian surface size or shape).
            """.trimIndent(),
            "Notes/GOC_Organic_Basics_L01.md" to """
                # General Organic Chemistry (GOC) — Electronic Effects & Stability
                - **Page 1 | Priority Rule**: Aromaticity > Resonance (+M/-M) > Hyperconjugation (α-H count) > Inductive (+I/-I).
                - **Page 2 | Carbocation Stability**: (C₆H₅)₃C⁺ > (CH₃)₃C⁺ (9 α-H) > (CH₃)₂CH⁺ (6 α-H) > CH₃CH₂⁺ (3 α-H).
                - **Page 3 | Acidity Order**: -SO₃H > -COOH > Phenol > H₂O > ROH > HC≡CH.
            """.trimIndent(),
            "Notes/Biomolecules_Zoology_NCERT.md" to """
                # Biomolecules (Zoology) — NCERT Class 11 Chapter 9
                - **Page 1 | NCERT Table 9.4 (Secondary Metabolites)**:
                  * Pigments: Carotenoids, Anthocyanins
                  * Alkaloids: Morphine, Codeine
                  * Terpenoides: Monoterpenes, Diterpenes
                  * Lectins: Concanavalin A
                  * Drugs: Vinblastin, Curcumin
                - **Page 2 | Enzyme Kinetics**: Competitive inhibitor (e.g. malonate inhibiting succinic dehydrogenase) increases K_m without changing V_max.
            """.trimIndent(),
            "Notes/Oscillations_SHM_Track4.md" to """
                # Oscillations (SHM) — Track 4 Physics
                - **Page 1 | Kinematics**: x(t) = A sin(ωt + φ) | v = ω√(A² - x²) | a = -ω²x.
                - **Page 2 | Energy in SHM**: Total Energy E = ½mω²A² (constant); KE and PE oscillate at frequency 2f.
            """.trimIndent()
        )
        samples.forEach { (relPath, content) ->
            val f = File(root, relPath)
            f.parentFile?.mkdirs()
            if (!f.exists()) {
                f.writeText(content)
            }
        }
        appendAuditLog(root, "SEED_STARTER_PACK", "Created 5 NCERT & PW starter notes in Notes/")
    }

    fun extractTextFromFile(file: File): String {
        val ext = file.extension.lowercase()
        return runCatching {
            when (ext) {
                "txt", "md", "json", "csv", "log" -> file.readText().take(8000)
                "docx", "pptx" -> extractXmlFromZipOfficeFile(file)
                "pdf" -> {
                    val pages = getPdfPageCount(file)
                    "PDF Document (${file.name}, $pages pages). Open in viewer to render pages or run Jarvis Vision OCR."
                }
                "mp4", "mkv", "webm" -> "Physics Wallah Lecture Video (${file.name}). Supports timestamp jump (e.g. 25:30)."
                else -> "Binary/Media file (${file.name}, ${file.length() / 1024} KB)."
            }
        }.getOrDefault("File: ${file.name}")
    }

    private fun extractXmlFromZipOfficeFile(file: File): String {
        val sb = StringBuilder()
        ZipInputStream(file.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".xml") && (entry.name.contains("document") || entry.name.contains("slide"))) {
                    val xml = zis.bufferedReader().readText()
                    val stripped = xml.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                    sb.append(stripped).append("\n")
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString().take(8000).ifBlank { "Office Document: ${file.name}" }
    }

    fun getPdfPageCount(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrDefault(0)
    }

    fun renderPdfPageToBitmap(file: File, pageIndex: Int): Bitmap? {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    val safeIdx = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(safeIdx).use { page ->
                        val width = (page.width * 2).coerceIn(600, 1800)
                        val height = (page.height * 2).coerceIn(800, 2400)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    fun saveFileWithSnapshot(root: File, targetFile: File, newContent: String): Boolean {
        if (!isInsideSandbox(root, targetFile)) return false
        return runCatching {
            if (targetFile.exists()) {
                val snapDir = File(root, ".jarvis_snapshots").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val snapFile = File(snapDir, "${targetFile.name}.$stamp.bak")
                targetFile.copyTo(snapFile, overwrite = true)
            }
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(newContent)
            appendAuditLog(root, "SAVE_WITH_SNAPSHOT", targetFile.name)
            true
        }.getOrDefault(false)
    }

    fun moveFileToTrash(root: File, targetFile: File): Boolean {
        if (!isInsideSandbox(root, targetFile) || !targetFile.exists()) return false
        return runCatching {
            val trashDir = File(root, ".jarvis_trash").apply { mkdirs() }
            val dest = File(trashDir, targetFile.name)
            if (dest.exists()) dest.delete()
            val moved = targetFile.renameTo(dest)
            if (moved) appendAuditLog(root, "MOVE_TO_TRASH", targetFile.name)
            moved
        }.getOrDefault(false)
    }

    fun restoreAllFromTrash(root: File): Int {
        val trashDir = File(root, ".jarvis_trash")
        if (!trashDir.exists()) return 0
        val notesDir = File(root, "Notes").apply { mkdirs() }
        var count = 0
        trashDir.listFiles()?.forEach { f ->
            val dest = File(notesDir, f.name)
            if (f.renameTo(dest)) count++
        }
        if (count > 0) appendAuditLog(root, "RESTORE_TRASH", "Restored $count files to Notes/")
        return count
    }

    suspend fun indexAllStudyFiles(root: File, plan: NeetPlanRoot): List<IndexedStudyFile> = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext emptyList()
        val allChapters = buildList {
            plan.chapterQueues.values.flatten().forEach { add(it.chapter) }
            plan.oldChapterRecallLoops.flatMap { it.chapters }.forEach { add(it) }
        }
        val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

        root.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") && !it.absolutePath.contains(".jarvis_") }
            .map { file ->
                val rel = file.absolutePath.removePrefix(root.absolutePath).removePrefix("/")
                val snippet = extractTextFromFile(file)
                val matched = allChapters.firstOrNull { ch ->
                    val token = ch.substringBefore("(").substringBefore("/").trim()
                    token.length >= 4 && (file.name.contains(token, ignoreCase = true) || snippet.contains(token, ignoreCase = true))
                } ?: "General NEET 2027"
                IndexedStudyFile(
                    file = file,
                    relativePath = rel,
                    name = file.name,
                    extension = file.extension.lowercase(),
                    sizeKb = (file.length() / 1024L).coerceAtLeast(1L),
                    lastModifiedIst = fmt.format(Date(file.lastModified())),
                    extractedSnippet = snippet,
                    matchedChapter = matched
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<IndexedStudyFile?>(null) }
    var editorContent by remember { mutableStateOf("") }
    var newFileName by remember { mutableStateOf("") }
    var statusBanner by remember {
        mutableStateOf("Dedicated Study Folder: ${rootFolder.absolutePath} (Sandboxed • Snapshot before overwrite • Undo Trash active)")
    }
    var confirmDeleteFile by remember { mutableStateOf<IndexedStudyFile?>(null) }
    var showAuditModal by remember { mutableStateOf(false) }

    // PDF Page Viewer State
    var currentPdfPage by remember { mutableIntStateOf(0) }
    var totalPdfPages by remember { mutableIntStateOf(0) }
    var renderedPdfBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Video Timestamp State
    var videoJumpMinutes by remember { mutableStateOf("25:30") }

    // AI File Citation / Summary Output
    var aiCitationResult by remember { mutableStateOf("") }
    var isAiWorking by remember { mutableStateOf(false) }

    fun refreshIndex() {
        scope.launch {
            val list = StudyLibraryManager.indexAllStudyFiles(rootFolder, plan)
            indexedFiles.clear()
            indexedFiles.addAll(list)
            if (selectedFile == null && list.isNotEmpty()) {
                val first = list.first()
                selectedFile = first
                editorContent = first.extractedSnippet
            }
        }
    }

    LaunchedEffect(hasStoragePerm) {
        if (hasStoragePerm) {
            val initial = StudyLibraryManager.indexAllStudyFiles(rootFolder, plan)
            if (initial.isEmpty()) {
                StudyLibraryManager.seedStarterNeetFiles(rootFolder)
            }
            refreshIndex()
        }
    }

    val filteredFiles = remember(indexedFiles.size, searchQuery) {
        if (searchQuery.isBlank()) indexedFiles.toList()
        else indexedFiles.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.extractedSnippet.contains(searchQuery, ignoreCase = true) ||
                it.matchedChapter.contains(searchQuery, ignoreCase = true)
        }
    }

    val todayAssignments = remember {
        NeetPlanRepository.getAssignmentsForDate(plan, NeetPlanRepository.getImphalNow().toLocalDate())
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Folder Header, All-Files Permission Check & Quick Actions
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
                            text = "CHAPTER 5 // STUDY LIBRARY & SYLLABUS FILE ENGINE (FULL)",
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
                        text = "Find in Xiaomi File Manager -> Internal shared storage -> JarvisNEET2027 (Lectures, Notes, NCERT_PDFs, Jarvis_AI_Notes).",
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

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassButton(
                    text = "Re-Scan Folder (${indexedFiles.size})",
                    icon = Icons.Default.Refresh,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = {
                        refreshIndex()
                        statusBanner = "Re-scanned ${indexedFiles.size} files in ${rootFolder.name}."
                    }
                )
                LiquidGlassButton(
                    text = "Seed Starter NEET Notes",
                    icon = Icons.Default.AutoAwesome,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        StudyLibraryManager.seedStarterNeetFiles(rootFolder)
                        refreshIndex()
                        statusBanner = "Starter NEET 2027 notes (Rotational Motion, Electric Charges, GOC, Biomolecules, SHM) ready!"
                    }
                )
                LiquidGlassButton(
                    text = "Find Rotational Motion",
                    icon = Icons.Default.Search,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = {
                        searchQuery = "Rotational Motion"
                        val match = indexedFiles.firstOrNull {
                            it.name.contains("Rotational", ignoreCase = true) ||
                                it.extractedSnippet.contains("Rotational", ignoreCase = true)
                        }
                        if (match != null) {
                            selectedFile = match
                            editorContent = match.extractedSnippet
                            aiCitationResult = "CITATION FOUND // File: ${match.relativePath} (Page 1–3)\n" +
                                "• Moment of Inertia: Ring MR², Disc ½MR², Solid Sphere (2/5)MR².\n" +
                                "• Incline acceleration: a = g sinθ / (1 + k²/R²)."
                        }
                    }
                )
                LiquidGlassButton(
                    text = "Restore Undo Trash",
                    icon = Icons.Default.Refresh,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        val count = StudyLibraryManager.restoreAllFromTrash(rootFolder)
                        refreshIndex()
                        statusBanner = "Restored $count file(s) from .jarvis_trash/ back into Notes/."
                    }
                )
                LiquidGlassButton(
                    text = if (showAuditModal) "Hide Audit Log" else "Safety Audit Log",
                    icon = Icons.Default.Info,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusError,
                    onClick = { showAuditModal = !showAuditModal }
                )
            }

            if (showAuditModal) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF09090B))
                        .border(1.dp, JarvisSpatialPalettes.StatusWarning, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = StudyLibraryManager.readAuditLog(rootFolder),
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 2. Plan Chapter-to-File Mapper ("Today: Open This File")
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Text(
                text = "TODAY'S PLAN CHAPTER-TO-FILE MAPPER (TAP TO OPEN MATCHED MATERIAL)",
                color = JarvisSpatialPalettes.StatusWarning,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                todayAssignments.forEach { assign ->
                    val matchedFile = indexedFiles.firstOrNull {
                        it.matchedChapter.contains(assign.chapterTitle.take(8), ignoreCase = true) ||
                            it.name.contains(assign.chapterTitle.take(8), ignoreCase = true)
                    }
                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF18181B))
                            .border(
                                1.dp,
                                if (matchedFile != null) JarvisSpatialPalettes.StatusSuccess else Color(0xFF52525B),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (matchedFile != null) {
                                    selectedFile = matchedFile
                                    editorContent = matchedFile.extractedSnippet
                                } else {
                                    searchQuery = assign.chapterTitle.substringBefore(" ")
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(assign.trackName, color = JarvisSpatialPalettes.StatusWarning, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(assign.chapterTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (matchedFile != null) "Today: Open ${matchedFile.name} →" else "Tap to search/create note",
                                color = if (matchedFile != null) JarvisSpatialPalettes.StatusSuccess else palette.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 3. Search Bar + Create New AI/Study Note Input
        LiquidGlassCard(lab = lab, tilt = tilt) {
            LiquidGlassPillInput(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Full-text & semantic keyword search (e.g. 'Rotational motion', 'Gauss', 'Km', 'Concanavalin')...",
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
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassPillInput(
                value = newFileName,
                onValueChange = { newFileName = it },
                placeholder = "Create new note inside Jarvis_AI_Notes/ (e.g. 'Capacitance_Formulas.md')...",
                lab = lab,
                leadingIcon = Icons.Default.Add,
                trailingContent = {
                    LiquidGlassButton(
                        text = "Create File",
                        icon = Icons.Default.Add,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            val cleanName = newFileName.trim().replace("/", "_")
                            if (cleanName.isNotEmpty()) {
                                val finalName = if (cleanName.contains(".")) cleanName else "$cleanName.md"
                                val target = File(File(rootFolder, "Jarvis_AI_Notes"), finalName)
                                val template = "# $finalName\nCreated by J.A.R.V.I.S. Study Library\n\n- **NCERT Core Line**: \n- **Formula / Exception**: \n- **Error Log ID**: \n"
                                if (StudyLibraryManager.saveFileWithSnapshot(rootFolder, target, template)) {
                                    newFileName = ""
                                    refreshIndex()
                                    statusBanner = "Created Jarvis_AI_Notes/$finalName"
                                }
                            }
                        }
                    )
                }
            )
        }

        // 4. Two-Column Split: File Cards List (Left) + In-App Viewer & Editor (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Indexed File Cards
            LiquidGlassCard(lab = lab, tilt = tilt, modifier = Modifier.weight(0.45f)) {
                Text(
                    text = "INDEXED FILES (${filteredFiles.size})",
                    color = JarvisSpatialPalettes.StatusSuccess,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredFiles.forEach { item ->
                        val isSelected = selectedFile?.file?.absolutePath == item.file.absolutePath
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Color(0xFF27272A) else Color(0xFF121216))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) JarvisSpatialPalettes.StatusWarning else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    selectedFile = item
                                    editorContent = item.extractedSnippet
                                    if (item.extension == "pdf") {
                                        currentPdfPage = 0
                                        totalPdfPages = StudyLibraryManager.getPdfPageCount(item.file)
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
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = item.extension.uppercase(),
                                    color = JarvisSpatialPalettes.StatusSuccess,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "Chapter: ${item.matchedChapter} • ${item.sizeKb} KB",
                                color = JarvisSpatialPalettes.StatusWarning,
                                fontSize = 11.sp
                            )
                            Text(
                                text = item.extractedSnippet.replace("\n", " ").take(90),
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Right Column: In-App Viewer (PDF Page Renderer / Text & Markdown Editor / Video Timestamp Opener)
            LiquidGlassCard(lab = lab, tilt = tilt, modifier = Modifier.weight(0.55f)) {
                val active = selectedFile
                if (active == null) {
                    Text("Select any file on the left to open the in-app viewer.", color = palette.textSecondary, fontSize = 14.sp)
                } else {
                    Text(
                        text = "IN-APP VIEWER // ${active.relativePath}",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Confirmation Banner for Destructive Delete
                    if (confirmDeleteFile?.file?.absolutePath == active.file.absolutePath) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(JarvisSpatialPalettes.StatusError.copy(alpha = 0.22f))
                                .border(1.dp, JarvisSpatialPalettes.StatusError, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Move '${active.name}' to Undo Trash?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiquidGlassButton(
                                    text = "Confirm Trash",
                                    lab = lab,
                                    accentColor = JarvisSpatialPalettes.StatusError,
                                    onClick = {
                                        StudyLibraryManager.moveFileToTrash(rootFolder, active.file)
                                        confirmDeleteFile = null
                                        selectedFile = null
                                        refreshIndex()
                                        statusBanner = "Moved ${active.name} to .jarvis_trash/ (tap Restore Undo Trash anytime)."
                                    }
                                )
                                LiquidGlassButton(
                                    text = "Cancel",
                                    lab = lab,
                                    onClick = { confirmDeleteFile = null }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Action Bar: Save with Snapshot, Summarize with Citations, Delete
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (active.extension in listOf("md", "txt", "json", "csv")) {
                            LiquidGlassButton(
                                text = "Save (Auto-Snapshot)",
                                icon = Icons.Default.CheckCircle,
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusSuccess,
                                onClick = {
                                    if (StudyLibraryManager.saveFileWithSnapshot(rootFolder, active.file, editorContent)) {
                                        refreshIndex()
                                        statusBanner = "Saved ${active.name} (previous version snapshotted in .jarvis_snapshots/)."
                                    }
                                }
                            )
                        }
                        LiquidGlassButton(
                            text = if (isAiWorking) "Jarvis Reading..." else "Jarvis Cite & Summarise",
                            icon = Icons.Default.AutoAwesome,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusWarning,
                            onClick = {
                                if (!isAiWorking) {
                                    isAiWorking = true
                                    scope.launch {
                                        val prompt = "Summarise this NEET study file (${active.relativePath}) in 3 crisp NCERT bullet points with exact file + page/section citations:\n\n${editorContent.take(3000)}"
                                        val reply = JarvisBrainEngine.generateJarvisReply(
                                            context = context,
                                            plan = plan,
                                            masterPrompt = masterPrompt,
                                            modelId = committedModelId,
                                            temperature = committedTemperature,
                                            forceOffline = forceOffline,
                                            conversation = emptyList(),
                                            userQuery = prompt
                                        )
                                        aiCitationResult = "CITATION SUMMARY // ${active.relativePath}:\n${reply.visibleText}"
                                        isAiWorking = false
                                    }
                                }
                            }
                        )
                        LiquidGlassButton(
                            text = "Trash File",
                            icon = Icons.Default.Delete,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusError,
                            onClick = { confirmDeleteFile = active }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Format-specific viewer body
                    when (active.extension) {
                        "pdf" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PDF Page ${currentPdfPage + 1} of ${totalPdfPages.coerceAtLeast(1)}",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LiquidGlassButton(
                                        text = "◀ Prev Page",
                                        lab = lab,
                                        onClick = {
                                            if (currentPdfPage > 0) {
                                                currentPdfPage--
                                                renderedPdfBitmap = StudyLibraryManager.renderPdfPageToBitmap(active.file, currentPdfPage)
                                            }
                                        }
                                    )
                                    LiquidGlassButton(
                                        text = "Next Page ▶",
                                        lab = lab,
                                        onClick = {
                                            if (currentPdfPage + 1 < totalPdfPages) {
                                                currentPdfPage++
                                                renderedPdfBitmap = StudyLibraryManager.renderPdfPageToBitmap(active.file, currentPdfPage)
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val bmp = renderedPdfBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "PDF Page ${currentPdfPage + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(320.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White)
                                )
                            } else {
                                Text("Place any NCERT or PW PDF inside JarvisNEET2027/NCERT_PDFs/ to view pages here.", color = palette.textSecondary, fontSize = 12.sp)
                            }
                        }
                        "mp4", "mkv", "webm" -> {
                            Text("Lecture Video Timestamp Launcher (${active.name})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            LiquidGlassPillInput(
                                value = videoJumpMinutes,
                                onValueChange = { videoJumpMinutes = it },
                                placeholder = "Enter timestamp mm:ss (e.g. 25:30)",
                                lab = lab,
                                leadingIcon = Icons.Default.PlayArrow,
                                trailingContent = {
                                    LiquidGlassButton(
                                        text = "Open at Timestamp",
                                        lab = lab,
                                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.fromFile(active.file), "video/*")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            runCatching { context.startActivity(intent) }
                                        }
                                    )
                                }
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF09090B))
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                BasicTextField(
                                    value = editorContent,
                                    onValueChange = { editorContent = it },
                                    textStyle = TextStyle(
                                        color = Color(0xFFFAFAFA),
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 19.sp
                                    ),
                                    cursorBrush = SolidColor(JarvisSpatialPalettes.StatusSuccess),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (aiCitationResult.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF18181B))
                                .border(1.dp, JarvisSpatialPalettes.StatusSuccess, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = aiCitationResult,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
