package dev.charly.mantenimiento.data

/** Rendimiento calculado para una carga (solo las de tanque lleno con una carga llena anterior). */
data class Efficiency(
    val entryId: Long,
    val date: Long,
    val distance: Double,
    val liters: Double,
    val kmPerLiter: Double,
)

data class VehicleStats(
    val efficiencies: List<Efficiency>,
    val last: Efficiency?,
    val average: Double?,
    val best: Double?,
    val worst: Double?,
    val totalSpent: Double,
    val totalLiters: Double,
    val trackedDistance: Double,
    val costPerKm: Double?,
    val currentKm: Double,
    val maintenanceSpent: Double,
)

/**
 * Cálculo "lleno a lleno": la distancia entre dos cargas de tanque lleno dividida entre
 * todos los litros que entraron desde la primera (sin contarla). Las cargas parciales se
 * acumulan hasta la siguiente llena, así el resultado es correcto aunque no llenes siempre.
 */
fun efficiencies(entries: List<FuelEntry>): List<Efficiency> {
    val sorted = entries.sortedWith(compareBy({ it.odometer }, { it.date }))
    val out = mutableListOf<Efficiency>()
    var lastFullKm: Double? = null
    var liters = 0.0
    for (e in sorted) {
        if (lastFullKm == null) {
            if (e.fullTank) lastFullKm = e.odometer
            continue
        }
        liters += e.liters
        if (e.fullTank) {
            val dist = e.odometer - lastFullKm
            if (dist > 0 && liters > 0) out += Efficiency(e.id, e.date, dist, liters, dist / liters)
            lastFullKm = e.odometer
            liters = 0.0
        }
    }
    return out
}

/**
 * Rendimiento que daría una carga nueva (para la vista previa del formulario), tomando en
 * cuenta las cargas parciales desde la última llena.
 */
fun previewEfficiency(existing: List<FuelEntry>, odometer: Double, liters: Double, fullTank: Boolean, editingId: Long?): Double? {
    if (!fullTank || liters <= 0) return null
    val probe = FuelEntry(id = -1, vehicleId = 0, date = Long.MAX_VALUE, odometer = odometer, liters = liters, fullTank = true)
    return efficiencies(existing.filterNot { it.id == editingId } + probe).firstOrNull { it.entryId == -1L }?.kmPerLiter
}

fun statsFor(d: AppData, vehicleId: Long): VehicleStats {
    val fuel = d.fuel.filter { it.vehicleId == vehicleId }
    val eff = efficiencies(fuel)
    val dist = eff.sumOf { it.distance }
    val lit = eff.sumOf { it.liters }
    val spent = fuel.sumOf { it.total ?: 0.0 }
    // Costo por km: solo con las cargas que tienen precio y entran en el cálculo
    val pricedIds = fuel.filter { it.total != null }.map { it.id }.toSet()
    val pricedEff = eff.filter { it.entryId in pricedIds }
    val avgPrice = fuel.mapNotNull { it.pricePerLiter }.takeIf { it.isNotEmpty() }?.average()
    val costPerKm = if (pricedEff.isNotEmpty() && avgPrice != null && dist > 0) avgPrice * lit / dist else null
    return VehicleStats(
        efficiencies = eff.sortedBy { it.date },
        last = eff.maxByOrNull { it.date },
        average = if (lit > 0) dist / lit else null,
        best = eff.maxOfOrNull { it.kmPerLiter },
        worst = eff.minOfOrNull { it.kmPerLiter },
        totalSpent = spent,
        totalLiters = fuel.sumOf { it.liters },
        trackedDistance = dist,
        costPerKm = costPerKm,
        currentKm = currentKm(d, vehicleId),
        maintenanceSpent = d.maintenance.filter { it.vehicleId == vehicleId }.sumOf { it.cost ?: 0.0 },
    )
}

/** Kilometraje anterior y siguiente a una fecha, para validar el odómetro al capturar. */
fun neighborsKm(fuel: List<FuelEntry>, date: Long, editingId: Long?): Pair<FuelEntry?, FuelEntry?> {
    val others = fuel.filterNot { it.id == editingId }.sortedBy { it.date }
    return others.lastOrNull { it.date <= date } to others.firstOrNull { it.date > date }
}

/** Estado de un mantenimiento programado. */
data class DueStatus(
    val daysLeft: Long?,
    val kmLeft: Double?,
    /** 0..1 — qué tanto se ha consumido del intervalo (lo que esté más cerca de vencer). */
    val progress: Float,
    val overdue: Boolean,
    val soon: Boolean,
)

fun dueStatus(s: ScheduledMaintenance, currentKm: Double, now: Long, soonDays: Int, soonKm: Double): DueStatus {
    val daysLeft = s.dueDate?.let { Math.floorDiv(startOfDay(it) - startOfDay(now), Store.DAY_MS) }
    val kmLeft = s.dueKm?.let { it - currentKm }
    val pDate = s.dueDate?.let { due ->
        val span = (due - s.createdAt).coerceAtLeast(1)
        ((now - s.createdAt).toFloat() / span).coerceIn(0f, 1f)
    }
    val pKm = s.dueKm?.let { due ->
        val from = s.createdKm ?: currentKm
        val span = (due - from).coerceAtLeast(1.0)
        ((currentKm - from) / span).toFloat().coerceIn(0f, 1f)
    }
    val overdue = (daysLeft != null && daysLeft < 0) || (kmLeft != null && kmLeft <= 0)
    val soon = !overdue && ((daysLeft != null && daysLeft <= soonDays) || (kmLeft != null && kmLeft <= soonKm))
    return DueStatus(daysLeft, kmLeft, listOfNotNull(pDate, pKm).maxOrNull() ?: 0f, overdue, soon)
}

fun startOfDay(ms: Long): Long {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = ms
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
