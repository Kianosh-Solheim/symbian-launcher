package com.example

import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

object NotificationStore {
    val notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())

    /** Fingerprint of the last published list; identical snapshots are dropped. */
    private var lastFingerprint: List<Pair<String, Long>> = emptyList()

    @Synchronized
    fun updateNotifications(activeNotifications: Array<StatusBarNotification>) {
        val list = activeNotifications.toList()
        val fingerprint = list.map { it.key to it.notification.`when` + it.postTime }
        VoipCallMonitor.processNotifications(activeNotifications)
        if (fingerprint == lastFingerprint) return
        lastFingerprint = fingerprint
        notifications.value = list
    }
}
