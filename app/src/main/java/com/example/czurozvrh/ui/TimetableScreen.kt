package com.example.czurozvrh.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.czurozvrh.data.Lesson
import com.example.czurozvrh.notify.LessonNotifier
import com.example.czurozvrh.data.Timetable
import com.example.czurozvrh.data.TimetableParser.dayNames
import com.example.czurozvrh.data.TimetableParser.dayNamesLong
import com.example.czurozvrh.data.WeekParity
import com.example.czurozvrh.data.colorKey
import com.example.czurozvrh.widget.AgendaWidgetReceiver
import com.example.czurozvrh.widget.NextLessonWidgetReceiver
import com.example.czurozvrh.data.subjectKeys
import com.example.czurozvrh.ui.theme.LocalSubjectPalette
import com.example.czurozvrh.ui.theme.NowRed
import com.example.czurozvrh.ui.theme.SubjectColors
import com.example.czurozvrh.ui.theme.TabularNums
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val cs = Locale.forLanguageTag("cs")
private val dayMonth = DateTimeFormatter.ofPattern("d. MMMM", cs)
private val hm = DateTimeFormatter.ofPattern("H:mm")

private fun mondayOf(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Výchozí týden: o víkendu rovnou ukaž příští (jako Bakaláři). */
private fun defaultMonday(): LocalDate {
    val today = LocalDate.now()
    return if (today.dayOfWeek.value >= 6) mondayOf(today).plusWeeks(1) else mondayOf(today)
}

private fun weekRangeText(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    return if (monday.month == sunday.month) "${monday.dayOfMonth}.–${sunday.format(dayMonth)}"
    else "${monday.format(dayMonth)} – ${sunday.format(dayMonth)}"
}

private fun durationText(d: Duration): String {
    val h = d.toHours(); val m = d.toMinutes() % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

private fun LocalTime.fmt(): String = format(hm)

@Composable
private fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(30_000); value = LocalDateTime.now() }
    }
    return now
}

private val tnum = TextStyle(fontFeatureSettings = TabularNums)

@Composable
private fun subjectColors(l: Lesson): SubjectColors = LocalSubjectPalette.current.of(l.colorKey)

/** Dny, které se mají v týdnu zobrazit: Po–Pá (+ víkend, jen když je výuka), případně jen dny s výukou. */
private fun visibleDays(weekLessons: List<Lesson>, hideEmptyDays: Boolean): List<Int> {
    val withLessons = weekLessons.map { it.dayOfWeek }.toSet()
    return (1..7).filter { d -> if (hideEmptyDays || d >= 6) d in withLessons else true }
}

// =====================================================================
// Obrazovka
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(state: UiState, vm: RozvrhViewModel) {
    var mondayEpoch by rememberSaveable { mutableLongStateOf(defaultMonday().toEpochDay()) }
    val monday = LocalDate.ofEpochDay(mondayEpoch)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Lesson?>(null) }
    var settings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val now = rememberNow()

    val tt = state.timetable
    val weekLessons = tt?.lessonsInWeek(monday).orEmpty()

    // Odpočet hodiny v notifikacích – na Androidu 13+ je potřeba oprávnění.
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onNotificationPermissionResult()
    }
    val requestNotifications = {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !LessonNotifier.canPost(context))
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) { if (state.lessonCountdown) requestNotifications() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    val basePalette = LocalSubjectPalette.current
    val palette = remember(tt, basePalette) {
        basePalette.ordered(tt?.subjectKeys().orEmpty())
    }
    CompositionLocalProvider(LocalSubjectPalette provides palette) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Header(
                tt = tt,
                monday = monday,
                tab = tab,
                onTab = { tab = it },
                onPrev = { mondayEpoch = monday.minusWeeks(1).toEpochDay() },
                onNext = { mondayEpoch = monday.plusWeeks(1).toEpochDay() },
                onToday = { mondayEpoch = defaultMonday().toEpochDay() },
                isCurrentWeek = monday == defaultMonday(),
                vm = vm,
                onSettings = { settings = true },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                tt == null -> EmptyState(
                    if (state.loading) "Načítám rozvrh…" else "Rozvrh zatím není stažený",
                    if (state.loading) null else "Táhni dolů pro obnovení.",
                )
                tt.isOutsideTeaching(monday) -> EmptyState(
                    "Mimo období výuky",
                    "Rozvrh platí ${tt.validFrom?.format(dayMonth) ?: "?"} – ${tt.validTo?.format(dayMonth) ?: "?"}.",
                )
                weekLessons.isEmpty() -> EmptyState("Volný týden", "Tento týden nemáš žádnou výuku.")
                tab == 0 -> WeekGrid(tt, monday, weekLessons, now, state.hideEmptyDays, onClick = { selected = it })
                else -> DayView(monday, weekLessons, now, tt, state.hideEmptyDays, onClick = { selected = it })
            }
        }
    }

    selected?.let { LessonSheet(it, onDismiss = { selected = null }) }
    if (settings) SettingsDialog(state, vm, requestNotifications, onDismiss = { settings = false })
    }
}

// =====================================================================
// Záhlaví
// =====================================================================

@Composable
private fun Header(
    tt: Timetable?,
    monday: LocalDate,
    tab: Int,
    onTab: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    isCurrentWeek: Boolean,
    vm: RozvrhViewModel,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val weekNo = tt?.validFrom?.let { ChronoUnit.WEEKS.between(mondayOf(it), monday) + 1 }
        ?.takeIf { tt.validTo == null || !monday.isAfter(tt.validTo) }?.takeIf { it >= 1 }
    val parity = tt?.parityRef?.parityOf(monday)

    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (weekNo != null) "$weekNo. týden" else "Rozvrh",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        weekRangeText(monday),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (parity != null && parity != WeekParity.ANY) {
                        Spacer(Modifier.width(8.dp))
                        Pill(if (parity == WeekParity.EVEN) "sudý" else "lichý")
                    }
                }
            }
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Předchozí týden") }
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Další týden") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Obnovit") }, onClick = { menu = false; vm.refresh() })
                    DropdownMenuItem(text = { Text("Nastavení") }, onClick = { menu = false; onSettings() })
                    vm.timetableWebUrl()?.let { url ->
                        DropdownMenuItem(text = { Text("Otevřít v UIS") }, onClick = {
                            menu = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        })
                    }
                    DropdownMenuItem(text = { Text("Odhlásit") }, onClick = { menu = false; vm.logout() })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("Týden", "Den"), tab, onTab, Modifier.weight(1f))
            AnimatedVisibility(!isCurrentWeek, enter = fadeIn(), exit = fadeOut()) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        onClick = onToday,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text("Dnes", Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val bg by animateColorAsState(
                if (i == selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent, label = "seg",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String?) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 120.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DateRange, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

// =====================================================================
// Týden – kalendářová mřížka (dny ve sloupcích, čas svisle)
// =====================================================================

private val HourHeight = 64.dp
private val GutterWidth = 44.dp

/** Překrývající se hodiny v jednom dni rozdělí do vedlejších sloupečků. */
private fun lanes(lessons: List<Lesson>): Pair<List<Pair<Lesson, Int>>, Int> {
    val laneEnds = mutableListOf<LocalTime>()
    val placed = lessons.sortedBy { it.start }.map { l ->
        val idx = laneEnds.indexOfFirst { !it.isAfter(l.start) }
        if (idx >= 0) { laneEnds[idx] = l.end; l to idx } else { laneEnds += l.end; l to laneEnds.lastIndex }
    }
    return placed to laneEnds.size.coerceAtLeast(1)
}

@Composable
private fun WeekGrid(
    tt: Timetable,
    monday: LocalDate,
    weekLessons: List<Lesson>,
    now: LocalDateTime,
    hideEmptyDays: Boolean,
    onClick: (Lesson) -> Unit,
) {
    val all = tt.lessons
    val firstHour = all.minOfOrNull { it.start.hour } ?: 7
    val lastHour = all.maxOfOrNull { if (it.end.minute > 0) it.end.hour + 1 else it.end.hour } ?: 18
    val hourCount = (lastHour - firstHour).coerceAtLeast(1)
    val days = visibleDays(weekLessons, hideEmptyDays)
    val today = now.toLocalDate()
    val line = MaterialTheme.colorScheme.outlineVariant

    fun y(t: LocalTime): Dp = HourHeight * ((t.hour - firstHour) + t.minute / 60f)

    Column(Modifier.fillMaxSize()) {
        // záhlaví dnů
        Row(Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp)) {
            Spacer(Modifier.width(GutterWidth))
            for (d in days) {
                val date = monday.plusDays((d - 1).toLong())
                val isToday = date == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        dayNames[d].uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium.merge(tnum),
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(HourHeight * hourCount + 16.dp)
                    .padding(top = 8.dp, end = 8.dp, bottom = 8.dp),
            ) {
                // časová osa
                Box(Modifier.width(GutterWidth).fillMaxHeight()) {
                    for (i in 0..hourCount) {
                        Text(
                            "${firstHour + i}:00",
                            Modifier.offset(y = HourHeight * i - 7.dp).width(GutterWidth - 8.dp),
                            style = MaterialTheme.typography.labelSmall.merge(tnum),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                // mřížka + sloupce dnů
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            val step = HourHeight.toPx()
                            for (i in 0..hourCount) {
                                drawLine(line, Offset(0f, i * step), Offset(size.width, i * step), 1.dp.toPx())
                                if (i < hourCount) drawLine(
                                    line.copy(alpha = 0.5f), Offset(0f, i * step + step / 2), Offset(size.width, i * step + step / 2),
                                    1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                                )
                            }
                        },
                ) {
                    for (d in days) {
                        val date = monday.plusDays((d - 1).toLong())
                        val (placed, laneCount) = lanes(weekLessons.filter { it.dayOfWeek == d })
                        BoxWithConstraints(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (date == today) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent),
                        ) {
                            val laneW = maxWidth / laneCount
                            for ((l, lane) in placed) {
                                WeekBlock(
                                    l,
                                    Modifier
                                        .offset(x = laneW * lane, y = y(l.start))
                                        .width(laneW)
                                        .height(y(l.end) - y(l.start))
                                        .padding(horizontal = 2.dp, vertical = 1.dp),
                                    onClick = { onClick(l) },
                                )
                            }
                            if (date == today && now.hour in firstHour until lastHour) {
                                val ny = y(now.toLocalTime())
                                Box(Modifier.offset(y = ny - 1.dp).fillMaxWidth().height(2.dp).background(NowRed))
                                Box(Modifier.offset(x = (-4).dp, y = ny - 4.dp).size(8.dp).clip(CircleShape).background(NowRed))
                            }
                        }
                    }
                }
            }
            UpdatedFooter(tt)
        }
    }
}

@Composable
private fun WeekBlock(l: Lesson, modifier: Modifier, onClick: () -> Unit) {
    val c = subjectColors(l)
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.container)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(c.accent))
        val wide = maxWidth > 120.dp
        // úzké sloupce (celý týden na telefonu): menší písmo a české dělení slov se spojovníkem
        val narrow = maxWidth < 90.dp
        val lineH = if (narrow) 13.sp else 14.sp
        val nameLines = ((maxHeight - 26.dp) / (if (narrow) 14.dp else 15.dp)).toInt().coerceIn(1, 5)
        Column(Modifier.padding(start = if (narrow) 7.dp else 8.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)) {
            Text(
                l.name.ifBlank { l.shortName },
                style = MaterialTheme.typography.labelMedium.merge(
                    TextStyle(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph, localeList = LocaleList("cs")),
                ),
                fontWeight = FontWeight.Bold,
                fontSize = if (narrow) 10.5.sp else 11.5.sp,
                lineHeight = lineH,
                color = c.onContainer,
                maxLines = nameLines,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (wide && l.teacher.isNotBlank()) "${l.room} · ${l.teacher}" else l.room,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpdatedFooter(tt: Timetable) {
    val fetched = java.time.Instant.ofEpochMilli(tt.fetchedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
    val whenText = if (fetched.toLocalDate() == LocalDate.now()) "dnes ${fetched.toLocalTime().fmt()}"
    else "${fetched.toLocalDate().format(dayMonth)} ${fetched.toLocalTime().fmt()}"
    Text(
        listOfNotNull(tt.semester.ifBlank { null }, "aktualizováno $whenText").joinToString("  ·  "),
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

// =====================================================================
// Den – časová osa
// =====================================================================

@Composable
private fun DayView(
    monday: LocalDate,
    weekLessons: List<Lesson>,
    now: LocalDateTime,
    tt: Timetable,
    hideEmptyDays: Boolean,
    onClick: (Lesson) -> Unit,
) {
    val today = now.toLocalDate()
    val days = visibleDays(weekLessons, hideEmptyDays)
    var chosen by rememberSaveable(monday) { mutableIntStateOf(0) }
    val day = chosen.takeIf { it in days }
        ?: (if (mondayOf(today) == monday) days.firstOrNull { it >= today.dayOfWeek.value } else null)
        ?: days.firstOrNull() ?: 1
    val date = monday.plusDays((day - 1).toLong())
    val lessons = weekLessons.filter { it.dayOfWeek == day }.sortedBy { it.start }
    val nowT = now.toLocalTime()
    val isToday = date == today
    val nextIdx = if (isToday) lessons.indexOfFirst { it.start.isAfter(nowT) } else -1

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            DayStrip(monday, days, day, today, weekLessons, onSelect = { chosen = it })
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                Text(
                    "${dayNamesLong[day]} ${date.format(dayMonth)}",
                    style = MaterialTheme.typography.titleLarge,
                )
                val summary = if (lessons.isEmpty()) "Bez výuky"
                else "${lessons.size} ${hodinWord(lessons.size)}  ·  ${lessons.first().start.fmt()} – ${lessons.maxOf { it.end }.fmt()}"
                Text(summary, style = MaterialTheme.typography.bodyMedium.merge(tnum),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (lessons.isEmpty()) item {
            Text("Volno 🎉", Modifier.fillMaxWidth().padding(40.dp), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        for (i in lessons.indices) {
            val l = lessons[i]
            if (i > 0) {
                val gap = Duration.between(lessons[i - 1].end, l.start)
                if (gap.toMinutes() >= 30) item(key = "gap$i") { GapRow(gap) }
            }
            item(key = "l$i${l.start}${l.code}") {
                val running = isToday && !nowT.isBefore(l.start) && nowT.isBefore(l.end)
                val progress = if (running)
                    Duration.between(l.start, nowT).toMinutes().toFloat() / Duration.between(l.start, l.end).toMinutes().coerceAtLeast(1)
                else null
                val badge = when {
                    running -> "Teď · ještě ${durationText(Duration.between(nowT, l.end))}"
                    i == nextIdx -> "Za ${durationText(Duration.between(nowT, l.start))}"
                    else -> null
                }
                TimelineRow(l, badge, progress, dimmed = isToday && !l.end.isAfter(nowT), onClick = { onClick(l) })
            }
        }
        item { UpdatedFooter(tt) }
    }
}

private fun hodinWord(n: Int) = when (n) { 1 -> "hodina"; in 2..4 -> "hodiny"; else -> "hodin" }

@Composable
private fun DayStrip(
    monday: LocalDate,
    days: List<Int>,
    selected: Int,
    today: LocalDate,
    weekLessons: List<Lesson>,
    onSelect: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in days) {
            val date = monday.plusDays((d - 1).toLong())
            val isSel = d == selected
            val hasLessons = weekLessons.any { it.dayOfWeek == d }
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "day",
            )
            val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable { onSelect(d) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(dayNames[d], style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) fg.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge.merge(tnum),
                    color = if (hasLessons || isSel) fg else fg.copy(alpha = 0.4f))
                Box(
                    Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(
                        when {
                            date == today && isSel -> fg
                            date == today -> MaterialTheme.colorScheme.primary
                            else -> Color.Transparent
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun GapRow(gap: Duration) {
    val line = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 2.dp).height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(64.dp))
        Box(
            Modifier.width(2.dp).fillMaxHeight().drawBehind {
                drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            },
        )
        Spacer(Modifier.width(14.dp))
        Text("Volno ${durationText(gap)}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimelineRow(l: Lesson, badge: String?, progress: Float?, dimmed: Boolean, onClick: () -> Unit) {
    val c = subjectColors(l)
    val alpha = if (dimmed) 0.5f else 1f
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Column(Modifier.width(56.dp).padding(top = 14.dp), horizontalAlignment = Alignment.End) {
            Text(l.start.fmt(), style = MaterialTheme.typography.titleSmall.merge(tnum),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(l.end.fmt(), style = MaterialTheme.typography.bodySmall.merge(tnum),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(c.container.copy(alpha = if (dimmed) 0.55f else 1f))
                .clickable(onClick = onClick),
        ) {
            Row {
                Box(Modifier.padding(vertical = 14.dp).padding(start = 12.dp).width(4.dp).height(40.dp)
                    .clip(CircleShape).background(c.accent.copy(alpha = alpha)))
                Column(Modifier.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            l.type.uppercase().ifBlank { l.code },
                            style = MaterialTheme.typography.labelSmall,
                            color = c.accent.copy(alpha = alpha),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        badge?.let {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = c.accent, contentColor = Color.White) {
                                Text(it, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(l.name, style = MaterialTheme.typography.titleSmall, color = c.onContainer.copy(alpha = alpha),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (l.room.isNotBlank()) IconText(Icons.Filled.Place, l.room, c.muted.copy(alpha = c.muted.alpha * alpha))
                        if (l.room.isNotBlank() && l.teacher.isNotBlank()) Spacer(Modifier.width(12.dp))
                        if (l.teacher.isNotBlank()) IconText(Icons.Filled.Person, l.teacher, c.muted.copy(alpha = c.muted.alpha * alpha))
                    }
                }
            }
            progress?.let {
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.accent.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(it.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
                }
            }
        }
    }
}

@Composable
private fun IconText(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =====================================================================
// Detail hodiny
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonSheet(l: Lesson, onDismiss: () -> Unit) {
    val c = subjectColors(l)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = c.container, contentColor = c.onContainer) {
                    Text(listOf(l.code, l.type).filter { it.isNotBlank() }.joinToString("  ·  "),
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(l.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            val whenText = (l.date?.let { "${dayNamesLong[l.dayOfWeek]} ${it.format(dayMonth)}" } ?: dayNamesLong[l.dayOfWeek]) +
                ", ${l.start.fmt()} – ${l.end.fmt()}"
            val weeks = when (l.parity) { WeekParity.EVEN -> "Jen sudé týdny"; WeekParity.ODD -> "Jen liché týdny"; else -> "Každý týden" }
            DetailRow(Icons.Filled.DateRange, whenText, weeks)
            DetailRow(Icons.Filled.Place, l.room, "Místnost")
            DetailRow(Icons.Filled.Person, l.teacher, "Vyučující")
            val extra = listOf(l.note, l.groups.takeIf { it.isNotBlank() }?.let { "Skupiny: $it" }.orEmpty())
                .filter { it.isNotBlank() }.joinToString("\n")
            DetailRow(Icons.Filled.Info, extra, "Poznámka")
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, value: String, label: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =====================================================================
// Nastavení
// =====================================================================

@Composable
private fun SettingsDialog(state: UiState, vm: RozvrhViewModel, requestNotifications: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Hotovo") } },
        title = { Text("Nastavení", style = MaterialTheme.typography.titleLarge) },
        text = {
            val context = LocalContext.current
            Column {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .clickable { vm.setHideEmptyDays(!state.hideEmptyDays) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Skrýt dny bez výuky", style = MaterialTheme.typography.titleSmall)
                        Text("Zobrazí jen dny, kdy máš nějakou hodinu.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = state.hideEmptyDays, onCheckedChange = vm::setHideEmptyDays)
                }
                val setCountdown = { on: Boolean ->
                    vm.setLessonCountdown(on)
                    if (on) requestNotifications()
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .clickable { setCountdown(!state.lessonCountdown) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Odpočet v notifikacích", style = MaterialTheme.typography.titleSmall)
                        Text("Během hodiny ukazuje nahoře, kolik zbývá do konce.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = state.lessonCountdown, onCheckedChange = setCountdown)
                }
                Spacer(Modifier.height(18.dp))
                Text("Widgety na plochu", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                WidgetPinRow("Rozvrh na den", "Zbývající hodiny dnes / zítra") {
                    pinWidget(context, AgendaWidgetReceiver::class.java)
                }
                Spacer(Modifier.height(8.dp))
                WidgetPinRow("Další hodina", "Co teď probíhá a co bude dál") {
                    pinWidget(context, NextLessonWidgetReceiver::class.java)
                }
            }
        },
    )
}

@Composable
private fun WidgetPinRow(title: String, subtitle: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAdd) { Text("Přidat") }
    }
}

/** Požádá launcher o připnutí widgetu na plochu (Android sám ukáže potvrzení). */
private fun pinWidget(context: android.content.Context, receiver: Class<*>) {
    val mgr = android.appwidget.AppWidgetManager.getInstance(context)
    if (mgr.isRequestPinAppWidgetSupported) {
        mgr.requestPinAppWidget(android.content.ComponentName(context, receiver), null, null)
    } else {
        android.widget.Toast.makeText(context, "Přidej widget ručně: podrž prst na ploše → Widgety → Rozvrh ČZU",
            android.widget.Toast.LENGTH_LONG).show()
    }
}
