package dev.charly.mantenimiento.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ToggleButton
import dev.charly.mantenimiento.data.VehicleImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.charly.mantenimiento.R
import dev.charly.mantenimiento.data.EffUnit
import dev.charly.mantenimiento.data.FuelEntry
import dev.charly.mantenimiento.data.MaintenanceEntry
import dev.charly.mantenimiento.data.ScheduledMaintenance
import dev.charly.mantenimiento.data.Store
import dev.charly.mantenimiento.data.Vehicle
import dev.charly.mantenimiento.data.neighborsKm
import dev.charly.mantenimiento.data.previewEfficiency
import dev.charly.mantenimiento.data.startOfDay
import dev.charly.mantenimiento.ui.theme.PALETTE_HUES
import dev.charly.mantenimiento.ui.theme.swatchColor
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

val MAINTENANCE_TYPES = listOf(
    "Cambio de aceite", "Filtro de aire", "Filtro de gasolina", "Rotación de llantas",
    "Alineación y balanceo", "Frenos", "Afinación", "Batería", "Anticongelante",
    "Verificación", "Lavado",
)

// ======================= Piezas comunes =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, content: @Composable (close: (() -> Unit) -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Cierra con la animación del sheet y luego avisa
    val close: (() -> Unit) -> Unit = { after ->
        scope.launch { state.hide() }.invokeOnCompletion { after(); onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            content(close)
        }
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    error: String? = null,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PlainField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Convierte una fecha local a la medianoche UTC que usa el DatePicker, y de regreso. */
private fun localToUtcMidnight(ms: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = ms }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcMidnightToLocal(utc: Long, keepTimeOf: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    val t = Calendar.getInstance().apply { timeInMillis = keepTimeOf }
    return Calendar.getInstance().apply {
        clear()
        set(
            u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH),
            t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE),
        )
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateChip(label: String, date: Long, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    AssistChip(
        onClick = { haptics.click(); open = true },
        label = { Text("$label: ${formatDate(date)}") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_event), null, Modifier.size(AssistChipDefaults.IconSize)) },
        shape = CircleShape,
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localToUtcMidnight(date))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(utcMidnightToLocal(it, date)) }
                    haptics.select()
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { haptics.toggle(!checked); onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = { haptics.toggle(it); onChange(it) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypePicker(type: String, onType: (String) -> Unit) {
    val haptics = LocalHaptics.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MAINTENANCE_TYPES.forEach { t ->
            FilterChip(
                selected = type == t,
                onClick = { haptics.select(); onType(t) },
                label = { Text(t) },
                leadingIcon = if (type == t) {
                    { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    PlainField("Tipo de mantenimiento", type, onType)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SaveRow(enabled: Boolean, onSave: () -> Unit, onDelete: (() -> Unit)? = null) {
    val haptics = LocalHaptics.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        if (onDelete != null) {
            OutlinedButton(
                onClick = { haptics.error(); onDelete() },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Eliminar")
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { haptics.success(); onSave() },
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.height(56.dp),
        ) {
            Icon(painterResource(R.drawable.ic_check), null)
            Spacer(Modifier.width(8.dp))
            Text("Guardar", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ConfirmDelete(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_delete), null) },
        title = { Text("¿Eliminar $what?") },
        text = { Text("Esto no se puede deshacer.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Tarjeta de vista previa con el rendimiento que va a quedar. */
@Composable
private fun PreviewCard(distance: Double?, eff: Double?, price: Double?, unit: EffUnit, prevEff: Double?) {
    val colors = MaterialTheme.colorScheme
    val shown = animatedNumber(eff?.let { unit.value(it) } ?: 0.0)
    val visible = eff != null || distance != null
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(Motion.bouncy()) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rendimiento", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (eff != null) "${formatNum(shown)} ${unit.label}" else "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                    val parts = buildList {
                        distance?.let { add("${formatKm(it)} recorridos") }
                        price?.let { add("${formatMoney(it)}/L") }
                    }
                    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                if (eff != null && prevEff != null) {
                    val better = eff >= prevEff // eff siempre va en km/L
                    val pct = ((eff - prevEff) / prevEff * 100).roundToInt()
                    Icon(
                        painterResource(if (better) R.drawable.ic_trending_up else R.drawable.ic_trending_down),
                        null,
                        tint = if (better) colors.primary else colors.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${if (pct >= 0) "+" else ""}$pct%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ======================= Carga de combustible =======================

@Composable
fun FuelSheet(
    vehicle: Vehicle,
    existing: List<FuelEntry>,
    editing: FuelEntry?,
    unit: EffUnit,
    onSave: (FuelEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val store = Store.get(androidx.compose.ui.platform.LocalContext.current)
    var date by rememberSaveable { mutableStateOf(editing?.date ?: System.currentTimeMillis()) }
    var km by rememberSaveable { mutableStateOf(editing?.odometer?.let { formatPlain(it) } ?: "") }
    var liters by rememberSaveable { mutableStateOf(editing?.liters?.let { formatPlain(it) } ?: "") }
    var total by rememberSaveable { mutableStateOf(editing?.total?.let { formatPlain(it) } ?: "") }
    var full by rememberSaveable { mutableStateOf(editing?.fullTank ?: true) }
    var notes by rememberSaveable { mutableStateOf(editing?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val kmV = parseNum(km)
    val litersV = parseNum(liters)
    val totalV = parseNum(total)
    val (prev, next) = neighborsKm(existing, date, editing?.id)
    val prevKm = prev?.odometer ?: vehicle.initialKm.takeIf { it > 0 }
    val kmError = when {
        kmV == null -> null
        prev != null && kmV <= prev.odometer -> "Debe ser mayor a ${formatKm(prev.odometer)} (carga anterior)"
        next != null && kmV >= next.odometer -> "Debe ser menor a ${formatKm(next.odometer)} (carga siguiente)"
        else -> null
    }
    val eff = if (kmV != null && litersV != null && kmError == null) {
        previewEfficiency(existing, kmV, litersV, full, editing?.id)
    } else null
    val distance = if (kmV != null && prevKm != null && kmV > prevKm) kmV - prevKm else null
    val price = if (totalV != null && litersV != null && litersV > 0) totalV / litersV else null
    val prevEff = dev.charly.mantenimiento.data.efficiencies(existing.filterNot { it.id == editing?.id })
        .filter { it.date <= date }.maxByOrNull { it.date }?.kmPerLiter

    FormSheet(if (editing == null) "Carga de gasolina" else "Editar carga", onDismiss) { close ->
        Text(vehicle.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        DateChip("Fecha", date, { date = it })
        Spacer(Modifier.height(12.dp))
        NumberField(
            "Kilometraje (odómetro)", km, { km = it }, suffix = "km", error = kmError,
            decimal = false,
        )
        if (kmError == null && prevKm != null) {
            Text(
                "Anterior: ${formatKm(prevKm)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Litros", liters, { liters = it }, Modifier.weight(1f), suffix = "L")
            NumberField("Total pagado", total, { total = it }, Modifier.weight(1f), suffix = "$")
        }
        Spacer(Modifier.height(4.dp))
        SwitchRow(
            "Tanque lleno",
            if (full) "Se calcula el rendimiento desde la carga llena anterior"
            else "Carga parcial: se suma a la siguiente carga llena",
            full,
        ) { full = it }
        PreviewCard(distance, eff, price, unit, prevEff)
        Spacer(Modifier.height(8.dp))
        PlainField("Notas (gasolinera, tipo de gasolina…)", notes, { notes = it }, singleLine = false)

        SaveRow(
            enabled = kmV != null && litersV != null && litersV > 0 && kmError == null,
            onSave = {
                val e = FuelEntry(
                    id = editing?.id ?: store.newId(),
                    vehicleId = vehicle.id,
                    date = date,
                    odometer = kmV!!,
                    liters = litersV!!,
                    total = totalV,
                    fullTank = full,
                    notes = notes.trim(),
                )
                close { onSave(e) }
            },
            onDelete = editing?.let { { confirmDelete = true } },
        )
    }
    if (confirmDelete && editing != null) {
        ConfirmDelete("esta carga", onConfirm = { confirmDelete = false; onDelete(editing.id); onDismiss() }, onDismiss = { confirmDelete = false })
    }
}

/** Número sin separadores de miles, para editar. */
fun formatPlain(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

// ======================= Mantenimiento realizado =======================

@Composable
fun MaintenanceSheet(
    vehicle: Vehicle,
    currentKm: Double,
    editing: MaintenanceEntry?,
    onSave: (MaintenanceEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val store = Store.get(androidx.compose.ui.platform.LocalContext.current)
    var date by rememberSaveable { mutableStateOf(editing?.date ?: System.currentTimeMillis()) }
    var type by rememberSaveable { mutableStateOf(editing?.type ?: "") }
    var km by rememberSaveable { mutableStateOf(editing?.odometer?.let { formatPlain(it) } ?: "") }
    var cost by rememberSaveable { mutableStateOf(editing?.cost?.let { formatPlain(it) } ?: "") }
    var notes by rememberSaveable { mutableStateOf(editing?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    FormSheet(if (editing == null) "¿Qué le hiciste hoy?" else "Editar mantenimiento", onDismiss) { close ->
        Text(vehicle.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        DateChip("Fecha", date, { date = it })
        Spacer(Modifier.height(12.dp))
        TypePicker(type) { type = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                "Kilometraje", km, { km = it }, Modifier.weight(1f), suffix = "km", decimal = false,
            )
            NumberField("Costo", cost, { cost = it }, Modifier.weight(1f), suffix = "$")
        }
        if (km.isEmpty() && currentKm > 0) {
            TextButton(onClick = { km = formatPlain(currentKm) }) { Text("Usar ${formatKm(currentKm)}") }
        }
        Spacer(Modifier.height(8.dp))
        PlainField("Notas (taller, refacciones…)", notes, { notes = it }, singleLine = false)
        SaveRow(
            enabled = type.isNotBlank(),
            onSave = {
                val e = MaintenanceEntry(
                    id = editing?.id ?: store.newId(),
                    vehicleId = vehicle.id,
                    date = date,
                    type = type.trim(),
                    odometer = parseNum(km),
                    cost = parseNum(cost),
                    notes = notes.trim(),
                )
                close { onSave(e) }
            },
            onDelete = editing?.let { { confirmDelete = true } },
        )
    }
    if (confirmDelete && editing != null) {
        ConfirmDelete("este registro", onConfirm = { confirmDelete = false; onDelete(editing.id); onDismiss() }, onDismiss = { confirmDelete = false })
    }
}

// ======================= Programar mantenimiento =======================

@Composable
fun ScheduleSheet(
    vehicle: Vehicle,
    currentKm: Double,
    editing: ScheduledMaintenance?,
    onSave: (ScheduledMaintenance) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val store = Store.get(androidx.compose.ui.platform.LocalContext.current)
    val now = System.currentTimeMillis()
    var type by rememberSaveable { mutableStateOf(editing?.type ?: "") }
    var byDate by rememberSaveable { mutableStateOf(editing?.dueDate != null || editing == null) }
    var date by rememberSaveable { mutableStateOf(editing?.dueDate ?: (now + 90 * Store.DAY_MS)) }
    var byKm by rememberSaveable { mutableStateOf(editing?.dueKm != null || editing == null) }
    var inKm by rememberSaveable {
        mutableStateOf(editing?.dueKm?.let { formatPlain((it - currentKm).coerceAtLeast(0.0)) } ?: "5000")
    }
    var repeat by rememberSaveable { mutableStateOf(editing?.let { it.repeatDays != null || it.repeatKm != null } ?: true) }
    var notes by rememberSaveable { mutableStateOf(editing?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val inKmV = parseNum(inKm)
    val days = ((startOfDay(date) - startOfDay(now)) / Store.DAY_MS).toInt()

    FormSheet(if (editing == null) "Programar mantenimiento" else "Editar programado", onDismiss) { close ->
        Text(vehicle.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        TypePicker(type) { type = it }
        Spacer(Modifier.height(12.dp))
        Text("¿Cuándo toca? Lo que llegue primero", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        SwitchRow("Por fecha", if (byDate) "En $days ${if (days == 1) "día" else "días"}" else null, byDate) { byDate = it }
        AnimatedVisibility(byDate, enter = expandVertically(Motion.bouncy()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            DateChip("Fecha", date, { date = it }, Modifier.padding(bottom = 4.dp))
        }
        SwitchRow(
            "Por kilometraje",
            if (byKm && inKmV != null) "Al llegar a ${formatKm(currentKm + inKmV)}" else null,
            byKm,
        ) { byKm = it }
        AnimatedVisibility(byKm, enter = expandVertically(Motion.bouncy()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            NumberField("Dentro de", inKm, { inKm = it }, suffix = "km", decimal = false)
        }
        SwitchRow("Repetir", "Al marcarlo como hecho se vuelve a programar con el mismo intervalo", repeat) { repeat = it }
        Spacer(Modifier.height(8.dp))
        PlainField("Notas", notes, { notes = it }, singleLine = false)
        Text(
            "Te avisaremos con una notificación cuando esté por vencer y cuando venza.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        SaveRow(
            enabled = type.isNotBlank() && (byDate || (byKm && inKmV != null && inKmV > 0)) && (!byDate || days > 0 || editing != null),
            onSave = {
                val s = ScheduledMaintenance(
                    id = editing?.id ?: store.newId(),
                    vehicleId = vehicle.id,
                    type = type.trim(),
                    dueDate = if (byDate) date else null,
                    dueKm = if (byKm && inKmV != null) currentKm + inKmV else null,
                    repeatDays = if (repeat && byDate) days.coerceAtLeast(1) else null,
                    repeatKm = if (repeat && byKm) inKmV else null,
                    notes = notes.trim(),
                    createdAt = now,
                    createdKm = currentKm,
                )
                close { onSave(s) }
            },
            onDelete = editing?.let { { confirmDelete = true } },
        )
    }
    if (confirmDelete && editing != null) {
        ConfirmDelete("este programado", onConfirm = { confirmDelete = false; onDelete(editing.id); onDismiss() }, onDismiss = { confirmDelete = false })
    }
}

/** Confirmar que ya se hizo un mantenimiento programado. */
@Composable
fun CompleteSheet(
    scheduled: ScheduledMaintenance,
    currentKm: Double,
    onSave: (odometer: Double?, cost: Double?, date: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var km by rememberSaveable { mutableStateOf(if (currentKm > 0) formatPlain(currentKm) else "") }
    var cost by rememberSaveable { mutableStateOf("") }
    FormSheet("Marcar como hecho", onDismiss) { close ->
        Text(scheduled.type, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        DateChip("Fecha", date, { date = it })
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Kilometraje", km, { km = it }, Modifier.weight(1f), suffix = "km", decimal = false)
            NumberField("Costo", cost, { cost = it }, Modifier.weight(1f), suffix = "$")
        }
        if (scheduled.repeatDays != null || scheduled.repeatKm != null) {
            Text(
                "Se volverá a programar automáticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SaveRow(enabled = true, onSave = { close { onSave(parseNum(km), parseNum(cost), date) } })
    }
}

// ======================= Vehículo =======================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VehicleSheet(
    editing: Vehicle?,
    onSave: (Vehicle) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val store = Store.get(androidx.compose.ui.platform.LocalContext.current)
    val haptics = LocalHaptics.current
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3 < 0.5f }
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var model by rememberSaveable { mutableStateOf(editing?.model ?: "") }
    var year by rememberSaveable { mutableStateOf(editing?.year ?: "") }
    var plate by rememberSaveable { mutableStateOf(editing?.plate ?: "") }
    var km by rememberSaveable { mutableStateOf(editing?.initialKm?.takeIf { it > 0 }?.let { formatPlain(it) } ?: "") }
    var color by rememberSaveable { mutableStateOf(editing?.colorIndex ?: 0) }
    var image by rememberSaveable { mutableStateOf(editing?.image) }
    var fit by rememberSaveable { mutableStateOf(editing?.imageFit ?: true) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // Selector de fotos del sistema: no pide permisos de almacenamiento
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        importError = false
        scope.launch {
            val name = withContext(Dispatchers.IO) { VehicleImages.import(context, uri) }
            importing = false
            if (name != null) {
                // Si ya habías elegido otra en este formulario y no estaba guardada, se borra
                if (image != null && image != editing?.image) VehicleImages.delete(context, image)
                image = name
                haptics.success()
            } else {
                importError = true
                haptics.error()
            }
        }
    }

    FormSheet(if (editing == null) "Nuevo vehículo" else "Editar vehículo", onDismiss) { close ->
        // ---------- Imagen ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            val spin by animateFloatAsState(if (image != null) 360f else 0f, Motion.bouncy(), label = "img")
            Box(contentAlignment = Alignment.Center) {
                VehicleAvatar(
                    image = image,
                    fit = fit,
                    color = swatchColor(PALETTE_HUES[color], dark),
                    size = 96.dp,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    modifier = Modifier
                        .graphicsLayer { rotationZ = spin }
                        .clickable { haptics.click(); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentRotation = -spin,
                )
                if (importing) LoadingIndicator()
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Imagen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (importError) "No se pudo abrir esa imagen, prueba con otra."
                    else "El logo de la marca o una foto de tu vehículo",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (importError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { haptics.click(); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !importing,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(if (image == null) "Elegir" else "Cambiar") }
                    if (image != null) {
                        TextButton(onClick = {
                            haptics.fall()
                            if (image != editing?.image) VehicleImages.delete(context, image)
                            image = null
                        }) { Text("Quitar") }
                    }
                }
            }
        }
        AnimatedVisibility(image != null, enter = expandVertically(Motion.bouncy()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                ToggleButton(
                    checked = fit,
                    onCheckedChange = { haptics.select(); fit = true },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Completa (logo)") }
                ToggleButton(
                    checked = !fit,
                    onCheckedChange = { haptics.select(); fit = false },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Recortada (foto)") }
            }
        }
        Spacer(Modifier.height(16.dp))
        PlainField("Nombre (ej. Mi Aveo, La camioneta)", name, { name = it })
        Spacer(Modifier.height(8.dp))
        PlainField("Marca y modelo", model, { model = it })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Año", year, { year = it.take(4) }, Modifier.weight(1f), decimal = false)
            OutlinedTextField(
                value = plate,
                onValueChange = { plate = it.uppercase() },
                label = { Text("Placas") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        NumberField("Kilometraje actual", km, { km = it }, suffix = "km", decimal = false)
        Spacer(Modifier.height(16.dp))
        Text(if (image != null) "Color (se usa si quitas la imagen)" else "Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            PALETTE_HUES.forEachIndexed { i, hue ->
                val sel by animateFloatAsState(if (color == i) 1f else 0f, Motion.bouncy(), label = "c")
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { val s = 1f + 0.2f * sel; scaleX = s; scaleY = s; rotationZ = 90f * sel }
                        .clip(if (color == i) MaterialShapes.Cookie6Sided.toShape() else CircleShape)
                        .background(swatchColor(hue, dark))
                        .clickable { haptics.select(); color = i },
                ) {
                    if (color == i) {
                        Icon(
                            painterResource(R.drawable.ic_check), null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = -90f },
                        )
                    }
                }
            }
        }
        SaveRow(
            enabled = name.isNotBlank(),
            onSave = {
                val v = Vehicle(
                    id = editing?.id ?: store.newId(),
                    name = name.trim(),
                    model = model.trim(),
                    year = year.trim(),
                    plate = plate.trim(),
                    initialKm = parseNum(km) ?: 0.0,
                    colorIndex = color,
                    image = image,
                    imageFit = fit,
                )
                close { onSave(v) }
            },
            onDelete = editing?.let { { confirmDelete = true } },
        )
    }
    if (confirmDelete && editing != null) {
        ConfirmDelete(
            "${editing.name} y todos sus registros",
            onConfirm = { confirmDelete = false; onDelete(editing.id); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}
