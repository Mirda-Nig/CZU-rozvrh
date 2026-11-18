package com.example.czurozvrh.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Smyšlený rozvrh pro ukázkovou verzi (`gradlew installDebug -Pdemo=true`) – na screenshoty,
 * aby se nikde neukazoval skutečný rozvrh. Předměty, vyučující i místnosti jsou vymyšlené.
 */
object DemoData {
    private fun l(
        day: Int, from: String, to: String, code: String, name: String, type: String,
        room: String, teacher: String, parity: WeekParity = WeekParity.ANY,
    ) = Lesson(
        dayOfWeek = day, date = null, start = LocalTime.parse(from), end = LocalTime.parse(to),
        code = code, name = name, type = type, room = room, teacher = teacher,
        note = if (parity == WeekParity.EVEN) "Sudý týden" else if (parity == WeekParity.ODD) "Lichý týden" else "",
        parity = parity, groups = "1b-demo",
    )

    fun timetable(today: LocalDate = LocalDate.now()): Timetable {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lessons = listOf(
            l(1, "09:00", "10:30", "DEM101", "Úvod do programování", "Přednáška", "Aula", "J. Novák"),
            l(1, "10:45", "12:15", "DEM101", "Úvod do programování", "Cvičení", "E 107", "P. Svobodová"),
            l(1, "13:00", "14:30", "DEM205", "Ekonomie I", "Přednáška", "T 210", "M. Dvořák"),
            l(2, "08:00", "09:30", "DEM110", "Matematika I", "Přednáška", "Aula", "K. Černá"),
            l(2, "09:45", "11:15", "DEM110", "Matematika I", "Cvičení", "M 220", "T. Procházka", WeekParity.EVEN),
            l(2, "14:00", "15:30", "DEM330", "Angličtina B1", "Cvičení", "J 24", "L. Kučerová"),
            l(3, "10:30", "12:00", "DEM240", "Databázové systémy", "Přednáška", "E 323", "R. Veselý"),
            l(3, "12:15", "13:45", "DEM240", "Databázové systémy", "Cvičení", "E 211", "A. Marková", WeekParity.EVEN),
            l(3, "15:45", "17:15", "DEM150", "Statistika", "Přednáška", "T 101", "V. Horák"),
            l(4, "08:00", "09:30", "DEM150", "Statistika", "Cvičení", "T 105", "V. Horák"),
            l(4, "11:00", "12:30", "DEM205", "Ekonomie I", "Cvičení", "T 212", "E. Pokorná", WeekParity.EVEN),
            l(4, "14:00", "15:30", "DEM180", "Základy práva", "Přednáška", "Aula", "D. Král"),
            l(5, "08:00", "09:00", "DEM900", "Tělesná výchova", "Cvičení", "Sportovní hala", "F. Beneš"),
        )
        return Timetable(
            lessons = lessons,
            parityRef = ParityRef(monday, WeekParity.EVEN),
            semester = "ZS ukázka",
            fetchedAt = System.currentTimeMillis(),
            validFrom = monday.minusWeeks(7),
            validTo = monday.plusWeeks(5),
        )
    }
}
