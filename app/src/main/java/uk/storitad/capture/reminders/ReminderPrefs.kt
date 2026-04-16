package uk.storitad.capture.reminders

import android.content.Context
import java.time.LocalTime

class ReminderPrefs(ctx: Context) {
    private val prefs = ctx.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var eveningMinutes: Int
        get() = prefs.getInt(KEY_EVENING, DEFAULT_EVENING)
        set(value) = prefs.edit().putInt(KEY_EVENING, value.coerceIn(0, 1439)).apply()

    var morningMinutes: Int
        get() = prefs.getInt(KEY_MORNING, DEFAULT_MORNING)
        set(value) = prefs.edit().putInt(KEY_MORNING, value.coerceIn(0, 1439)).apply()

    val eveningTime: LocalTime get() = LocalTime.of(eveningMinutes / 60, eveningMinutes % 60)
    val morningTime: LocalTime get() = LocalTime.of(morningMinutes / 60, morningMinutes % 60)

    companion object {
        private const val FILE_NAME = "reminders"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_EVENING = "eveningMinutes"
        private const val KEY_MORNING = "morningMinutes"
        private const val DEFAULT_EVENING = 20 * 60  // 20:00
        private const val DEFAULT_MORNING = 9 * 60   //  9:00
    }
}
