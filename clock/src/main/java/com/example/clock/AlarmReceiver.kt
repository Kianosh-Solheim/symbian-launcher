package com.example.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives alarm/timer firings and system events that require rescheduling (boot, clock or time-zone change). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getStringExtra("ALARM_ID") ?: return
                AlarmRingService.start(context, id)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> AlarmStore.scheduleAll(context)
        }
    }

    companion object { const val ACTION_FIRE = "no.kianosh.symbianclock.ALARM_FIRE" }
}
