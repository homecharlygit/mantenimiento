package dev.charly.mantenimiento.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dev.charly.mantenimiento.MainActivity
import dev.charly.mantenimiento.R
import dev.charly.mantenimiento.data.Prefs
import dev.charly.mantenimiento.data.ScheduledMaintenance
import dev.charly.mantenimiento.data.Store
import dev.charly.mantenimiento.data.Vehicle
import dev.charly.mantenimiento.data.currentKm
import dev.charly.mantenimiento.data.dueStatus
import dev.charly.mantenimiento.data.startOfDay
import dev.charly.mantenimiento.ui.formatKm

object Notifier {
    const val CH_DAILY = "daily_question"
    const val CH_MAINT = "maintenance_due"

    const val TAG_DAILY = "daily"
    const val TAG_SCHED = "sched"

    const val EXTRA_OPEN = "open"
    const val EXTRA_VEHICLE = "vehicleId"
    const val EXTRA_SCHEDULED = "scheduledId"
    const val OPEN_MAINTENANCE = "maintenance"
    const val OPEN_SCHEDULED = "scheduled"

    const val KEY_REPLY = "what"

    fun createChannels(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_DAILY, "Pregunta diaria", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Te pregunta a la hora que elijas qué mantenimiento le hiciste a tu vehículo"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MAINT, "Mantenimiento programado", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos cuando un mantenimiento está por vencer o ya venció"
            }
        )
    }

    fun canPost(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(c).areNotificationsEnabled()
    }

    fun idFor(id: Long): Int = (id xor (id ushr 32)).toInt()

    private fun openApp(c: Context, open: String, vehicleId: Long, scheduledId: Long? = null): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, open)
            putExtra(EXTRA_VEHICLE, vehicleId)
            scheduledId?.let { putExtra(EXTRA_SCHEDULED, it) }
        }
        return PendingIntent.getActivity(
            c, idFor(vehicleId) xor open.hashCode() xor (scheduledId?.let(::idFor) ?: 0), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(c: Context, action: String, code: Int, mutable: Boolean, extras: Intent.() -> Unit): PendingIntent {
        val i = Intent(c, NotificationActionReceiver::class.java).setAction(action).apply(extras)
        val flag = if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or flag)
    }

    /** "¿Qué mantenimiento le hiciste hoy a …?" con respuesta directa desde la notificación. */
    fun postDailyQuestion(c: Context, v: Vehicle) {
        if (!canPost(c)) return
        val id = idFor(v.id)
        val reply = RemoteInput.Builder(KEY_REPLY).setLabel("Ej. cambio de aceite, lavado…").build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_build, "Responder",
            action(c, NotificationActionReceiver.ACTION_REPLY, id, mutable = true) {
                putExtra(EXTRA_VEHICLE, v.id)
            },
        ).addRemoteInput(reply).setAllowGeneratedReplies(false).build()
        val nothing = NotificationCompat.Action.Builder(
            R.drawable.ic_close, "Nada hoy",
            action(c, NotificationActionReceiver.ACTION_NOTHING, id + 1, mutable = false) {
                putExtra(EXTRA_VEHICLE, v.id)
            },
        ).build()

        val n = NotificationCompat.Builder(c, CH_DAILY)
            .setSmallIcon(R.drawable.ic_build)
            .setContentTitle("¿Le hiciste algo a ${v.name} hoy?")
            .setContentText("Registra el mantenimiento de hoy o toca \"Nada hoy\".")
            .setContentIntent(openApp(c, OPEN_MAINTENANCE, v.id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(replyAction)
            .addAction(nothing)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(TAG_DAILY, id, n) }
    }

    /** Reemplaza la pregunta por una confirmación que se quita sola. */
    fun postSaved(c: Context, vehicleId: Long, text: String) {
        if (!canPost(c)) return
        val n = NotificationCompat.Builder(c, CH_DAILY)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("Guardado")
            .setContentText(text)
            .setTimeoutAfter(4000)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(TAG_DAILY, idFor(vehicleId), n) }
    }

    fun cancelDaily(c: Context, vehicleId: Long) =
        NotificationManagerCompat.from(c).cancel(TAG_DAILY, idFor(vehicleId))

    fun cancelScheduled(c: Context, scheduledId: Long) =
        NotificationManagerCompat.from(c).cancel(TAG_SCHED, idFor(scheduledId))

    private fun postScheduled(c: Context, v: Vehicle, s: ScheduledMaintenance, title: String, text: String) {
        if (!canPost(c)) return
        val id = idFor(s.id)
        val done = NotificationCompat.Action.Builder(
            R.drawable.ic_check, "Ya lo hice",
            action(c, NotificationActionReceiver.ACTION_DONE, id, mutable = false) {
                putExtra(EXTRA_SCHEDULED, s.id)
            },
        ).build()
        val n = NotificationCompat.Builder(c, CH_MAINT)
            .setSmallIcon(R.drawable.ic_event)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(c, OPEN_SCHEDULED, v.id, s.id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(done)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(TAG_SCHED, id, n) }
    }

    /**
     * Revisa los mantenimientos programados y avisa una vez cuando están por vencer y otra
     * cuando vencen. Se llama desde el worker diario y al registrar una carga o un cambio.
     */
    fun checkScheduled(c: Context) {
        val store = Store.get(c)
        val settings = Prefs.load(c)
        val d = store.data
        val now = System.currentTimeMillis()
        for (s in d.scheduled) {
            val v = d.vehicles.firstOrNull { it.id == s.vehicleId } ?: continue
            val km = currentKm(d, v.id)
            val st = dueStatus(s, km, now, settings.soonDays, settings.soonKm.toDouble())
            val dueToday = st.daysLeft != null && st.daysLeft <= 0
            val reached = st.overdue || dueToday
            val detail = buildList {
                st.daysLeft?.let {
                    add(
                        when {
                            it < 0 -> "venció hace ${-it} ${if (it == -1L) "día" else "días"}"
                            it == 0L -> "vence hoy"
                            else -> "faltan $it ${if (it == 1L) "día" else "días"}"
                        }
                    )
                }
                st.kmLeft?.let {
                    add(if (it <= 0) "te pasaste ${formatKm(-it)}" else "faltan ${formatKm(it)}")
                }
            }.joinToString(" · ")

            if (reached && !s.warnedDue) {
                postScheduled(c, v, s, "${s.type} — ${v.name}", "Ya toca: $detail.")
                store.upsertScheduled(s.copy(warnedDue = true, warnedSoon = true))
            } else if (st.soon && !s.warnedSoon) {
                postScheduled(c, v, s, "Se acerca: ${s.type}", "${v.name}: $detail.")
                store.upsertScheduled(s.copy(warnedSoon = true))
            }
        }
    }

    /** Lo que hace el worker diario a la hora configurada. */
    fun runDaily(c: Context) {
        val settings = Prefs.load(c)
        val d = Store.get(c).data
        if (settings.reminderEnabled) {
            val today = startOfDay(System.currentTimeMillis())
            for (v in d.vehicles) {
                // Si ya registraste algo hoy para ese vehículo no se pregunta
                val already = d.maintenance.any { it.vehicleId == v.id && startOfDay(it.date) == today }
                if (!already) postDailyQuestion(c, v)
            }
        }
        checkScheduled(c)
    }
}
