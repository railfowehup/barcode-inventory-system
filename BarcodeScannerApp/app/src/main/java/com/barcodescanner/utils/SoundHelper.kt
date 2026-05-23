package com.barcodescanner.utils

import android.content.Context
import android.media.MediaPlayer
import android.provider.Settings

/**
 * 声音播放工具类
 */
object SoundHelper {

    private var mediaPlayer: MediaPlayer? = null

    fun init(context: Context) {
        try {
            mediaPlayer = MediaPlayer.create(context, Settings.System.DEFAULT_NOTIFICATION_URI)
            mediaPlayer?.setVolume(1.0f, 1.0f)
        } catch (_: Exception) {}
    }

    fun playBeep() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) seekTo(0)
                start()
            }
        } catch (_: Exception) {}
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
