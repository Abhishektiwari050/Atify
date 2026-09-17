package io.github.sekademi.spotufi.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Helper to detect external USB DACs/dongles and inspect direct bit-perfect capabilities.
 */
object DirectUsbDacHelper {

    private const val PREFS_NAME = "DirectDacPrefs"
    private const val KEY_DIRECT_DAC_ENABLED = "direct_dac_mode_enabled"

    data class UsbDacInfo(
        val name: String,
        val maxSampleRateHz: Int,
        val supportsHiRes: Boolean,
        val formats: List<String>,
    )

    fun isDirectDacModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DIRECT_DAC_ENABLED, true)
    }

    fun setDirectDacModeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DIRECT_DAC_ENABLED, enabled).apply()
    }

    fun getConnectedUsbDac(context: Context): UsbDacInfo? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val usbDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        } ?: return null

        val sampleRates = usbDevice.sampleRates
        val maxRate = if (sampleRates.isNotEmpty()) sampleRates.maxOrNull() ?: 48000 else 48000
        val isHiRes = maxRate >= 96000

        val formats = mutableListOf<String>()
        if (maxRate >= 192000) formats.add("192kHz")
        else if (maxRate >= 96000) formats.add("96kHz")
        else formats.add("48kHz")

        val name = if (usbDevice.productName.isNotBlank()) usbDevice.productName.toString() else "External USB Audio DAC"

        return UsbDacInfo(
            name = name,
            maxSampleRateHz = maxRate,
            supportsHiRes = isHiRes,
            formats = formats,
        )
    }
}
