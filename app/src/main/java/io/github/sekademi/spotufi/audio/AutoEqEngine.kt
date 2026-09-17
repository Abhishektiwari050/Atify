package io.github.sekademi.spotufi.audio

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class AutoEqBand(
    val freq: Int,
    val gain: Float,
    val q: Float = 1.0f,
)

data class AutoEqProfile(
    val name: String,
    val aliases: List<String>,
    val type: String,
    val preampDb: Float,
    val bands: List<AutoEqBand>,
)

/**
 * AutoEQ Headphone Profile Engine.
 * Loads calibration curves for studio headphones, ANC sets, and IEMs.
 * Provides automatic Bluetooth headphone matching and parametric EQ calibration.
 */
object AutoEqEngine {
    private const val TAG = "AutoEqEngine"
    private const val PREFS_NAME = "AutoEqPrefs"
    private const val KEY_SELECTED_PROFILE = "selected_autoeq_profile"
    private const val KEY_AUTO_DETECT_ENABLED = "autoeq_auto_detect_enabled"
    private const val KEY_AUTOEQ_ENABLED = "autoeq_enabled"

    private var cachedProfiles: List<AutoEqProfile>? = null

    fun getProfiles(context: Context): List<AutoEqProfile> {
        cachedProfiles?.let { return it }
        return try {
            val jsonString = context.assets.open("autoeq_presets.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<AutoEqProfile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val type = obj.optString("type", "Headphone")
                val preampDb = obj.optDouble("preampDb", 0.0).toFloat()
                val aliasesJson = obj.optJSONArray("aliases")
                val aliases = mutableListOf<String>()
                if (aliasesJson != null) {
                    for (j in 0 until aliasesJson.length()) {
                        aliases.add(aliasesJson.getString(j))
                    }
                }
                val bandsJson = obj.getJSONArray("bands")
                val bands = mutableListOf<AutoEqBand>()
                for (k in 0 until bandsJson.length()) {
                    val bObj = bandsJson.getJSONObject(k)
                    bands.add(
                        AutoEqBand(
                            freq = bObj.getInt("freq"),
                            gain = bObj.getDouble("gain").toFloat(),
                            q = bObj.optDouble("q", 1.0).toFloat(),
                        )
                    )
                }
                list.add(AutoEqProfile(name, aliases, type, preampDb, bands))
            }
            cachedProfiles = list
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load AutoEQ presets: ${e.message}")
            emptyList()
        }
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTOEQ_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTOEQ_ENABLED, enabled).apply()
        if (enabled) {
            getActiveProfile(context)?.let { applyProfile(context, it) }
        } else {
            EqualizerEngine.setEnabled(context, false)
        }
    }

    fun isAutoDetectEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_DETECT_ENABLED, true)
    }

    fun setAutoDetectEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_DETECT_ENABLED, enabled).apply()
    }

    fun getActiveProfile(context: Context): AutoEqProfile? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SELECTED_PROFILE, null) ?: return null
        return getProfiles(context).firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun setActiveProfile(context: Context, profile: AutoEqProfile?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (profile == null) {
            prefs.edit().remove(KEY_SELECTED_PROFILE).apply()
        } else {
            prefs.edit().putString(KEY_SELECTED_PROFILE, profile.name).apply()
            if (isEnabled(context)) {
                applyProfile(context, profile)
            }
        }
    }

    fun findProfileForDevice(context: Context, deviceName: String?): AutoEqProfile? {
        if (deviceName.isNullOrBlank()) return null
        val profiles = getProfiles(context)
        val cleanName = deviceName.trim().lowercase()

        // 1. Direct match on profile name
        profiles.firstOrNull { cleanName.contains(it.name.lowercase()) || it.name.lowercase().contains(cleanName) }
            ?.let { return it }

        // 2. Match on aliases
        for (p in profiles) {
            for (alias in p.aliases) {
                if (cleanName.contains(alias.lowercase()) || alias.lowercase().contains(cleanName)) {
                    return p
                }
            }
        }
        return null
    }

    fun applyProfile(context: Context, profile: AutoEqProfile) {
        // Apply EQ curve through EqualizerEngine
        EqualizerEngine.setEnabled(context, true)
        val bands = EqualizerEngine.getBands(context)

        for (band in bands) {
            val centerFreq = band.centerFreqHz
            // Find closest AutoEQ band
            val matching = profile.bands.minByOrNull { kotlin.math.abs(it.freq - centerFreq) }
            if (matching != null) {
                // Convert gain in dB to millibels (1 dB = 100 mB)
                val millibels = (matching.gain * 100f).toInt().coerceIn(band.minMillibels, band.maxMillibels)
                EqualizerEngine.setBandLevel(context, band.index, millibels)
            }
        }
        Log.i(TAG, "Applied AutoEQ calibration profile: ${profile.name} (${profile.bands.size} bands)")
    }
}
