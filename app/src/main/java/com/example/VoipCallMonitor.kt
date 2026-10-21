package com.example

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object VoipCallMonitor {
    val voipCallState = MutableStateFlow(ActiveCallState())

    private val VOIP_APPS = listOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.facebook.orca",
        "org.thoughtcrime.securesms",
        "com.snapchat.android",
        "com.viber.voip",
        "com.discord"
    )

    fun processNotifications(notifs: Array<StatusBarNotification>) {
        val callNotif = notifs.firstOrNull { sbn ->
            val pkg = sbn.packageName
            val isVoipApp = VOIP_APPS.contains(pkg)
            val isCallCategory = sbn.notification.category == Notification.CATEGORY_CALL
            // Some apps use ongoing and a specific category, or we just trust ongoing from these apps if they have chronometer or specific actions
            (isVoipApp && (sbn.isOngoing || isCallCategory)) || (isCallCategory && sbn.isOngoing)
        }

        if (callNotif != null) {
            val notif = callNotif.notification
            val title = notif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val callerName = if (title.isNotEmpty()) title else if (text.isNotEmpty()) text else "VoIP Call"

            // Get start time from notification
            var startTime = System.currentTimeMillis()
            if (notif.extras.getBoolean("android.showChronometer") && notif.`when` > 0) {
                // chronometer base (when) is usually in SystemClock.elapsedRealtime()
                val base = notif.`when`
                val elapsed = android.os.SystemClock.elapsedRealtime() - base
                startTime = System.currentTimeMillis() - elapsed
            } else if (notif.`when` > 0) {
                startTime = notif.`when`
            }

            var hIntent: PendingIntent? = null
            notif.actions?.forEach { action ->
                val actionTitle = action.title?.toString()?.lowercase(java.util.Locale.ROOT) ?: ""
                if (actionTitle.contains("end") || actionTitle.contains("hang") || actionTitle.contains("decline") || 
                    actionTitle.contains("disconnect") || actionTitle.contains("avslutt") || actionTitle.contains("stop")) {
                    hIntent = action.actionIntent
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (action.semanticAction == 6 || action.semanticAction == 4) {
                        hIntent = action.actionIntent
                    }
                }
            }

            voipCallState.update { current ->
                current.copy(
                    isActive = true,
                    contactName = callerName,
                    startTime = if (current.isActive && current.startTime > 0L) current.startTime else startTime,
                    hangUpIntent = hIntent
                )
            }
        } else {
            voipCallState.update { ActiveCallState() }
        }
    }
}
