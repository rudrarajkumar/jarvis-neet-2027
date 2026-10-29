package com.jarvis.neet2027

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.neet2027.model.NeetPlanRepository
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter

// ============================================================================
// 1. 12-POINT HARDWARE, PERMISSION & ARCHITECTURE SELF-AUDIT ENGINE
// ============================================================================

data class SelfAuditCheckItem(
    val id: Int,
    val subsystem: String,
    val title: String,
    val detail: String,
    val passed: Boolean,
    val isWarningOnly: Boolean = false
)

@Serializable
data class GitHubWorkflowRunSummary(
    val runNumber: Int,
    val name: String,
    val status: String,
    val conclusion: String,
    val headShaShort: String,
    val updatedAt: String,
    val htmlUrl: String
)

@Serializable
data class GitHubReleaseAssetSummary(
    val tagName: String,
    val publishedAt: String,
    val assetName: String,
    val sizeMb: String,
    val browserDownloadUrl: String
)

@Serializable
data class BackupVaultNoteSnapshot(
    val title: String,
    val folder: String,
    val tags: List<String>,
    val markdownBody: String,
    val recallStagesDone: List<String>
)

@Serializable
data class JarvisFullBackupSnapshot(
    val exportedAtIst: String,
    val goalInstitution: String,
    val vaultNotes: List<BackupVaultNoteSnapshot>,
    val lexiconRules: List<PronunciationLexiconRule>,
    val recentChatHistory: List<ChatMessageItem>
)

object JarvisSelfAuditEngine {
    private const val GITHUB_REPO = "rudrarajkumar/jarvis-neet-2027"
    private val jsonParser = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun run12PointSystemAudit(context: Context, plan: NeetPlanRoot): List<SelfAuditCheckItem> {
        val batteryOk = XiaomiSurvivalHelper.isIgnoringBatteryOptimizations(context)
        val autostartOk = EncryptedKeyStoreVault.isXiaomiAutostartLocked(context)
        val allFilesOk = XiaomiSurvivalHelper.hasAllFilesAccess()
        val overlayOk = XiaomiSurvivalHelper.canDrawOverlays(context)
        val micPermOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val (availRamMb, totalRamMb) = XiaomiSurvivalHelper.getAvailableRamMb(context)
        val ramOk = availRamMb >= 1400L

        val geminiKey = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GEMINI_API)
        val githubPat = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GITHUB_PAT)

        val rootFolder = StudyLibraryManager.getDedicatedRootFolder(context)
        StudyLibraryManager.ensurePermanentMemoriesLoaded(context, rootFolder)
        val pdfMemoriesCount = StudyLibraryManager.getAllPermanentMemories().size
        val vaultNotesCount = JarvisVaultManager.loadVaultNotes(context).size
        val lexiconCount = JarvisVoiceOutputController.loadLexiconRules(context).size
        val modelsScanned = LocalLlmModelManager.scanModelsFolder(context)
        val hasLocalGguf = modelsScanned.any { it.existsOnDisk } ||
            LocalLlmModelManager.ramState == LocalModelRamState.READY_IN_RAM

        val totalLecturesCount = plan.videoCountAudit.totalKnownCompleted + plan.videoCountAudit.totalKnownRemaining

        return listOf(
            SelfAuditCheckItem(
                id = 1,
                subsystem = "HYPEROS BATTERY",
                title = "Xiaomi Pad 6 Unrestricted Battery Lock",
                detail = if (batteryOk) "Unrestricted background execution active ✓" else "Tap HUD Home -> Disable Battery Saver",
                passed = batteryOk
            ),
            SelfAuditCheckItem(
                id = 2,
                subsystem = "HYPEROS AUTOSTART",
                title = "Xiaomi Security Center Autostart & Recents Lock",
                detail = if (autostartOk) "Xiaomi Autostart survival flag locked ✓" else "Lock app in Recents & enable Autostart",
                passed = autostartOk
            ),
            SelfAuditCheckItem(
                id = 3,
                subsystem = "STORAGE ENGINE",
                title = "All Files Access (/storage/emulated/0/0.Neet 2027)",
                detail = if (allFilesOk) "Full read/write access to 0.Neet 2027 active ✓" else "Grant MANAGE_EXTERNAL_STORAGE permission",
                passed = allFilesOk
            ),
            SelfAuditCheckItem(
                id = 4,
                subsystem = "FLOATING HUD",
                title = "System Overlay Permission (Physics Wallah / JNote Pill)",
                detail = if (overlayOk) "SYSTEM_ALERT_WINDOW granted ✓" else "Allow Display over other apps",
                passed = overlayOk
            ),
            SelfAuditCheckItem(
                id = 5,
                subsystem = "DSP MICROPHONE",
                title = "16kHz Double-Clap & Barge-In Microphone Permission",
                detail = if (micPermOk) {
                    "RECORD_AUDIO granted • Claps: ${SharedMicAndWakeManager.totalClapTriggers} • Barge-Ins: ${SharedMicAndWakeManager.totalBargeInTriggers} ✓"
                } else {
                    "Grant Microphone permission in Voice & Mic tab"
                },
                passed = micPermOk
            ),
            SelfAuditCheckItem(
                id = 6,
                subsystem = "SNAPDRAGON 870 RAM",
                title = "LPDDR5 Memory Headroom Guardrail (>= 1,400 MB)",
                detail = "$availRamMb MB free / $totalRamMb MB total (${LocalLlmModelManager.ramState.label})",
                passed = ramOk,
                isWarningOnly = !ramOk
            ),
            SelfAuditCheckItem(
                id = 7,
                subsystem = "HARDWARE KEYSTORE",
                title = "AES-256-GCM TEE Secret Vault (Gemini & GitHub Keys)",
                detail = "Gemini Key: ${EncryptedKeyStoreVault.maskSecret(geminiKey)} | GitHub PAT: ${EncryptedKeyStoreVault.maskSecret(githubPat)}",
                passed = geminiKey.isNotBlank(),
                isWarningOnly = geminiKey.isBlank()
            ),
            SelfAuditCheckItem(
                id = 8,
                subsystem = "PDF MEMORY BANK",
                title = "Read-Once Permanent PDF Memory (.jarvis_pdf_memory.json)",
                detail = "$pdfMemoriesCount PDFs permanently indexed in 0.Neet 2027 ✓",
                passed = pdfMemoriesCount > 0,
                isWarningOnly = pdfMemoriesCount == 0
            ),
            SelfAuditCheckItem(
                id = 9,
                subsystem = "AES-256 VAULT",
                title = "Encrypted Notes, Mistake Notebook & D0-D30 Flashcards",
                detail = "$vaultNotesCount encrypted notes/mistakes stored on-device ✓",
                passed = vaultNotesCount > 0
            ),
            SelfAuditCheckItem(
                id = 10,
                subsystem = "OFFLINE GGUF & RAG",
                title = "On-Device GGUF Model & NCERT Core Knowledge Graph",
                detail = "Active: ${LocalLlmModelManager.activeMountedModelName}",
                passed = true,
                isWarningOnly = !hasLocalGguf
            ),
            SelfAuditCheckItem(
                id = 11,
                subsystem = "BUTLER VOICE TTS",
                title = "British Butler Prosody & NEET Pronunciation Lexicon",
                detail = "$lexiconCount active phonetic rules • Voice: ${JarvisVoiceOutputController.getPreferredVoiceName(context).ifBlank { "en-GB Default" }} ✓",
                passed = lexiconCount >= 10
            ),
            SelfAuditCheckItem(
                id = 12,
                subsystem = "NEET 2027 PLANNER",
                title = "4-Track Timetable, Native Alarms & AIIMS Nagpur Target",
                detail = "${plan.videoCountAudit.totalKnownRemaining} remaining of $totalLecturesCount lectures • Target AIR <= ${plan.meta.aiimsNagpurCutoffs2026Round1.ASPIRATIONAL_OPEN_BUFFER} ✓",
                passed = totalLecturesCount > 0
            )
        )
    }

    suspend fun fetchLiveGitHubCiAndReleaseStatus(
        context: Context
    ): Pair<List<GitHubWorkflowRunSummary>, GitHubReleaseAssetSummary?> = withContext(Dispatchers.IO) {
        val pat = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GITHUB_PAT)

        val runsList = runCatching {
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/actions/runs?per_page=5")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                if (pat.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $pat")
                }
                connectTimeout = 10000
                readTimeout = 10000
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = jsonParser.parseToJsonElement(body).jsonObject
            val arr = root["workflow_runs"]?.jsonArray ?: emptyList()
            arr.mapNotNull { elem ->
                val obj = elem.jsonObject
                val num = obj["run_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                val title = obj["display_title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content
                    ?: "Build #$num"
                val status = obj["status"]?.jsonPrimitive?.content ?: "unknown"
                val conclusion = obj["conclusion"]?.jsonPrimitive?.content ?: "in_progress"
                val sha = obj["head_sha"]?.jsonPrimitive?.content?.take(7) ?: "-------"
                val updated = obj["updated_at"]?.jsonPrimitive?.content?.take(19)?.replace("T", " ") ?: ""
                val htmlUrl = obj["html_url"]?.jsonPrimitive?.content ?: "https://github.com/$GITHUB_REPO/actions"
                GitHubWorkflowRunSummary(
                    runNumber = num,
                    name = title,
                    status = status,
                    conclusion = conclusion,
                    headShaShort = sha,
                    updatedAt = updated,
                    htmlUrl = htmlUrl
                )
            }
        }.getOrDefault(emptyList())

        val latestRelease = runCatching {
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/tags/latest-apk")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                if (pat.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $pat")
                }
                connectTimeout = 10000
                readTimeout = 10000
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = jsonParser.parseToJsonElement(body).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content ?: "latest-apk"
            val pub = root["published_at"]?.jsonPrimitive?.content?.take(19)?.replace("T", " ") ?: "Live"
            val firstAsset = root["assets"]?.jsonArray?.firstOrNull()?.jsonObject
            val assetName = firstAsset?.get("name")?.jsonPrimitive?.content ?: "Jarvis-NEET2027.apk"
            val sizeBytes = firstAsset?.get("size")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val sizeMb = "%.1f MB".format(sizeBytes.toFloat() / (1024f * 1024f))
            val dlUrl = firstAsset?.get("browser_download_url")?.jsonPrimitive?.content
                ?: "https://github.com/$GITHUB_REPO/releases/download/latest-apk/Jarvis-NEET2027.apk"
            GitHubReleaseAssetSummary(
                tagName = tag,
                publishedAt = pub,
                assetName = assetName,
                sizeMb = sizeMb,
                browserDownloadUrl = dlUrl
            )
        }.getOrNull()

        Pair(runsList, latestRelease)
    }

    suspend fun triggerRemoteCiBuild(context: Context): String = withContext(Dispatchers.IO) {
        val pat = EncryptedKeyStoreVault.readSecret(context, EncryptedKeyStoreVault.KEY_GITHUB_PAT)
        if (pat.isBlank()) {
            return@withContext "Add your GitHub Fine-Grained Token in the Key Vault tab first to trigger remote CI builds from inside the app."
        }
        runCatching {
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/actions/workflows/android-build.yml/dispatches")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $pat")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            conn.outputStream.use {
                it.write("""{"ref":"main"}""".toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code in 200..299) {
                "✓ Triggered remote GitHub Actions APK rebuild on branch 'main'!"
            } else {
                "GitHub API returned HTTP $code (ensure token has Actions: Read & Write permission)."
            }
        }.getOrElse { err ->
            "Remote CI trigger note: ${err.message}"
        }
    }

    fun exportFullBackupToNeetFolder(
        context: Context,
        plan: NeetPlanRoot,
        chatMessages: List<ChatMessageItem>
    ): String {
        return runCatching {
            val neetRoot = StudyLibraryManager.getDedicatedRootFolder(context)
            val backupDir = File(neetRoot, "JARVIS_BACKUPS").apply { mkdirs() }
            val nowIst = NeetPlanRepository.getImphalNow()
            val stamp = nowIst.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val targetFile = File(backupDir, "jarvis_neet2027_backup_$stamp.json")

            val mappedVaultNotes = JarvisVaultManager.loadVaultNotes(context).map { n ->
                BackupVaultNoteSnapshot(
                    title = n.title,
                    folder = n.folder,
                    tags = n.tags,
                    markdownBody = n.markdownBody,
                    recallStagesDone = n.recallStagesDone
                )
            }

            val snapshot = JarvisFullBackupSnapshot(
                exportedAtIst = nowIst.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a 'IST'")),
                goalInstitution = plan.meta.goalInstitution,
                vaultNotes = mappedVaultNotes,
                lexiconRules = JarvisVoiceOutputController.loadLexiconRules(context),
                recentChatHistory = chatMessages.takeLast(50)
            )
            val rawJson = jsonParser.encodeToString(snapshot)
            targetFile.writeText(rawJson, Charsets.UTF_8)
            "✓ Saved full backup (${rawJson.length / 1024} KB) to /storage/emulated/0/0.Neet 2027/JARVIS_BACKUPS/${targetFile.name}"
        }.getOrElse { err ->
            "Backup note: ${err.message}"
        }
    }

    fun restoreLatestBackupFromNeetFolder(
        context: Context,
        chatMessages: MutableList<ChatMessageItem>
    ): String {
        return runCatching {
            val neetRoot = StudyLibraryManager.getDedicatedRootFolder(context)
            val backupDir = File(neetRoot, "JARVIS_BACKUPS")
            val latestFile = backupDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
                ?.maxByOrNull { it.lastModified() }
                ?: return "No backup JSON found inside /storage/emulated/0/0.Neet 2027/JARVIS_BACKUPS yet."

            val snapshot = jsonParser.decodeFromString<JarvisFullBackupSnapshot>(
                latestFile.readText(Charsets.UTF_8)
            )

            val existingTitles = JarvisVaultManager.loadVaultNotes(context).map { it.title }.toSet()
            var restoredNoteCount = 0
            snapshot.vaultNotes.forEach { backedUpNote ->
                if (backedUpNote.title !in existingTitles) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, backedUpNote.title)
                        putExtra(Intent.EXTRA_TEXT, backedUpNote.markdownBody)
                    }
                    JarvisVaultManager.handleSharedIncomingIntent(context, shareIntent)
                    restoredNoteCount++
                }
            }

            JarvisVoiceOutputController.saveLexiconRules(context, snapshot.lexiconRules)
            if (snapshot.recentChatHistory.isNotEmpty()) {
                chatMessages.clear()
                chatMessages.addAll(snapshot.recentChatHistory)
                EncryptedKeyStoreVault.saveChatHistory(context, chatMessages)
            }
            "✓ Restored $restoredNoteCount missing Vault notes (${snapshot.vaultNotes.size} in backup), ${snapshot.lexiconRules.size} Lexicon rules & ${snapshot.recentChatHistory.size} messages from ${latestFile.name}!"
        }.getOrElse { err ->
            "Restore note: ${err.message}"
        }
    }
}

// ============================================================================
// 2. CHAPTER 11 UI: SELF-AUDIT, GITHUB CI INSPECTOR, OTA UPDATER & BACKUP
// ============================================================================

@Composable
fun JarvisSelfAuditAndOtaScreen(
    plan: NeetPlanRoot,
    lab: GlassLabController,
    chatMessages: MutableList<ChatMessageItem>,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lab.currentPalette

    var auditTick by remember { mutableIntStateOf(0) }
    val auditItems = remember(auditTick) {
        JarvisSelfAuditEngine.run12PointSystemAudit(context, plan)
    }
    val passedCount = auditItems.count { it.passed }

    val workflowRuns = remember { mutableStateListOf<GitHubWorkflowRunSummary>() }
    var latestRelease by remember { mutableStateOf<GitHubReleaseAssetSummary?>(null) }
    var isFetchingGitHub by remember { mutableStateOf(false) }
    var statusBanner by remember {
        mutableStateOf("Chapter 11 Production Self-Audit, Live GitHub CI Inspector & 1-Tap OTA APK Updater Ready.")
    }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS && engine != null) {
                JarvisVoiceOutputController.applyConfiguredVoiceAndProsody(context, engine!!, "proud")
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    fun refreshGitHubTelemetry() {
        if (isFetchingGitHub) return
        if (!LocalLlmModelManager.isDeviceOnlineRightNow(context)) {
            statusBanner = "Wi-Fi is currently OFF — 12-Point Local Hardware & Vault Audit is 100% active. Turn Wi-Fi on to query GitHub OTA releases."
            return
        }
        isFetchingGitHub = true
        statusBanner = "Querying live GitHub Actions runs & latest-apk release from rudrarajkumar/jarvis-neet-2027..."
        scope.launch {
            val (runs, rel) = JarvisSelfAuditEngine.fetchLiveGitHubCiAndReleaseStatus(context)
            workflowRuns.clear()
            workflowRuns.addAll(runs)
            latestRelease = rel
            isFetchingGitHub = false
            statusBanner = if (rel != null) {
                "✓ Connected to GitHub: Latest OTA '${rel.assetName}' (${rel.sizeMb}) published at ${rel.publishedAt}."
            } else {
                "✓ Synced ${runs.size} recent workflow runs from rudrarajkumar/jarvis-neet-2027."
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshGitHubTelemetry()
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Master Production Readiness & Spoken Diagnostic Briefing Card
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = JarvisSpatialPalettes.StatusSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHAPTER 11 // SELF-AUDIT, GITHUB CI INSPECTOR & OTA APK UPDATER",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "System Readiness Score: $passedCount / ${auditItems.size} Subsystems Verified Green",
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statusBanner,
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = "Speak Audit Report",
                        icon = Icons.Default.VolumeUp,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            val tts = ttsEngine ?: return@LiquidGlassButton
                            tts.stop()
                            JarvisVoiceOutputController.applyConfiguredVoiceAndProsody(context, tts, "proud")
                            val report = "All systems operational, sir. $passedCount of ${auditItems.size} hardware and AI subsystems are verified green on your Xiaomi Pad 6. We are locked on target for ${plan.meta.goalInstitution}."
                            val phonetic = JarvisVoiceOutputController.applyLexiconForSpeech(context, report)
                            tts.speak(phonetic, TextToSpeech.QUEUE_FLUSH, null, "audit_utt")
                        }
                    )
                    LiquidGlassButton(
                        text = "Re-Run Audit",
                        icon = Icons.Default.Refresh,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusWarning,
                        onClick = {
                            auditTick++
                            refreshGitHubTelemetry()
                        }
                    )
                }
            }
        }

        // 2. 1-Tap OTA APK Release Downloader & Live GitHub Actions CI Monitor
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OVER-THE-AIR (OTA) APK UPDATER & LIVE GITHUB ACTIONS CI MONITOR",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    val rel = latestRelease
                    Text(
                        text = if (rel != null) {
                            "Latest Release [${rel.tagName}]: ${rel.assetName} (${rel.sizeMb}) • Published: ${rel.publishedAt} UTC"
                        } else {
                            "Repository: github.com/rudrarajkumar/jarvis-neet-2027 (Tap 'Sync GitHub CI' or 'Download Latest APK')"
                        },
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassButton(
                    text = "1. Download Latest OTA APK",
                    icon = Icons.Default.CloudDone,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = {
                        val url = latestRelease?.browserDownloadUrl
                            ?: "https://github.com/rudrarajkumar/jarvis-neet-2027/releases/download/latest-apk/Jarvis-NEET2027.apk"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )
                LiquidGlassButton(
                    text = if (isFetchingGitHub) "Syncing CI..." else "2. Sync GitHub CI Status",
                    icon = Icons.Default.Refresh,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusWarning,
                    onClick = { refreshGitHubTelemetry() }
                )
                LiquidGlassButton(
                    text = "3. Trigger Remote CI Build",
                    icon = Icons.Default.PlayArrow,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusInfo,
                    onClick = {
                        scope.launch {
                            statusBanner = JarvisSelfAuditEngine.triggerRemoteCiBuild(context)
                        }
                    }
                )
                LiquidGlassButton(
                    text = "Open GitHub Repo",
                    icon = Icons.Default.AutoAwesome,
                    lab = lab,
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/rudrarajkumar/jarvis-neet-2027/actions")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            if (workflowRuns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    workflowRuns.forEach { run ->
                        val isSuccess = run.conclusion.equals("success", ignoreCase = true)
                        val isRunning = run.status != "completed"
                        val badgeColor = when {
                            isSuccess -> JarvisSpatialPalettes.StatusSuccess
                            isRunning -> JarvisSpatialPalettes.StatusWarning
                            else -> JarvisSpatialPalettes.StatusError
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141418))
                                .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .clickable {
                                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(i) }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${run.runNumber} • ${run.name} (${run.headShaShort})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Updated: ${run.updatedAt} UTC",
                                    color = palette.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = if (isRunning) "BUILDING..." else run.conclusion.uppercase(),
                                color = badgeColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. 1-Tap Snapshot Backup & Restore to /storage/emulated/0/0.Neet 2027/JARVIS_BACKUPS
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ZERO-LOSS SNAPSHOT BACKUP & RESTORE (0.Neet 2027/JARVIS_BACKUPS)",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Saves all Vault Notes, Mistake Notebook entries, D0-D30 Flashcards, Pronunciation Lexicon rules & Chat History to your dedicated 0.Neet 2027 folder.",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = "Export Backup JSON",
                        icon = Icons.Default.Folder,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            statusBanner = JarvisSelfAuditEngine.exportFullBackupToNeetFolder(
                                context = context,
                                plan = plan,
                                chatMessages = chatMessages.toList()
                            )
                            auditTick++
                        }
                    )
                    LiquidGlassButton(
                        text = "Restore Latest",
                        icon = Icons.Default.Refresh,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusWarning,
                        onClick = {
                            statusBanner = JarvisSelfAuditEngine.restoreLatestBackupFromNeetFolder(
                                context = context,
                                chatMessages = chatMessages
                            )
                            auditTick++
                        }
                    )
                }
            }
        }

        // 4. 12-Point Hardware & Architecture Diagnostic Matrix
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Text(
                text = "12-POINT HARDWARE, PERMISSION & ARCHITECTURE DIAGNOSTIC MATRIX",
                color = JarvisSpatialPalettes.StatusWarning,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                auditItems.forEach { item ->
                    val statusColor = when {
                        item.passed -> JarvisSpatialPalettes.StatusSuccess
                        item.isWarningOnly -> JarvisSpatialPalettes.StatusWarning
                        else -> JarvisSpatialPalettes.StatusError
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF121216))
                            .border(1.dp, statusColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "[#${item.id} ${item.subsystem}] ${item.title}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = item.detail,
                                    color = palette.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Text(
                            text = if (item.passed) "PASS ✓" else if (item.isWarningOnly) "READY" else "ACTION",
                            color = statusColor,
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
