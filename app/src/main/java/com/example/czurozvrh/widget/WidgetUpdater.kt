package com.example.czurozvrh.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.czurozvrh.data.TimetableRepository
import com.example.czurozvrh.data.nextBoundary
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object WidgetUpdater {
    private const val PERIODIC = "widget-periodic"
    private const val TICK = "widget-tick"
    private val REFETCH_AFTER: Duration = Duration.ofHours(6)

    /** Překreslí všechny widgety a naplánuje další překreslení na začátek/konec nejbližší hodiny. */
    suspend fun updateAll(context: Context) {
        AgendaWidget().updateAll(context)
        NextLessonWidget().updateAll(context)
        scheduleNextTick(context)
    }

    /** Záložní obnova každých 30 min (přežije i restart telefonu). */
    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetWorker>(30, TimeUnit.MINUTES).build(),
        )
    }

    private fun scheduleNextTick(context: Context) {
        val tt = TimetableRepository.get(context).cache.load() ?: return
        val now = LocalDateTime.now()
        val next = tt.nextBoundary(now) ?: now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
        val delay = Duration.between(now, next).plusSeconds(5)
        WorkManager.getInstance(context).enqueueUniqueWork(
            TICK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("tick" to true))
                .build(),
        )
    }

    internal suspend fun maybeRefetch(context: Context) {
        val repo = TimetableRepository.get(context)
        if (repo.store.credentials == null) return
        val tt = repo.cache.load()
        val age = Duration.ofMillis(System.currentTimeMillis() - (tt?.fetchedAt ?: 0L))
        if (tt == null || age > REFETCH_AFTER) runCatching { repo.fetch() }
    }
}

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!inputData.getBoolean("tick", false)) WidgetUpdater.maybeRefetch(applicationContext)
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }
}
