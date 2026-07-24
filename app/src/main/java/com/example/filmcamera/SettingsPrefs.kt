package com.example.filmcamera

import android.content.Context

object SettingsPrefs {
    private const val PREF_NAME = "film_camera_prefs"
    private const val KEY_GRID = "grid_enabled"
    private const val KEY_ASPECT = "aspect_ratio_index"

    // 화면비율 0=꽉 채움(센서 기본), 1=16:9, 2=1:1
    const val ASPECT_FULL = 0
    const val ASPECT_16_9 = 1
    const val ASPECT_1_1 = 2

    fun isGridEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_GRID, false)
    }

    fun setGridEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GRID, enabled).apply()
    }

    fun getAspectRatio(context: Context): Int {
        return prefs(context).getInt(KEY_ASPECT, ASPECT_FULL)
    }

    fun setAspectRatio(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_ASPECT, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
