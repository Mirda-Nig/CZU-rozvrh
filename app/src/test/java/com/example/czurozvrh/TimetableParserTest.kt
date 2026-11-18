package com.example.czurozvrh

import com.example.czurozvrh.data.TimetableParser
import com.example.czurozvrh.data.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimetableParserTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parsesUisListFormat() {
        val t = TimetableParser.parse(res("rozvrh_list.html"), LocalDate.of(2026, 11, 18))
        assertEquals(337, t.lessons.size)
        val l = t.lessons.first { it.code == "TCT70E" }
        assertEquals("Aplikovaná hydromechanika", l.name)
        assertEquals("Přednáška", l.type)
        assertEquals("M216/2", l.room)
        assertEquals(LocalDate.of(2020, 10, 2), l.date)
        assertEquals(5, l.dayOfWeek)
        assertEquals(LocalTime.of(12, 15), l.start)
        assertEquals(LocalTime.of(15, 15), l.end)
        assertTrue(l.isLecture)
        println("semestr=${t.semester} parity=${t.parityRef} první=${t.lessons.first()}")
    }

    @Test
    fun detectsLoginPage() {
        assertTrue(TimetableParser.isLoginPage(res("login.html")))
        assertFalse(TimetableParser.isLoginPage(res("rozvrh_list.html")))
    }

    @Test
    fun parsesWeekParityAndWeeklyRows() {
        val html = """<html><body><div>18. 11. 2026 20:33 - 8. (sudý) výukový týden (ZS 2026/2027)</div>
            <table><thead><tr><th>Den</th><th>Od</th><th>Do</th><th>Předmět</th><th>Akce</th><th>Místnost</th><th>Vyučující</th><th>Týden</th></tr></thead>
            <tbody><tr><td>Út</td><td>9:00</td><td>10:30</td><td>EIE01E Informatika</td><td>Cvičení</td><td>E107</td><td>J. Novák</td><td>lichý</td></tr>
            <tr><td>St</td><td>13:00</td><td>14:30</td><td>Matematika</td><td>Přednáška</td><td>AULA</td><td></td><td></td></tr></tbody></table></body></html>"""
        val t = TimetableParser.parse(html, LocalDate.of(2026, 11, 18))
        assertEquals("ZS 2026/2027", t.semester)
        assertEquals(WeekParity.EVEN, t.parityRef!!.parity)
        assertEquals(LocalDate.of(2026, 11, 16), t.parityRef!!.monday)
        assertEquals(WeekParity.ODD, t.parityRef!!.parityOf(LocalDate.of(2026, 11, 23)))
        val inf = t.lessons.first { it.code == "EIE01E" }
        assertEquals(WeekParity.ODD, inf.parity)
        assertEquals(2, inf.dayOfWeek)
        assertFalse(inf.occursIn(LocalDate.of(2026, 11, 16), WeekParity.EVEN))
        assertTrue(inf.occursIn(LocalDate.of(2026, 11, 23), WeekParity.ODD))
        assertEquals("", t.lessons.first { it.dayOfWeek == 3 }.code)
    }
}

class FootnoteParityTest {
    private val html = """<html><body>18. 11. 2026 - 8. (sudý) výukový týden (ZS 2026/2027)
        <table><thead><tr><th>Den</th><th>Od</th><th>Do</th><th>Předmět</th><th>Akce</th><th>Místnost</th><th>Vyučující</th><th>Omezení</th></tr></thead><tbody>
        <tr><td>Čt</td><td>07:00</td><td>08:30</td><td>EIE51E Úvod do principu počítačů - INFO1</td><td>Přednáška</td><td>TI</td><td>A. B</td><td>1b-infop</td></tr>
        <tr><td>Čt</td><td>08:45</td><td>10:15</td><td>EREP3E Teorie řízení&nbsp;<sup>(1)</sup></td><td>Cvičení</td><td>E455</td><td>C. D</td><td>1b-infop10</td></tr>
        <tr><td>Pá</td><td>14:00</td><td>15:30</td><td>ELX03E_1 Angličtina - B1&nbsp;<sup>(2,3)</sup></td><td>Cvičení</td><td>JIHS24</td><td>E. F</td><td></td></tr>
        </tbody></table>
        Poznámky:<br/><table><tbody><tr><td>(1)</td><td>Sudý týden</td></tr><tr><td>(2)</td><td> </td></tr><tr><td>(3)</td><td>Lichý týden</td></tr></tbody></table>
        Platnost: 28. 9. 2026 - 20. 12. 2026<br/></body></html>"""

    @Test
    fun filtersByFootnoteParityAndValidity() {
        val t = TimetableParser.parse(html, LocalDate.of(2026, 11, 18))
        assertEquals(LocalDate.of(2026, 9, 28), t.validFrom)
        assertEquals(LocalDate.of(2026, 12, 20), t.validTo)
        val rep = t.lessons.first { it.code == "EREP3E" }
        assertEquals("Teorie řízení", rep.name)       // bez "(1)"
        assertEquals(WeekParity.EVEN, rep.parity)
        assertEquals("Sudý týden", rep.note)
        assertEquals("1b-infop10", rep.groups)
        assertEquals(WeekParity.ODD, t.lessons.first { it.code == "ELX03E_1" }.parity)

        val even = t.lessonsInWeek(LocalDate.of(2026, 11, 16)).map { it.code }
        val odd = t.lessonsInWeek(LocalDate.of(2026, 11, 23)).map { it.code }
        assertEquals(listOf("EIE51E", "EREP3E"), even)
        assertEquals(listOf("EIE51E", "ELX03E_1"), odd)
        assertTrue(t.lessonsInWeek(LocalDate.of(2027, 1, 4)).isEmpty())   // po konci platnosti
        assertTrue(t.isOutsideTeaching(LocalDate.of(2026, 9, 21)))
        assertEquals(WeekParity.ODD, t.parityRef!!.parityOf(LocalDate.of(2026, 9, 28))) // 1. týden = lichý
    }
}

class OwnTimetableSmokeTest {
    @Test
    fun printWeeks() {
        val f = java.io.File(System.getenv("CZU_OWN_HTML") ?: return)
        if (!f.exists()) return
        val t = TimetableParser.parse(f.readText(), LocalDate.of(2026, 11, 18))
        for (m in listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23))) {
            println("WEEK $m ${t.parityRef?.parityOf(m)}: " +
                t.lessonsInWeek(m).joinToString { "${it.dayOfWeek} ${it.start} ${it.code}/${it.type.take(3)}" })
        }
        t.lessons.filter { it.note.isNotBlank() }.forEach { println("NOTE ${it.code} ${it.parity} | ${it.note.replace('\n', ';')}") }
    }
}
