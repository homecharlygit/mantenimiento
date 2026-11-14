package dev.charly.mantenimiento

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import dev.charly.mantenimiento.notify.Notifier
import dev.charly.mantenimiento.ui.AppRoot
import dev.charly.mantenimiento.ui.AppViewModel
import dev.charly.mantenimiento.ui.PendingOpen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(vm.settings.dark)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val dark = vm.settings.dark
            // Íconos de la barra de estado según el modo de la app, no el del sistema
            LaunchedEffect(dark) { applySystemBars(dark) }
            AppRoot(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Abrir desde una notificación: va directo al formulario correspondiente. */
    private fun handleIntent(intent: Intent?) {
        val what = intent?.getStringExtra(Notifier.EXTRA_OPEN) ?: return
        val vehicle = intent.getLongExtra(Notifier.EXTRA_VEHICLE, -1)
        val sched = intent.getLongExtra(Notifier.EXTRA_SCHEDULED, -1).takeIf { it != -1L }
        if (vehicle != -1L) {
            vm.selectVehicle(vehicle)
            vm.pendingOpen = PendingOpen(what, vehicle, sched)
        }
        intent.removeExtra(Notifier.EXTRA_OPEN)
    }

    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
