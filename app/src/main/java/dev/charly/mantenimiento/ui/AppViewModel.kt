package dev.charly.mantenimiento.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.charly.mantenimiento.data.UpdateInfo
import dev.charly.mantenimiento.data.UpdateRepository
import kotlinx.coroutines.launch
import java.io.File
import dev.charly.mantenimiento.data.AppSettings
import dev.charly.mantenimiento.data.FuelEntry
import dev.charly.mantenimiento.data.MaintenanceEntry
import dev.charly.mantenimiento.data.Prefs
import dev.charly.mantenimiento.data.ScheduledMaintenance
import dev.charly.mantenimiento.data.Store
import dev.charly.mantenimiento.data.Vehicle
import dev.charly.mantenimiento.notify.Notifier
import dev.charly.mantenimiento.notify.Reminders

data class UpdateUi(
    val info: UpdateInfo? = null,
    val checking: Boolean = false,
    /** null = sin descarga, 0..1 progreso, -1 tamaño desconocido */
    val progress: Float? = null,
    val file: File? = null,
    val error: Boolean = false,
    val upToDate: Boolean = false,
    val dismissed: Boolean = false,
    val needsPermission: Boolean = false,
)

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

    val updates = UpdateRepository(ctx)

    var updateUi by mutableStateOf(UpdateUi())
        private set

    init {
        Notifier.createChannels(ctx)
        Reminders.schedule(ctx)
        // Busca actualizaciones solo si pasaron más de 6 horas desde la última vez
        if (System.currentTimeMillis() - updates.lastCheckMillis() > 6 * 60 * 60 * 1000L) checkForUpdates()
    }

    // ---------------- Actualizaciones ----------------

    fun checkForUpdates() {
        if (updateUi.checking) return
        updateUi = updateUi.copy(checking = true, error = false, upToDate = false)
        viewModelScope.launch {
            updateUi = try {
                val info = updates.check()
                updateUi.copy(info = info, checking = false, upToDate = info == null, dismissed = false)
            } catch (e: Exception) {
                updateUi.copy(checking = false, error = true)
            }
        }
    }

    fun dismissUpdate() {
        updateUi = updateUi.copy(dismissed = true)
    }

    /** Descarga el APK y al terminar abre el instalador. */
    fun downloadAndInstall() {
        val info = updateUi.info ?: return
        updateUi.file?.let { if (it.exists()) { install(it); return } }
        if (updateUi.progress != null) return
        updateUi = updateUi.copy(progress = 0f, error = false)
        viewModelScope.launch {
            try {
                val file = updates.download(info) { p -> updateUi = updateUi.copy(progress = p) }
                updateUi = updateUi.copy(progress = null, file = file)
                install(file)
            } catch (e: Exception) {
                updateUi = updateUi.copy(progress = null, error = true)
            }
        }
    }

    private fun install(file: File) {
        if (!updates.canInstall()) {
            updateUi = updateUi.copy(needsPermission = true)
            updates.openInstallPermissionSettings()
        } else {
            updateUi = updateUi.copy(needsPermission = false)
            updates.install(file)
        }
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
