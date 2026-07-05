package com.soundbooster.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioBoostService : Service() {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var audioManager: AudioManager? = null
    private var currentLevel = 50

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(EXTRA_LEVEL, currentLevel)
            applyBoost(level)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE_LEVEL), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE_LEVEL))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val level = intent?.getIntExtra(EXTRA_LEVEL, 50) ?: 50
        startForeground(NOTIFICATION_ID, buildNotification(level))
        applyBoost(level)
        return START_STICKY
    }

    private fun applyBoost(level: Int) {
        currentLevel = level

        // 1. Максимальная громкость системы
        audioManager?.let { am ->
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        }

        // 2. LoudnessEnhancer на глобальную сессию (0 = весь вывод)
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(0).apply {
                setTargetGain(level * 10)  // 0–1000 мБ 
                enabled = true
            }
        } catch (_: Exception) {}

        // 3. Эквалайзер — поднять все полосы
        try {
            equalizer?.release()
            equalizer = Equalizer(0, 0).apply {
                enabled = true
                val maxLevel = bandLevelRange[1]
                val boost = ((maxLevel.toInt() * level) / 100).toShort()
                for (i in 0 until numberOfBands) {
                    setBandLevel(i.toShort(), boost)
                }
            }
        } catch (_: Exception) {}

        // 4. BassBoost для насыщенности
        try {
            bassBoost?.release()
            bassBoost = BassBoost(0, 0).apply {
                setStrength((level * 10).coerceAtMost(1000).toShort())
                enabled = true
            }
        } catch (_: Exception) {}

        updateNotification(level)
    }

    private fun releaseEffects() {
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
        equalizer = null
        bassBoost = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Sound Booster", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Усиление звука активно"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(level: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioBoostService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sound Booster — АКТИВЕН")
            .setContentText("Усиление: +${level * 10}%  •  Нажмите для управления")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(level: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(level))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
        releaseEffects()
    }

    companion object {
        const val ACTION_UPDATE_LEVEL = "com.soundbooster.app.UPDATE_LEVEL"
        const val ACTION_STOP = "com.soundbooster.app.STOP"
        const val EXTRA_LEVEL = "level"
        const val CHANNEL_ID = "sound_booster_channel"
        const val NOTIFICATION_ID = 1337
    }
}
