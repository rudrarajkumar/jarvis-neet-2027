package com.jarvis.neet2027.model

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Serializable
data class NeetPlanRoot(
    val meta: PlanMeta,
    val videoCountAudit: VideoCountAudit,
    val dailyTimetableIST: List<TimetableSlot>,
    val phases: List<StudyPhase>,
    val oldChapterRecallLoops: List<RecallLoop>,
    val spacedRepetitionOffsetsDays: List<Int>,
    val errorCauses: List<String>,
    val weekdayFocusByDay: Map<String, String>,
    val chapterQueues: Map<String, List<ChapterQueueItem>>,
    val sundayGuide: List<SundayEntry>
)

@Serializable
data class PlanMeta(
    val planTitle: String,
    val goalInstitution: String,
    val planningVersionDate: String,
    val planStartDate: String,
    val firstLectureDate: String,
    val provisionalExamDate: String,
    val officialDateConfirmed: Boolean,
    val scheduledStudyDaysCount: Int,
    val studyDaysOfWeek: List<String>,
    val recoveryAndTestDay: String,
    val dailyLectureSlots: Int,
    val dailySelfStudyHours: Double,
    val dailyRecallMinutes: Int,
    val sleepWindowStart: String,
    val sleepWindowEnd: String,
    val minimumSleepHours: Int,
    val homeProfile: HomeProfile,
    val learningSetup: LearningSetup,
    val aiimsNagpurCutoffs2026Round1: CutoffData
)

@Serializable
data class HomeProfile(
    val district: String,
    val state: String,
    val pin: String,
    val country: String,
    val timezone: String,
    val utcOffset: String,
    val defaultCoordinates: GeoCoordinates
)

@Serializable
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
    val userConfirmed: Boolean
)

@Serializable
data class LearningSetup(
    val lectures: String,
    val notebookAndPractice: String,
    val coreRule: String
)

@Serializable
data class CutoffData(
    val OPEN: Int,
    val ASPIRATIONAL_OPEN_BUFFER: Int,
    val OBC_NCL: Int,
    val EWS: Int,
    val SC: Int,
    val ST: Int,
    val defaultCategory: String
)

@Serializable
data class VideoCountAudit(
    val totalKnownCompleted: Int,
    val totalKnownRemaining: Int,
    val totalKnownSum: Int,
    val tracks: List<TrackAudit>
)

@Serializable
data class TrackAudit(
    val id: String,
    val name: String,
    val completedLectures: Int,
    val completedBreakdown: String = "",
    val remainingLectures: Int,
    val activeDaysRequired: Int,
    val completionDateInTable: String,
    val sequenceRule: String
)

@Serializable
data class TimetableSlot(
    val slotId: String,
    val startTime: String,
    val endTime: String,
    val type: String,
    val trackId: String? = null,
    val durationMinutes: Int? = null,
    val title: String,
    val instruction: String
)

@Serializable
data class StudyPhase(
    val phaseId: Int,
    val name: String,
    val startDate: String,
    val endDate: String,
    val purpose: String,
    val testRule: String
)

@Serializable
data class RecallLoop(
    val loopId: String,
    val title: String,
    val chapters: List<String>,
    val method: String
)

@Serializable
data class ChapterQueueItem(
    val order: Int,
    val chapter: String,
    val lectures: Int,
    val startDate: String,
    val endDate: String
)

@Serializable
data class SundayEntry(
    val date: String,
    val week: Int,
    val type: String,
    val title: String,
    val instructions: String
)

data class DayAssignment(
    val trackId: String,
    val trackName: String,
    val chapterTitle: String,
    val lectureNumberInChapter: Int,
    val totalLecturesInChapter: Int,
    val isReview: Boolean
)

object NeetPlanRepository {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun loadPlan(context: Context): NeetPlanRoot {
        val rawJson = context.assets.open("neet_plan.json").bufferedReader().use { it.readText() }
        return jsonParser.decodeFromString(NeetPlanRoot.serializer(), rawJson)
    }

    fun loadMasterPrompt(context: Context): String {
        return context.assets.open("jarvis_system_prompt.txt").bufferedReader().use { it.readText() }
    }

    fun getImphalNow(): ZonedDateTime {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
    }

    fun getDaysUntilExam(plan: NeetPlanRoot, today: LocalDate = getImphalNow().toLocalDate()): Long {
        val examDate = LocalDate.parse(plan.meta.provisionalExamDate)
        return ChronoUnit.DAYS.between(today, examDate)
    }

    fun getCurrentTimetableSlot(plan: NeetPlanRoot, nowTime: LocalTime = getImphalNow().toLocalTime()): TimetableSlot {
        for (slot in plan.dailyTimetableIST) {
            val start = LocalTime.parse(slot.startTime)
            val end = LocalTime.parse(slot.endTime)
            val inSlot = if (end.isAfter(start)) {
                !nowTime.isBefore(start) && nowTime.isBefore(end)
            } else {
                !nowTime.isBefore(start) || nowTime.isBefore(end)
            }
            if (inSlot) return slot
        }
        return plan.dailyTimetableIST.first()
    }

    fun getAssignmentsForDate(plan: NeetPlanRoot, date: LocalDate): List<DayAssignment> {
        val start = LocalDate.parse(plan.meta.firstLectureDate)
        val effectiveDate = if (date.isBefore(start)) start else date
        if (effectiveDate.dayOfWeek.name == "SUNDAY") return emptyList()

        var studyDayIndex = 0
        var cursor = start
        while (cursor.isBefore(effectiveDate)) {
            if (cursor.dayOfWeek.name != "SUNDAY") {
                studyDayIndex++
            }
            cursor = cursor.plusDays(1)
        }

        return plan.videoCountAudit.tracks.map { track ->
            val queue = plan.chapterQueues[track.id].orEmpty()
            var remainingIndex = studyDayIndex
            var chosenItem: ChapterQueueItem? = null
            var lectureNum = 1
            for (item in queue) {
                if (remainingIndex < item.lectures) {
                    chosenItem = item
                    lectureNum = remainingIndex + 1
                    break
                }
                remainingIndex -= item.lectures
            }
            val finalItem = chosenItem ?: queue.last()
            val isReviewSlot = finalItem.chapter.contains("Review", ignoreCase = true)
            DayAssignment(
                trackId = track.id,
                trackName = track.name,
                chapterTitle = finalItem.chapter,
                lectureNumberInChapter = lectureNum,
                totalLecturesInChapter = finalItem.lectures,
                isReview = isReviewSlot
            )
        }
    }
}
