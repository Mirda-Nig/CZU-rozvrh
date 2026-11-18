package com.example.czurozvrh

import com.example.czurozvrh.data.Lesson
import com.example.czurozvrh.data.ParityRef
import com.example.czurozvrh.data.Timetable
import com.example.czurozvrh.data.WeekParity
import com.example.czurozvrh.data.agenda
import com.example.czurozvrh.data.nextBoundary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AgendaTest {
    private fun l(day: Int, from: String, to: String, code: String, parity: WeekParity = WeekParity.ANY) = Lesson(
        day, null, LocalTime.parse(from), LocalTime.parse(to), code, code, "Cvičení", "R", "T", "", parity,
    )

    private val tt = Timetable(
        lessons = listOf(
            l(4, "07:00", "08:30", "A"), l(4, "10:30", "12:00", "B"),
            l(5, "08:00", "09:00", "C"), l(5, "12:15", "13:45", "D", WeekParity.EVEN),
        ),
        parityRef = ParityRef(LocalDate.of(2026, 11, 16), WeekParity.EVEN),
        semester = "", fetchedAt = 0,
        validFrom = LocalDate.of(2026, 9, 28), validTo = LocalDate.of(2026, 12, 20),
    )

    @Test fun wednesdayShowsThursday() {
        val a = tt.agenda(LocalDateTime.of(2026, 11, 18, 21, 0))!!
        assertEquals(LocalDate.of(2026, 11, 19), a.date)
        assertEquals(listOf("A", "B"), a.lessons.map { it.code })
    }

    @Test fun duringDayShowsOnlyRemaining() {
        val a = tt.agenda(LocalDateTime.of(2026, 11, 19, 8, 0))!!
        assertEquals(listOf("A", "B"), a.lessons.map { it.code })   // A ještě běží
        assertEquals(listOf("B"), tt.agenda(LocalDateTime.of(2026, 11, 19, 9, 0))!!.lessons.map { it.code })
    }

    @Test fun afterLastLessonJumpsAndRespectsParity() {
        // pátek 20. 11. (sudý) po výuce -> čtvrtek 26. 11.; v pátek 27. 11. (lichý) jen C
        assertEquals(LocalDate.of(2026, 11, 26), tt.agenda(LocalDateTime.of(2026, 11, 20, 18, 0))!!.date)
        assertEquals(listOf("C"), tt.agenda(LocalDateTime.of(2026, 11, 27, 7, 0))!!.lessons.map { it.code })
    }

    @Test fun afterSemesterNothing() {
        assertEquals(null, tt.agenda(LocalDateTime.of(2026, 12, 19, 10, 0)))
    }

    @Test fun boundaries() {
        assertEquals(LocalDateTime.of(2026, 11, 19, 8, 30), tt.nextBoundary(LocalDateTime.of(2026, 11, 19, 7, 10)))
        assertEquals(LocalDateTime.of(2026, 11, 19, 10, 30), tt.nextBoundary(LocalDateTime.of(2026, 11, 19, 8, 30)))
        assertEquals(null, tt.nextBoundary(LocalDateTime.of(2026, 11, 19, 13, 0)))
    }
}
