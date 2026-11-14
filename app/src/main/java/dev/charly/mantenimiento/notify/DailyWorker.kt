package dev.charly.mantenimiento.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.charly.mantenimiento.data.Prefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Corre una vez al día a la hora que elegiste en Ajustes: hace la pregunta diaria y revisa
 * los mantenimientos programados. Al terminar se vuelve a programar para el día siguiente
 * (WorkManager sobrevive reinicios del teléfono).
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Notifier.runDaily(applicationContext) }
        Reminders.schedule(applicationContext)
        return Result.success()
    }
}

object Reminders {
    private const val WORK = "daily"

    /** Milisegundos hasta la próxima vez que toca (hoy si aún no pasa la hora, si no mañana). */
    fun delayToNext(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Un minuto de margen para no dispararse dos veces seguidas
        if (c.timeInMillis <= now + 60_000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis - now
    }

    fun schedule(c: Context) {
        val s = Prefs.load(c)
        val req = OneTimeWorkRequestBuilder<DailyWorker>()
            .setInitialDelay(delayToNext(s.reminderHour, s.reminderMinute), TimeUnit.MILLISECONDS)
            .addTag(WORK)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }
}
