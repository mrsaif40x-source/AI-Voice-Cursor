package com.aivoicecursor.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.aivoicecursor.AiVoiceCursorApp
import com.aivoicecursor.core.AgentRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Foreground (microphone) service. It only listens after the user pressed Start and always shows
 * a persistent notification with a Stop action, so nothing runs without the user's knowledge.
 */
class VoiceAgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val container get() = (application as AiVoiceCursorApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown("Stopped")
            return START_NOT_STICKY
        }
        startAsForeground()
        if (job?.isActive != true) job = scope.launch { listen() }
        return START_NOT_STICKY
    }

    private suspend fun listen() = coroutineScope {
        val recognizer = container.recognizer
        launch { recognizer.results().collect { text -> container.pipeline.handle(text) } }
        AgentRuntime.listening.value = true
        AgentRuntime.status.value = "Listening…"
        recognizer.start().onFailure { shutdown("Cannot start listening: ${it.message}") }
    }

    private fun shutdown(message: String) {
        job?.cancel()
        container.recognizer.stop()
        AgentRuntime.listening.value = false
        AgentRuntime.status.value = message
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Voice listening", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, VoiceAgentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("AI Voice Cursor is listening")
            .setContentText("Offline. Tap Stop to end.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    override fun onDestroy() {
        scope.cancel()
        container.recognizer.stop()
        AgentRuntime.listening.value = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.aivoicecursor.START"
        const val ACTION_STOP = "com.aivoicecursor.STOP"
        private const val CHANNEL = "listening"
        private const val NOTIFICATION_ID = 1
    }
}
