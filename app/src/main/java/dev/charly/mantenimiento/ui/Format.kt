package dev.charly.mantenimiento.ui

import dev.charly.mantenimiento.data.EffUnit
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val MX: Locale = Locale.forLanguageTag("es-MX")

fun formatNum(v: Double, decimals: Int = 1): String =
    NumberFormat.getNumberInstance(MX).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(v)

fun formatKm(v: Double): String = "${formatNum(v, 0)} km"

fun formatMoney(v: Double): String =
    NumberFormat.getCurrencyInstance(MX).apply { currency = java.util.Currency.getInstance("MXN") }.format(v)

fun formatDate(ms: Long): String = SimpleDateFormat("d MMM yyyy", MX).format(Date(ms))
fun formatShortDate(ms: Long): String = SimpleDateFormat("d MMM", MX).format(Date(ms))
fun formatTime(hour: Int, minute: Int): String = "%d:%02d".format(hour, minute)

/** Convierte km/L a la unidad elegida. */
fun EffUnit.value(kmPerLiter: Double): Double = when (this) {
    EffUnit.KM_PER_L -> kmPerLiter
    EffUnit.L_PER_100 -> if (kmPerLiter > 0) 100.0 / kmPerLiter else 0.0
}

val EffUnit.label: String get() = when (this) {
    EffUnit.KM_PER_L -> "km/L"
    EffUnit.L_PER_100 -> "L/100 km"
}

/** En L/100 km menos es mejor. */
val EffUnit.higherIsBetter: Boolean get() = this == EffUnit.KM_PER_L

fun EffUnit.format(kmPerLiter: Double): String = "${formatNum(value(kmPerLiter))} $label"

/** Lee números escritos con coma o punto, y con separadores de miles. */
fun parseNum(s: String): Double? {
    val t = s.trim().replace(" ", "").replace("$", "")
    if (t.isEmpty()) return null
    val normalized = if (t.count { it == ',' } == 1 && !t.contains('.')) t.replace(',', '.') else t.replace(",", "")
    return normalized.toDoubleOrNull()
}
