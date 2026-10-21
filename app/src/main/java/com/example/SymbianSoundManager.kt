package com.example

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

object SymbianSoundManager {
    private var audioTrack: AudioTrack? = null
    
    init {
        try {
            val sampleRate = 11025
            val durationMs = 10
            val numSamples = (durationMs * sampleRate) / 1000
            val sample = ByteArray(numSamples)
            for (i in 0 until numSamples) {
                // Short percussive click sound
                val decay = 1.0 - (i.toDouble() / numSamples)
                val wave = if (i % 8 < 4) 1.0 else -1.0
                sample[i] = (wave * decay * 64).toInt().toByte()
            }
            audioTrack = AudioTrack(
                AudioManager.STREAM_SYSTEM,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_8BIT,
                sample.size,
                AudioTrack.MODE_STATIC
            )
            audioTrack?.write(sample, 0, sample.size)
        } catch (e: Exception) {}
    }
    
    fun playClick() {
        try {
            audioTrack?.stop()
            audioTrack?.reloadStaticData()
            audioTrack?.play()
        } catch (e: Exception) {}
    }
}
