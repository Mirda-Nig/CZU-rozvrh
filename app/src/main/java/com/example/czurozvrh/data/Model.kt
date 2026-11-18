package com.example.czurozvrh.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class WeekParity { ANY, EVEN, ODD }

data class Lesson(
    /** 1 = pondělí … 7 = neděle */
    val dayOfWeek: Int,
    /** Konkrétní datum (u blokové / kombinované výuky), jinak null = opakuje se každý týden. */
    val date: LocalDate?,
    val start: LocalTime,
    val end: LocalTime,
    val code: String,
    val name: String,
    val type: String,
    val room: String,
    val teacher: String,
    /** Texty poznámek z UIS, např. "Sudý týden". */
    val note: String,
    val parity: WeekParity,
    /** Sloupec "Omezení" – studijní skupiny. */
    val groups: String = "",
) {
    val isLecture get() = type.lowercase().startsWith("před")
    val isExercise get() = type.lowercase().startsWith("cvi")

    /** Krátký název do políčka v mřížce. */
    val shortName: String
        get() = code.ifBlank { name.split(" ").take(2).joinToString(" ") }

    fun occursIn(monday: LocalDate, weekParity: WeekParity?): Boolean {
        if (date != null) return !date.isBefore(monday) && date.isBefore(monday.plusDays(7))
        if (parity == WeekParity.ANY || weekParity == null) return true
        return parity == weekParity
    }

    fun toJson() = JSONObject().apply {
        put("day", dayOfWeek)
        put("date", date?.toString() ?: "")
        put("start", start.toString())
        put("end", end.toString())
        put("code", code); put("name", name); put("type", type)
        put("room", room); put("teacher", teacher); put("note", note); put("groups", groups)
        put("parity", parity.name)
    }

    companion object {
        fun fromJson(o: JSONObject) = Lesson(
            dayOfWeek = o.getInt("day"),
            date = o.optString("date").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
            start = LocalTime.parse(o.getString("start")),
            end = LocalTime.parse(o.getString("end")),
            code = o.optString("code"), name = o.optString("name"), type = o.optString("type"),
            room = o.optString("room"), teacher = o.optString("teacher"), note = o.optString("note"),
            parity = runCatching { WeekParity.valueOf(o.optString("parity")) }.getOrDefault(WeekParity.ANY),
            groups = o.optString("groups"),
        )
    }
}

/** Referenční bod pro sudé/liché týdny: pondělí týdne a jeho parita, jak ji hlásí UIS. */
data class ParityRef(val monday: LocalDate, val parity: WeekParity) {
    fun parityOf(otherMonday: LocalDate): WeekParity {
        val weeks = ChronoUnit.WEEKS.between(monday, otherMonday)
        if (weeks % 2 == 0L) return parity
        return if (parity == WeekParity.EVEN) WeekParity.ODD else WeekParity.EVEN
    }
}

data class Timetable(
    val lessons: List<Lesson>,
    val parityRef: ParityRef?,
    val semester: String,
    val fetchedAt: Long,
    /** "Platnost" rozvrhu z UIS – mimo toto období se výuka nekoná. */
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
) {
    /** Hodiny, které opravdu probíhají v týdnu začínajícím [monday]. */
    fun lessonsInWeek(monday: LocalDate): List<Lesson> {
        if (isOutsideTeaching(monday)) return emptyList()
        val parity = parityRef?.parityOf(monday)
        return lessons.filter { l ->
            l.occursIn(monday, parity) && (l.date != null || run {
                val day = monday.plusDays((l.dayOfWeek - 1).toLong())
                (validFrom == null || !day.isBefore(validFrom)) && (validTo == null || !day.isAfter(validTo))
            })
        }
    }

    fun isOutsideTeaching(monday: LocalDate): Boolean =
        (validFrom != null && monday.plusDays(6).isBefore(validFrom)) || (validTo != null && monday.isAfter(validTo))

    fun toJson(): String = JSONObject().apply {
        put("lessons", JSONArray().apply { lessons.forEach { put(it.toJson()) } })
        parityRef?.let {
            put("refMonday", it.monday.toString()); put("refParity", it.parity.name)
        }
        put("semester", semester)
        put("fetchedAt", fetchedAt)
        put("validFrom", validFrom?.toString() ?: "")
        put("validTo", validTo?.toString() ?: "")
    }.toString()

    companion object {
        fun fromJson(s: String): Timetable {
            val o = JSONObject(s)
            val arr = o.getJSONArray("lessons")
            val ref = o.optString("refMonday").takeIf { it.isNotBlank() }?.let {
                ParityRef(LocalDate.parse(it), WeekParity.valueOf(o.getString("refParity")))
            }
            return Timetable(
                lessons = (0 until arr.length()).map { Lesson.fromJson(arr.getJSONObject(it)) },
                parityRef = ref,
                semester = o.optString("semester"),
                fetchedAt = o.optLong("fetchedAt"),
                validFrom = o.optString("validFrom").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                validTo = o.optString("validTo").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
            )
        }
    }
}
