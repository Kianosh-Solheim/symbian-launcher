package com.example

import android.content.Context
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build

object SpeakerUtils {
    fun toggleSpeaker(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val enable = !audioManager.isSpeakerphoneOn
        
        try {
            // Unconditionally set this, as it still affects global routing for MODE_IN_CALL on many devices
            audioManager.isSpeakerphoneOn = enable
            
            // For VoIP calls on Android 12+, also try setCommunicationDevice
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enable) {
                    val devices = audioManager.availableCommunicationDevices
                    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                    val devices = audioManager.availableCommunicationDevices
                    val earpiece = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("SpeakerUtils", "Error toggling speaker", e)
        }
    }
}
