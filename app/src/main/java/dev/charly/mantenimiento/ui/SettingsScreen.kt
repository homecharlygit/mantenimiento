package dev.charly.mantenimiento.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.charly.mantenimiento.R
import dev.charly.mantenimiento.data.EffUnit
import dev.charly.mantenimiento.notify.Notifier
import dev.charly.mantenimiento.ui.theme.PALETTE_DYNAMIC
import dev.charly.mantenimiento.ui.theme.PALETTE_HUES
import dev.charly.mantenimiento.ui.theme.supportsDynamicColor
import dev.charly.mantenimiento.ui.theme.swatchColor
import kotlin.math.roundToInt

private val COLOR_NAMES = listOf("Rojo", "Naranja", "Amarillo", "Verde", "Turquesa", "Azul", "Morado", "Rosa")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val st = vm.settings
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // El permiso puede cambiar fuera de la app; se revisa cada vez que regresas
    var canNotify by remember { mutableStateOf(Notifier.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Notifier.canPost(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = Notifier.canPost(context)
    }
    var pickTime by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Regresar")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + 32.dp,
            ),
        ) {
            // ---------- Apariencia ----------
            item { SectionHeader("Apariencia", Modifier.staggeredEnter(0)) }
            item {
                val source = remember { MutableInteractionSource() }
                val press = rememberPressScale(source, 0.96f)
                Surface(
                    onClick = { haptics.toggle(!st.dark); vm.setDark(!st.dark) },
                    interactionSource = source,
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEnter(1)
                        .graphicsLayer { scaleX = press; scaleY = press },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        val spin by animateFloatAsState(if (st.dark) 0f else -180f, Motion.wobbly(), label = "moon")
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { rotationZ = spin }
                                .clip(MaterialShapes.Cookie6Sided.toShape())
                                .background(colors.secondaryContainer),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_dark_mode),
                                contentDescription = null,
                                tint = colors.onSecondaryContainer,
                                modifier = Modifier.graphicsLayer { rotationZ = -spin },
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Modo oscuro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (st.dark) "Activado" else "Modo claro",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = st.dark,
                            onCheckedChange = { haptics.toggle(it); vm.setDark(it) },
                            thumbContent = {
                                Icon(
                                    painterResource(if (st.dark) R.drawable.ic_dark_mode else R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
            }

            // ---------- Color del tema ----------
            item { SectionHeader("Color del tema", Modifier.staggeredEnter(2)) }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().staggeredEnter(3),
                ) {
                    Column(Modifier.padding(vertical = 18.dp, horizontal = 8.dp)) {
                        val options = buildList {
                            if (supportsDynamicColor) {
                                val d = if (st.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                                add(Triple(PALETTE_DYNAMIC, Brush.sweepGradient(listOf(d.primary, d.tertiary, d.secondary, d.primary)), "Fondo"))
                            }
                            PALETTE_HUES.forEachIndexed { i, hue ->
                                add(Triple(i, Brush.linearGradient(listOf(swatchColor(hue, st.dark), swatchColor(hue, st.dark))), COLOR_NAMES[i]))
                            }
                        }
                        options.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { (index, fill, name) ->
                                    Swatch(
                                        fill = fill,
                                        sample = if (index == PALETTE_DYNAMIC) colors.primary else swatchColor(PALETTE_HUES[index], st.dark),
                                        label = name,
                                        selected = st.palette == index,
                                        onClick = { haptics.select(); vm.setPalette(index) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }

            // ---------- Pregunta diaria ----------
            item { SectionHeader("Pregunta diaria de mantenimiento", Modifier.staggeredEnter(4)) }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().staggeredEnter(5),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val ring by animateFloatAsState(if (st.reminderEnabled) 0f else -30f, Motion.wobbly(), label = "bell")
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .graphicsLayer { rotationZ = ring }
                                    .clip(MaterialShapes.Cookie6Sided.toShape())
                                    .background(colors.secondaryContainer),
                            ) {
                                Icon(painterResource(R.drawable.ic_notifications), null, tint = colors.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Preguntar cada día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Una notificación te pregunta qué le hiciste a cada vehículo; puedes responder ahí mismo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = st.reminderEnabled,
                                onCheckedChange = { haptics.toggle(it); vm.setReminderEnabled(it) },
                            )
                        }
                        AnimatedVisibility(st.reminderEnabled) {
                            Column(Modifier.padding(top = 16.dp)) {
                                val source = remember { MutableInteractionSource() }
                                val press = rememberPressScale(source, 0.95f)
                                Surface(
                                    onClick = { haptics.click(); pickTime = true },
                                    interactionSource = source,
                                    shape = RoundedCornerShape(20.dp),
                                    color = colors.primaryContainer,
                                    contentColor = colors.onPrimaryContainer,
                                    modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = press; scaleY = press },
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                        Icon(painterResource(R.drawable.ic_schedule), null)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Hora", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Text(
                                            formatTime(st.reminderHour, st.reminderMinute),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { haptics.rise(); vm.testReminder() },
                                        enabled = canNotify && vm.activeVehicle != null,
                                        shapes = ButtonDefaults.shapes(),
                                    ) { Text("Probar ahora") }
                                }
                            }
                        }
                        if (!canNotify) {
                            Spacer(Modifier.height(12.dp))
                            Surface(shape = RoundedCornerShape(18.dp), color = colors.errorContainer, contentColor = colors.onErrorContainer) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Las notificaciones están desactivadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Sin ellas no llegan la pregunta diaria ni los avisos de mantenimiento.", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }) { Text("Activar") }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- Avisos de programados ----------
            item { SectionHeader("Avisar antes de que venza", Modifier.staggeredEnter(6)) }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().staggeredEnter(7),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text("Por fecha", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Connected(
                            options = listOf(1, 3, 7, 14),
                            selected = st.soonDays,
                            label = { if (it == 1) "1 día" else "$it días" },
                            onSelect = { haptics.select(); vm.setSoonDays(it) },
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Por kilometraje", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Connected(
                            options = listOf(200, 500, 1000, 2000),
                            selected = st.soonKm,
                            label = { formatNum(it.toDouble(), 0) },
                            onSelect = { haptics.select(); vm.setSoonKm(it) },
                        )
                    }
                }
            }

            // ---------- Unidad ----------
            item { SectionHeader("Unidad de rendimiento", Modifier.staggeredEnter(8)) }
            val units = listOf(
                Triple(EffUnit.KM_PER_L, "Kilómetros por litro", "km/L — más es mejor"),
                Triple(EffUnit.L_PER_100, "Litros a los 100 km", "L/100 km — menos es mejor"),
            )
            units.forEachIndexed { i, (u, name, desc) ->
                item(key = "unit-${u.code}") {
                    ChoiceRow(
                        badge = if (u == EffUnit.KM_PER_L) "K" else "L",
                        title = name,
                        subtitle = desc,
                        selected = st.unit == u,
                        index = i,
                        count = units.size,
                        onClick = { haptics.spin(); vm.setUnit(u) },
                        modifier = Modifier.staggeredEnter(9 + i).padding(bottom = 3.dp),
                    )
                }
            }

            // ---------- Desenfoque ----------
            item { SectionHeader("Desenfoque", Modifier.staggeredEnter(11)) }
            item {
                BlurPreview(
                    type = st.blur,
                    title = "Vista previa",
                    modifier = Modifier.staggeredEnter(12).padding(bottom = 6.dp),
                )
            }
            val blurOptions = listOf(
                Triple(BlurType.KAWASE, "Dual Kawase", "Más rápido y suave; shaders AGSL (Android 13+)"),
                Triple(BlurType.GAUSSIAN, "Gaussiano", "El desenfoque clásico del sistema (Android 12+)"),
            )
            blurOptions.forEachIndexed { i, (type, name, desc) ->
                item(key = "blur-${type.code}") {
                    ChoiceRow(
                        badge = name.first().toString(),
                        title = name,
                        subtitle = desc,
                        selected = st.blur == type,
                        index = i,
                        count = blurOptions.size,
                        onClick = { haptics.spin(); vm.setBlur(type) },
                        modifier = Modifier.staggeredEnter(13 + i).padding(bottom = 3.dp),
                    )
                }
            }
            val blurNote = when {
                !BlurSupport.gaussian -> "Tu versión de Android no soporta desenfoque (requiere Android 12)."
                !BlurSupport.kawase && st.blur == BlurType.KAWASE -> "Kawase requiere Android 13; se usará Gaussiano."
                else -> null
            }
            if (blurNote != null) {
                item {
                    Text(blurNote, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
                }
            }
            item {
                Text(
                    "Se aplica detrás de los formularios, del menú de agregar y en las transiciones entre pantallas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp).staggeredEnter(15),
                )
            }

            // ---------- Respuesta háptica ----------
            item { SectionHeader("Respuesta háptica", Modifier.staggeredEnter(16)) }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().staggeredEnter(17),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tilt by animateFloatAsState(if (st.haptics) 0f else -25f, Motion.wobbly(), label = "tilt")
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .graphicsLayer { rotationZ = tilt }
                                    .clip(MaterialShapes.Cookie6Sided.toShape())
                                    .background(colors.secondaryContainer),
                            ) {
                                Icon(painterResource(R.drawable.ic_vibration), null, tint = colors.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Vibración", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Vibraciones dinámicas al tocar y animar", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Switch(
                                checked = st.haptics,
                                onCheckedChange = { on ->
                                    if (on) {
                                        vm.setHaptics(true)
                                        haptics.enabled = true
                                        haptics.success()
                                    } else {
                                        haptics.fall()
                                        vm.setHaptics(false)
                                    }
                                },
                            )
                        }
                        AnimatedVisibility(visible = st.haptics) {
                            Column(Modifier.padding(top = 16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Intensidad", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(
                                        "${(st.hapticStrength * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                    )
                                }
                                Slider(
                                    value = st.hapticStrength,
                                    onValueChange = { v ->
                                        val stepped = (v * 10).roundToInt() / 10f
                                        if (stepped != st.hapticStrength) {
                                            vm.setHapticStrength(stepped)
                                            haptics.intensity = stepped
                                            haptics.click(1f)
                                        }
                                    },
                                    valueRange = 0.1f..1f,
                                    steps = 8,
                                )
                                if (!haptics.rich) {
                                    Text("Tu teléfono solo soporta vibración básica.", style = MaterialTheme.typography.bodySmall, color = colors.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(st.reminderHour, st.reminderMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("¿A qué hora te pregunto?") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.success()
                    vm.setReminderTime(state.hour, state.minute)
                    pickTime = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancelar") } },
        )
    }
}

/** Botones conectados de M3 Expressive para elegir una opción. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> Connected(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { i, o ->
            ToggleButton(
                checked = o == selected,
                onCheckedChange = { onSelect(o) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) { Text(label(o), maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Swatch(
    fill: Brush,
    sample: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val press = rememberPressScale(source, 0.82f)
    val sel by animateFloatAsState(if (selected) 1f else 0f, Motion.bouncy(), label = "sel")
    val rotation by animateFloatAsState(if (selected) 120f else 0f, Motion.wobbly(), label = "rot")
    val cookie = MaterialShapes.Cookie9Sided.toShape()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer {
                    val s = press * (1f + 0.14f * sel)
                    scaleX = s
                    scaleY = s
                }
                .clickable(interactionSource = source, indication = null, onClick = onClick),
        ) {
            // Círculo cuando no está elegido, galleta giratoria cuando sí (cross-fade entre formas)
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = (1f - sel).coerceIn(0f, 1f) }
                    .clip(CircleShape)
                    .background(fill)
            )
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = sel.coerceIn(0f, 1f); rotationZ = rotation }
                    .clip(cookie)
                    .background(fill)
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = scaleIn(Motion.wobbly(), initialScale = 0f) + fadeIn(),
                exit = scaleOut(targetScale = 0f) + fadeOut(),
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = if (sample.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Muestra cómo se ve el desenfoque elegido sobre figuras de colores del tema. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BlurPreview(type: BlurType, title: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val wobble by rememberInfiniteTransition(label = "preview").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "wobble",
    )
    // Al cambiar de tipo, el blur "late" con un pulso para que se note el cambio
    val pulse = remember { Animatable(14f) }
    LaunchedEffect(type) {
        pulse.snapTo(30f)
        pulse.animateTo(14f, Motion.wobbly())
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainer,
        modifier = modifier.fillMaxWidth().height(150.dp),
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxSize()
                    .appBlur(pulse.value.dp, type),
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .offset(x = (40 + 24 * wobble).dp, y = 18.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(colors.primary)
                )
                Box(
                    Modifier
                        .size(80.dp)
                        .offset(x = (150 - 20 * wobble).dp, y = 50.dp)
                        .clip(CircleShape)
                        .background(colors.tertiary)
                )
                Box(
                    Modifier
                        .size(70.dp)
                        .offset(x = (240 + 16 * wobble).dp, y = 12.dp)
                        .clip(MaterialShapes.Sunny.toShape())
                        .background(colors.secondary)
                )
                Text(
                    "km/L",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = colors.surface.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceRow(
    badge: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val press = rememberPressScale(source, 0.95f)
    val spin by animateFloatAsState(if (selected) 90f else 0f, Motion.wobbly(), label = "badge")
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = segmentShape(index, count),
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = press; scaleY = press },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = spin }
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(if (selected) colors.primary else colors.secondaryContainer),
            ) {
                Text(
                    badge,
                    fontWeight = FontWeight.Black,
                    color = if (selected) colors.onPrimary else colors.onSecondaryContainer,
                    modifier = Modifier.graphicsLayer { rotationZ = -spin },
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(Motion.wobbly(), initialScale = 0f) + fadeIn(),
                exit = scaleOut(targetScale = 0f) + fadeOut(),
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
            }
        }
    }
}
