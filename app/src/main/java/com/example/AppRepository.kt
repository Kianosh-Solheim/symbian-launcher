package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.text.format.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val name: String,
    val icon: Drawable
)

data class ContactEvent(
    val date: String,
    val label: String,
    val type: Int
)

data class PostalAddress(
    val formattedAddress: String,
    val label: String
)

data class OrganizationInfo(
    val company: String,
    val title: String,
    val department: String = ""
)

data class FormattedContactDate(
    val naturalDate: String,
    val daysRemaining: Int,
    val countdownText: String,
    val ageText: String?
)

fun formatContactDate(rawDate: String?, isBirthday: Boolean = false): FormattedContactDate? {
    if (rawDate.isNullOrBlank()) return null
    val clean = rawDate.trim()
    
    var year: Int? = null
    var month: Int? = null
    var day: Int? = null
    
    try {
        if (clean.startsWith("--") || clean.startsWith("-")) {
            val parts = clean.removePrefix("--").removePrefix("-").split("-", "/", ".")
            if (parts.size >= 2) {
                month = parts[0].toIntOrNull()
                day = parts[1].toIntOrNull()
            }
        } else if (clean.matches(Regex("""^\d{4}[-/.]\d{1,2}[-/.]\d{1,2}$"""))) {
            val parts = clean.split("-", "/", ".")
            year = parts[0].toIntOrNull()
            month = parts[1].toIntOrNull()
            day = parts[2].toIntOrNull()
        } else if (clean.matches(Regex("""^\d{8}$"""))) {
            year = clean.substring(0, 4).toIntOrNull()
            month = clean.substring(4, 6).toIntOrNull()
            day = clean.substring(6, 8).toIntOrNull()
        } else if (clean.matches(Regex("""^\d{1,2}[-/.]\d{1,2}[-/.]\d{4}$"""))) {
            val parts = clean.split("-", "/", ".")
            val p0 = parts[0].toIntOrNull() ?: 1
            val p1 = parts[1].toIntOrNull() ?: 1
            year = parts[2].toIntOrNull()
            if (p0 > 12) {
                day = p0
                month = p1
            } else {
                month = p0
                day = p1
            }
        } else if (clean.matches(Regex("""^\d{1,2}[-/.]\d{1,2}$"""))) {
            val parts = clean.split("-", "/", ".")
            val p0 = parts[0].toIntOrNull() ?: 1
            val p1 = parts[1].toIntOrNull() ?: 1
            if (p0 > 12) {
                day = p0
                month = p1
            } else {
                month = p0
                day = p1
            }
        } else {
            val dateFormats = listOf(
                "yyyy-MM-dd",
                "MMMM d, yyyy",
                "MMM d, yyyy",
                "d MMMM yyyy",
                "d MMM yyyy",
                "MMMM d",
                "MMM d"
            )
            for (fmt in dateFormats) {
                try {
                    val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                    sdf.isLenient = true
                    val parsed = sdf.parse(clean)
                    if (parsed != null) {
                        val cal = java.util.Calendar.getInstance()
                        cal.time = parsed
                        if (fmt.contains("y")) {
                            year = cal.get(java.util.Calendar.YEAR)
                        }
                        month = cal.get(java.util.Calendar.MONTH) + 1
                        day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                        break
                    }
                } catch (e: Exception) {}
            }
        }
    } catch (e: Exception) {}

    if (month == null || day == null || month !in 1..12 || day !in 1..31) {
        return FormattedContactDate(
            naturalDate = clean,
            daysRemaining = -1,
            countdownText = if (isBirthday) "Birthday" else "Event",
            ageText = null
        )
    }

    val todayCal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    
    val targetCal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        set(java.util.Calendar.YEAR, todayCal.get(java.util.Calendar.YEAR))
        set(java.util.Calendar.MONTH, month - 1)
        set(java.util.Calendar.DAY_OF_MONTH, day)
    }

    if (targetCal.before(todayCal)) {
        targetCal.add(java.util.Calendar.YEAR, 1)
    }

    val diffMillis = targetCal.timeInMillis - todayCal.timeInMillis
    val daysRemaining = (diffMillis / (1000L * 60 * 60 * 24)).toInt()

    val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val monthName = monthNames.getOrElse(month - 1) { "Month $month" }
    
    val naturalDate = if (year != null && year in 1900..todayCal.get(java.util.Calendar.YEAR)) {
        "$monthName $day, $year"
    } else {
        "$monthName $day"
    }

    val countdownText = if (isBirthday) {
        when (daysRemaining) {
            0 -> "Birthday is today! 🎂"
            1 -> "Birthday is tomorrow 🎉"
            else -> "Birthday in $daysRemaining days"
        }
    } else {
        when (daysRemaining) {
            0 -> "Today! 🎉"
            1 -> "Tomorrow"
            else -> "In $daysRemaining days"
        }
    }

    val ageText = if (isBirthday && year != null && year in 1900..todayCal.get(java.util.Calendar.YEAR)) {
        val nextAge = targetCal.get(java.util.Calendar.YEAR) - year
        if (daysRemaining == 0) {
            "Turns $nextAge today"
        } else {
            "Turning $nextAge"
        }
    } else null

    return FormattedContactDate(
        naturalDate = naturalDate,
        daysRemaining = daysRemaining,
        countdownText = countdownText,
        ageText = ageText
    )
}

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val emails: List<EmailAddress> = emptyList(),
    val photoUri: String? = null,
    val notes: List<String> = emptyList(),
    val birthday: String? = null,
    val events: List<ContactEvent> = emptyList(),
    val addresses: List<PostalAddress> = emptyList(),
    val organization: OrganizationInfo? = null,
    val websites: List<String> = emptyList(),
    val nicknames: List<String> = emptyList()
) {
    val primaryPhoneNumber: String get() = phoneNumbers.firstOrNull()?.number ?: ""
}

data class CallLogEntry(
    val id: Long,
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long
) {
    fun getFormattedDate(): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = date
        val now = java.util.Calendar.getInstance()
        
        val isToday = cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
        
        val isYesterday = cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) - 1
                
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val timeStr = timeFormat.format(java.util.Date(date))
        
        return when {
            isToday -> "Today, $timeStr"
            isYesterday -> "Yesterday, $timeStr"
            else -> {
                val dateFormat = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                dateFormat.format(java.util.Date(date))
            }
        }
    }

    fun getFormattedDuration(): String {
        if (type == android.provider.CallLog.Calls.MISSED_TYPE || 
            type == android.provider.CallLog.Calls.REJECTED_TYPE ||
            type == android.provider.CallLog.Calls.BLOCKED_TYPE ||
            duration == 0L) {
            return when (type) {
                android.provider.CallLog.Calls.MISSED_TYPE -> "Missed call"
                android.provider.CallLog.Calls.REJECTED_TYPE -> "Declined"
                android.provider.CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                else -> "0s"
            }
        }
        val minutes = duration / 60
        val seconds = duration % 60
        val hours = minutes / 60
        val remMinutes = minutes % 60
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (remMinutes > 0 || hours > 0) append("${remMinutes}m ")
            append("${seconds}s")
        }.trim()
    }
}

data class PhoneNumber(
    val number: String,
    val normalizedNumber: String,
    val label: String
)

data class EmailAddress(
    val address: String,
    val label: String
)

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val color: Int? = null
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val dtStart: Long,
    val dtEnd: Long? = null,
    val allDay: Boolean = false,
    val calendarId: Long = 0L
) {
    fun getEffectiveEndTime(): Long {
        if (dtEnd != null && dtEnd > dtStart) return dtEnd
        if (allDay) {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = dtStart
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
            cal.set(java.util.Calendar.MINUTE, 59)
            cal.set(java.util.Calendar.SECOND, 59)
            cal.set(java.util.Calendar.MILLISECOND, 999)
            return cal.timeInMillis
        }
        return dtStart + (60 * 60 * 1000L) // Default duration of 1 hour if not specified
    }

    fun isOngoing(now: Long = System.currentTimeMillis()): Boolean {
        return dtStart <= now && getEffectiveEndTime() > now
    }

    fun getRemainingTimeFormatted(now: Long = System.currentTimeMillis()): String {
        val remainingMs = getEffectiveEndTime() - now
        if (remainingMs <= 0) return "0s"
        val totalSeconds = remainingMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim()
    }

    fun getStartsInFormatted(now: Long = System.currentTimeMillis()): String {
        val diffMs = dtStart - now
        if (diffMs <= 0) return "Starts in 00h 00m"
        val totalSeconds = diffMs / 1000
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val timeHhMm = String.format(java.util.Locale.US, "%02dh %02dm", hours, minutes)
        return if (days > 0) {
            "Starts in ${days}d $timeHhMm"
        } else {
            "Starts in $timeHhMm"
        }
    }
}

private val t9Map = mapOf(
    'A' to '2', 'B' to '2', 'C' to '2',
    'D' to '3', 'E' to '3', 'F' to '3',
    'G' to '4', 'H' to '4', 'I' to '4',
    'J' to '5', 'K' to '5', 'L' to '5',
    'M' to '6', 'N' to '6', 'O' to '6',
    'P' to '7', 'Q' to '7', 'R' to '7', 'S' to '7',
    'T' to '8', 'U' to '8', 'V' to '8',
    'W' to '9', 'X' to '9', 'Y' to '9', 'Z' to '9'
)

fun String.toT9(): String {
    val sb = java.lang.StringBuilder(this.length)
    for (i in 0 until this.length) {
        val c = this[i].uppercaseChar()
        val mapped = t9Map[c]
        if (mapped != null) {
            sb.append(mapped)
        }
    }
    return sb.toString()
}

class AppRepository(private val context: Context) {
    suspend fun getInstalledApps(iconPackManager: IconPackManager, globalIconPack: String?, overrides: Map<String, String>): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
        
        iconPackManager.loadIconPack(globalIconPack)

        apps.mapNotNull { resolveInfo ->
            try {
                // Ignore our own launcher
                if (resolveInfo.activityInfo.packageName == context.packageName) return@mapNotNull null
                
                val pkgName = resolveInfo.activityInfo.packageName
                val actName = resolveInfo.activityInfo.name
                
                var icon = resolveInfo.loadIcon(pm)
                
                val overridePack = overrides[pkgName]
                if (overridePack != null && overridePack != "DEFAULT") {
                    val parts = overridePack.split("|", limit = 2)
                    if (parts.size == 2) {
                        icon = iconPackManager.getIcon(parts[0], pkgName, actName, icon, parts[1])
                    } else {
                        icon = iconPackManager.getIcon(overridePack, pkgName, actName, icon)
                    }
                } else if (overridePack == null && globalIconPack != null) {
                    icon = iconPackManager.getIcon(globalIconPack, pkgName, actName, icon)
                }
                
                AppInfo(
                    packageName = pkgName,
                    activityName = actName,
                    name = resolveInfo.loadLabel(pm).toString(),
                    icon = icon
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name.lowercase() }
    }
    
    suspend fun getContacts(): List<ContactInfo> = withContext(Dispatchers.IO) {
        val contactsMap = mutableMapOf<String, ContactInfo>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.Data.PHOTO_URI,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA2,
                    ContactsContract.Data.DATA3,
                    ContactsContract.Data.DATA4,
                    ContactsContract.Data.DATA5
                ),
                "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
                ),
                ContactsContract.Data.DISPLAY_NAME + " ASC"
            )
            
            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                val photoIdx = it.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val mimeIdx = it.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = it.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = it.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = it.getColumnIndex(ContactsContract.Data.DATA3)
                val data4Idx = it.getColumnIndex(ContactsContract.Data.DATA4)
                val data5Idx = it.getColumnIndex(ContactsContract.Data.DATA5)
                
                while (it.moveToNext()) {
                    val id = it.getString(idIdx) ?: continue
                    val name = it.getString(nameIdx) ?: ""
                    val photoUri = it.getString(photoIdx)
                    val mime = it.getString(mimeIdx)
                    val data1 = it.getString(data1Idx) ?: ""
                    val data2 = if (!it.isNull(data2Idx)) it.getInt(data2Idx) else 0
                    val data3 = it.getString(data3Idx)
                    val data4 = it.getString(data4Idx)
                    val data5 = it.getString(data5Idx)
                    
                    val existingInfo = contactsMap[id]
                    val phoneList = existingInfo?.phoneNumbers?.toMutableList() ?: mutableListOf()
                    val emailList = existingInfo?.emails?.toMutableList() ?: mutableListOf()
                    val noteList = existingInfo?.notes?.toMutableList() ?: mutableListOf()
                    val eventList = existingInfo?.events?.toMutableList() ?: mutableListOf()
                    val addressList = existingInfo?.addresses?.toMutableList() ?: mutableListOf()
                    val websiteList = existingInfo?.websites?.toMutableList() ?: mutableListOf()
                    val nicknameList = existingInfo?.nicknames?.toMutableList() ?: mutableListOf()
                    var bday = existingInfo?.birthday
                    var orgInfo = existingInfo?.organization
                    
                    when (mime) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, data2, data3).toString()
                                val rawNumber = data1
                                val normalized = rawNumber.replace(Regex("[^0-9+]"), "").let { num ->
                                    if (num.startsWith("+47")) num.substring(3) else num
                                }
                                if (phoneList.none { it.normalizedNumber.endsWith(normalized) || normalized.endsWith(it.normalizedNumber) }) {
                                    phoneList.add(PhoneNumber(rawNumber, normalized, label))
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val label = ContactsContract.CommonDataKinds.Email.getTypeLabel(context.resources, data2, data3).toString()
                                if (emailList.none { it.address.equals(data1, ignoreCase = true) }) {
                                    emailList.add(EmailAddress(data1, label))
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val label = ContactsContract.CommonDataKinds.Event.getTypeLabel(context.resources, data2, data3).toString()
                                if (data2 == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) {
                                    bday = data1
                                }
                                if (eventList.none { it.date == data1 && it.type == data2 }) {
                                    eventList.add(ContactEvent(data1, label, data2))
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank() && !noteList.contains(data1)) {
                                noteList.add(data1)
                            }
                        }
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val label = ContactsContract.CommonDataKinds.StructuredPostal.getTypeLabel(context.resources, data2, data3).toString()
                                if (addressList.none { it.formattedAddress == data1 }) {
                                    addressList.add(PostalAddress(data1, label))
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            val company = data1
                            val title = data4 ?: ""
                            val dept = data5 ?: ""
                            if (company.isNotBlank() || title.isNotBlank()) {
                                orgInfo = OrganizationInfo(company, title, dept)
                            }
                        }
                        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank() && !websiteList.contains(data1)) {
                                websiteList.add(data1)
                            }
                        }
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank() && !nicknameList.contains(data1)) {
                                nicknameList.add(data1)
                            }
                        }
                    }
                    
                    contactsMap[id] = ContactInfo(
                        id = id,
                        name = name,
                        phoneNumbers = phoneList,
                        emails = emailList,
                        photoUri = photoUri ?: existingInfo?.photoUri,
                        notes = noteList,
                        birthday = bday,
                        events = eventList,
                        addresses = addressList,
                        organization = orgInfo,
                        websites = websiteList,
                        nicknames = nicknameList
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        contactsMap.values.filter { it.phoneNumbers.isNotEmpty() || it.emails.isNotEmpty() || it.name.isNotBlank() }.sortedBy { it.name.lowercase() }
    }

    suspend fun getCallLogsForContact(contact: ContactInfo): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CallLogEntry>()
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                return@withContext emptyList()
            }
            val numbersToMatch = contact.phoneNumbers.map { it.normalizedNumber }.filter { it.isNotBlank() }
            val rawNumbers = contact.phoneNumbers.map { it.number.replace(Regex("[^0-9+]"), "") }.filter { it.isNotBlank() }
            val allNormalized = (numbersToMatch + rawNumbers).distinct()

            val cursor = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(
                    android.provider.CallLog.Calls._ID,
                    android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.TYPE,
                    android.provider.CallLog.Calls.DATE,
                    android.provider.CallLog.Calls.DURATION
                ),
                null,
                null,
                "${android.provider.CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(android.provider.CallLog.Calls._ID)
                val numIdx = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(android.provider.CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val num = it.getString(numIdx) ?: ""
                    val normNum = num.replace(Regex("[^0-9+]"), "")
                    val isMatch = allNormalized.any { target ->
                        normNum == target || 
                        (normNum.length >= 7 && target.length >= 7 && (normNum.endsWith(target) || target.endsWith(normNum)))
                    }
                    if (isMatch) {
                        list.add(
                            CallLogEntry(
                                id = it.getLong(idIdx),
                                number = num,
                                type = it.getInt(typeIdx),
                                date = it.getLong(dateIdx),
                                duration = it.getLong(durIdx)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun getAvailableCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )
        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val colorIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val name = it.getString(nameIdx) ?: "Calendar $id"
                    val account = it.getString(accountIdx) ?: ""
                    val color = if (colorIdx != -1 && !it.isNull(colorIdx)) it.getInt(colorIdx) else null
                    list.add(CalendarInfo(id, name, account, color))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    /**
     * Ongoing and upcoming events for the next 30 days. Uses calendar *instances*, so repeating events
     * (weekly lectures, birthdays …) appear on each day they happen, not only on their first date.
     */
    suspend fun getUpcomingEvents(selectedCalendarIds: Set<Long>? = null): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (selectedCalendarIds != null && selectedCalendarIds.isEmpty()) return@withContext emptyList()
        val events = mutableListOf<CalendarEvent>()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, now - 24 * 3_600_000L)
            android.content.ContentUris.appendId(it, now + 30 * 24 * 3_600_000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_ID
        )
        val selection = selectedCalendarIds?.let { ids -> "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString(",") { "?" }})" }
        val args = selectedCalendarIds?.map { it.toString() }?.toTypedArray()
        try {
            context.contentResolver.query(uri, projection, selection, args, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && events.size < 40) {
                    val allDay = !c.isNull(4) && c.getInt(4) == 1
                    var begin = c.getLong(2)
                    var end = if (c.isNull(3)) null else c.getLong(3)
                    if (allDay) {
                        // All-day instances are stored at UTC midnight; move them to local midnight.
                        val off = java.util.TimeZone.getDefault().getOffset(begin)
                        begin -= off; end = end?.let { it - java.util.TimeZone.getDefault().getOffset(it) }
                    }
                    val e = CalendarEvent(c.getLong(0), c.getString(1) ?: "", begin, end, allDay, if (c.isNull(5)) 0L else c.getLong(5))
                    if (e.getEffectiveEndTime() > now) events.add(e)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Ongoing events first, then upcoming in chronological order.
        events.sortedWith(compareBy<CalendarEvent> { !it.isOngoing(now) }.thenBy { it.dtStart })
    }

    data class WeatherForecast(
        val time: String,
        val temperature: Double,
        val symbolCode: String,
        val lowTemp: Double? = null,
        val highTemp: Double? = null,
        val humidity: Double? = null,
        val nightSymbol: String? = null,
        val morningSymbol: String? = null,
        val afternoonSymbol: String? = null,
        val eveningSymbol: String? = null,
        val precipitation: Double? = null,
        val windSpeed: Double? = null,
        val isToday: Boolean = false,
        val dateMillis: Long? = null
    )
    data class WeatherResult(val info: String, val locationName: String?, val hourly: List<WeatherForecast> = emptyList(), val daily: List<WeatherForecast> = emptyList(), val currentTemp: Double = 0.0, val currentSymbol: String = "", val feelsLike: Double? = null, val humidity: Double? = null, val precipitation: Double? = null, val windSpeed: Double? = null)

    suspend fun getWeather(isFahrenheit: Boolean, useGps: Boolean, queryCity: String? = null, is24Hour: Boolean = false, openMeteo: Boolean = false): WeatherResult? = withContext(Dispatchers.IO) {
        try {
            var lat: Double? = null
            var lon: Double? = null
            var locationName: String? = null

            if (queryCity != null) {
                // Android's geocoder first; the free Open-Meteo geocoder when the phone has none.
                try {
                    if (android.location.Geocoder.isPresent()) {
                        @Suppress("DEPRECATION")
                        val addresses = android.location.Geocoder(context, java.util.Locale.getDefault()).getFromLocationName(queryCity, 1)
                        if (!addresses.isNullOrEmpty()) {
                            lat = addresses[0].latitude
                            lon = addresses[0].longitude
                            locationName = addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea ?: queryCity
                        }
                    }
                } catch (e: Exception) { android.util.Log.w("SymbianWeather", "Geocoder failed", e) }
                if (lat == null) {
                    geocodeOnline(queryCity)?.let { (la, lo, name) -> lat = la; lon = lo; locationName = name }
                }
                if (lat == null) {
                    android.util.Log.w("SymbianWeather", "Place not found: $queryCity")
                    return@withContext null
                }
            } else if (useGps) {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                    var location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) 
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    
                    // If last known location is too old or null, try to get a fresh one
                    if (location == null || (System.currentTimeMillis() - location.time > 10 * 60 * 1000)) {
                        try {
                            location = kotlinx.coroutines.withTimeoutOrNull(8_000) { kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    locationManager.getCurrentLocation(
                                        android.location.LocationManager.NETWORK_PROVIDER,
                                        null,
                                        context.mainExecutor,
                                        { loc -> cont.resume(loc) }
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    val listener = object : android.location.LocationListener {
                                        override fun onLocationChanged(loc: android.location.Location) {
                                            locationManager.removeUpdates(this)
                                            if (cont.isActive) cont.resume(loc)
                                        }
                                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                                        override fun onProviderEnabled(provider: String) {}
                                        override fun onProviderDisabled(provider: String) {}
                                    }
                                    locationManager.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, listener, android.os.Looper.getMainLooper())
                                    
                                    // Timeout fallback
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        locationManager.removeUpdates(listener)
                                        if (cont.isActive) cont.resume(null)
                                    }, 5000)
                                }
                            } } ?: location
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (location != null) {
                        lat = location.latitude
                        lon = location.longitude
                    }
                }
            }

            val weatherPrefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
            if (lat == null || lon == null) {
                // Fall back to IP-based location (HTTPS first), then to the last place that worked.
                val ip = ipLocation()
                if (ip != null) {
                    lat = ip.first; lon = ip.second
                    if (locationName == null) locationName = ip.third
                } else if (weatherPrefs.contains("lat")) {
                    lat = weatherPrefs.getFloat("lat", 0f).toDouble()
                    lon = weatherPrefs.getFloat("lon", 0f).toDouble()
                    locationName = locationName ?: weatherPrefs.getString("name", null)
                } else {
                    android.util.Log.w("SymbianWeather", "No location available")
                    return@withContext null
                }
            }
            weatherPrefs.edit().putFloat("lat", lat!!.toFloat()).putFloat("lon", lon!!.toFloat()).apply()

            if (openMeteo) {
                return@withContext openMeteoWeather(lat!!, lon!!, locationName ?: weatherPrefs.getString("name", null), isFahrenheit, is24Hour)
                    ?.also { r -> r.locationName?.let { weatherPrefs.edit().putString("name", it).apply() } }
            }

            // Call MET Norway
            // https://api.met.no/weatherapi/locationforecast/2.0/compact?lat={lat}&lon={lon}
            // MET Norway asks for at most 4 decimals (better caching, fewer rejections).
            val latStr = String.format(java.util.Locale.US, "%.4f", lat)
            val lonStr = String.format(java.util.Locale.US, "%.4f", lon)
            val metUrl = java.net.URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$latStr&lon=$lonStr")
            val connection = metUrl.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SymbianLauncher/1.0 github.com/SymbianLauncher")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val metText = connection.inputStream.bufferedReader().use { it.readText() }
            val metJson = org.json.JSONObject(metText)
            val properties = metJson.getJSONObject("properties")
            val timeseries = properties.getJSONArray("timeseries")
            val currentData = timeseries.getJSONObject(0).getJSONObject("data")
            
            val currentDetails = currentData.getJSONObject("instant").getJSONObject("details")
            val temperatureC = currentDetails.getDouble("air_temperature")
            val windSpeed = if (currentDetails.has("wind_speed")) currentDetails.getDouble("wind_speed") else null
            val humidity = if (currentDetails.has("relative_humidity")) currentDetails.getDouble("relative_humidity") else null
            
            var precipitation: Double? = null
            if (currentData.has("next_1_hours")) {
                val next1Details = currentData.getJSONObject("next_1_hours").optJSONObject("details")
                if (next1Details != null && next1Details.has("precipitation_amount")) {
                    precipitation = next1Details.getDouble("precipitation_amount")
                }
            }
            
            val hourlyForecasts = mutableListOf<WeatherForecast>()
            val dailyForecasts = mutableListOf<WeatherForecast>()
            
            // Collect next 24 hours for hourly, and one per day (e.g. 12:00) for daily
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val outTimeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val outDayFormat = java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault())
            
            val addedDays = mutableSetOf<String>()
            
            class DailyForecastBucket(
                val date: java.util.Date,
                val dayKey: String,
                val temps: MutableList<Double> = mutableListOf(),
                val hums: MutableList<Double> = mutableListOf(),
                val winds: MutableList<Double> = mutableListOf(),
                var precipSum: Double = 0.0,
                val periodSymbols: Array<String?> = arrayOfNulls(4),
                val periodScores: IntArray = intArrayOf(999, 999, 999, 999)
            )
            val dailyBuckets = mutableMapOf<String, DailyForecastBucket>()

            for (i in 0 until timeseries.length()) {
                val item = timeseries.getJSONObject(i)
                val timeStr = item.getString("time")
                val date = sdf.parse(timeStr) ?: continue
                
                val itemData = item.getJSONObject("data")
                val details = itemData.getJSONObject("instant").getJSONObject("details")
                val tC = details.getDouble("air_temperature")
                val hum = if (details.has("relative_humidity")) details.getDouble("relative_humidity") else null
                val wind = if (details.has("wind_speed")) details.getDouble("wind_speed") else null
                val tVal = if (isFahrenheit) (tC * 9/5) + 32 else tC
                
                var precipVal = 0.0
                if (itemData.has("next_1_hours")) {
                    val pObj = itemData.getJSONObject("next_1_hours")
                    if (pObj.has("details")) {
                        precipVal = pObj.getJSONObject("details").optDouble("precipitation_amount", 0.0)
                    }
                } else if (itemData.has("next_6_hours")) {
                    val pObj = itemData.getJSONObject("next_6_hours")
                    if (pObj.has("details")) {
                        precipVal = pObj.getJSONObject("details").optDouble("precipitation_amount", 0.0)
                    }
                }
                
                var rawSym = ""
                var isFrom6Hours = false
                if (itemData.has("next_6_hours")) {
                    rawSym = itemData.getJSONObject("next_6_hours").getJSONObject("summary").optString("symbol_code", "")
                    isFrom6Hours = true
                } else if (itemData.has("next_1_hours")) {
                    rawSym = itemData.getJSONObject("next_1_hours").getJSONObject("summary").optString("symbol_code", "")
                } else if (itemData.has("next_12_hours")) {
                    rawSym = itemData.getJSONObject("next_12_hours").getJSONObject("summary").optString("symbol_code", "")
                }
                
                val displaySym = rawSym.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                
                // Hourly (all items for the daily expansion)
                val formatStr = if (is24Hour) "HH:mm" else "hh:mm a"
                val timeFormat = java.text.SimpleDateFormat(formatStr, java.util.Locale.getDefault())
                hourlyForecasts.add(WeatherForecast(
                    time = timeFormat.format(date).lowercase(), 
                    temperature = tVal, 
                    symbolCode = displaySym, 
                    lowTemp = null, 
                    highTemp = null, 
                    humidity = hum, 
                    dateMillis = date.time,
                    windSpeed = wind,
                    precipitation = precipVal
                ))
                
                // Daily grouping in local time
                val localCal = java.util.Calendar.getInstance().apply { time = date }
                val localHour = localCal.get(java.util.Calendar.HOUR_OF_DAY)
                val dayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getDefault() }.format(date)
                
                val bucket = dailyBuckets.getOrPut(dayKey) { DailyForecastBucket(date, dayKey) }
                bucket.temps.add(tVal)
                if (hum != null) bucket.hums.add(hum)
                if (wind != null) bucket.winds.add(wind)
                if (precipVal > 0.0) bucket.precipSum += precipVal
                
                // Assign period:
                // 0: Night (00:00 - 05:59, ideal hour 3)
                // 1: Morning (06:00 - 11:59, ideal hour 9)
                // 2: Afternoon (12:00 - 17:59, ideal hour 15)
                // 3: Evening (18:00 - 23:59, ideal hour 21)
                val periodIdx = when (localHour) {
                    in 0..5 -> 0
                    in 6..11 -> 1
                    in 12..17 -> 2
                    else -> 3
                }
                val targetHour = when (periodIdx) {
                    0 -> 3
                    1 -> 9
                    2 -> 15
                    else -> 21
                }
                
                val score = if (isFrom6Hours && (localHour == 0 || localHour == 6 || localHour == 12 || localHour == 18)) {
                    0
                } else {
                    kotlin.math.abs(localHour - targetHour) + 1
                }
                
                if (rawSym.isNotEmpty() && score < bucket.periodScores[periodIdx]) {
                    bucket.periodSymbols[periodIdx] = rawSym
                    bucket.periodScores[periodIdx] = score
                }
            }
            
            val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getDefault() }.format(java.util.Date())
            val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            
            for ((dayKey, bucket) in dailyBuckets) {
                if (bucket.temps.isEmpty()) continue
                val isToday = (dayKey == todayKey)
                val minTemp = bucket.temps.minOrNull() ?: 0.0
                val maxTemp = bucket.temps.maxOrNull() ?: 0.0
                val maxHum = bucket.hums.maxOrNull() ?: 0.0
                val maxWind = bucket.winds.maxOrNull() ?: 0.0
                
                val repSymbol = bucket.periodSymbols[2] ?: bucket.periodSymbols[1] ?: bucket.periodSymbols[3] ?: bucket.periodSymbols[0] ?: ""
                val fallback = repSymbol
                
                var nightSym: String? = bucket.periodSymbols[0] ?: fallback
                var morningSym: String? = bucket.periodSymbols[1] ?: fallback
                var afternoonSym: String? = bucket.periodSymbols[2] ?: fallback
                var eveningSym: String? = bucket.periodSymbols[3] ?: fallback
                
                // For today, clear past periods so they appear blank like in Yr.no
                if (isToday) {
                    if (nowHour >= 6) nightSym = null
                    if (nowHour >= 12) morningSym = null
                    if (nowHour >= 18) afternoonSym = null
                    if (nowHour >= 24) eveningSym = null
                }
                
                dailyForecasts.add(
                    WeatherForecast(
                        time = dayKey,
                        temperature = 0.0,
                        symbolCode = repSymbol,
                        lowTemp = minTemp,
                        highTemp = maxTemp,
                        humidity = maxHum,
                        nightSymbol = nightSym,
                        morningSymbol = morningSym,
                        afternoonSymbol = afternoonSym,
                        eveningSymbol = eveningSym,
                        precipitation = bucket.precipSum,
                        windSpeed = maxWind,
                        isToday = isToday,
                        dateMillis = bucket.date.time
                    )
                )
            }
            // limit daily to next 8 days
            if (dailyForecasts.size > 8) {
                dailyForecasts.subList(8, dailyForecasts.size).clear()
            }

            
            val tempValue = if (isFahrenheit) (temperatureC * 9/5) + 32 else temperatureC
            val tempStr = kotlin.math.round(tempValue).toInt().toString()
            val tempUnitStr = if (isFahrenheit) "°F" else "°C"

            var symbolCode = ""
            if (currentData.has("next_1_hours")) {
                symbolCode = currentData.getJSONObject("next_1_hours").getJSONObject("summary").getString("symbol_code")
                // Format symbol code slightly
                symbolCode = symbolCode.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            }

            
            val weatherStr = if (symbolCode.isNotEmpty()) {
                "$tempStr$tempUnitStr $symbolCode"
            } else {
                "$tempStr$tempUnitStr"
            }
            
            if (locationName == null) {
                try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val a = addresses[0]
                    val specific = a.subLocality ?: a.thoroughfare
                    val city = a.locality ?: a.subAdminArea
                    locationName = if (specific != null && city != null && specific != city) {
                        "$specific, $city"
                    } else {
                        city ?: a.adminArea
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                }
            }
            
            val feelsLike = tempValue // simplified feels like
            WeatherResult(
                info = weatherStr, 
                locationName = locationName, 
                hourly = hourlyForecasts, 
                daily = dailyForecasts,
                currentTemp = tempValue,
                currentSymbol = symbolCode,
                feelsLike = feelsLike,
                humidity = humidity,
                precipitation = precipitation,
                windSpeed = windSpeed
            )

        } catch (e: Exception) {
            android.util.Log.w("SymbianWeather", "Weather fetch failed", e)
            null
        }
    }

    data class WeatherWarning(
        val id: String, val title: String, val event: String, val color: String, val area: String,
        val description: String, val instruction: String, val consequences: String,
        val start: Long, val end: Long, val web: String
    )

    /** Official MET Norway warnings (farevarsler) covering a location. Empty outside Norway. */
    suspend fun getWeatherWarnings(lat: Double, lon: Double, norwegian: Boolean): List<WeatherWarning>? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api.met.no/weatherapi/metalerts/2.0/current.json?lang=${if (norwegian) "no" else "en"}" +
                "&lat=" + String.format(java.util.Locale.US, "%.4f", lat) + "&lon=" + String.format(java.util.Locale.US, "%.4f", lon))
            val c = url.openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "SymbianLauncher/2.0 github.com/Kianosh-Solheim/symbian-launcher")
            val j = c.inputStream.bufferedReader().use { org.json.JSONObject(it.readText()) }
            val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val features = j.optJSONArray("features") ?: return@withContext emptyList()
            (0 until features.length()).mapNotNull { i ->
                val f = features.optJSONObject(i) ?: return@mapNotNull null
                val p = f.optJSONObject("properties") ?: return@mapNotNull null
                if (p.optString("status").equals("Test", true)) return@mapNotNull null
                val interval = f.optJSONObject("when")?.optJSONArray("interval")
                fun t(k: Int) = try { iso.parse(interval?.optString(k) ?: "")?.time ?: 0L } catch (_: Exception) { 0L }
                WeatherWarning(
                    id = p.optString("id"), title = p.optString("title"),
                    event = p.optString("eventAwarenessName").ifBlank { p.optString("event") },
                    color = p.optString("riskMatrixColor").ifBlank { p.optString("awareness_level").split(";").getOrNull(1)?.trim() ?: "Yellow" },
                    area = p.optString("area"), description = p.optString("description"), instruction = p.optString("instruction"),
                    consequences = p.optString("consequences"), start = t(0), end = t(1), web = p.optString("web")
                )
            }.sortedByDescending { listOf("yellow", "orange", "red").indexOf(it.color.lowercase()) }
        } catch (e: Exception) {
            android.util.Log.w("SymbianWeather", "Warnings fetch failed", e); null
        }
    }

    /** Place name → coordinates via Open-Meteo's free geocoder (no key needed). */
    fun geocodeOnline(query: String): Triple<Double, Double, String>? = try {
        val lang = if (java.util.Locale.getDefault().language == "nb" || java.util.Locale.getDefault().language == "no") "nb" else "en"
        val url = java.net.URL("https://geocoding-api.open-meteo.com/v1/search?count=1&language=$lang&name=" + java.net.URLEncoder.encode(query, "UTF-8"))
        val c = url.openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 6000
        val j = c.inputStream.bufferedReader().use { org.json.JSONObject(it.readText()) }
        j.optJSONArray("results")?.optJSONObject(0)?.let { r ->
            Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name").ifEmpty { query })
        }
    } catch (e: Exception) {
        android.util.Log.w("SymbianWeather", "Online geocoding failed", e); null
    }

    /** WMO weather code → MET Norway symbol code, so icons work the same for both providers. */
    private fun wmoToMet(code: Int, isDay: Boolean): String {
        val dn = if (isDay) "_day" else "_night"
        return when (code) {
            0 -> "clearsky$dn"; 1 -> "fair$dn"; 2 -> "partlycloudy$dn"; 3 -> "cloudy"
            45, 48 -> "fog"
            51, 53, 55 -> "lightrain"; 56, 57 -> "lightsleet"
            61 -> "lightrain"; 63 -> "rain"; 65 -> "heavyrain"; 66, 67 -> "sleet"
            71 -> "lightsnow"; 73, 77 -> "snow"; 75 -> "heavysnow"
            80 -> "lightrainshowers$dn"; 81 -> "rainshowers$dn"; 82 -> "heavyrainshowers$dn"
            85 -> "lightsnowshowers$dn"; 86 -> "heavysnowshowers$dn"
            95 -> "rainandthunder"; 96, 99 -> "heavyrainandthunder"
            else -> "cloudy"
        }
    }

    private fun displaySymbol(raw: String) =
        raw.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }

    /** Forecast from Open-Meteo, shaped exactly like the MET Norway result. */
    private fun openMeteoWeather(lat: Double, lon: Double, knownName: String?, isFahrenheit: Boolean, is24Hour: Boolean): WeatherResult? = try {
        val url = java.net.URL(
            "https://api.open-meteo.com/v1/forecast?latitude=" + String.format(java.util.Locale.US, "%.4f", lat) +
            "&longitude=" + String.format(java.util.Locale.US, "%.4f", lon) +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,is_day" +
            "&hourly=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,is_day" +
            "&timezone=auto&forecast_days=9&wind_speed_unit=ms" + if (isFahrenheit) "&temperature_unit=fahrenheit" else ""
        )
        val c = url.openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "SymbianLauncher/2.0")
        val j = c.inputStream.bufferedReader().use { org.json.JSONObject(it.readText()) }
        val cur = j.getJSONObject("current")
        val curSym = wmoToMet(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1)
        val temp = cur.getDouble("temperature_2m")
        val h = j.getJSONObject("hourly")
        val times = h.getJSONArray("time"); val temps = h.getJSONArray("temperature_2m"); val codes = h.getJSONArray("weather_code")
        val hums = h.getJSONArray("relative_humidity_2m"); val precs = h.getJSONArray("precipitation")
        val winds = h.getJSONArray("wind_speed_10m"); val days = h.getJSONArray("is_day")
        val parse = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US)
        val outTime = java.text.SimpleDateFormat(if (is24Hour) "HH:mm" else "hh:mm a", java.util.Locale.getDefault())
        val now = System.currentTimeMillis() - 3_600_000L
        val hourly = mutableListOf<WeatherForecast>()
        data class Bucket(val key: String, val date: Long, val t: MutableList<Double> = mutableListOf(), val hum: MutableList<Double> = mutableListOf(),
                          val wind: MutableList<Double> = mutableListOf(), var prec: Double = 0.0, val sym: Array<String?> = arrayOfNulls(4))
        val buckets = linkedMapOf<String, Bucket>()
        for (i in 0 until times.length()) {
            val ts = times.getString(i); val date = parse.parse(ts) ?: continue
            val sym = wmoToMet(codes.optInt(i), days.optInt(i, 1) == 1)
            val t = temps.optDouble(i); val hu = hums.optDouble(i); val pr = precs.optDouble(i, 0.0); val wi = winds.optDouble(i)
            if (date.time >= now) hourly += WeatherForecast(time = outTime.format(date).lowercase(), temperature = t, symbolCode = displaySymbol(sym),
                humidity = hu, dateMillis = date.time, windSpeed = wi, precipitation = pr)
            val key = ts.substring(0, 10)
            val b = buckets.getOrPut(key) { Bucket(key, date.time) }
            b.t += t; if (!hu.isNaN()) b.hum += hu; if (!wi.isNaN()) b.wind += wi; b.prec += pr
            when (ts.substring(11, 13).toInt()) { 3 -> b.sym[0] = sym; 9 -> b.sym[1] = sym; 15 -> b.sym[2] = sym; 21 -> b.sym[3] = sym }
        }
        val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val daily = buckets.values.filter { it.key >= todayKey }.take(8).map { b ->
            val rep = b.sym[2] ?: b.sym[1] ?: b.sym[3] ?: b.sym[0] ?: ""
            val today = b.key == todayKey
            WeatherForecast(time = b.key, temperature = 0.0, symbolCode = rep, lowTemp = b.t.minOrNull(), highTemp = b.t.maxOrNull(),
                humidity = b.hum.maxOrNull(), nightSymbol = if (today && nowHour >= 6) null else b.sym[0] ?: rep,
                morningSymbol = if (today && nowHour >= 12) null else b.sym[1] ?: rep, afternoonSymbol = if (today && nowHour >= 18) null else b.sym[2] ?: rep,
                eveningSymbol = b.sym[3] ?: rep, precipitation = b.prec, windSpeed = b.wind.maxOrNull(), isToday = today, dateMillis = b.date)
        }
        var name = knownName
        if (name == null) try {
            @Suppress("DEPRECATION")
            name = android.location.Geocoder(context, java.util.Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea }
        } catch (_: Exception) { }
        val unit = if (isFahrenheit) "°F" else "°C"
        WeatherResult(
            info = "${kotlin.math.round(temp).toInt()}$unit ${displaySymbol(curSym)}", locationName = name,
            hourly = hourly.take(48), daily = daily, currentTemp = temp, currentSymbol = displaySymbol(curSym),
            feelsLike = cur.optDouble("apparent_temperature").takeIf { !it.isNaN() }, humidity = cur.optDouble("relative_humidity_2m").takeIf { !it.isNaN() },
            precipitation = cur.optDouble("precipitation").takeIf { !it.isNaN() }, windSpeed = cur.optDouble("wind_speed_10m").takeIf { !it.isNaN() }
        )
    } catch (e: Exception) {
        android.util.Log.w("SymbianWeather", "Open-Meteo failed", e); null
    }

    /** Approximate location from the public IP. Tries HTTPS providers before the HTTP-only one. */
    private fun ipLocation(): Triple<Double, Double, String?>? {
        fun get(url: String): org.json.JSONObject? = try {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.setRequestProperty("User-Agent", "SymbianLauncher/2.0")
            c.inputStream.bufferedReader().use { org.json.JSONObject(it.readText()) }
        } catch (e: Exception) {
            android.util.Log.w("SymbianWeather", "IP lookup failed: $url", e); null
        }
        get("https://ipwho.is/")?.let { j ->
            if (j.optBoolean("success", false) && j.has("latitude"))
                return Triple(j.getDouble("latitude"), j.getDouble("longitude"), j.optString("city").ifEmpty { null })
        }
        get("https://ipapi.co/json/")?.let { j ->
            if (j.has("latitude") && !j.isNull("latitude"))
                return Triple(j.getDouble("latitude"), j.getDouble("longitude"), j.optString("city").ifEmpty { null })
        }
        get("http://ip-api.com/json/")?.let { j ->
            if (j.has("lat")) return Triple(j.getDouble("lat"), j.getDouble("lon"), j.optString("city").ifEmpty { null })
        }
        return null
    }
    
    fun launchApp(appInfo: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(appInfo.packageName, appInfo.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
