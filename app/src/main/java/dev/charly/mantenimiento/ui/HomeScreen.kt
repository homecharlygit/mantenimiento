package dev.charly.mantenimiento.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.charly.mantenimiento.R
import dev.charly.mantenimiento.data.EffUnit
import dev.charly.mantenimiento.data.FuelEntry
import dev.charly.mantenimiento.data.MaintenanceEntry
import dev.charly.mantenimiento.data.ScheduledMaintenance
import dev.charly.mantenimiento.data.Vehicle
import dev.charly.mantenimiento.data.VehicleStats
import dev.charly.mantenimiento.data.dueStatus
import dev.charly.mantenimiento.data.statsFor
import dev.charly.mantenimiento.notify.Notifier
import dev.charly.mantenimiento.ui.theme.PALETTE_HUES
import dev.charly.mantenimiento.ui.theme.swatchColor
import kotlin.math.roundToInt

private sealed interface Sheet {
    data class Fuel(val editing: FuelEntry?) : Sheet
    data class Maint(val editing: MaintenanceEntry?) : Sheet
    data class Schedule(val editing: ScheduledMaintenance?) : Sheet
    data class Complete(val s: ScheduledMaintenance) : Sheet
    data class Car(val editing: Vehicle?) : Sheet
}

private enum class Tab(val label: String, val icon: Int) {
    FUEL("Cargas", R.drawable.ic_gas),
    MAINT("Hechos", R.drawable.ic_build),
    SCHED("Programados", R.drawable.ic_event),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenSettings: () -> Unit, onOpenAbout: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    val data = vm.data
    val vehicle = vm.activeVehicle
    val st = vm.settings
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.FUEL) }

    // Permiso de notificaciones (Android 13+), una vez por sesión
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canPost(context)) {
            asked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Abrir desde una notificación
    LaunchedEffect(vm.pendingOpen) {
        val p = vm.pendingOpen ?: return@LaunchedEffect
        vm.pendingOpen = null
        when (p.what) {
            Notifier.OPEN_MAINTENANCE -> { tab = Tab.MAINT; sheet = Sheet.Maint(null) }
            Notifier.OPEN_SCHEDULED -> {
                tab = Tab.SCHED
                data.scheduled.firstOrNull { it.id == p.scheduledId }?.let { sheet = Sheet.Complete(it) }
            }
        }
    }

    BackHandler(enabled = fabOpen) { fabOpen = false }

    val stats = remember(data, vehicle?.id) { vehicle?.let { statsFor(data, it.id) } }
    val blur by animateDpAsState(
        if (sheet != null || fabOpen) 18.dp else 0.dp,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "homeBlur",
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            vehicle?.name ?: "Mis vehículos",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = listOfNotNull(
                            vehicle?.model?.takeIf { it.isNotBlank() },
                            vehicle?.year?.takeIf { it.isNotBlank() },
                            vehicle?.plate?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(sub, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { haptics.click(); onOpenAbout() }) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = "Acerca de")
                    }
                    IconButton(onClick = { haptics.click(); onOpenSettings() }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Ajustes")
                    }
                },
                scrollBehavior = scroll,
                modifier = Modifier.appBlur(blur),
            )
        },
        floatingActionButton = {
            if (vehicle != null) {
                FloatingActionButtonMenu(
                    expanded = fabOpen,
                    button = {
                        ToggleFloatingActionButton(
                            checked = fabOpen,
                            onCheckedChange = { haptics.spin(); fabOpen = it },
                        ) {
                            Icon(
                                painterResource(if (checkedProgress > 0.5f) R.drawable.ic_close else R.drawable.ic_add),
                                contentDescription = "Agregar",
                                modifier = Modifier.graphicsLayer { rotationZ = 135f * checkedProgress - if (checkedProgress > 0.5f) 135f else 0f },
                            )
                        }
                    },
                ) {
                    val items = listOf(
                        Triple(R.drawable.ic_gas, "Carga de gasolina") { sheet = Sheet.Fuel(null); tab = Tab.FUEL },
                        Triple(R.drawable.ic_build, "Mantenimiento hecho") { sheet = Sheet.Maint(null); tab = Tab.MAINT },
                        Triple(R.drawable.ic_event, "Programar mantenimiento") { sheet = Sheet.Schedule(null); tab = Tab.SCHED },
                    )
                    items.forEach { (icon, label, action) ->
                        FloatingActionButtonMenuItem(
                            onClick = { haptics.rise(); fabOpen = false; action() },
                            icon = { Icon(painterResource(icon), contentDescription = null) },
                            text = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().appBlur(blur)) {
            if (vehicle == null || stats == null) {
                EmptyGarage(Modifier.padding(pad)) { sheet = Sheet.Car(null) }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding() + 120.dp,
                    ),
                ) {
                    item(key = "cars") {
                        VehicleChips(
                            vehicles = data.vehicles.sortedBy { it.name.lowercase() },
                            selected = vehicle.id,
                            onSelect = { haptics.select(); vm.selectVehicle(it) },
                            onEdit = { sheet = Sheet.Car(it) },
                            onAdd = { sheet = Sheet.Car(null) },
                            modifier = Modifier.staggeredEnter(0),
                        )
                    }
                    item(key = "hero-${vehicle.id}") {
                        HeroCard(stats, st.unit, vehicle, Modifier.staggeredEnter(1).padding(top = 12.dp))
                    }
                    // Lo más urgente de lo programado
                    val urgent = data.scheduled.filter { it.vehicleId == vehicle.id }
                        .map { it to dueStatus(it, stats.currentKm, System.currentTimeMillis(), st.soonDays, st.soonKm.toDouble()) }
                        .filter { it.second.overdue || it.second.soon }
                        .sortedByDescending { it.second.progress }
                    if (urgent.isNotEmpty()) {
                        item(key = "urgent") {
                            val (s, ds) = urgent.first()
                            UrgentBanner(s, ds.overdue, describeDue(ds.daysLeft, ds.kmLeft), Modifier.staggeredEnter(2).padding(top = 12.dp)) {
                                sheet = Sheet.Complete(s)
                            }
                        }
                    }
                    item(key = "chart-${vehicle.id}") {
                        EfficiencyChart(stats.efficiencies, st.unit, Modifier.staggeredEnter(3).padding(top = 12.dp))
                    }
                    item(key = "tabs") {
                        TabButtons(tab, { haptics.select(); tab = it }, Modifier.staggeredEnter(4).padding(top = 20.dp, bottom = 12.dp))
                    }
                    item(key = "list-${vehicle.id}") {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                (slideInVertically(Motion.bouncy()) { it / 6 } + fadeIn(tween(220)) + scaleIn(Motion.bouncy(), initialScale = 0.96f)) togetherWith
                                    fadeOut(tween(120))
                            },
                            label = "tab",
                        ) { t ->
                            when (t) {
                                Tab.FUEL -> FuelList(data.fuel.filter { it.vehicleId == vehicle.id }, stats, st.unit) {
                                    sheet = Sheet.Fuel(it)
                                }
                                Tab.MAINT -> MaintList(data.maintenance.filter { it.vehicleId == vehicle.id }) {
                                    sheet = Sheet.Maint(it)
                                }
                                Tab.SCHED -> SchedList(
                                    data.scheduled.filter { it.vehicleId == vehicle.id }, stats.currentKm,
                                    st.soonDays, st.soonKm.toDouble(),
                                    onEdit = { sheet = Sheet.Schedule(it) },
                                    onDone = { sheet = Sheet.Complete(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------- Formularios ----------------
    val close = { sheet = null }
    when (val s = sheet) {
        is Sheet.Fuel -> if (vehicle != null) FuelSheet(
            vehicle, data.fuel.filter { it.vehicleId == vehicle.id }, s.editing, st.unit,
            onSave = vm::saveFuel, onDelete = vm::deleteFuel, onDismiss = close,
        )
        is Sheet.Maint -> if (vehicle != null) MaintenanceSheet(
            vehicle, stats?.currentKm ?: 0.0, s.editing,
            onSave = vm::saveMaintenance, onDelete = vm::deleteMaintenance, onDismiss = close,
        )
        is Sheet.Schedule -> if (vehicle != null) ScheduleSheet(
            vehicle, stats?.currentKm ?: 0.0, s.editing,
            onSave = vm::saveScheduled, onDelete = vm::deleteScheduled, onDismiss = close,
        )
        is Sheet.Complete -> CompleteSheet(
            s.s, stats?.currentKm ?: 0.0,
            onSave = { km, cost, date -> vm.completeScheduled(s.s, km, cost, date) },
            onDismiss = close,
        )
        is Sheet.Car -> VehicleSheet(s.editing, onSave = vm::saveVehicle, onDelete = vm::deleteVehicle, onDismiss = close)
        null -> {}
    }
}

fun describeDue(daysLeft: Long?, kmLeft: Double?): String = buildList {
    daysLeft?.let {
        add(
            when {
                it < 0 -> "venció hace ${-it} ${if (it == -1L) "día" else "días"}"
                it == 0L -> "vence hoy"
                it == 1L -> "mañana"
                else -> "en $it días"
            }
        )
    }
    kmLeft?.let { add(if (it <= 0) "pasado por ${formatKm(-it)}" else "faltan ${formatKm(it)}") }
}.joinToString(" · ")

// ======================= Piezas =======================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyGarage(modifier: Modifier, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spin by rememberInfiniteTransition(label = "empty").animateFloat(
        0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .staggeredEnter(0)
                .graphicsLayer { rotationZ = spin }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(colors.primaryContainer),
        ) {
            Icon(
                painterResource(R.drawable.ic_car), null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = -spin },
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Agrega tu primer vehículo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.staggeredEnter(1),
        )
        Text(
            "Registra tus cargas de gasolina para calcular el rendimiento y lleva el control del mantenimiento.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).staggeredEnter(2),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp).staggeredEnter(3)) {
            Icon(painterResource(R.drawable.ic_add), null)
            Spacer(Modifier.width(8.dp))
            Text("Agregar vehículo", style = MaterialTheme.typography.titleMedium)
        }
    }
}

fun vehicleColor(v: Vehicle, dark: Boolean): Color =
    swatchColor(PALETTE_HUES[v.colorIndex.coerceIn(PALETTE_HUES.indices)], dark)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VehicleChips(
    vehicles: List<Vehicle>,
    selected: Long,
    onSelect: (Long) -> Unit,
    onEdit: (Vehicle) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val dark = colors.background.let { (it.red + it.green + it.blue) / 3 < 0.5f }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        items(vehicles, key = { it.id }) { v ->
            val sel = v.id == selected
            val k by animateFloatAsState(if (sel) 1f else 0f, Motion.bouncy(), label = "chip")
            val source = remember { MutableInteractionSource() }
            val press = rememberPressScale(source, 0.9f)
            Surface(
                shape = RoundedCornerShape((24 - 8 * k).dp),
                color = if (sel) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = if (sel) colors.onPrimaryContainer else colors.onSurface,
                modifier = Modifier
                    .graphicsLayer { scaleX = press; scaleY = press }
                    .clip(RoundedCornerShape((24 - 8 * k).dp))
                    .combinedClickable(
                        interactionSource = source,
                        indication = null,
                        // Tocar el que ya está elegido (o mantener presionado) lo edita
                        onClick = { if (sel) onEdit(v) else onSelect(v.id) },
                        onLongClick = { haptics.select(); onEdit(v) },
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer { rotationZ = 60f * k }
                            .clip(if (sel) MaterialShapes.Cookie6Sided.toShape() else CircleShape)
                            .background(vehicleColor(v, dark)),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_car), null,
                            tint = colors.surface,
                            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = -60f * k },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(v.name, style = MaterialTheme.typography.titleSmall, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                    if (sel) {
                        Spacer(Modifier.width(6.dp))
                        Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(16.dp).graphicsLayer { alpha = k })
                    }
                }
            }
        }
        item(key = "add") {
            Surface(
                onClick = { haptics.click(); onAdd() },
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "Agregar vehículo")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(stats: VehicleStats, unit: EffUnit, vehicle: Vehicle, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val last = stats.last
    val shown = animatedNumber(last?.let { unit.value(it.kmPerLiter) } ?: 0.0)
    val spin by rememberInfiniteTransition(label = "hero").animateFloat(
        0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            // Galleta decorativa que gira lento detrás del número
            Box(
                Modifier
                    .size(170.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .graphicsLayer { rotationZ = spin }
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(colors.primary.copy(alpha = 0.14f))
            )
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_gas), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Último rendimiento", style = MaterialTheme.typography.labelLarge)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (last != null) formatNum(shown) else "—",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(unit.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
                }
                val avg = stats.average
                if (last != null && avg != null && stats.efficiencies.size > 1) {
                    val diff = ((last.kmPerLiter - avg) / avg * 100).roundToInt()
                    val better = diff >= 0
                    Surface(
                        shape = CircleShape,
                        color = if (better) colors.primary else colors.error,
                        contentColor = if (better) colors.onPrimary else colors.onError,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(painterResource(if (better) R.drawable.ic_trending_up else R.drawable.ic_trending_down), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${if (better) "+" else ""}$diff% vs. tu promedio",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                } else if (last == null) {
                    Text(
                        "Registra dos cargas de tanque lleno para calcular el rendimiento.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Promedio", stats.average?.let { unit.format(it) } ?: "—", Modifier.weight(1f))
                    MiniStat("Odómetro", formatKm(stats.currentKm), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("En gasolina", formatMoney(stats.totalSpent), Modifier.weight(1f))
                    MiniStat("Costo por km", stats.costPerKm?.let { formatMoney(it) } ?: "—", Modifier.weight(1f))
                }
                if (stats.best != null && stats.worst != null && stats.efficiencies.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("Mejor", unit.format(stats.best), Modifier.weight(1f))
                        MiniStat("Peor", unit.format(stats.worst), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UrgentBanner(s: ScheduledMaintenance, overdue: Boolean, detail: String, modifier: Modifier, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val wobble by rememberInfiniteTransition(label = "urgent").animateFloat(
        -8f, 8f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "w",
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (overdue) colors.errorContainer else colors.tertiaryContainer,
        contentColor = if (overdue) colors.onErrorContainer else colors.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(
                painterResource(R.drawable.ic_notifications), null,
                modifier = Modifier.graphicsLayer { rotationZ = if (overdue) wobble else 0f },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (overdue) "Ya toca: ${s.type}" else "Se acerca: ${s.type}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onDone, shapes = ButtonDefaults.shapes()) { Text("Hecho") }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabButtons(tab: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        val all = Tab.entries
        all.forEachIndexed { i, t ->
            ToggleButton(
                checked = tab == t,
                onCheckedChange = { onTab(t) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    all.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Icon(painterResource(t.icon), null, Modifier.size(18.dp))
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(t.label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyList(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
    )
}

@Composable
private fun ListRow(
    index: Int,
    count: Int,
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val press = rememberPressScale(source, 0.96f)
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = segmentShape(index, count),
        color = colors.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 3.dp)
            .staggeredEnter(index.coerceAtMost(8))
            .graphicsLayer { scaleX = press; scaleY = press },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer),
            ) {
                Icon(painterResource(icon), null, tint = colors.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
            }
            trailing?.let { Spacer(Modifier.width(8.dp)); it() }
        }
    }
}

@Composable
private fun FuelList(entries: List<FuelEntry>, stats: VehicleStats, unit: EffUnit, onEdit: (FuelEntry) -> Unit) {
    if (entries.isEmpty()) return EmptyList("Aún no hay cargas. Toca + para registrar la primera con el kilometraje del tablero.")
    val byId = stats.efficiencies.associateBy { it.entryId }
    val sorted = entries.sortedByDescending { it.date }
    val colors = MaterialTheme.colorScheme
    Column {
        sorted.forEachIndexed { i, e -> key(e.id) {
            val eff = byId[e.id]
            ListRow(
                index = i, count = sorted.size, icon = R.drawable.ic_gas,
                title = "${formatKm(e.odometer)} · ${formatNum(e.liters, 2)} L",
                subtitle = listOfNotNull(
                    formatDate(e.date),
                    e.total?.let { formatMoney(it) },
                    e.pricePerLiter?.let { "${formatMoney(it)}/L" },
                    e.notes.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                onClick = { onEdit(e) },
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (eff != null) colors.primaryContainer else colors.surfaceContainerHighest,
                    contentColor = if (eff != null) colors.onPrimaryContainer else colors.onSurfaceVariant,
                ) {
                    Text(
                        when {
                            eff != null -> unit.format(eff.kmPerLiter)
                            !e.fullTank -> "Parcial"
                            else -> "Base"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        } }
    }
}

@Composable
private fun MaintList(entries: List<MaintenanceEntry>, onEdit: (MaintenanceEntry) -> Unit) {
    if (entries.isEmpty()) return EmptyList("Sin mantenimientos registrados. Cada día a la hora que elijas te preguntaremos qué le hiciste.")
    val sorted = entries.sortedByDescending { it.date }
    val total = entries.sumOf { it.cost ?: 0.0 }
    Column {
        if (total > 0) {
            Text(
                "Total invertido: ${formatMoney(total)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        sorted.forEachIndexed { i, e -> key(e.id) {
            ListRow(
                index = i, count = sorted.size, icon = R.drawable.ic_build,
                title = e.type,
                subtitle = listOfNotNull(
                    formatDate(e.date),
                    e.odometer?.let { formatKm(it) },
                    e.cost?.let { formatMoney(it) },
                    e.notes.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                onClick = { onEdit(e) },
            )
        } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SchedList(
    entries: List<ScheduledMaintenance>,
    currentKm: Double,
    soonDays: Int,
    soonKm: Double,
    onEdit: (ScheduledMaintenance) -> Unit,
    onDone: (ScheduledMaintenance) -> Unit,
) {
    if (entries.isEmpty()) return EmptyList("Nada programado. Programa el cambio de aceite, la verificación o lo que quieras y te avisamos.")
    val colors = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val withStatus = entries.map { it to dueStatus(it, currentKm, now, soonDays, soonKm) }
        .sortedByDescending { it.second.progress }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        withStatus.forEachIndexed { i, (s, ds) -> key(s.id) {
            val source = remember { MutableInteractionSource() }
            val press = rememberPressScale(source, 0.97f)
            val container = when {
                ds.overdue -> colors.errorContainer
                ds.soon -> colors.tertiaryContainer
                else -> colors.surfaceContainer
            }
            val content = when {
                ds.overdue -> colors.onErrorContainer
                ds.soon -> colors.onTertiaryContainer
                else -> colors.onSurface
            }
            val progress by animateFloatAsState(ds.progress, Motion.bouncy(), label = "p")
            Surface(
                onClick = { onEdit(s) },
                interactionSource = source,
                shape = RoundedCornerShape(24.dp),
                color = container,
                contentColor = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEnter(i.coerceAtMost(8))
                    .graphicsLayer { scaleX = press; scaleY = press },
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(describeDue(ds.daysLeft, ds.kmLeft), style = MaterialTheme.typography.bodySmall)
                            val repeat = listOfNotNull(
                                s.repeatKm?.let { "cada ${formatKm(it)}" },
                                s.repeatDays?.let { "cada $it días" },
                            )
                            if (repeat.isNotEmpty()) {
                                Text("Se repite ${repeat.joinToString(" o ")}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        FilledTonalButton(onClick = { onDone(s) }, shapes = ButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hecho")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (ds.overdue) colors.error else colors.primary,
                    )
                }
            }
        } }
    }
}
