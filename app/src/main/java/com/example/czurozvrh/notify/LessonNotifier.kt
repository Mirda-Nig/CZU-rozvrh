package com.example.czurozvrh.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.czurozvrh.BuildConfig
import com.example.czurozvrh.MainActivity
import com.example.czurozvrh.R
import com.example.czurozvrh.data.DemoData
import com.example.czurozvrh.data.Timetable
import com.example.czurozvrh.data.TimetableRepository
import com.example.czurozvrh.data.lessonsOn
import com.example.czurozvrh.data.nextBoundary
import com.example.czurozvrh.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Průběžná notifikace s odpočtem do konce právě probíhající hodiny (systémový chronometr,
 * stejně jako u stopek/časovače). O přestávce mezi hodinami odpočítává do začátku další.
 * Na Androidu 16+ se zobrazí i jako čip ve stavovém řádku (Live Update).
 */
object LessonNotifier {
    private const val CHANNEL = "lesson-countdown"
    private const val NOTIFICATION_ID = 1001
    private const val PREF = "lessonCountdown"
    /** Zelená z loga ČZU (stejná jako podklad ikony aplikace). */
    private const val CZU_GREEN = 0xFF73BE46.toInt()

    /** Přestávka delší než tohle už se neodpočítává (to je spíš volno). */
    private val MAX_BREAK: Duration = Duration.ofMinutes(45)
    private val hm = DateTimeFormatter.ofPattern("H:mm")
    private fun LocalTime.fmt() = format(hm)

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(PREF, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF, enabled).apply()
        update(context)
    }

    fun canPost(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun loadTimetable(context: Context): Timetable? =
        if (BuildConfig.DEMO) DemoData.timetable()
        else TimetableRepository.get(context).cache.load()

    /** Ukáže / schová notifikaci podle aktuálního času a naplánuje další kontrolu na nejbližší začátek/konec hodiny. */
    fun update(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        val tt = loadTimetable(context)
        if (tt == null || !isEnabled(context) || !canPost(context)) {
            nm.cancel(NOTIFICATION_ID)
            cancelAlarm(context)
            return
        }
        ensureChannel(context)

        val now = LocalDateTime.now()
        val t = now.toLocalTime()
        val today = tt.lessonsOn(now.toLocalDate())
        val current = today.firstOrNull { !t.isBefore(it.start) && t.isBefore(it.end) }
        val next = today.firstOrNull { it.start.isAfter(t) }
        val hadLessonBefore = today.any { !it.end.isAfter(t) }

        val notification = when {
            current != null -> {
                val after = today.firstOrNull { !it.start.isBefore(current.end) }
                build(
                    context,
                    target = current.end,
                    title = current.name,
                    text = listOf(current.room, "do ${current.end.fmt()}").filter { it.isNotBlank() }.joinToString(" · "),
                    sub = after?.let { "Potom ${it.start.fmt()} ${it.shortName}" },
                )
            }
            next != null && hadLessonBefore && Duration.between(t, next.start) <= MAX_BREAK ->
                build(
                    context,
                    target = next.start,
                    title = "Přestávka · další ${next.shortName}",
                    text = listOf(next.room, "od ${next.start.fmt()}").filter { it.isNotBlank() }.joinToString(" · "),
                    sub = next.name,
                )
            else -> null
        }

        @Suppress("MissingPermission") // ověřeno v canPost()
        if (notification != null) nm.notify(NOTIFICATION_ID, notification) else nm.cancel(NOTIFICATION_ID)

        scheduleAlarm(context, tt.nextBoundary(now))
    }

    private fun build(
        context: Context,
        target: LocalTime,
        title: String,
        text: String,
        sub: String?,
    ): android.app.Notification {
        val now = LocalDateTime.now()
        val targetAt = now.toLocalDate().atTime(target)
        val targetMillis = targetAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lesson)
            .setColor(CZU_GREEN)
            .setContentTitle(title)
            .setContentText(text)
            .apply { if (sub != null) setSubText(sub) }
            .setWhen(targetMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            // Sama zmizí přesně v okamžiku konce, i kdyby se budík opozdil.
            .setTimeoutAfter(Duration.between(now, targetAt).toMillis().coerceAtLeast(1000))
            .setRequestPromotedOngoing(true)
            .build()
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Odpočet hodiny", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Kolik zbývá do konce právě probíhající hodiny"
                setShowBadge(false)
            }
        )
    }

    // ---------- budík na další začátek/konec hodiny ----------

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, LessonAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun cancelAlarm(context: Context) =
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))

    private fun scheduleAlarm(context: Context, at: LocalDateTime?) {
        val am = context.getSystemService(AlarmManager::class.java)
        // Dnes už nic? Zkontroluj zase po půlnoci.
        val target = at ?: LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
        val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1000
        val pi = alarmIntent(context)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }
}

/** Budík na začátek/konec hodiny + obnova po restartu telefonu nebo změně času. */
class LessonAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LessonNotifier.update(context)
        // Přesně na hranici hodiny překresli i widgety (jejich odpočet jinak doběhne do mínusu).
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { WidgetUpdater.updateAll(context) } finally { pending.finish() }
        }
    }
}
