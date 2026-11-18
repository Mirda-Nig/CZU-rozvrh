package com.example.czurozvrh.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Parsuje stránku UIS `rozvrhy_view.pl` ve formátu `format=list` ("Seznam rozvrhových akcí").
 * Sloupce se mapují podle textu v záhlaví, takže nevadí, když jich UIS přidá/ubere.
 *
 * Sudé/liché týdny UIS neuvádí ve sloupci, ale jako odkaz na poznámku: u předmětu je
 * `<sup>(1)</sup>` a pod rozvrhem tabulka "Poznámky:" s řádkem "(1) Sudý týden".
 */
object TimetableParser {

    private const val NBSP = ' '
    private val dateRe = Regex("""(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})""")
    private val timeRe = Regex("""(\d{1,2}):(\d{2})""")
    private val weekRe = Regex("""(\d+)\.\s*\((sudý|lichý)\)\s*výukový týden""", RegexOption.IGNORE_CASE)
    private val semesterRe = Regex("""\((ZS|LS) (\d{4}/\d{4})\)""")
    private val footnoteRe = Regex("""^\((\d+)\)$""")
    private val numberRe = Regex("""\d+""")
    private val validityRe =
        Regex("""Platnost:\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})\s*-\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})""")
    private val evenRe = Regex("""\bsud""")
    private val oddRe = Regex("""\blich""")
    private val codeRe = Regex("""^[A-Z0-9_]{3,}$""")

    private val days = mapOf(
        "po" to 1, "út" to 2, "ut" to 2, "st" to 3, "čt" to 4, "ct" to 4,
        "pá" to 5, "pa" to 5, "so" to 6, "ne" to 7,
    )

    fun isLoginPage(html: String) = html.contains("name=\"credential_0\"")

    private fun clean(s: String) = s.replace(NBSP, ' ').trim()

    fun parse(html: String, today: LocalDate = LocalDate.now()): Timetable {
        val doc = Jsoup.parse(html)
        val footnotes = parseFootnotes(doc)
        val lessons = parseLessons(doc, footnotes)
        val text = doc.body()?.text().orEmpty().replace(NBSP, ' ')
        val parityRef = weekRe.find(text)?.let {
            val p = if (it.groupValues[2].lowercase() == "sudý") WeekParity.EVEN else WeekParity.ODD
            ParityRef(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), p)
        }
        val semester = semesterRe.find(text)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }.orEmpty()
        val validity = validityRe.find(text)?.groupValues?.let { g ->
            runCatching {
                LocalDate.of(g[3].toInt(), g[2].toInt(), g[1].toInt()) to
                    LocalDate.of(g[6].toInt(), g[5].toInt(), g[4].toInt())
            }.getOrNull()
        }
        return Timetable(lessons, parityRef, semester, System.currentTimeMillis(), validity?.first, validity?.second)
    }

    /** Tabulka "Poznámky:" pod rozvrhem: (1) -> "Sudý týden" atd. */
    private fun parseFootnotes(doc: Document): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        for (row in doc.select("tr")) {
            val tds = row.select("> td")
            if (tds.size != 2) continue
            val n = footnoteRe.find(tds[0].text().trim())?.groupValues?.get(1)?.toIntOrNull() ?: continue
            map[n] = clean(tds[1].text())
        }
        return map
    }

    private fun parityOf(text: String): WeekParity? {
        val t = text.lowercase()
        return when {
            evenRe.containsMatchIn(t) -> WeekParity.EVEN
            oddRe.containsMatchIn(t) -> WeekParity.ODD
            else -> null
        }
    }

    private fun parseLessons(doc: Document, footnotes: Map<Int, String>): List<Lesson> {
        val result = mutableListOf<Lesson>()
        for (table in doc.select("table")) {
            val headers = table.select("thead th").map { it.text().trim().lowercase() }
                .ifEmpty { table.select("tr").firstOrNull()?.select("th")?.map { it.text().trim().lowercase() } ?: emptyList() }
            fun idx(vararg prefixes: String) = headers.indexOfFirst { h -> prefixes.any { h.startsWith(it) } }
            val iDay = idx("den")
            val iFrom = idx("od")
            val iTo = idx("do")
            if (iDay < 0 || iFrom < 0 || iTo < 0) continue
            val iSubj = idx("předmět", "predmet")
            val iType = idx("akce")
            val iRoom = idx("místnost", "mistnost")
            val iTeacher = idx("vyučující", "vyucujici")
            val iGroups = idx("omezení", "omezeni")
            val iWeek = idx("týden", "tyden", "periodicita", "perioda")

            for (row in table.select("tbody tr")) {
                val tds = row.select("> td")
                // odkazy na poznámky (<sup>(1,2)</sup>) vytáhnout a z textu buněk odstranit
                val refs = tds.flatMap { td -> td.select("sup").map { it.text() } }
                    .flatMap { s -> numberRe.findAll(s).map { it.value.toInt() } }
                tds.forEach { it.select("sup").remove() }
                val cells = tds.map { clean(it.text()) }
                if (cells.size <= maxOf(iDay, iFrom, iTo)) continue
                fun cell(i: Int) = if (i in cells.indices) cells[i] else ""

                val dayCell = cell(iDay)
                val date = dateRe.find(dayCell)?.let {
                    runCatching {
                        LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
                    }.getOrNull()
                }
                val dow = date?.dayOfWeek?.value
                    ?: days[dayCell.take(2).lowercase()]
                    ?: continue
                val start = parseTime(cell(iFrom)) ?: continue
                val end = parseTime(cell(iTo)) ?: continue

                val subject = cell(iSubj)
                val firstToken = subject.substringBefore(' ')
                val (code, name) = if (codeRe.matches(firstToken) && firstToken.any { it.isDigit() })
                    firstToken to subject.substringAfter(' ').trim()
                else "" to subject

                val notes = refs.distinct().mapNotNull { footnotes[it] }.filter { it.isNotBlank() }
                val parity = (notes + listOf(cell(iWeek), dayCell)).firstNotNullOfOrNull { parityOf(it) }
                    ?: WeekParity.ANY

                result += Lesson(
                    dayOfWeek = dow, date = date, start = start, end = end,
                    code = code, name = name.ifBlank { subject }, type = cell(iType),
                    room = cell(iRoom), teacher = cell(iTeacher), note = notes.joinToString("\n"),
                    parity = parity, groups = cell(iGroups),
                )
            }
        }
        return result.distinct().sortedWith(compareBy({ it.date ?: LocalDate.MIN }, { it.dayOfWeek }, { it.start }))
    }

    private fun parseTime(s: String): LocalTime? = timeRe.find(s)?.let {
        runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull()
    }

    val dayNames = listOf("", "Po", "Út", "St", "Čt", "Pá", "So", "Ne")
    val dayNamesLong = listOf("", "Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek", "Sobota", "Neděle")
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d. M.")
}
