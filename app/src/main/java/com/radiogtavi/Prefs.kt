package com.radiogtavi

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("radio_gtavi", Context.MODE_PRIVATE)

    var stationIds: List<String>
        get() = sp.getString("stations", "")!!.split(",").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("stations", v.joinToString(",")).apply()

    var lastStationId: String?
        get() = sp.getString("last", null)
        set(v) = sp.edit().putString("last", v).apply()

    var token: String?
        get() = if (System.currentTimeMillis() < sp.getLong("token_exp", 0))
            sp.getString("token", null) else null
        set(v) = sp.edit()
            .putString("token", v)
            .putLong("token_exp", System.currentTimeMillis() + 55 * 60 * 1000)
            .apply()
}
