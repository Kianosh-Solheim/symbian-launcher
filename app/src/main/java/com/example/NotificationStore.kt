package com.example

import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object NotificationStore {
    val notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
    
    fun updateNotifications(activeNotifications: Array<StatusBarNotification>) {
        notifications.update { activeNotifications.toList() }
        VoipCallMonitor.processNotifications(activeNotifications)
    }
}
