package com.example.czurozvrh.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.czurozvrh.MainActivity
import com.example.czurozvrh.R
import com.example.czurozvrh.data.Lesson
import com.example.czurozvrh.data.Timetable
import com.example.czurozvrh.data.TimetableParser.dayNamesLong
import com.example.czurozvrh.data.TimetableRepository
import com.example.czurozvrh.data.agenda
import com.example.czurozvrh.data.colorKey
import com.example.czurozvrh.data.lessonsOn
import com.example.czurozvrh.data.subjectKeys
import com.example.czurozvrh.ui.theme.DarkInk
import com.example.czurozvrh.ui.theme.DarkMuted
import com.example.czurozvrh.ui.theme.DarkSurface
import com.example.czurozvrh.ui.theme.LightInk
import com.example.czurozvrh.ui.theme.LightMuted
import com.example.czurozvrh.ui.theme.LightSurface
import com.example.czurozvrh.ui.theme.SubjectPalette
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------
// Společné
// ---------------------------------------------------------------------

private val cs = Locale.forLanguageTag("cs")
private val dayMonth = DateTimeFormatter.ofPattern("d. MMMM", cs)
private val hm = DateTimeFormatter.ofPattern("H:mm")
private fun LocalTime.fmt() = format(hm)

private val Bg = ColorProvider(day = LightSurface, night = DarkSurface)
private val Ink = ColorProvider(day = LightInk, night = DarkInk)
private val Muted = ColorProvider(day = LightMuted, night = DarkMuted)

/** Barvy předmětů pro světlý i tmavý režim – stejné pořadí jako v aplikaci. */
private class WidgetColors(tt: Timetable) {
    private val keys = tt.subjectKeys()
    private val light = SubjectPalette(false, LightSurface, LightInk).ordered(keys)
    private val dark = SubjectPalette(true, DarkSurface, DarkInk).ordered(keys)
    fun container(l: Lesson) = ColorProvider(day = light.of(l.colorKey).container, night = dark.of(l.colorKey).container)
    fun accent(l: Lesson) = ColorProvider(day = light.of(l.colorKey).accent, night = dark.of(l.colorKey).accent)
    fun ink(l: Lesson) = ColorProvider(day = light.of(l.colorKey).onContainer, night = dark.of(l.colorKey).onContainer)
    fun muted(l: Lesson) = ColorProvider(day = light.of(l.colorKey).muted, night = dark.of(l.colorKey).muted)
    fun accentDay(l: Lesson) = light.of(l.colorKey).accent
    fun accentNight(l: Lesson) = dark.of(l.colorKey).accent
}

/**
 * Živý odpočet do [target] (dnes) – systémový Chronometer, který tiká sám každou sekundu.
 * Ukazuje „40:09“, nad hodinu „1:05:35“.
 */
@Composable
private fun Countdown(
    target: LocalTime, now: LocalDateTime, day: Color, night: Color,
    sizeSp: Float = 11f, modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val targetMillis = now.toLocalDate().atTime(target).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val base = SystemClock.elapsedRealtime() + (targetMillis - System.currentTimeMillis())
    val views = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setChronometer(R.id.countdown, base, null, true)
        setChronometerCountDown(R.id.countdown, true)
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (Build.VERSION.SDK_INT >= 31) {
            setColorInt(R.id.countdown, "setTextColor", day.toArgb(), night.toArgb())
        } else {
            val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            setTextColor(R.id.countdown, (if (isNight) night else day).toArgb())
        }
    }
    AndroidRemoteViews(views, modifier)
}

private data class WidgetData(val tt: Timetable?, val loggedIn: Boolean, val now: LocalDateTime)

private fun loadData(context: Context): WidgetData {
    if (com.example.czurozvrh.BuildConfig.DEMO) return WidgetData(com.example.czurozvrh.data.DemoData.timetable(), true, LocalDateTime.now())
    val repo = TimetableRepository.get(context)
    return WidgetData(repo.cache.load(), repo.store.credentials != null, LocalDateTime.now())
}

private fun hodinWord(n: Int) = when (n) { 1 -> "hodina"; in 2..4 -> "hodiny"; else -> "hodin" }

private fun dayTitle(date: LocalDate, today: LocalDate) = when (date) {
    today -> "Dnes"
    today.plusDays(1) -> "Zítra"
    else -> dayNamesLong[date.dayOfWeek.value]
}

private val openApp get() = actionStartActivity<MainActivity>()

@Composable
private fun Message(title: String, subtitle: String) {
    Column(
        GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(16.dp).clickable(openApp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.height(2.dp))
        Text(subtitle, style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 2)
    }
}

// ---------------------------------------------------------------------
// Widget 1: hodiny na den
// ---------------------------------------------------------------------

class AgendaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent { AgendaContent(data) }
    }
}

@Composable
private fun AgendaContent(data: WidgetData) {
    val tt = data.tt
    when {
        !data.loggedIn -> return Message("Rozvrh ČZU", "Otevři aplikaci a přihlas se.")
        tt == null -> return Message("Rozvrh ČZU", "Rozvrh zatím není stažený.")
    }
    tt!!
    val now = data.now
    val today = now.toLocalDate()
    val agenda = tt.agenda(now)
    val colors = WidgetColors(tt)
    val compact = LocalSize.current.height < 150.dp
    // Odpočet je v hlavičce – v seznamu (LazyColumn) by Chronometer netikal.
    val current = agenda?.takeIf { it.date == today }?.lessons
        ?.firstOrNull { !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(it.end) }

    Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(14.dp).clickable(openApp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    agenda?.let { dayTitle(it.date, today) } ?: "Volno",
                    style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                val sub = when {
                    agenda == null -> "Žádná výuka v nejbližších týdnech"
                    agenda.date == today -> {
                        val all = tt.lessonsOn(today)
                        "${today.format(dayMonth)} · zbývá ${agenda.lessons.size} z ${all.size}"
                    }
                    else -> "${agenda.date.format(dayMonth)} · ${agenda.lessons.size} ${hodinWord(agenda.lessons.size)} od ${agenda.lessons.first().start.fmt()}"
                }
                Text(sub, style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
            }
            if (current != null) {
                Column {
                    Countdown(current.end, now, colors.accentDay(current), colors.accentNight(current), sizeSp = 17f, modifier = GlanceModifier.width(76.dp))
                    Text("do konce", style = TextStyle(color = Muted, fontSize = 10.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.width(10.dp))
            }
            Image(ImageProvider(R.drawable.czu_logo), contentDescription = null,
                modifier = GlanceModifier.size(26.dp).cornerRadius(8.dp))
        }
        if (agenda != null) {
            Spacer(GlanceModifier.height(10.dp))
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(agenda.lessons, itemId = { it.hashCode().toLong() }) { l ->
                    val running = agenda.date == today && !now.toLocalTime().isBefore(l.start) && now.toLocalTime().isBefore(l.end)
                    Column {
                        AgendaRow(l, running, colors, compact)
                        Spacer(GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(l: Lesson, running: Boolean, colors: WidgetColors, compact: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().background(colors.container(l)).cornerRadius(14.dp)
            .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp).clickable(openApp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.width(44.dp)) {
            Text(l.start.fmt(), style = TextStyle(color = colors.ink(l), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (!compact) Text(l.end.fmt(), style = TextStyle(color = colors.muted(l), fontSize = 11.sp), maxLines = 1)
        }
        Box(GlanceModifier.width(4.dp).height(if (compact) 26.dp else 34.dp).background(colors.accent(l)).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    Text("TEĎ  ", style = TextStyle(color = colors.accent(l), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Text(l.name, style = TextStyle(color = colors.ink(l), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Text(
                listOf(l.room, l.type).filter { it.isNotBlank() }.joinToString(" · "),
                style = TextStyle(color = colors.muted(l), fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedulePeriodic(context)
    }
}

// ---------------------------------------------------------------------
// Widget 2: teď / další hodina
// ---------------------------------------------------------------------

class NextLessonWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent { NextLessonContent(data) }
    }
}

@Composable
private fun NextLessonContent(data: WidgetData) {
    val tt = data.tt
    when {
        !data.loggedIn -> return Message("Rozvrh ČZU", "Přihlas se v aplikaci.")
        tt == null -> return Message("Rozvrh ČZU", "Rozvrh není stažený.")
    }
    tt!!
    val now = data.now
    val today = now.toLocalDate()
    val t = now.toLocalTime()
    val todayLessons = tt.lessonsOn(today)
    val current = todayLessons.firstOrNull { !t.isBefore(it.start) && t.isBefore(it.end) }
    val agenda = tt.agenda(now)
    val upcoming = agenda?.lessons?.firstOrNull { agenda.date != today || it.start.isAfter(t) }
    val upcomingDate = agenda?.date
    val shown = current ?: upcoming ?: return Message("Volno", "Žádná výuka v nejbližších týdnech.")
    val colors = WidgetColors(tt)
    val size = LocalSize.current
    val tall = size.height >= 100.dp

    // Dnes se odpočítává živě (Chronometer), jinak jen text.
    val label = when {
        current != null -> null
        upcomingDate == today -> null
        upcomingDate == today.plusDays(1) -> "ZÍTRA · ${shown.start.fmt()}"
        else -> "${dayNamesLong[upcomingDate!!.dayOfWeek.value].uppercase()} · ${shown.start.fmt()}"
    }

    Row(
        GlanceModifier.fillMaxSize().background(colors.container(shown)).cornerRadius(24.dp).padding(12.dp).clickable(openApp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(4.dp).fillMaxHeight().background(colors.accent(shown)).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            val labelStyle = TextStyle(color = colors.accent(shown), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (label != null) {
                Text(label, style = labelStyle, maxLines = 1)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (current != null) "TEĎ · ještě " else "ZA ", style = labelStyle, maxLines = 1)
                    Countdown(if (current != null) current.end else shown.start, now, colors.accentDay(shown), colors.accentNight(shown))
                    if (current == null) Text(" · ${shown.start.fmt()}", style = labelStyle, maxLines = 1)
                }
            }
            Text(
                shown.name,
                style = TextStyle(color = colors.ink(shown), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = if (tall) 2 else 1,
            )
            Text(
                listOf(shown.room, "${shown.start.fmt()}–${shown.end.fmt()}").filter { it.isNotBlank() }.joinToString(" · "),
                style = TextStyle(color = colors.muted(shown), fontSize = 12.sp),
                maxLines = 1,
            )
            // když právě něco probíhá, ukaž i co bude potom
            if (tall && current != null && upcoming != null && upcomingDate == today) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "Potom ${upcoming.start.fmt()} ${upcoming.name}",
                    style = TextStyle(color = colors.muted(shown), fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedulePeriodic(context)
    }
}
