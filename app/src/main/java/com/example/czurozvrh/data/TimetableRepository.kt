package com.example.czurozvrh.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Je potřeba ruční přihlášení (špatné heslo, 2FA, chybí údaje). */
class NeedsManualLogin(msg: String, val pending2FA: LoginResult.Need2FA? = null) : IOException(msg)

/**
 * Jediný zdroj dat pro aplikaci i widgety (jeden cookie jar = jedna UIS session).
 */
class TimetableRepository private constructor(context: Context) {
    val store = SecureStore(context)
    val cache = TimetableCache(context)
    private val cookies = PersistentCookieJar(context)
    val client = UisClient(cookies)
    private val mutex = Mutex()

    /** Stáhne rozvrh; když session vypršela, tiše se přihlásí uloženými údaji. Výsledek uloží do cache. */
    suspend fun fetch(): Timetable = mutex.withLock {
        val id = store.studentId ?: client.detectStudentId()?.also { store.studentId = it }
            ?: throw NeedsManualLogin("Chybí číslo rozvrhu studenta.")
        val t = try {
            client.fetchTimetable(id)
        } catch (e: NotLoggedInException) {
            val creds = store.credentials ?: throw NeedsManualLogin("Přihlas se prosím.")
            when (val r = client.login(creds)) {
                LoginResult.Success -> client.fetchTimetable(id)
                is LoginResult.Need2FA -> throw NeedsManualLogin("UIS vyžaduje ověřovací kód.", r)
                is LoginResult.Failed -> {
                    if (r.message.startsWith("Nepodařilo se spojit")) throw IOException(r.message)
                    throw NeedsManualLogin(r.message)
                }
            }
        }
        cache.save(t)
        t
    }

    fun logout() {
        client.logout()
        store.credentials = null
        store.studentId = null
        cache.clear()
    }

    companion object {
        @Volatile private var instance: TimetableRepository? = null
        fun get(context: Context): TimetableRepository =
            instance ?: synchronized(this) {
                instance ?: TimetableRepository(context.applicationContext).also { instance = it }
            }
    }
}

// ---------- výběr hodin pro widgety ----------

fun Timetable.lessonsOn(date: LocalDate): List<Lesson> =
    lessonsInWeek(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        .filter { it.dayOfWeek == date.dayOfWeek.value }
        .sortedBy { it.start }

/** Den, který má widget ukázat: dnes (pokud ještě něco zbývá), jinak nejbližší další den s výukou. */
data class Agenda(val date: LocalDate, val lessons: List<Lesson>)

fun Timetable.agenda(now: LocalDateTime, lookaheadDays: Int = 21): Agenda? {
    val today = now.toLocalDate()
    val rest = lessonsOn(today).filter { it.end.isAfter(now.toLocalTime()) }
    if (rest.isNotEmpty()) return Agenda(today, rest)
    for (i in 1..lookaheadDays) {
        val d = today.plusDays(i.toLong())
        val l = lessonsOn(d)
        if (l.isNotEmpty()) return Agenda(d, l)
    }
    return null
}

/** Nejbližší okamžik, kdy se má widget překreslit (začátek/konec nějaké dnešní hodiny). */
fun Timetable.nextBoundary(now: LocalDateTime): LocalDateTime? =
    lessonsOn(now.toLocalDate())
        .flatMap { listOf(it.start, it.end) }
        .map { now.toLocalDate().atTime(it) }
        .filter { it.isAfter(now) }
        .minOrNull()

// ---------- barvy předmětů (stejné v aplikaci i widgetech) ----------

val Lesson.colorKey: String get() = code.ifBlank { name }

/** Předměty v pořadí, v jakém se poprvé objeví v týdnu – podle toho dostávají barvy. */
fun Timetable.subjectKeys(): List<String> =
    lessons.sortedBy { it.dayOfWeek * 10000 + it.start.toSecondOfDay() / 60 }.map { it.colorKey }.distinct()
