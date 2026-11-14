package dev.charly.mantenimiento.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dev.charly.mantenimiento.data.MaintenanceEntry
import dev.charly.mantenimiento.data.Store
import dev.charly.mantenimiento.data.currentKm

/** Botones de las notificaciones: responder la pregunta diaria, "Nada hoy" y "Ya lo hice". */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        when (intent.action) {
            ACTION_REPLY -> {
                val vehicleId = intent.getLongExtra(Notifier.EXTRA_VEHICLE, -1)
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim().orEmpty()
                if (vehicleId == -1L || text.isEmpty()) {
                    Notifier.cancelDaily(context, vehicleId)
                    return
                }
                store.upsertMaintenance(
                    MaintenanceEntry(
                        id = store.newId(),
                        vehicleId = vehicleId,
                        date = System.currentTimeMillis(),
                        type = text.replaceFirstChar { it.uppercase() },
                    )
                )
                Notifier.postSaved(context, vehicleId, text)
            }

            ACTION_NOTHING -> Notifier.cancelDaily(context, intent.getLongExtra(Notifier.EXTRA_VEHICLE, -1))

            ACTION_DONE -> {
                val id = intent.getLongExtra(Notifier.EXTRA_SCHEDULED, -1)
                val s = store.data.scheduled.firstOrNull { it.id == id }
                if (s != null) {
                    store.completeScheduled(
                        s, odometer = currentKm(store.data, s.vehicleId), cost = null,
                        date = System.currentTimeMillis(),
                    )
                }
                Notifier.cancelScheduled(context, id)
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "dev.charly.mantenimiento.REPLY"
        const val ACTION_NOTHING = "dev.charly.mantenimiento.NOTHING"
        const val ACTION_DONE = "dev.charly.mantenimiento.DONE"
    }
}
