package com.example

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class SymbianNotificationService : NotificationListenerService() {

    companion object {
        private var ref: WeakReference<SymbianNotificationService>? = null

        /** The connected listener, if any. Held weakly so a dead service is never leaked. */
        val instance: SymbianNotificationService? get() = ref?.get()

        /**
         * Asks Android to reconnect the listener. Budget phones often kill the listener process
         * and never restart it, which silently stops notifications; called whenever the launcher
         * comes to the front and the listener is not connected.
         */
        fun ensureConnected(context: Context) {
            if (instance != null) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestRebind(ComponentName(context, SymbianNotificationService::class.java))
                } catch (_: Exception) { }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) = scheduleUpdate()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = scheduleUpdate()

    override fun onListenerConnected() {
        ref = WeakReference(this)
        scheduleUpdate(immediate = true)
    }

    override fun onListenerDisconnected() {
        ref = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { requestRebind(ComponentName(this, SymbianNotificationService::class.java)) } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        ref = null
        scope.cancel()
        super.onDestroy()
    }

    fun clearNotification(key: String) {
        try { cancelNotification(key) } catch (_: Exception) { }
    }

    fun clearAll() {
        try { cancelAllNotifications() } catch (_: Exception) { }
    }

    /**
     * Coalesces bursts (a group summary plus children, progress updates, media ticks) into a
     * single store update, so the UI recomposes once instead of a dozen times.
     */
    private fun scheduleUpdate(immediate: Boolean = false) {
        pending?.cancel()
        pending = scope.launch {
            if (!immediate) delay(250)
            try {
                NotificationStore.updateNotifications(activeNotifications ?: emptyArray())
            } catch (_: Exception) { }
        }
    }
}
