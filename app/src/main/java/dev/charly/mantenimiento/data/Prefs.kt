package dev.charly.mantenimiento.data

import android.content.Context
import android.content.res.Configuration
import dev.charly.mantenimiento.ui.BlurType
import dev.charly.mantenimiento.ui.theme.DEFAULT_PALETTE

enum class EffUnit(val code: String) {
    KM_PER_L("kml"), L_PER_100("l100");

    companion object {
        fun from(code: String?) = entries.firstOrNull { it.code == code } ?: KM_PER_L
    }
}

data class AppSettings(
    val palette: Int = DEFAULT_PALETTE,
    val dark: Boolean = true,
    val blur: BlurType = BlurType.KAWASE,
    val haptics: Boolean = true,
    val hapticStrength: Float = 0.7f,
    val unit: EffUnit = EffUnit.KM_PER_L,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val soonDays: Int = 3,
    val soonKm: Int = 500,
    val activeVehicle: Long? = null,
)

/** Ajustes en SharedPreferences. Los workers también los leen. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun load(c: Context): AppSettings {
        val p = sp(c)
        val systemDark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return AppSettings(
            palette = p.getInt("palette", DEFAULT_PALETTE),
            // La primera vez sigue al sistema; después, lo que elijas con el toggle
            dark = p.getBoolean("dark", systemDark),
            blur = BlurType.from(p.getString("blur", null)),
            haptics = p.getBoolean("haptics", true),
            hapticStrength = p.getFloat("hapticStrength", 0.7f),
            unit = EffUnit.from(p.getString("unit", null)),
            reminderEnabled = p.getBoolean("reminderEnabled", true),
            reminderHour = p.getInt("reminderHour", 20),
            reminderMinute = p.getInt("reminderMinute", 0),
            soonDays = p.getInt("soonDays", 3),
            soonKm = p.getInt("soonKm", 500),
            activeVehicle = p.getLong("activeVehicle", -1L).takeIf { it != -1L },
        )
    }

    fun save(c: Context, s: AppSettings) {
        sp(c).edit()
            .putInt("palette", s.palette)
            .putBoolean("dark", s.dark)
            .putString("blur", s.blur.code)
            .putBoolean("haptics", s.haptics)
            .putFloat("hapticStrength", s.hapticStrength)
            .putString("unit", s.unit.code)
            .putBoolean("reminderEnabled", s.reminderEnabled)
            .putInt("reminderHour", s.reminderHour)
            .putInt("reminderMinute", s.reminderMinute)
            .putInt("soonDays", s.soonDays)
            .putInt("soonKm", s.soonKm)
            .putLong("activeVehicle", s.activeVehicle ?: -1L)
            .apply()
    }
}
