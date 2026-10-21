package com.example

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SymbianNotificationService : NotificationListenerService() {
    
    companion object {
        var instance: SymbianNotificationService? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        updateStore()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        updateStore()
    }

    override fun onListenerConnected() {
        instance = this
        updateStore()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    fun clearNotification(key: String) {
        cancelNotification(key)
    }

    fun clearAll() {
        cancelAllNotifications()
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private fun updateStore() {
        scope.launch {
            try {
                val notifs = activeNotifications ?: emptyArray()
                NotificationStore.updateNotifications(notifs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
