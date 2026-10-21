package com.example

import java.util.UUID

data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val enabled: Boolean = true
) {
    fun toDisplayString(): String {
        val h = hour.toString().padStart(2, '0')
        val m = minute.toString().padStart(2, '0')
        return "$h:$m"
    }
}

data class WorldClockItem(
    val id: String = UUID.randomUUID().toString(),
    val city: String,
    val timeZoneId: String
)
