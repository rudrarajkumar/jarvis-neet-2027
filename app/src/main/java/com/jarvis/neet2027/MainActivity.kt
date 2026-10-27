package com.jarvis.neet2027

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.neet2027.model.NeetPlanRepository
import com.jarvis.neet2027.util.XiaomiSurvivalHelper
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val JarvisEmerald = Color(0xFF10B981)
private val JarvisDeepBg = Color(0xFF040D0A)
private val JarvisGlassFill = Color(0xCC0A1F17)
private val JarvisGlassBorder = Color(0x6610B981)
private val JarvisTextPrimary = Color(0xFFECFDF5)
private val JarvisTextMuted = Color(0xFF6EE7B7)
private val JarvisAmber = Color(0xFFF59E0B)

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisEmerald,
    background = JarvisDeepBg,
    surface = JarvisGlassFill,
    onPrimary = Color(0xFF022C22),
    onBackground = JarvisTextPrimary,
    onSurface = JarvisTextPrimary
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = JarvisColorScheme) {
                JarvisChapterOneScreen()
            }
        }
    }
}

@Composable
fun JarvisChapterOneScreen() {
    val context = LocalContext.current
    val plan = remember { NeetPlanRepository.loadPlan(context) }
    val masterPrompt = remember { NeetPlanRepository.loadMasterPrompt(context) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            tick++
        }
    }

    val imphalNow = remember(tick) { NeetPlanRepository.getImphalNow() }
    val daysToNeet = remember(tick) { NeetPlanRepository.getDaysUntilExam(plan, imphalNow.toLocalDate()) }
    val activeSlot = remember(tick) { NeetPlanRepository.getCurrentTimetableSlot(plan, imphalNow.toLocalTime()) }
    val dayOneAssignments = remember {
        NeetPlanRepository.getAssignmentsForDate(plan, LocalDate.parse(plan.meta.firstLectureDate))
    }

    var batteryOk by remember(tick) { mutableStateOf(XiaomiSurvivalHelper.isIgnoringBatteryOptimizations(context)) }
    var filesOk by remember(tick) { mutableStateOf(XiaomiSurvivalHelper.hasAllFilesAccess()) }
    var overlayOk by remember(tick) { mutableStateOf(XiaomiSurvivalHelper.canDrawOverlays(context)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF09261C), JarvisDeepBg, Color(0xFF020705))
                )
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(JarvisEmerald)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "J.A.R.V.I.S. HUD // CHAPTER 1 ONLINE",
                                color = JarvisEmerald,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Very good, sir. NEET 2027 Brain & Master Prompt verified.",
                            color = JarvisTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Home: ${plan.meta.homeProfile.district}, ${plan.meta.homeProfile.state} (${plan.meta.homeProfile.pin}) | TZ: ${plan.meta.homeProfile.timezone}",
                            color = JarvisTextMuted,
                            fontSize = 13.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = imphalNow.format(DateTimeFormatter.ofPattern("hh:mm:ss a 'IST'")),
                            color = JarvisTextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "$daysToNeet DAYS TO NEET UG 2027 (PROVISIONAL)",
                            color = JarvisEmerald,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Target: ${plan.meta.goalInstitution} (Open AIR <= ${plan.meta.aiimsNagpurCutoffs2026Round1.ASPIRATIONAL_OPEN_BUFFER})",
                            color = JarvisTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassPanel(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = JarvisEmerald)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE IMPHAL TIMETABLE SLOT (${activeSlot.startTime} - ${activeSlot.endTime})",
                            color = JarvisEmerald,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = activeSlot.title, color = JarvisTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = activeSlot.instruction, color = JarvisTextMuted, fontSize = 13.sp)
                }

                GlassPanel(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = JarvisEmerald)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NEET 2027 BRAIN TELEMETRY",
                            color = JarvisEmerald,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${plan.videoCountAudit.totalKnownCompleted} Completed | ${plan.videoCountAudit.totalKnownRemaining} Remaining (791 Total)",
                        color = JarvisTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "156 Study Days | 27 Sundays | 6 Recall Loops | Sleep: ${plan.meta.sleepWindowStart}-${plan.meta.sleepWindowEnd}",
                        color = JarvisTextMuted,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Master Prompt Loaded: ${masterPrompt.length} chars",
                        color = JarvisTextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }

            GlassPanel {
                Text(
                    text = "DAY 1 LECTURE QUEUE VERIFICATION (MONDAY, 2 NOV 2026)",
                    color = JarvisEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(155.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dayOneAssignments) { item ->
                        Surface(
                            color = Color(0x99061510),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, JarvisGlassBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = item.trackName,
                                    color = JarvisEmerald,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = item.chapterTitle,
                                    color = JarvisTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lecture L0${item.lectureNumberInChapter}/${item.totalLecturesInChapter}",
                                    color = JarvisTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = JarvisEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STEP 1.6 // XIAOMI PAD 6 HYPEROS SURVIVAL CONSOLE",
                        color = JarvisEmerald,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap each button below to lock J.A.R.V.I.S. into Xiaomi HyperOS so wake-word listening, alarms, and your Study Library never get killed.",
                    color = JarvisTextMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassActionButton(
                        modifier = Modifier.weight(1f),
                        label = if (batteryOk) "Battery: Unrestricted ✓" else "1. Disable Battery Saver",
                        isDone = batteryOk,
                        onClick = {
                            XiaomiSurvivalHelper.openBatteryOptimizationDialog(context)
                            batteryOk = XiaomiSurvivalHelper.isIgnoringBatteryOptimizations(context)
                        }
                    )
                    GlassActionButton(
                        modifier = Modifier.weight(1f),
                        label = "2. Xiaomi Autostart & BG",
                        isDone = false,
                        onClick = { XiaomiSurvivalHelper.openXiaomiAutostartSettings(context) }
                    )
                    GlassActionButton(
                        modifier = Modifier.weight(1f),
                        label = if (filesOk) "Study Folder Access ✓" else "3. Grant All Files Access",
                        isDone = filesOk,
                        onClick = {
                            XiaomiSurvivalHelper.openAllFilesAccessSettings(context)
                            filesOk = XiaomiSurvivalHelper.hasAllFilesAccess()
                        }
                    )
                    GlassActionButton(
                        modifier = Modifier.weight(1f),
                        label = if (overlayOk) "HUD Overlay Ready ✓" else "4. Allow HUD Overlay",
                        isDone = overlayOk,
                        onClick = {
                            XiaomiSurvivalHelper.openOverlaySettings(context)
                            overlayOk = XiaomiSurvivalHelper.canDrawOverlays(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = JarvisGlassFill,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(Color(0x9934D399), Color(0x3310B981), Color(0x88059669))
            )
        ),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
fun GlassActionButton(
    modifier: Modifier = Modifier,
    label: String,
    isDone: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isDone) JarvisEmerald else JarvisAmber),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDone) Color(0x4410B981) else Color(0x33F59E0B),
            contentColor = JarvisTextPrimary
        )
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.BatteryChargingFull,
            contentDescription = null,
            tint = if (isDone) JarvisEmerald else JarvisAmber,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
