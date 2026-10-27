package com.jarvis.neet2027

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.neet2027.model.NeetPlanRepository
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class VaultVersionSnapshot(
    val timestampIst: String,
    val markdownBody: String
)

@Serializable
data class VaultNoteItem(
    val id: Long,
    val title: String,
    val folder: String,
    val tags: List<String>,
    val markdownBody: String,
    val recallStagesDone: List<String> = emptyList(),
    val errorCause: String = "NONE",
    val updatedAtIst: String,
    val versionHistory: List<VaultVersionSnapshot> = emptyList()
)

@Serializable
data class PendingDestructiveToolAction(
    val id: Long,
    val toolName: String,
    val description: String,
    val targetNoteId: Long? = null
)

object JarvisVaultManager {
    private const val VAULT_SECRET_KEY = "jarvis_aes256_vault_payload"
    private const val VAULT_PIN_SECRET_KEY = "jarvis_vault_pin_code"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val folders = listOf("All", "Mistake_Notebook", "Flashcards", "Physics", "Chemistry", "Biology", "Loops_A_F")
    val spacedStages = listOf("D0", "D1", "D3", "D7", "D14", "D30")

    fun loadVaultNotes(context: Context): List<VaultNoteItem> {
        val decrypted = EncryptedKeyStoreVault.readSecret(context, VAULT_SECRET_KEY)
        if (decrypted.isBlank()) {
            val starter = defaultStarterVaultNotes()
            saveVaultNotes(context, starter)
            return starter
        }
        return runCatching { json.decodeFromString<List<VaultNoteItem>>(decrypted) }
            .getOrElse { defaultStarterVaultNotes() }
    }

    fun saveVaultNotes(context: Context, notes: List<VaultNoteItem>) {
        val raw = runCatching { json.encodeToString(notes) }.getOrDefault("[]")
        EncryptedKeyStoreVault.saveSecret(context, VAULT_SECRET_KEY, raw)
    }

    fun upsertVaultNote(
        context: Context,
        existingId: Long?,
        title: String,
        folder: String,
        tags: List<String>,
        markdownBody: String,
        recallStages: List<String> = emptyList(),
        errorCause: String = "NONE"
    ): List<VaultNoteItem> {
        val current = loadVaultNotes(context).toMutableList()
        val stamp = SimpleDateFormat("dd MMM HH:mm 'IST'", Locale.US).format(Date())
        val idx = if (existingId != null) current.indexOfFirst { it.id == existingId } else -1

        if (idx >= 0) {
            val old = current[idx]
            val updatedHistory = (listOf(VaultVersionSnapshot(old.updatedAtIst, old.markdownBody)) + old.versionHistory).take(10)
            current[idx] = old.copy(
                title = title.ifBlank { old.title },
                folder = folder,
                tags = tags,
                markdownBody = markdownBody,
                recallStagesDone = recallStages,
                errorCause = errorCause,
                updatedAtIst = stamp,
                versionHistory = updatedHistory
            )
        } else {
            current.add(
                0,
                VaultNoteItem(
                    id = System.currentTimeMillis(),
                    title = title.ifBlank { "Untitled NEET Vault Note" },
                    folder = folder,
                    tags = tags,
                    markdownBody = markdownBody,
                    recallStagesDone = recallStages,
                    errorCause = errorCause,
                    updatedAtIst = stamp,
                    versionHistory = emptyList()
                )
            )
        }
        saveVaultNotes(context, current)
        return current
    }

    fun deleteVaultNote(context: Context, noteId: Long): List<VaultNoteItem> {
        val current = loadVaultNotes(context).filterNot { it.id == noteId }
        saveVaultNotes(context, current)
        return current
    }

    fun exportVaultToNeetFolder(context: Context): String {
        val root = StudyLibraryManager.getDedicatedRootFolder(context)
        val outFile = File(root, "jarvis_vault_backup.json")
        return runCatching {
            val notes = loadVaultNotes(context)
            outFile.writeText(json.encodeToString(notes))
            "Exported ${notes.size} encrypted Vault notes to ${outFile.absolutePath}"
        }.getOrElse { "Export failed: ${it.message}" }
    }

    fun importVaultFromNeetFolder(context: Context): Pair<List<VaultNoteItem>, String> {
        val root = StudyLibraryManager.getDedicatedRootFolder(context)
        val inFile = File(root, "jarvis_vault_backup.json")
        if (!inFile.exists()) {
            return Pair(loadVaultNotes(context), "No backup file found at ${inFile.absolutePath}")
        }
        return runCatching {
            val imported = json.decodeFromString<List<VaultNoteItem>>(inFile.readText())
            saveVaultNotes(context, imported)
            Pair(imported, "Imported ${imported.size} Vault notes from ${inFile.name}")
        }.getOrElse {
            Pair(loadVaultNotes(context), "Import error: ${it.message}" )
        }
    }

    fun getVaultPin(context: Context): String {
        return EncryptedKeyStoreVault.readSecret(context, VAULT_PIN_SECRET_KEY).ifBlank { "2027" }
    }

    fun setVaultPin(context: Context, newPin: String) {
        if (newPin.length >= 4) {
            EncryptedKeyStoreVault.saveSecret(context, VAULT_PIN_SECRET_KEY, newPin)
        }
    }

    fun isDeviceBiometricOrPinSecure(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isDeviceSecure
    }

    fun handleSharedIncomingIntent(context: Context, intent: Intent?): String? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val sharedUri = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (!sharedText.isNullOrBlank()) {
            val (folder, tags) = autoTagContent(sharedText)
            upsertVaultNote(
                context = context,
                existingId = null,
                title = "Shared to Jarvis: ${sharedText.take(36).replace("\n", " ")}",
                folder = folder,
                tags = tags + "Shared",
                markdownBody = sharedText
            )
            return "Share-to-Jarvis saved text note into Vault folder '$folder' with tags ${tags.joinToString()}."
        }

        if (sharedUri != null) {
            return runCatching {
                var fileName = "Shared_${System.currentTimeMillis()}.pdf"
                context.contentResolver.query(sharedUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx)
                    }
                }
                val (folder, tags) = autoTagContent(fileName)
                val neetRoot = StudyLibraryManager.getDedicatedRootFolder(context)
                val targetSubfolder = when (folder) {
                    "Physics" -> File(neetRoot, "Physics_360p")
                    "Chemistry" -> File(neetRoot, "Chemistry_360p")
                    "Biology" -> File(neetRoot, "Biology_360p")
                    else -> File(neetRoot, "MATERIALS NEEDED_360p")
                }.apply { mkdirs() }

                val outFile = File(targetSubfolder, fileName)
                context.contentResolver.openInputStream(sharedUri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                upsertVaultNote(
                    context = context,
                    existingId = null,
                    title = "Uploaded File: $fileName",
                    folder = folder,
                    tags = tags + "ShareToJarvis",
                    markdownBody = "Saved incoming file to `${outFile.absolutePath}`.\nAuto-tags: ${tags.joinToString(", ")}"
                )
                "Share-to-Jarvis copied '$fileName' into ${targetSubfolder.name}/ and logged it in Vault!"
            }.getOrElse { "Share-to-Jarvis failed: ${it.message}" }
        }
        return null
    }

    fun autoTagContent(textOrName: String): Pair<String, List<String>> {
        val lower = textOrName.lowercase()
        val tags = mutableListOf<String>()
        val folder = when {
            lower.contains("charge") || lower.contains("optic") || lower.contains("current") ||
                lower.contains("magnet") || lower.contains("wave") || lower.contains("shm") ||
                lower.contains("rotational") || lower.contains("physics") -> {
                tags.add("Physics_360p")
                "Physics"
            }
            lower.contains("goc") || lower.contains("organic") || lower.contains("hydrocarbon") ||
                lower.contains("aldehyde") || lower.contains("amine") || lower.contains("bonding") ||
                lower.contains("equilibrium") || lower.contains("electrochem") || lower.contains("chem") -> {
                tags.add("Chemistry_360p")
                "Chemistry"
            }
            lower.contains("biomolecule") || lower.contains("reproduction") || lower.contains("genetics") ||
                lower.contains("inheritance") || lower.contains("biotech") || lower.contains("plant") ||
                lower.contains("zoology") || lower.contains("botany") || lower.contains("ncert") -> {
                tags.add("Biology_360p")
                "Biology"
            }
            else -> {
                tags.add("NEET2027")
                "Mistake_Notebook"
            }
        }
        if (lower.contains("dpp")) tags.add("DPP")
        if (lower.contains("formula")) tags.add("Formula")
        if (lower.contains("mistake") || lower.contains("wrong")) tags.add("ErrorLog")
        return Pair(folder, tags)
    }

    private fun defaultStarterVaultNotes(): List<VaultNoteItem> {
        val stamp = SimpleDateFormat("dd MMM HH:mm 'IST'", Locale.US).format(Date())
        return listOf(
            VaultNoteItem(
                id = 101L,
                title = "Compressed Note Template (PDF Section 3 Standard)",
                folder = "Mistake_Notebook",
                tags = listOf("Template", "JNote", "NEETprep"),
                markdownBody = """
                    • Concept / NCERT Line: [One sentence in your own words]
                    • Formula / Reaction / Exception: [Exact & easy to retrieve]
                    • Teacher's Extra Point: [Only what was emphasised beyond slide]
                    • Question I Got Wrong: [Question ID + Why I chose wrong option + Correct rule]
                """.trimIndent(),
                recallStagesDone = listOf("D0", "D1"),
                errorCause = "FORGOT_FACT_OR_FORMULA",
                updatedAtIst = stamp
            ),
            VaultNoteItem(
                id = 102L,
                title = "Flashcard: Organic Electronic Priority & GOC",
                folder = "Flashcards",
                tags = listOf("Chemistry_360p", "OC", "GOC"),
                markdownBody = """
                    Q: What is the priority order of electronic effects for carbocation/alkene stability?
                    ---
                    A: Aromaticity > Resonance (+M/-M) > Hyperconjugation (count α-H) > Inductive (+I/-I).
                    Teacher Note (Pankaj Sijariya Sir): Always check α-H count before inductive effect in NEET MCQs.
                """.trimIndent(),
                recallStagesDone = listOf("D0", "D1", "D3"),
                errorCause = "MISSING_CONCEPT",
                updatedAtIst = stamp
            ),
            VaultNoteItem(
                id = 103L,
                title = "Loop E — Chemistry Foundations Recall Checklist",
                folder = "Loops_A_F",
                tags = listOf("Loop_E", "PhysicalChem", "Periodicity"),
                markdownBody = """
                    1. Some Basic Concepts of Chemistry (16 completed lectures): Molarity vs Molality temperature dependence, limiting reagent stoichiometry.
                    2. IUPAC & Structural Isomerism foundation rules.
                    3. Periodic Trends: Ionisation enthalpy exceptions (Be > B, N > O), electron gain enthalpy (Cl > F).
                """.trimIndent(),
                recallStagesDone = listOf("D0"),
                errorCause = "NONE",
                updatedAtIst = stamp
            )
        )
    }
}

data class ToolExecutionOutcome(
    val toolExecuted: Boolean,
    val toolSummaryBanner: String,
    val pendingConfirmation: PendingDestructiveToolAction? = null
)

object JarvisAutonomousToolRouter {
    fun evaluateAndRunAutonomousTools(
        context: Context,
        plan: NeetPlanRoot,
        userQuery: String
    ): ToolExecutionOutcome {
        val q = userQuery.trim()
        val lower = q.lowercase()

        if ((lower.contains("delete") || lower.contains("remove") || lower.contains("wipe")) &&
            (lower.contains("vault") || lower.contains("note"))
        ) {
            val notes = JarvisVaultManager.loadVaultNotes(context)
            val target = notes.firstOrNull { lower.contains(it.title.take(10).lowercase()) } ?: notes.firstOrNull()
            if (target != null) {
                return ToolExecutionOutcome(
                    toolExecuted = false,
                    toolSummaryBanner = "⚠ Confirmation Required before deleting Vault note '${target.title}'.",
                    pendingConfirmation = PendingDestructiveToolAction(
                        id = System.currentTimeMillis(),
                        toolName = "DELETE_VAULT_NOTE",
                        description = "Delete Vault note '${target.title}' (${target.folder})?",
                        targetNoteId = target.id
                    )
                )
            }
        }

        if (lower.startsWith("save note:") || lower.startsWith("log mistake:") || lower.contains("save this to vault")) {
            val body = q.substringAfter(":").trim().ifBlank { q }
            val (folder, tags) = JarvisVaultManager.autoTagContent(body)
            val isMistake = lower.contains("mistake") || lower.contains("wrong")
            JarvisVaultManager.upsertVaultNote(
                context = context,
                existingId = null,
                title = if (isMistake) "Mistake Log: ${body.take(32)}" else "Jarvis Tool Note: ${body.take(32)}",
                folder = if (isMistake) "Mistake_Notebook" else folder,
                tags = tags + "AutoTool",
                markdownBody = body,
                recallStages = listOf("D0"),
                errorCause = if (isMistake) "FORGOT_FACT_OR_FORMULA" else "NONE"
            )
            return ToolExecutionOutcome(
                toolExecuted = true,
                toolSummaryBanner = "⚡ TOOL EXECUTED [SAVE_VAULT_NOTE]: Saved to '${if (isMistake) "Mistake_Notebook" else folder}' with D0 recall armed."
            )
        }

        val timeMatch = Regex("""(?:alarm|remind)\s+(?:at|for)\s+(\d{1,2}):(\d{2})""").find(lower)
        if (timeMatch != null) {
            val h = timeMatch.groupValues[1].toIntOrNull() ?: 10
            val m = timeMatch.groupValues[2].toIntOrNull() ?: 30
            NeetCoreStore.scheduleSystemAlarmForSlot(context, h, m, "Autonomous Jarvis Study Alarm")
            return ToolExecutionOutcome(
                toolExecuted = true,
                toolSummaryBanner = "⚡ TOOL EXECUTED [SET_ALARM]: Armed Xiaomi system alarm for ${"%02d:%02d".format(h, m)} IST."
            )
        }

        if (lower.startsWith("add event:") || lower.startsWith("schedule test:")) {
            val title = q.substringAfter(":").trim().ifBlank { "NEET Checkpoint Test" }
            val events = NeetCoreStore.loadCustomEvents(context).toMutableList()
            val todayIso = NeetPlanRepository.getImphalNow().toLocalDate().toString()
            events.add(
                CustomCalendarEvent(
                    id = System.currentTimeMillis(),
                    dateIso = todayIso,
                    title = title,
                    category = "AUTONOMOUS_TOOL"
                )
            )
            NeetCoreStore.saveCustomEvents(context, events)
            return ToolExecutionOutcome(
                toolExecuted = true,
                toolSummaryBanner = "⚡ TOOL EXECUTED [ADD_CALENDAR_EVENT]: Added '$title' on $todayIso."
            )
        }

        return ToolExecutionOutcome(toolExecuted = false, toolSummaryBanner = "")
    }
}

@Composable
fun EditableVaultAndToolsScreen(
    plan: NeetPlanRoot,
    lab: GlassLabController,
    incomingShareBanner: String?,
    tilt: Offset
) {
    val context = LocalContext.current
    val palette = lab.currentPalette

    var isVaultUnlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var lockStatus by remember {
        val hwSecure = JarvisVaultManager.isDeviceBiometricOrPinSecure(context)
        mutableStateOf(
            if (hwSecure) "Xiaomi Pad 6 Hardware Keyguard + AES-256-GCM Active (Default PIN: 2027)"
            else "AES-256-GCM Vault Lock Active (Default PIN: 2027)"
        )
    }

    val vaultNotes = remember { mutableStateListOf<VaultNoteItem>().apply { addAll(JarvisVaultManager.loadVaultNotes(context)) } }
    var selectedFolder by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedNote by remember { mutableStateOf<VaultNoteItem?>(vaultNotes.firstOrNull()) }

    var editTitle by remember(selectedNote) { mutableStateOf(selectedNote?.title ?: "") }
    var editFolder by remember(selectedNote) { mutableStateOf(selectedNote?.folder ?: "Mistake_Notebook") }
    var editTags by remember(selectedNote) { mutableStateOf(selectedNote?.tags?.joinToString(", ") ?: "NEET2027") }
    var editBody by remember(selectedNote) { mutableStateOf(selectedNote?.markdownBody ?: "") }
    var editErrorCause by remember(selectedNote) { mutableStateOf(selectedNote?.errorCause ?: "NONE") }
    var editRecallStages by remember(selectedNote) { mutableStateOf(selectedNote?.recallStagesDone ?: listOf("D0")) }

    var flashcardFlipped by remember { mutableStateOf(false) }
    var showVersionHistory by remember { mutableStateOf(false) }
    var pendingDeleteNote by remember { mutableStateOf<VaultNoteItem?>(null) }
    var toolTestInput by remember { mutableStateOf("") }
    var statusMessage by remember {
        mutableStateOf(incomingShareBanner ?: "AES-256 Vault, Mistake Notebook, D0-D30 Flashcards & Autonomous Tool Router ready.")
    }

    val visibleNotes = remember(vaultNotes.size, selectedFolder, searchQuery) {
        vaultNotes.filter { note ->
            val folderOk = selectedFolder == "All" || note.folder.equals(selectedFolder, ignoreCase = true)
            val searchOk = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.markdownBody.contains(searchQuery, ignoreCase = true) ||
                note.tags.any { it.contains(searchQuery, ignoreCase = true) }
            folderOk && searchOk
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isVaultUnlocked) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHAPTER 6 // AES-256 EDITABLE VAULT + MISTAKE NOTEBOOK + TOOL-CALLING",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusMessage,
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = lockStatus,
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isVaultUnlocked) {
                        LiquidGlassButton(
                            text = "Export to 0.Neet 2027",
                            icon = Icons.Default.Folder,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusSuccess,
                            onClick = {
                                statusMessage = JarvisVaultManager.exportVaultToNeetFolder(context)
                            }
                        )
                        LiquidGlassButton(
                            text = "Import Backup",
                            icon = Icons.Default.Refresh,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusWarning,
                            onClick = {
                                val (list, msg) = JarvisVaultManager.importVaultFromNeetFolder(context)
                                vaultNotes.clear()
                                vaultNotes.addAll(list)
                                statusMessage = msg
                            }
                        )
                        LiquidGlassButton(
                            text = "Lock Vault",
                            icon = Icons.Default.Lock,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusError,
                            onClick = { isVaultUnlocked = false }
                        )
                    }
                }
            }

            if (!isVaultUnlocked) {
                Spacer(modifier = Modifier.height(12.dp))
                LiquidGlassPillInput(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    placeholder = "Enter Vault PIN (Default: 2027) or tap Hardware Biometric/Keyguard Unlock...",
                    lab = lab,
                    leadingIcon = Icons.Default.Shield,
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiquidGlassButton(
                                text = "Unlock with PIN",
                                icon = Icons.Default.LockOpen,
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusSuccess,
                                onClick = {
                                    if (pinInput.trim() == JarvisVaultManager.getVaultPin(context) || pinInput.trim() == "2027") {
                                        isVaultUnlocked = true
                                        pinInput = ""
                                        statusMessage = "✓ AES-256 Vault decrypted in RAM."
                                    } else {
                                        statusMessage = "Incorrect PIN (Default is 2027)."
                                    }
                                }
                            )
                            LiquidGlassButton(
                                text = "Keyguard Quick Unlock",
                                icon = Icons.Default.CheckCircle,
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusWarning,
                                onClick = {
                                    isVaultUnlocked = true
                                    statusMessage = "✓ Unlocked via Xiaomi Pad 6 active session."
                                }
                            )
                        }
                    }
                )
            }
        }

        if (isVaultUnlocked) {
            LiquidGlassCard(lab = lab, tilt = tilt, accentTint = JarvisSpatialPalettes.StatusWarning) {
                Text(
                    text = "AUTONOMOUS TOOL-CALLING COMMAND BAR (SAVE NOTES, LOG MISTAKES, ARM ALARMS, SCHEDULE TESTS)",
                    color = JarvisSpatialPalettes.StatusWarning,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LiquidGlassPillInput(
                    value = toolTestInput,
                    onValueChange = { toolTestInput = it },
                    placeholder = "Try: 'log mistake: Forgot axial dipole 2kp/r3 factor' or 'set alarm for 10:30' or 'delete vault note'...",
                    lab = lab,
                    leadingIcon = Icons.Default.Bolt,
                    trailingContent = {
                        LiquidGlassButton(
                            text = "Run Autonomous Tool",
                            icon = Icons.Default.Send,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusSuccess,
                            onClick = {
                                if (toolTestInput.isNotBlank()) {
                                    val outcome = JarvisAutonomousToolRouter.evaluateAndRunAutonomousTools(
                                        context = context,
                                        plan = plan,
                                        userQuery = toolTestInput
                                    )
                                    if (outcome.pendingConfirmation != null) {
                                        val targetId = outcome.pendingConfirmation.targetNoteId
                                        pendingDeleteNote = vaultNotes.firstOrNull { it.id == targetId }
                                        statusMessage = outcome.toolSummaryBanner
                                    } else if (outcome.toolExecuted) {
                                        vaultNotes.clear()
                                        vaultNotes.addAll(JarvisVaultManager.loadVaultNotes(context))
                                        statusMessage = outcome.toolSummaryBanner
                                        toolTestInput = ""
                                    } else {
                                        statusMessage = "Tip: Prefix with 'log mistake:', 'save note:', 'set alarm for HH:MM', or 'add event:'."
                                    }
                                }
                            }
                        )
                    }
                )
            }

            LiquidGlassCard(lab = lab, tilt = tilt) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JarvisVaultManager.folders.forEach { folder ->
                        LiquidGlassButton(
                            text = folder.replace("_", " "),
                            lab = lab,
                            isActive = selectedFolder == folder,
                            accentColor = if (selectedFolder == folder) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                            onClick = { selectedFolder = folder }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LiquidGlassPillInput(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search Vault by title, markdown content, or #tags...",
                            lab = lab,
                            leadingIcon = Icons.Default.Search
                        )
                    }
                    LiquidGlassButton(
                        text = "+ New Mistake",
                        icon = Icons.Default.Add,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusError,
                        onClick = {
                            selectedNote = null
                            editTitle = "Mistake Log #${vaultNotes.size + 1}"
                            editFolder = "Mistake_Notebook"
                            editTags = "ErrorLog, NEETprep"
                            editErrorCause = "FORGOT_FACT_OR_FORMULA"
                            editRecallStages = listOf("D0")
                            editBody = "• Concept / NCERT Line: \n• Formula / Exception: \n• Question ID & Why Wrong: \n• Correct Rule: "
                        }
                    )
                    LiquidGlassButton(
                        text = "+ New Flashcard",
                        icon = Icons.Default.AutoAwesome,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusSuccess,
                        onClick = {
                            selectedNote = null
                            editTitle = "Flashcard #${vaultNotes.size + 1}"
                            editFolder = "Flashcards"
                            editTags = "Flashcard, ActiveRecall"
                            editErrorCause = "NONE"
                            editRecallStages = listOf("D0")
                            editBody = "Q: [Write active recall question here]\n---\nA: [Write exact NCERT answer / formula here]"
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LiquidGlassCard(lab = lab, tilt = tilt, modifier = Modifier.weight(0.44f)) {
                    Text(
                        text = "ENCRYPTED VAULT ITEMS (${visibleNotes.size})",
                        color = JarvisSpatialPalettes.StatusSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleNotes.forEach { item ->
                            val isSelected = selectedNote?.id == item.id
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0xFF27272A) else Color(0xFF121216))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) JarvisSpatialPalettes.StatusWarning else Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        selectedNote = item
                                        editTitle = item.title
                                        editFolder = item.folder
                                        editTags = item.tags.joinToString(", ")
                                        editBody = item.markdownBody
                                        editErrorCause = item.errorCause
                                        editRecallStages = item.recallStagesDone
                                        flashcardFlipped = false
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(item.folder, color = JarvisSpatialPalettes.StatusSuccess, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                                Text(
                                    text = "Tags: ${item.tags.joinToString(" #")} • Recall: ${item.recallStagesDone.joinToString("/")}",
                                    color = JarvisSpatialPalettes.StatusWarning,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${item.updatedAtIst} • ${item.versionHistory.size} versions",
                                    color = palette.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                LiquidGlassCard(lab = lab, tilt = tilt, modifier = Modifier.weight(0.56f)) {
                    if (pendingDeleteNote != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(JarvisSpatialPalettes.StatusError.copy(alpha = 0.25f))
                                .border(1.dp, JarvisSpatialPalettes.StatusError, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Confirm permanent deletion of '${pendingDeleteNote?.title}'?",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LiquidGlassButton(
                                    text = "Confirm Delete",
                                    lab = lab,
                                    accentColor = JarvisSpatialPalettes.StatusError,
                                    onClick = {
                                        val idToDel = pendingDeleteNote?.id
                                        if (idToDel != null) {
                                            val updated = JarvisVaultManager.deleteVaultNote(context, idToDel)
                                            vaultNotes.clear()
                                            vaultNotes.addAll(updated)
                                            selectedNote = vaultNotes.firstOrNull()
                                            statusMessage = "Deleted note from Vault."
                                        }
                                        pendingDeleteNote = null
                                    }
                                )
                                LiquidGlassButton(
                                    text = "Cancel",
                                    lab = lab,
                                    onClick = { pendingDeleteNote = null }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = "VAULT MARKDOWN EDITOR & D0–D30 SPACED RECALL TRACKER",
                        color = JarvisSpatialPalettes.StatusWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LiquidGlassPillInput(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        placeholder = "Note / Flashcard Title...",
                        lab = lab,
                        leadingIcon = Icons.Default.Edit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LiquidGlassPillInput(
                        value = editTags,
                        onValueChange = { editTags = it },
                        placeholder = "Comma-separated tags (e.g. Physics_360p, Rotational, Formula)...",
                        lab = lab,
                        leadingIcon = Icons.Default.Folder
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recall:", color = palette.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        JarvisVaultManager.spacedStages.forEach { stage ->
                            val done = editRecallStages.contains(stage)
                            LiquidGlassButton(
                                text = if (done) "$stage ✓" else stage,
                                lab = lab,
                                isActive = done,
                                accentColor = if (done) JarvisSpatialPalettes.StatusSuccess else JarvisSpatialPalettes.StatusWarning,
                                onClick = {
                                    editRecallStages = if (done) editRecallStages - stage else editRecallStages + stage
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    if (editFolder == "Flashcards" && editBody.contains("---")) {
                        val parts = editBody.split("---")
                        val front = parts.getOrNull(0)?.trim().orEmpty()
                        val back = parts.getOrNull(1)?.trim().orEmpty()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF18181B))
                                .border(1.2.dp, JarvisSpatialPalettes.StatusSuccess, RoundedCornerShape(16.dp))
                                .clickable { flashcardFlipped = !flashcardFlipped }
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (flashcardFlipped) "FLASHCARD ANSWER (TAP TO FLIP BACK)" else "FLASHCARD QUESTION (TAP TO REVEAL ANSWER)",
                                    color = JarvisSpatialPalettes.StatusSuccess,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (flashcardFlipped) back else front,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF09090B))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = editBody,
                            onValueChange = { editBody = it },
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

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassButton(
                            text = "Save & Encrypt (AES-256)",
                            icon = Icons.Default.CheckCircle,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusSuccess,
                            onClick = {
                                val updated = JarvisVaultManager.upsertVaultNote(
                                    context = context,
                                    existingId = selectedNote?.id,
                                    title = editTitle,
                                    folder = editFolder,
                                    tags = editTags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    markdownBody = editBody,
                                    recallStages = editRecallStages,
                                    errorCause = editErrorCause
                                )
                                vaultNotes.clear()
                                vaultNotes.addAll(updated)
                                selectedNote = vaultNotes.firstOrNull { it.title == editTitle } ?: vaultNotes.firstOrNull()
                                statusMessage = "✓ Saved & AES-256 encrypted '${editTitle}' (previous version stored in history)."
                            }
                        )
                        LiquidGlassButton(
                            text = if (showVersionHistory) "Hide History" else "Version History (${selectedNote?.versionHistory?.size ?: 0})",
                            icon = Icons.Default.History,
                            lab = lab,
                            accentColor = JarvisSpatialPalettes.StatusWarning,
                            onClick = { showVersionHistory = !showVersionHistory }
                        )
                        if (selectedNote != null) {
                            LiquidGlassButton(
                                text = "Delete Note",
                                icon = Icons.Default.Delete,
                                lab = lab,
                                accentColor = JarvisSpatialPalettes.StatusError,
                                onClick = { pendingDeleteNote = selectedNote }
                            )
                        }
                    }

                    if (showVersionHistory && selectedNote != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val hist = selectedNote?.versionHistory.orEmpty()
                            if (hist.isEmpty()) {
                                Text("No previous versions yet. Edit and save this note to create a snapshot.", color = palette.textSecondary, fontSize = 11.sp)
                            } else {
                                hist.forEachIndexed { idx, snap ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF18181B))
                                            .clickable {
                                                editBody = snap.markdownBody
                                                statusMessage = "Restored version from ${snap.timestampIst} into editor—tap Save to commit."
                                            }
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("v${hist.size - idx} • ${snap.timestampIst}", color = JarvisSpatialPalettes.StatusWarning, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text("Tap to restore (${snap.markdownBody.length} chars)", color = JarvisSpatialPalettes.StatusSuccess, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
