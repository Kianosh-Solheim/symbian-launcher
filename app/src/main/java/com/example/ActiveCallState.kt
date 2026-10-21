package com.example

import android.app.PendingIntent

data class ActiveCallState(
    val isActive: Boolean = false,
    val phoneNumber: String? = null,
    val contactName: String? = null,
    val photoUri: String? = null,
    val startTime: Long = 0L,
    val isSpeakerOn: Boolean = false,
    val hangUpIntent: PendingIntent? = null
)
