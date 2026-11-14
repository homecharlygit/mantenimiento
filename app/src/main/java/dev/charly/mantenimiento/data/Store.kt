package dev.charly.mantenimiento.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Todos los datos de la app en un JSON (filesDir/data.json). Es un singleton para que la UI,
 * los workers y el receptor de notificaciones vean y modifiquen lo mismo.
 * [data] es estado de Compose, así que la UI se actualiza sola.
 */
class Store private constructor(context: Context) {

    private val file = File(context.filesDir, "data.json")
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()

    var data by mutableStateOf(load())
        private set

    private fun load(): AppData = runCatching {
        if (file.exists()) appDataFromJson(JSONObject(file.readText())) else AppData()
    }.getOrElse { AppData() }

    /** Aplica un cambio y lo guarda en segundo plano (escritura atómica). */
    fun update(change: (AppData) -> AppData) {
        val snapshot = synchronized(lock) {
            data = change(data)
            data
        }
        io.execute {
            val tmp = File(file.parentFile, "data.json.tmp")
            tmp.writeText(snapshot.toJson().toString())
            tmp.renameTo(file)
        }
    }

    fun newId(): Long = System.currentTimeMillis() * 10 + (0..9).random()

    // ---------------- Atajos ----------------

    fun upsertVehicle(v: Vehicle) = update { d ->
        d.copy(vehicles = d.vehicles.filterNot { it.id == v.id } + v)
    }

    fun deleteVehicle(id: Long) = update { d ->
        d.copy(
            vehicles = d.vehicles.filterNot { it.id == id },
            fuel = d.fuel.filterNot { it.vehicleId == id },
            maintenance = d.maintenance.filterNot { it.vehicleId == id },
            scheduled = d.scheduled.filterNot { it.vehicleId == id },
        )
    }

    fun upsertFuel(e: FuelEntry) = update { d -> d.copy(fuel = d.fuel.filterNot { it.id == e.id } + e) }
    fun deleteFuel(id: Long) = update { d -> d.copy(fuel = d.fuel.filterNot { it.id == id }) }

    fun upsertMaintenance(e: MaintenanceEntry) =
        update { d -> d.copy(maintenance = d.maintenance.filterNot { it.id == e.id } + e) }
    fun deleteMaintenance(id: Long) = update { d -> d.copy(maintenance = d.maintenance.filterNot { it.id == id }) }

    fun upsertScheduled(s: ScheduledMaintenance) =
        update { d -> d.copy(scheduled = d.scheduled.filterNot { it.id == s.id } + s) }
    fun deleteScheduled(id: Long) = update { d -> d.copy(scheduled = d.scheduled.filterNot { it.id == id }) }

    /**
     * Marca un programado como hecho: crea el registro de mantenimiento y, si se repite,
     * lo vuelve a programar desde hoy / desde el kilometraje actual. Si no, lo quita.
     */
    fun completeScheduled(s: ScheduledMaintenance, odometer: Double?, cost: Double?, date: Long) = update { d ->
        val done = MaintenanceEntry(
            id = newId(), vehicleId = s.vehicleId, date = date, type = s.type,
            odometer = odometer, cost = cost, notes = s.notes,
        )
        val next = if (s.repeatDays != null || s.repeatKm != null) {
            s.copy(
                dueDate = s.repeatDays?.let { date + it * DAY_MS },
                dueKm = s.repeatKm?.let { r -> (odometer ?: currentKm(d, s.vehicleId)) + r },
                createdAt = date,
                createdKm = odometer ?: currentKm(d, s.vehicleId),
                warnedSoon = false,
                warnedDue = false,
            )
        } else null
        d.copy(
            maintenance = d.maintenance + done,
            scheduled = d.scheduled.filterNot { it.id == s.id } + listOfNotNull(next),
        )
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        @Volatile private var instance: Store? = null
        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}

/** Kilometraje más alto conocido del vehículo (cargas, mantenimientos o el inicial). */
fun currentKm(d: AppData, vehicleId: Long): Double {
    val v = d.vehicles.firstOrNull { it.id == vehicleId }
    val fuelMax = d.fuel.filter { it.vehicleId == vehicleId }.maxOfOrNull { it.odometer }
    val mMax = d.maintenance.filter { it.vehicleId == vehicleId }.mapNotNull { it.odometer }.maxOrNull()
    return listOfNotNull(v?.initialKm, fuelMax, mMax).maxOrNull() ?: 0.0
}
