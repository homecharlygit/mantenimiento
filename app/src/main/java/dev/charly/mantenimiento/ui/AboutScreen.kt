package dev.charly.mantenimiento.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.charly.mantenimiento.R
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/homecharlygit/mantenimiento"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(vm: AppViewModel, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }
    var showLicense by remember { mutableStateOf(false) }
    fun open(url: String) {
        runCatching { uri.openUri(url) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Acerca de", fontWeight = FontWeight.Bold) },
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
            // ---------- Encabezado ----------
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).staggeredEnter(0),
                ) {
                    AppBadge()
                    Spacer(Modifier.height(16.dp))
                    Text("Mantenimiento", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Versión $versionName",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Rendimiento de combustible y bitácora de mantenimiento para todos tus vehículos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }

            // ---------- Créditos ----------
            item { SectionHeader("Créditos", Modifier.staggeredEnter(1)) }
            item {
                InfoGroup(
                    rows = listOf(
                        InfoItem("Desarrollo", "Charly · @homecharlygit", "https://github.com/homecharlygit"),
                        InfoItem("Diseño", "Material 3 Expressive", "https://m3.material.io"),
                        InfoItem("Asistencia de código", "Claude · Anthropic", "https://claude.ai"),
                    ),
                    onOpen = ::open,
                    startIndex = 2,
                )
            }

            // ---------- Cómo se calcula ----------
            item { SectionHeader("Cómo se calcula", Modifier.staggeredEnter(5)) }
            item {
                InfoGroup(
                    rows = listOf(
                        InfoItem("Lleno a lleno", "Kilómetros desde la carga llena anterior entre los litros que entraron desde entonces", null),
                        InfoItem("Cargas parciales", "Se acumulan y cuentan en la siguiente carga de tanque lleno", null),
                        InfoItem("Costo por km", "Precio promedio por litro entre tu rendimiento promedio", null),
                    ),
                    onOpen = ::open,
                    startIndex = 6,
                )
            }

            // ---------- Hecho con ----------
            item { SectionHeader("Hecho con", Modifier.staggeredEnter(8)) }
            item {
                InfoGroup(
                    rows = listOf(
                        InfoItem("Kotlin", "2.3", "https://kotlinlang.org"),
                        InfoItem("Jetpack Compose", "BOM 2026.08.00", "https://developer.android.com/compose"),
                        InfoItem("Material 3 Expressive", "androidx.compose.material3 1.5.0-rc01",
                            "https://developer.android.com/jetpack/androidx/releases/compose-material3"),
                        InfoItem("WorkManager", "Recordatorios y avisos programados",
                            "https://developer.android.com/jetpack/androidx/releases/work"),
                    ),
                    onOpen = ::open,
                    startIndex = 9,
                )
            }

            // ---------- Licencia ----------
            item { SectionHeader("Licencia", Modifier.staggeredEnter(12)) }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().staggeredEnter(13),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("GNU GPL v3.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Software libre: puedes usarlo, estudiarlo, modificarlo y compartirlo, siempre que las versiones modificadas también se publiquen bajo GPL-3.0.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "© 2026 Charly (homecharlygit)",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showLicense = true }) { Text("Ver licencia") }
                            FilledTonalButton(onClick = { open(REPO_URL) }) { Text("Código fuente") }
                        }
                    }
                }
            }

            // ---------- Aviso ----------
            item { SectionHeader("Aviso", Modifier.staggeredEnter(14)) }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().staggeredEnter(15),
                ) {
                    Text("El rendimiento es un cálculo con los datos que capturas: entre más seguido registres cargas de tanque lleno, más preciso será. Los recordatorios no sustituyen el manual de servicio de tu vehículo. Tus datos se guardan solo en tu teléfono.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
    if (showLicense) LicenseSheet(onDismiss = { showLicense = false }, onOpenWeb = { open(LICENSE_URL) })
}

/** Texto completo de la GPL-3.0 incluido en la app (res/raw/license.txt), se lee sin internet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseSheet(onDismiss: () -> Unit, onOpenWeb: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember {
        runCatching {
            context.resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() }
        }.getOrDefault("")
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim().replace(Regex("\\s*\\n\\s*"), " ") }
            .filter { it.isNotEmpty() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item {
                Text("GNU General Public License v3.0", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "© 2026 Charly (homecharlygit). Esta app es software libre bajo los términos de la licencia que sigue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                FilledTonalButton(onClick = onOpenWeb, modifier = Modifier.padding(bottom = 16.dp)) { Text("Abrir en gnu.org") }
            }
            items(paragraphs) { p ->
                Text(
                    p,
                    style = if (p.length < 80 && p == p.uppercase()) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodySmall,
                    fontWeight = if (p.length < 80 && p == p.uppercase()) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }
}


/** Ícono de la app en una galleta que gira lento y "salta" al tocarla. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppBadge() {
    val colors = MaterialTheme.colorScheme
    val spin by rememberInfiniteTransition(label = "badge").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(1f) }
    val extra = remember { Animatable(0f) }

    Surface(
        onClick = {
            haptics.spin()
            scope.launch { pop.snapTo(0.7f); pop.animateTo(1f, Motion.wobbly()) }
            scope.launch { extra.animateTo(extra.targetValue + 180f, Motion.wobbly()) }
        },
        shape = MaterialShapes.Cookie12Sided.toShape(),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier
            .size(112.dp)
            .graphicsLayer {
                rotationZ = spin + extra.value
                scaleX = pop.value
                scaleY = pop.value
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_gas),
                contentDescription = null,
                modifier = Modifier.size(52.dp).graphicsLayer { rotationZ = -(spin + extra.value) },
            )
        }
    }
}

private data class InfoItem(val title: String, val subtitle: String, val url: String?)

@Composable
private fun InfoGroup(rows: List<InfoItem>, onOpen: (String) -> Unit, startIndex: Int) {
    val colors = MaterialTheme.colorScheme
    Column {
        rows.forEachIndexed { i, row ->
            val source = remember { MutableInteractionSource() }
            val press = rememberPressScale(source, 0.95f)
            Surface(
                onClick = { row.url?.let(onOpen) },
                enabled = row.url != null,
                interactionSource = source,
                shape = segmentShape(i, rows.size),
                color = colors.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp)
                    .staggeredEnter(startIndex + i)
                    .graphicsLayer { scaleX = press; scaleY = press },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    if (row.url != null) {
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            painterResource(R.drawable.ic_open_in_new),
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
