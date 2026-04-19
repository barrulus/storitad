package uk.storitad.capture.settings

import android.content.Context

class CaptureSettings(ctx: Context) {
    private val prefs = ctx.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var autoAttachLocation: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ATTACH_LOCATION, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ATTACH_LOCATION, value).apply()

    companion object {
        private const val FILE_NAME = "capture_settings"
        private const val KEY_AUTO_ATTACH_LOCATION = "autoAttachLocation"
    }
}
