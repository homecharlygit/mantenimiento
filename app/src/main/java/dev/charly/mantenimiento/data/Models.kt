package dev.charly.mantenimiento.data

import org.json.JSONArray
import org.json.JSONObject

data class Vehicle(
    val id: Long,
    val name: String,
    val model: String = "",
    val year: String = "",
    val plate: String = "",
    val initialKm: Double = 0.0,
    val colorIndex: Int = 0,
    /** Archivo en filesDir/vehicle_images, o null para usar el color con el ícono. */
    val image: String? = null,
    /** true = la imagen completa dentro del círculo (logos); false = recortada para llenarlo (fotos). */
    val imageFit: Boolean = true,
)

/** Una carga de combustible. [odometer] es lo que marca el tablero al cargar. */
data class FuelEntry(
    val id: Long,
    val vehicleId: Long,
    val date: Long,
    val odometer: Double,
    val liters: Double,
    val total: Double? = null,
    val fullTank: Boolean = true,
    val notes: String = "",
) {
    val pricePerLiter: Double? get() = total?.takeIf { liters > 0 }?.div(liters)
}

/** Mantenimiento ya realizado. */
data class MaintenanceEntry(
    val id: Long,
    val vehicleId: Long,
    val date: Long,
    val type: String,
    val odometer: Double? = null,
    val cost: Double? = null,
    val notes: String = "",
)

/**
 * Mantenimiento programado: vence por fecha, por kilometraje o lo que llegue primero.
 * Si tiene repetición, al marcarlo como hecho se vuelve a programar solo.
 */
data class ScheduledMaintenance(
    val id: Long,
    val vehicleId: Long,
    val type: String,
    val dueDate: Long? = null,
    val dueKm: Double? = null,
    val repeatDays: Int? = null,
    val repeatKm: Double? = null,
    val notes: String = "",
    /** Desde cuándo se cuenta el avance (para la barra de progreso). */
    val createdAt: Long = System.currentTimeMillis(),
    val createdKm: Double? = null,
    /** Ya se avisó que está por vencer / que venció (para no repetir la notificación). */
    val warnedSoon: Boolean = false,
    val warnedDue: Boolean = false,
)

data class AppData(
    val vehicles: List<Vehicle> = emptyList(),
    val fuel: List<FuelEntry> = emptyList(),
    val maintenance: List<MaintenanceEntry> = emptyList(),
    val scheduled: List<ScheduledMaintenance> = emptyList(),
)

// ---------------- JSON ----------------

private fun JSONObject.optDoubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
private fun JSONObject.optIntOrNull(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
private fun JSONObject.putOpt2(k: String, v: Any?): JSONObject = if (v == null) this else put(k, v)

private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> runCatching { f(getJSONObject(i)) }.getOrNull() }
}

fun AppData.toJson(): JSONObject = JSONObject().apply {
    put("version", 1)
    put("vehicles", JSONArray(vehicles.map {
        JSONObject().put("id", it.id).put("name", it.name).put("model", it.model).put("year", it.year)
            .put("plate", it.plate).put("initialKm", it.initialKm).put("colorIndex", it.colorIndex)
            .putOpt2("image", it.image).put("imageFit", it.imageFit)
    }))
    put("fuel", JSONArray(fuel.map {
        JSONObject().put("id", it.id).put("vehicleId", it.vehicleId).put("date", it.date)
            .put("odometer", it.odometer).put("liters", it.liters).putOpt2("total", it.total)
            .put("fullTank", it.fullTank).put("notes", it.notes)
    }))
    put("maintenance", JSONArray(maintenance.map {
        JSONObject().put("id", it.id).put("vehicleId", it.vehicleId).put("date", it.date).put("type", it.type)
            .putOpt2("odometer", it.odometer).putOpt2("cost", it.cost).put("notes", it.notes)
    }))
    put("scheduled", JSONArray(scheduled.map {
        JSONObject().put("id", it.id).put("vehicleId", it.vehicleId).put("type", it.type)
            .putOpt2("dueDate", it.dueDate).putOpt2("dueKm", it.dueKm)
            .putOpt2("repeatDays", it.repeatDays).putOpt2("repeatKm", it.repeatKm)
            .put("notes", it.notes).put("createdAt", it.createdAt).putOpt2("createdKm", it.createdKm)
            .put("warnedSoon", it.warnedSoon).put("warnedDue", it.warnedDue)
    }))
}

fun appDataFromJson(o: JSONObject): AppData = AppData(
    vehicles = o.optJSONArray("vehicles").mapObjects {
        Vehicle(
            id = it.getLong("id"), name = it.optString("name"), model = it.optString("model"),
            year = it.optString("year"), plate = it.optString("plate"),
            initialKm = it.optDouble("initialKm", 0.0), colorIndex = it.optInt("colorIndex", 0),
            image = it.optString("image").takeIf { s -> s.isNotEmpty() }, imageFit = it.optBoolean("imageFit", true),
        )
    },
    fuel = o.optJSONArray("fuel").mapObjects {
        FuelEntry(
            id = it.getLong("id"), vehicleId = it.getLong("vehicleId"), date = it.getLong("date"),
            odometer = it.getDouble("odometer"), liters = it.getDouble("liters"),
            total = it.optDoubleOrNull("total"), fullTank = it.optBoolean("fullTank", true),
            notes = it.optString("notes"),
        )
    },
    maintenance = o.optJSONArray("maintenance").mapObjects {
        MaintenanceEntry(
            id = it.getLong("id"), vehicleId = it.getLong("vehicleId"), date = it.getLong("date"),
            type = it.optString("type"), odometer = it.optDoubleOrNull("odometer"),
            cost = it.optDoubleOrNull("cost"), notes = it.optString("notes"),
        )
    },
    scheduled = o.optJSONArray("scheduled").mapObjects {
        ScheduledMaintenance(
            id = it.getLong("id"), vehicleId = it.getLong("vehicleId"), type = it.optString("type"),
            dueDate = it.optLongOrNull("dueDate"), dueKm = it.optDoubleOrNull("dueKm"),
            repeatDays = it.optIntOrNull("repeatDays"), repeatKm = it.optDoubleOrNull("repeatKm"),
            notes = it.optString("notes"),
            createdAt = it.optLongOrNull("createdAt") ?: System.currentTimeMillis(),
            createdKm = it.optDoubleOrNull("createdKm"),
            warnedSoon = it.optBoolean("warnedSoon"), warnedDue = it.optBoolean("warnedDue"),
        )
    },
)
