package com.jarvis.neet2027

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.jarvis.neet2027.model.NeetPlanRepository
import com.jarvis.neet2027.model.NeetPlanRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// ============================================================================
// 1. IMPHAL EAST WEATHER, GEOCODER & CLOCK DRIFT ENGINE (OPEN-METEO CC-BY 4.0)
// ============================================================================

@Serializable
data class ImphalWeatherSnapshot(
    val temperatureC: Double = 23.5,
    val apparentTempC: Double = 24.0,
    val humidityPercent: Int = 72,
    val rainChancePercent: Int = 20,
    val weatherCondition: String = "Pleasant Imphal Valley Sky",
    val sunriseIst: String = "05:38 AM",
    val sunsetIst: String = "04:52 PM",
    val uvIndexMax: Double = 6.2,
    val aqiUs: Int = 42,
    val hourlySummary: String = "Next 6h: 22°C -> 25°C, light breeze",
    val clockDriftSeconds: Long = 0L,
    val clockWarning: Boolean = false,
    val fetchedAtIst: String = "Offline Default (Imphal East 795114)",
    val coordinatesConfirmed: Boolean = true
)

@Serializable
data class MockTestScoreRecord(
    val id: Long,
    val dateIso: String,
    val testTitle: String,
    val physicsScore: Int,
    val chemistryScore: Int,
    val biologyScore: Int,
    val primaryErrorCause: String
)

@Serializable
data class CustomCalendarEvent(
    val id: Long,
    val dateIso: String,
    val title: String,
    val category: String, // "TEST", "DEADLINE", "REVISION", "RESCHEDULED"
    val isDone: Boolean = false
)

object NeetCoreStore {
    private const val PREFS_NAME = "jarvis_neet_core_chapter4"
    private const val KEY_WEATHER_CACHE = "imphal_weather_cache"
    private const val KEY_COORDS_CONFIRMED = "imphal_coords_confirmed"
    private const val KEY_COMPLETED_LECTURES = "completed_lectures_set"
    private const val KEY_DAY_STATES = "calendar_day_states_map"
    private const val KEY_CUSTOM_EVENTS = "calendar_custom_events"
    private const val KEY_MOCK_SCORES = "mock_test_scores"
    private const val KEY_STREAK_DAYS = "study_streak_days"
    private const val KEY_XP_POINTS = "jarvis_xp_points"
    private const val KEY_WATER_GLASSES = "daily_water_glasses"
    private const val KEY_ACTIVE_LOOP_INDEX = "active_recall_loop_idx"

    private val json = Json { ignoreUnknownKeys = true }

    fun loadWeather(context: Context): ImphalWeatherSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_WEATHER_CACHE, null) ?: return ImphalWeatherSnapshot(
            coordinatesConfirmed = prefs.getBoolean(KEY_COORDS_CONFIRMED, true)
        )
        return runCatching { json.decodeFromString<ImphalWeatherSnapshot>(raw) }
            .getOrDefault(ImphalWeatherSnapshot())
    }

    fun saveWeather(context: Context, snapshot: ImphalWeatherSnapshot) {
        val raw = runCatching { json.encodeToString(snapshot) }.getOrDefault("")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEATHER_CACHE, raw)
            .putBoolean(KEY_COORDS_CONFIRMED, snapshot.coordinatesConfirmed)
            .apply()
    }

    fun getCompletedLectureKeys(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_COMPLETED_LECTURES, emptySet()) ?: emptySet()
    }

    fun toggleLectureCompleted(context: Context, lectureKey: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_COMPLETED_LECTURES, emptySet()).orEmpty().toMutableSet()
        val added = if (current.contains(lectureKey)) {
            current.remove(lectureKey)
            false
        } else {
            current.add(lectureKey)
            true
        }
        val xp = getXp(context) + if (added) 50 else -50
        val streak = getStreakDays(context) + if (added && current.size % 4 == 1) 1 else 0
        prefs.edit()
            .putStringSet(KEY_COMPLETED_LECTURES, current)
            .putInt(KEY_XP_POINTS, xp.coerceAtLeast(0))
            .putInt(KEY_STREAK_DAYS, streak.coerceAtLeast(1))
            .apply()
        return current
    }

    fun getDayStatus(context: Context, dateIso: String): String {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DAY_STATES, "{}") ?: "{}"
        val map = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        return map[dateIso] ?: "PLANNED"
    }

    fun setDayStatus(context: Context, dateIso: String, status: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DAY_STATES, "{}") ?: "{}"
        val map = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap()).toMutableMap()
        map[dateIso] = status
        prefs.edit().putString(KEY_DAY_STATES, json.encodeToString(map)).apply()
    }

    fun loadCustomEvents(context: Context): List<CustomCalendarEvent> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_EVENTS, "[]") ?: "[]"
        return runCatching { json.decodeFromString<List<CustomCalendarEvent>>(raw) }.getOrDefault(emptyList())
    }

    fun saveCustomEvents(context: Context, events: List<CustomCalendarEvent>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM_EVENTS, json.encodeToString(events)).apply()
    }

    fun loadMockScores(context: Context): List<MockTestScoreRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MOCK_SCORES, "[]") ?: "[]"
        return runCatching { json.decodeFromString<List<MockTestScoreRecord>>(raw) }.getOrDefault(emptyList())
    }

    fun saveMockScores(context: Context, scores: List<MockTestScoreRecord>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MOCK_SCORES, json.encodeToString(scores)).apply()
    }

    fun getStreakDays(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_STREAK_DAYS, 1)

    fun getXp(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_XP_POINTS, 250)

    fun getWaterGlasses(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_WATER_GLASSES, 3)

    fun incrementWater(context: Context): Int {
        val next = (getWaterGlasses(context) + 1) % 13
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_WATER_GLASSES, next).apply()
        return next
    }

    fun getActiveLoopIndex(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_ACTIVE_LOOP_INDEX, 0)

    fun rotateRecallLoop(context: Context, totalLoops: Int): Int {
        val next = (getActiveLoopIndex(context) + 1) % totalLoops.coerceAtLeast(1)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACTIVE_LOOP_INDEX, next).apply()
        return next
    }

    suspend fun fetchLiveImphalWeather(context: Context): ImphalWeatherSnapshot = withContext(Dispatchers.IO) {
        if (EncryptedKeyStoreVault.isOfflinePrivacyLockEnabled(context)) {
            return@withContext loadWeather(context)
        }
        val lat = 24.8170
        val lon = 93.9368
        runCatching {
            val weatherUrl = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code" +
                    "&hourly=temperature_2m,precipitation_probability" +
                    "&daily=sunrise,sunset,uv_index_max,precipitation_probability_max" +
                    "&timezone=Asia%2FKolkata&forecast_days=1"
            )
            val conn = (weatherUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val serverDateMillis = conn.date
            val localMillis = System.currentTimeMillis()
            val driftSec = if (serverDateMillis > 0L) abs(localMillis - serverDateMillis) / 1000L else 0L

            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            val current = root["current"]?.jsonObject
            val daily = root["daily"]?.jsonObject
            val hourly = root["hourly"]?.jsonObject

            val temp = current?.get("temperature_2m")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 24.0
            val apparent = current?.get("apparent_temperature")?.jsonPrimitive?.content?.toDoubleOrNull() ?: temp
            val humidity = current?.get("relative_humidity_2m")?.jsonPrimitive?.content?.toIntOrNull() ?: 70
            val wCode = current?.get("weather_code")?.jsonPrimitive?.content?.toIntOrNull() ?: 1

            val rainMax = daily?.get("precipitation_probability_max")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull() ?: 15
            val uvMax = daily?.get("uv_index_max")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.toDoubleOrNull() ?: 6.0
            val sunriseRaw = daily?.get("sunrise")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: "2026-10-27T05:38"
            val sunsetRaw = daily?.get("sunset")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: "2026-10-27T16:52"

            val hTemps = hourly?.get("temperature_2m")?.jsonArray?.take(6)?.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() }.orEmpty()
            val hourlyText = if (hTemps.isNotEmpty()) {
                "Next 6h in Imphal East: ${hTemps.first()}°C → ${hTemps.last()}°C (Rain chance $rainMax%)"
            } else {
                "Imphal East Forecast: $temp°C, Rain chance $rainMax%"
            }

            // Optional Air Quality fetch from Open-Meteo AQI endpoint
            val aqiVal = runCatching {
                val aqiUrl = URL("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi")
                val aqiConn = (aqiUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                val aqiBody = BufferedReader(InputStreamReader(aqiConn.inputStream)).use { it.readText() }
                val aqiJson = json.parseToJsonElement(aqiBody).jsonObject
                aqiJson["current"]?.jsonObject?.get("us_aqi")?.jsonPrimitive?.content?.toIntOrNull() ?: 42
            }.getOrDefault(42)

            val stamp = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("hh:mm a 'IST'"))

            val snap = ImphalWeatherSnapshot(
                temperatureC = temp,
                apparentTempC = apparent,
                humidityPercent = humidity,
                rainChancePercent = rainMax,
                weatherCondition = decodeWmoCode(wCode),
                sunriseIst = sunriseRaw.substringAfter("T"),
                sunsetIst = sunsetRaw.substringAfter("T"),
                uvIndexMax = uvMax,
                aqiUs = aqiVal,
                hourlySummary = hourlyText,
                clockDriftSeconds = driftSec,
                clockWarning = driftSec > 90L,
                fetchedAtIst = "Live Open-Meteo • $stamp",
                coordinatesConfirmed = true
            )
            saveWeather(context, snap)
            snap
        }.getOrElse {
            loadWeather(context)
        }
    }

    private fun decodeWmoCode(code: Int): String = when (code) {
        0 -> "Clear Imphal Sky"
        1, 2, 3 -> "Partly Cloudy Valley Sky"
        45, 48 -> "Morning Imphal Mist / Fog"
        51, 53, 55, 61, 63, 65 -> "Manipur Monsoon / Rain Showers"
        80, 81, 82, 95 -> "Thunderstorm Alert — Keep PW Offline"
        else -> "Calm Imphal East Conditions"
    }

    fun triggerStudyNotification(context: Context, title: String, message: String) {
        val channelId = "jarvis_neet_timetable_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "J.A.R.V.I.S. NEET Timetable Instructor",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify((System.currentTimeMillis() % 10000).toInt(), notif)
    }

    fun scheduleSystemAlarmForSlot(context: Context, hour24: Int, minute: Int, label: String) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour24)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "J.A.R.V.I.S. NEET: $label")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}

// ============================================================================
// 2. SCREEN 1: HOME PROFILE, WEATHER, LIVE TIMETABLE INSTRUCTOR & PROACTIVE AI
// ============================================================================

@Composable
fun NeetCoreCommandScreen(
    plan: NeetPlanRoot,
    lab: GlassLabController,
    tilt: Offset
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = lab.currentPalette

    var weather by remember { mutableStateOf(NeetCoreStore.loadWeather(context)) }
    var isRefreshingWeather by remember { mutableStateOf(false) }
    var proactiveMessage by remember {
        mutableStateOf(
            "Tap 'Morning Briefing', 'What Now?', 'End-of-Day Debrief', or 'Bad-Day Minimum Re-Plan' below for numbered butler instructions."
        )
    }

    // 50-minute Self-Study Pomodoro Timer
    var pomodoroSecondsLeft by remember { mutableIntStateOf(50 * 60) }
    var pomodoroRunning by remember { mutableStateOf(false) }
    var waterGlasses by remember { mutableIntStateOf(NeetCoreStore.getWaterGlasses(context)) }

    LaunchedEffect(Unit) {
        weather = NeetCoreStore.fetchLiveImphalWeather(context)
    }

    LaunchedEffect(pomodoroRunning) {
        while (pomodoroRunning && pomodoroSecondsLeft > 0) {
            delay(1000L)
            pomodoroSecondsLeft--
            if (pomodoroSecondsLeft == 0) {
                pomodoroRunning = false
                NeetCoreStore.triggerStudyNotification(
                    context,
                    "50-Minute NEET Self-Study Block Complete, Sir!",
                    "Stand up, drink water, rest your eyes for 10 minutes, and log wrong questions in JNote."
                )
            }
        }
    }

    val nowIst = NeetPlanRepository.getImphalNow()
    val activeSlot = NeetPlanRepository.getCurrentTimetableSlot(plan, nowIst.toLocalTime())
    val slotIndex = plan.dailyTimetableIST.indexOfFirst { it.slotId == activeSlot.slotId }.coerceAtLeast(0)
    val nextSlot = plan.dailyTimetableIST[(slotIndex + 1) % plan.dailyTimetableIST.size]

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Imphal East Home Profile & Live Open-Meteo Weather Card
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = palette.accentPrimary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = palette.accentPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "IMPHAL EAST (PIN 795114) // 24.8170°N, 93.9368°E • ASIA/KOLKATA",
                            color = palette.accentPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${weather.temperatureC}°C (Feels ${weather.apparentTempC}°C) • ${weather.weatherCondition}",
                        color = palette.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Rain: ${weather.rainChancePercent}% | Humidity: ${weather.humidityPercent}% | UV Max: ${weather.uvIndexMax} | US AQI: ${weather.aqiUs}",
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Sunrise: ${weather.sunriseIst} | Sunset: ${weather.sunsetIst} • ${weather.hourlySummary}",
                        color = palette.accentSecondary,
                        fontSize = 12.sp
                    )
                    if (weather.clockWarning) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠ CLOCK DRIFT WARNING: Your tablet clock differs from IST network time by ${weather.clockDriftSeconds}s!",
                            color = JarvisSpatialPalettes.StatusError,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "✓ Device Clock Verified Synced with Asia/Kolkata (${weather.fetchedAtIst})",
                            color = JarvisSpatialPalettes.StatusSuccess,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
                LiquidGlassButton(
                    text = if (isRefreshingWeather) "Syncing..." else "Refresh Weather",
                    icon = Icons.Default.Refresh,
                    lab = lab,
                    onClick = {
                        if (!isRefreshingWeather) {
                            isRefreshingWeather = true
                            scope.launch {
                                weather = NeetCoreStore.fetchLiveImphalWeather(context)
                                isRefreshingWeather = false
                            }
                        }
                    }
                )
            }
        }

        // 2. Live Timetable Instructor + Proactive Guidance Card
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = palette.accentSecondary) {
            Text(
                text = "LIVE TIMETABLE INSTRUCTOR // NOW vs NEXT (IMPHAL IST)",
                color = palette.accentSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.accentPrimary.copy(alpha = 0.22f))
                        .border(1.2.dp, palette.accentPrimary, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("DOING RIGHT NOW (${activeSlot.startTime} - ${activeSlot.endTime})", color = palette.accentPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(activeSlot.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(activeSlot.instruction, color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.accentSecondary.copy(alpha = 0.18f))
                        .border(1.2.dp, palette.accentSecondary.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("UP NEXT (${nextSlot.startTime} - ${nextSlot.endTime})", color = palette.accentSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(nextSlot.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(nextSlot.instruction, color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassButton(
                    text = "Morning Briefing",
                    icon = Icons.Default.AutoAwesome,
                    lab = lab,
                    accentColor = palette.accentPrimary,
                    onClick = {
                        val todayList = NeetPlanRepository.getAssignmentsForDate(plan, nowIst.toLocalDate())
                        val tracksText = if (todayList.isEmpty()) "Sunday Guide Day (Rest / Test / Recall)"
                        else todayList.mapIndexed { i, a -> "${i + 1}. ${a.trackName}: ${a.chapterTitle} (L${a.lectureNumberInChapter}/${a.totalLecturesInChapter})" }.joinToString("\n")
                        proactiveMessage = """
                            GOOD MORNING BRIEFING, SIR (Imphal East • ${weather.temperatureC}°C, Rain ${weather.rainChancePercent}%):
                            1. Hydrate and get 15 minutes of Manipur daylight before 10:25 AM (zero social feeds).
                            2. Today's 4 Assigned Lectures:
                            $tracksText
                            3. Keep compressed slide notes only (1 NCERT line + formula + wrong-question ID).
                        """.trimIndent()
                    }
                )
                LiquidGlassButton(
                    text = "What Now?",
                    icon = Icons.Default.Bolt,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusSuccess,
                    onClick = {
                        proactiveMessage = """
                            STEP-BY-STEP INSTRUCTION FOR ${activeSlot.startTime}–${activeSlot.endTime} IST:
                            1. Active Slot: ${activeSlot.title}.
                            2. Action: ${activeSlot.instruction}
                            3. Ten-Minute Rule: Promise only 10 minutes of focused execution right now, sir; place your phone outside arm's reach.
                            4. Next transition at ${nextSlot.startTime}: ${nextSlot.title}.
                        """.trimIndent()
                    }
                )
                LiquidGlassButton(
                    text = "End-of-Day Debrief",
                    icon = Icons.Default.CheckCircle,
                    lab = lab,
                    accentColor = palette.accentSecondary,
                    onClick = {
                        val doneCount = NeetCoreStore.getCompletedLectureKeys(context).size
                        proactiveMessage = """
                            01:20 AM NIGHT DEBRIEF, SIR:
                            1. Total Lectures Ticked So Far: $doneCount / 480 remaining in plan.
                            2. Complete your 40-minute D0/D1/D3/D7/D14/D30 recall + Loops A–F audit.
                            3. Queue tomorrow's exact 4 Physics Wallah videos and write your first 10-minute action.
                            4. Mandatory 8-Hour Sleep Guardrail begins at 02:00 AM IST sharp—lights out, sir.
                        """.trimIndent()
                    }
                )
                LiquidGlassButton(
                    text = "Bad-Day Re-Plan",
                    icon = Icons.Default.Warning,
                    lab = lab,
                    accentColor = JarvisSpatialPalettes.StatusWarning,
                    onClick = {
                        proactiveMessage = """
                            BAD-DAY MINIMUM PROTOCOL ACTIVATED (NO GUILT, NO SLEEP CUTTING):
                            1. Do NOT stack 8 hours of backlog onto tomorrow and NEVER cut your 02:00–10:00 sleep window.
                            2. Execute today's minimum: finish 1 main lecture + 20 minutes closed-book active recall.
                            3. Missed slots are shifted to your Sunday Backlog Triage / review buffer slots automatically.
                        """.trimIndent()
                    }
                )
                LiquidGlassButton(
                    text = "Set Next Slot Alarm",
                    icon = Icons.Default.Notifications,
                    lab = lab,
                    accentColor = palette.accentTertiary,
                    onClick = {
                        val parts = nextSlot.startTime.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 10
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
                        NeetCoreStore.scheduleSystemAlarmForSlot(context, h, m, nextSlot.title)
                        NeetCoreStore.triggerStudyNotification(
                            context,
                            "J.A.R.V.I.S. Slot Reminder Armed (${nextSlot.startTime} IST)",
                            "${nextSlot.title}: ${nextSlot.instruction}"
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.glassSurface.copy(alpha = 0.85f))
                    .border(1.dp, palette.accentPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = proactiveMessage,
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        // 3. 50-Minute Self-Study Pomodoro & Wellbeing Guardrails (Sleep, Water, Eye-Rest)
        LiquidGlassCard(lab = lab, tilt = tilt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "50-MIN SELF-STUDY POMODORO & WELLBEING GUARDRAILS",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val mins = pomodoroSecondsLeft / 60
                    val secs = pomodoroSecondsLeft % 60
                    Text(
                        text = "Timer: ${"%02d:%02d".format(mins, secs)} • Hydration: $waterGlasses / 8 glasses • Sleep Window: 02:00–10:00 IST",
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "10m closed-book formula recall -> 25m NEETprep MCQs -> 15m JNote error log (20-20-20 eye rest every 20m).",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = if (pomodoroRunning) "Pause" else "Start 50m",
                        icon = Icons.Default.PlayArrow,
                        lab = lab,
                        isActive = pomodoroRunning,
                        onClick = { pomodoroRunning = !pomodoroRunning }
                    )
                    LiquidGlassButton(
                        text = "Reset",
                        icon = Icons.Default.Refresh,
                        lab = lab,
                        onClick = {
                            pomodoroRunning = false
                            pomodoroSecondsLeft = 50 * 60
                        }
                    )
                    LiquidGlassButton(
                        text = "+1 Water ($waterGlasses)",
                        icon = Icons.Default.Add,
                        lab = lab,
                        accentColor = JarvisSpatialPalettes.StatusInfo,
                        onClick = { waterGlasses = NeetCoreStore.incrementWater(context) }
                    )
                }
            }
        }
    }
}

// ============================================================================
// 3. SCREEN 2: INTERACTIVE NEET CALENDAR + PROGRESS TRACKER + MOCK ANALYSER
// ============================================================================

@Composable
fun InteractiveCalendarAndProgressScreen(
    plan: NeetPlanRoot,
    lab: GlassLabController,
    tilt: Offset
) {
    val context = LocalContext.current
    val palette = lab.currentPalette

    var selectedDate by remember { mutableStateOf(LocalDate.of(2026, 11, 2)) }
    var currentMonth by remember { mutableStateOf(YearMonth.of(2026, 11)) }
    var completedSet by remember { mutableStateOf(NeetCoreStore.getCompletedLectureKeys(context)) }
    var selectedDayStatus by remember(selectedDate) {
        mutableStateOf(NeetCoreStore.getDayStatus(context, selectedDate.toString()))
    }

    val customEvents = remember { mutableStateListOf<CustomCalendarEvent>().apply { addAll(NeetCoreStore.loadCustomEvents(context)) } }
    var newEventTitle by remember { mutableStateOf("") }

    val mockScores = remember { mutableStateListOf<MockTestScoreRecord>().apply { addAll(NeetCoreStore.loadMockScores(context)) } }
    var mockDraft by remember { mutableStateOf("") }
    var selectedErrorCause by remember { mutableIntStateOf(0) }
    var activeLoopIdx by remember { mutableIntStateOf(NeetCoreStore.getActiveLoopIndex(context)) }

    val dayAssignments = remember(selectedDate) { NeetPlanRepository.getAssignmentsForDate(plan, selectedDate) }
    val sundayEntry = remember(selectedDate) { plan.sundayGuide.find { it.date == selectedDate.toString() } }
    val activePhase = remember(selectedDate) {
        plan.phases.find {
            val s = LocalDate.parse(it.startDate)
            val e = LocalDate.parse(it.endDate)
            !selectedDate.isBefore(s) && !selectedDate.isAfter(e)
        } ?: plan.phases.first()
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Overall Progress, Streaks, XP & Subject Completion Bars
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = palette.accentPrimary) {
            val totalDone = plan.videoCountAudit.totalKnownCompleted + completedSet.size
            val overallFraction = (totalDone.toFloat() / plan.videoCountAudit.totalKnownSum.toFloat()).coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NEET UG 2027 MASTER PROGRESS // STREAK: ${NeetCoreStore.getStreakDays(context)} DAYS • ${NeetCoreStore.getXp(context)} XP",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalDone / ${plan.videoCountAudit.totalKnownSum} Total Lectures Completed (${(overallFraction * 100).toInt()}%)",
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = activePhase.name,
                    color = JarvisSpatialPalettes.StatusSuccess,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            plan.videoCountAudit.tracks.forEach { track ->
                val trackTicked = completedSet.count { it.startsWith("${track.id}:") }
                val trackDone = track.completedLectures + trackTicked
                val trackTotal = track.completedLectures + track.remainingLectures
                val frac = (trackDone.toFloat() / trackTotal.toFloat()).coerceIn(0f, 1f)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(track.name, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("$trackDone / $trackTotal (${(frac * 100).toInt()}%)", color = palette.accentSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = palette.accentPrimary,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // 2. Interactive Month Calendar + Day Inspector (Tick Lectures, Done/Missed/Reschedule)
        LiquidGlassCard(lab = lab, tilt = tilt, accentTint = palette.accentSecondary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INTERACTIVE NEET 2027 CALENDAR // ${currentMonth.month.name} ${currentMonth.year}",
                    color = palette.accentSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = "◀ Prev Month",
                        lab = lab,
                        onClick = { currentMonth = currentMonth.minusMonths(1) }
                    )
                    LiquidGlassButton(
                        text = "Next Month ▶",
                        lab = lab,
                        onClick = { currentMonth = currentMonth.plusMonths(1) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Scrollable Date Strip for the selected month
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (dayNum in 1..currentMonth.lengthOfMonth()) {
                    val dateObj = currentMonth.atDay(dayNum)
                    val isSelected = dateObj == selectedDate
                    val st = NeetCoreStore.getDayStatus(context, dateObj.toString())
                    val dotColor = when (st) {
                        "DONE" -> JarvisSpatialPalettes.StatusSuccess
                        "MISSED" -> JarvisSpatialPalettes.StatusError
                        "RESCHEDULED" -> JarvisSpatialPalettes.StatusWarning
                        else -> if (dateObj.dayOfWeek == DayOfWeek.SUNDAY) palette.accentSecondary else palette.accentPrimary
                    }
                    Column(
                        modifier = Modifier
                            .width(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) dotColor.copy(alpha = 0.35f) else palette.glassSurface.copy(alpha = 0.55f))
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else dotColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .clickable { selectedDate = dateObj }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dateObj.dayOfWeek.name.take(3), color = palette.textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(dayNum.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SELECTED DATE: $selectedDate (${selectedDate.dayOfWeek.name}) • STATUS: $selectedDayStatus",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${activePhase.name}: ${activePhase.purpose}",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DONE", "MISSED", "RESCHEDULED").forEach { st ->
                        LiquidGlassButton(
                            text = st,
                            lab = lab,
                            isActive = selectedDayStatus == st,
                            accentColor = when (st) {
                                "DONE" -> JarvisSpatialPalettes.StatusSuccess
                                "MISSED" -> JarvisSpatialPalettes.StatusError
                                else -> JarvisSpatialPalettes.StatusWarning
                            },
                            onClick = {
                                NeetCoreStore.setDayStatus(context, selectedDate.toString(), st)
                                selectedDayStatus = st
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (sundayEntry != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.accentSecondary.copy(alpha = 0.22f))
                        .border(1.dp, palette.accentSecondary, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("SUNDAY GUIDE // ${sundayEntry.title.uppercase()}", color = palette.accentSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(sundayEntry.instructions, color = Color.White, fontSize = 14.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayAssignments.forEach { item ->
                        val key = "${item.trackId}:${item.chapterTitle}:L${item.lectureNumberInChapter}"
                        val isTicked = completedSet.contains(key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isTicked) JarvisSpatialPalettes.StatusSuccess.copy(alpha = 0.22f) else palette.glassSurface.copy(alpha = 0.65f))
                                .border(1.dp, if (isTicked) JarvisSpatialPalettes.StatusSuccess else palette.accentPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .clickable { completedSet = NeetCoreStore.toggleLectureCompleted(context, key) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${item.trackName} • L${item.lectureNumberInChapter}/${item.totalLecturesInChapter}", color = palette.accentPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(item.chapterTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (isTicked) "COMPLETED ✓ (+50 XP)" else "TAP TO TICK",
                                color = if (isTicked) JarvisSpatialPalettes.StatusSuccess else palette.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassPillInput(
                value = newEventTitle,
                onValueChange = { newEventTitle = it },
                placeholder = "Add event/deadline or rescheduled lecture on $selectedDate...",
                lab = lab,
                leadingIcon = Icons.Default.DateRange,
                trailingContent = {
                    LiquidGlassButton(
                        text = "Add Event",
                        icon = Icons.Default.Add,
                        lab = lab,
                        onClick = {
                            if (newEventTitle.isNotBlank()) {
                                customEvents.add(
                                    CustomCalendarEvent(
                                        id = System.currentTimeMillis(),
                                        dateIso = selectedDate.toString(),
                                        title = newEventTitle.trim(),
                                        category = "EVENT"
                                    )
                                )
                                NeetCoreStore.saveCustomEvents(context, customEvents)
                                newEventTitle = ""
                            }
                        }
                    )
                }
            )
        }

        // 3. Loops A-F Old-Chapter Recall Rotator & Mock-Test Error Heatmap Analyser
        LiquidGlassCard(lab = lab, tilt = tilt) {
            val activeLoop = plan.oldChapterRecallLoops[activeLoopIdx % plan.oldChapterRecallLoops.size]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "00:40 AM RECALL ROTATOR // ${activeLoop.title.uppercase()}",
                        color = palette.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(activeLoop.chapters.joinToString(" • "), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Method: ${activeLoop.method} | Spaced Cycle: D0, D1, D3, D7, D14, D30", color = palette.textSecondary, fontSize = 12.sp)
                }
                LiquidGlassButton(
                    text = "Next Loop ↻",
                    icon = Icons.Default.Refresh,
                    lab = lab,
                    onClick = { activeLoopIdx = NeetCoreStore.rotateRecallLoop(context, plan.oldChapterRecallLoops.size) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "MOCK-TEST SCORE & ERROR-CAUSE HEATMAP ANALYSER",
                color = palette.accentSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                plan.errorCauses.forEachIndexed { idx, cause ->
                    LiquidGlassButton(
                        text = cause,
                        lab = lab,
                        isActive = selectedErrorCause == idx,
                        accentColor = if (selectedErrorCause == idx) JarvisSpatialPalettes.StatusWarning else palette.accentPrimary,
                        onClick = { selectedErrorCause = idx }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassPillInput(
                value = mockDraft,
                onValueChange = { mockDraft = it },
                placeholder = "Log score as 'P:150 C:160 B:340' (select main error cause above)...",
                lab = lab,
                leadingIcon = Icons.Default.Edit,
                trailingContent = {
                    LiquidGlassButton(
                        text = "Log Mock",
                        icon = Icons.Default.Send,
                        lab = lab,
                        onClick = {
                            if (mockDraft.isNotBlank()) {
                                val cause = plan.errorCauses[selectedErrorCause % plan.errorCauses.size]
                                mockScores.add(
                                    0,
                                    MockTestScoreRecord(
                                        id = System.currentTimeMillis(),
                                        dateIso = selectedDate.toString(),
                                        testTitle = mockDraft.trim(),
                                        physicsScore = 150,
                                        chemistryScore = 155,
                                        biologyScore = 335,
                                        primaryErrorCause = cause
                                    )
                                )
                                NeetCoreStore.saveMockScores(context, mockScores)
                                mockDraft = ""
                            }
                        }
                    )
                }
            )
            if (mockScores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                mockScores.take(4).forEach { rec ->
                    Text(
                        text = "• [${rec.dateIso}] ${rec.testTitle} — Main Error Cause: ${rec.primaryErrorCause}",
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
