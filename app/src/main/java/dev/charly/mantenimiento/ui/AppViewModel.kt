package dev.charly.mantenimiento.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import dev.charly.mantenimiento.data.AppSettings
import dev.charly.mantenimiento.data.FuelEntry
import dev.charly.mantenimiento.data.MaintenanceEntry
import dev.charly.mantenimiento.data.Prefs
import dev.charly.mantenimiento.data.ScheduledMaintenance
import dev.charly.mantenimiento.data.Store
import dev.charly.mantenimiento.data.Vehicle
import dev.charly.mantenimiento.notify.Notifier
import dev.charly.mantenimiento.notify.Reminders

/** Lo que pidió abrir una notificación (se consume una vez). */
data class PendingOpen(val what: String, val vehicleId: Long, val scheduledId: Long?)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    val store = Store.get(ctx)

    var settings by mutableStateOf(Prefs.load(ctx))
        private set

    var pendingOpen by mutableStateOf<PendingOpen?>(null)

    val data get() = store.data

    /** Vehículo elegido; si no hay ninguno guardado se toma el primero. */
    val activeVehicle: Vehicle?
        get() = data.vehicles.firstOrNull { it.id == settings.activeVehicle } ?: data.vehicles.firstOrNull()

    init {
        Notifier.createChannels(ctx)
        Reminders.schedule(ctx)
    }

    private fun edit(change: (AppSettings) -> AppSettings) {
        settings = change(settings)
        Prefs.save(ctx, settings)
    }

    fun setPalette(i: Int) = edit { it.copy(palette = i) }
    fun setDark(on: Boolean) = edit { it.copy(dark = on) }
    fun setBlur(t: BlurType) = edit { it.copy(blur = t) }
    fun setHaptics(on: Boolean) = edit { it.copy(haptics = on) }
    fun setHapticStrength(v: Float) = edit { it.copy(hapticStrength = v) }
    fun setUnit(u: dev.charly.mantenimiento.data.EffUnit) = edit { it.copy(unit = u) }
    fun setSoonDays(d: Int) = edit { it.copy(soonDays = d) }
    fun setSoonKm(k: Int) = edit { it.copy(soonKm = k) }
    fun selectVehicle(id: Long) = edit { it.copy(activeVehicle = id) }

    fun setReminderEnabled(on: Boolean) {
        edit { it.copy(reminderEnabled = on) }
        Reminders.schedule(ctx)
    }

    fun setReminderTime(hour: Int, minute: Int) {
        edit { it.copy(reminderHour = hour, reminderMinute = minute) }
        Reminders.schedule(ctx)
    }

    /** Manda la pregunta diaria ahora mismo para probar que llegan las notificaciones. */
    fun testReminder() {
        activeVehicle?.let { Notifier.postDailyQuestion(ctx, it) }
    }

    // ---------------- Datos ----------------

    fun saveVehicle(v: Vehicle) {
        store.upsertVehicle(v)
        if (settings.activeVehicle == null || data.vehicles.size == 1) selectVehicle(v.id)
    }

    fun deleteVehicle(id: Long) {
        store.deleteVehicle(id)
        if (settings.activeVehicle == id) edit { it.copy(activeVehicle = data.vehicles.firstOrNull()?.id) }
    }

    fun saveFuel(e: FuelEntry) {
        store.upsertFuel(e)
        Notifier.checkScheduled(ctx)
    }

    fun deleteFuel(id: Long) = store.deleteFuel(id)

    fun saveMaintenance(e: MaintenanceEntry) {
        store.upsertMaintenance(e)
        Notifier.cancelDaily(ctx, e.vehicleId)
        Notifier.checkScheduled(ctx)
    }

    fun deleteMaintenance(id: Long) = store.deleteMaintenance(id)

    fun saveScheduled(s: ScheduledMaintenance) {
        store.upsertScheduled(s)
        Notifier.checkScheduled(ctx)
    }

    fun deleteScheduled(id: Long) {
        store.deleteScheduled(id)
        Notifier.cancelScheduled(ctx, id)
    }

    fun completeScheduled(s: ScheduledMaintenance, odometer: Double?, cost: Double?, date: Long) {
        store.completeScheduled(s, odometer, cost, date)
        Notifier.cancelScheduled(ctx, s.id)
        Notifier.checkScheduled(ctx)
    }
}
