package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.StatusBarNotification
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

enum class AppTheme(val displayName: String) {
    DEFAULT("Default (Nokia Blue)"),
    GREY("Grey / Grayscale"),
    BLUE("Deep Blue"),
    RED("Ruby Red"),
    GREEN("Emerald Green"),
    YELLOW("Amber Gold"),
    PINK("Hot Pink"),
    CUSTOM("Custom Theme")
}

enum class T9SearchMode {
    APPS, CONTACTS
}

enum class DateFormatOption(val pattern: String, val label: String) {
    D_MMM("d MMM", "21 Aug"),
    D_MM("d/MM", "21/08"),
    DD_MM_YYYY("dd.MM.yyyy", "21.08.2026"),
    SYSTEM_DEFAULT("", "System default")
}


enum class LocationMethod { GPS, IP, MANUAL }
enum class WeatherProvider { MET_NORWAY, OPEN_METEO }
enum class ClockType { DIGITAL, ANALOGUE }
enum class WeekdayFormat { SHORT, FULL }

enum class TempUnit { CELSIUS, FAHRENHEIT }
enum class WindSpeedUnit { MS, KMH, MPH }
enum class WeatherDailyLanguage(val label: String) {
    NORWEGIAN("Norsk (Yr.no)"),
    ENGLISH("English")
}
enum class WeatherUpdateInterval(val minutes: Int, val label: String) {
    MIN_10(10, "Every 10 minutes"),
    MIN_15(15, "Every 15 minutes"),
    MIN_30(30, "Every 30 minutes"),
    MIN_60(60, "Every 60 minutes")
}

data class T9Result(
    val name: String,
    val detail: String,
    val appInfo: AppInfo? = null,
    val contactInfo: ContactInfo? = null
)

val NokiaPureFontFamily = FontFamily.SansSerif
val PixelBitmapFontFamily = FontFamily.Monospace

enum class AppFont(val displayName: String) {
    NOKIA_PURE("Symbian Sans (Clean)"),
    PIXEL("Retro Pixel (Monospace)"),
    SANS_SERIF("Sans Serif (Default)"),
    SERIF("Classic Serif");
    
    fun toFontFamily(): FontFamily {
        return when (this) {
            NOKIA_PURE -> FontFamily.SansSerif
            PIXEL -> FontFamily.Monospace
            MONOSPACE -> FontFamily.Monospace
            SANS_SERIF -> FontFamily.Default
            SERIF -> FontFamily.Serif
        }
    }
    
    companion object {
        val MONOSPACE = PIXEL
    }
}

data class LauncherState(
    val allApps: List<AppInfo> = emptyList(),
    val pinnedApps: List<AppInfo> = emptyList(),
    val contacts: List<ContactInfo> = emptyList(),
    val todayEvents: List<CalendarEvent> = emptyList(),
    val activeNotifications: List<StatusBarNotification> = emptyList(),
    val searchQuery: String = "",
    val t9Query: String = "",
    val t9SearchMode: T9SearchMode = T9SearchMode.APPS,
    val currentTheme: AppTheme = AppTheme.DEFAULT,
    val customBgColor: Long? = null,
    val customBarColor: Long? = null,
    val customTextColor: Long? = null,
    val customAccentColor: Long? = null,
    val customHighlightColor: Long? = null,
    val currentFont: AppFont = AppFont.NOKIA_PURE,
    val textScale: Float = 1.0f,
    val iconScale: Float = 1.0f,
    val showTime: Boolean = true,
    val clockType: ClockType = ClockType.DIGITAL,
    val is24HourFormat: Boolean = false,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.SYSTEM_DEFAULT,
    val showWeekday: Boolean = true,
    val weekdayFormat: WeekdayFormat = WeekdayFormat.SHORT,
    val messagingApps: List<String> = listOf("com.whatsapp", "com.google.android.apps.messaging", "org.telegram.messenger", "com.snapchat.android", "com.facebook.orca", "org.thoughtcrime.securesms"),
    val emailApps: List<String> = listOf("com.google.android.gm", "com.microsoft.office.outlook", "net.thunderbird.android", "ch.protonmail.android", "com.fsck.k9"),
    val weather: String? = null,
    val weatherLocationName: String? = null,
    val weatherCurrentTemp: Double = 0.0,
    val weatherCurrentSymbol: String = "",
    val weatherFeelsLike: Double? = null,
    val weatherHumidity: Double? = null,
    val weatherPrecipitation: Double? = null,
    val weatherWindSpeed: Double? = null,
    val weatherLastUpdated: Long? = null,
    val weatherUpdateOnlyWhenClicked: Boolean = false,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.MS,
    val weatherDailyLanguage: WeatherDailyLanguage = WeatherDailyLanguage.NORWEGIAN,
    val favoriteLocations: List<String> = emptyList(),
    val hourlyForecast: List<AppRepository.WeatherForecast> = emptyList(),
    val dailyForecast: List<AppRepository.WeatherForecast> = emptyList(),
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val showWeather: Boolean = true,
    val locationMethod: LocationMethod = LocationMethod.GPS,
    val weatherUpdateInterval: WeatherUpdateInterval = WeatherUpdateInterval.MIN_15,
    val manualLocationSearch: String = "",
    val weatherProvider: WeatherProvider = WeatherProvider.MET_NORWAY,
    val enableT9Search: Boolean = true,
    val t9SearchApps: Boolean = true,
    val t9SearchContacts: Boolean = true,
    val showPinnedAppsSection: Boolean = true,
    val showMessagesSection: Boolean = true,
    val showEmailSection: Boolean = true,
    val showCalendarSection: Boolean = true,
    val useIconPackForHub: Boolean = false,
    val showLauncherStatusBar: Boolean = true,
    val showAndroidStatusBar: Boolean = true,
    val showWallpaper: Boolean = false,
    val wallpaperTransparency: Float = 0.5f,
    val hapticFeedbackEnabled: Boolean = true,
    val symbianSoundsEnabled: Boolean = true,
    val hapticIntensity: Float = 0.5f,
    val currentIconPack: String? = null,
    val individualIconPacks: Map<String, String> = emptyMap(),
    val availableIconPacks: List<IconPackInfo> = emptyList(),
    val availableCalendars: List<CalendarInfo> = emptyList(),
    val selectedCalendarIds: Set<Long>? = null,
    val walletPasses: List<WalletPass> = emptyList(),
    val alarms: List<AlarmItem> = emptyList(),
    val worldClocks: List<WorldClockItem> = emptyList(),
    val walletCategoryTab: Int = 0,
    val clockTab: Int = 0,
    val weatherTab: Int = 0,
    val usageTab: Int = 0,
    val showClockScreen: Boolean = false,
    val isLoading: Boolean = true,
    val isExtremeBatterySaver: Boolean = false,
    val extremeBatterySaverApp1: String? = null,
    val extremeBatterySaverApp2: String? = null
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val walletRepository = WalletRepository(application)
    private val prefs: SharedPreferences = application.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    private val iconPackManager = IconPackManager(application)

    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()
    private var weatherJob: kotlinx.coroutines.Job? = null

    private var appsReloadJob: kotlinx.coroutines.Job? = null
    private var contactsReloadJob: kotlinx.coroutines.Job? = null
    private var lastCalendarLoad = 0L

    // Reload contacts when the address book changes (debounced: sync adapters fire in bursts).
    private val contactsObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { reloadContacts() }
    }

    init {
        loadSettings()
        refreshAll()
        startWeatherLoop()
        try {
            application.contentResolver.registerContentObserver(
                android.provider.ContactsContract.Contacts.CONTENT_URI, true, contactsObserver
            )
        } catch (_: Exception) { }
        
        viewModelScope.launch {
            NotificationStore.notifications.collect { notifs ->
                _state.update { it.copy(activeNotifications = notifs) }
            }
        }

        viewModelScope.launch {
            walletRepository.passes.collect { passes ->
                _state.update { it.copy(walletPasses = passes) }
            }
        }
    }

    fun updateState(update: (LauncherState) -> LauncherState) { _state.update(update); saveSettings() }

    fun logInternalAppUsage(appName: String, durationMs: Long) {
        if (durationMs <= 0) return
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dateString = sdf.format(java.util.Date())
        val key = "usage_internal_${dateString}_$appName"
        val current = prefs.getLong(key, 0L)
        prefs.edit().putLong(key, current + durationMs).apply()
    }

    fun getInternalAppUsage(appName: String, daysBack: Int): Long {
        var total = 0L
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val calendar = java.util.Calendar.getInstance()
        for (i in 0..daysBack) {
            val dateString = sdf.format(calendar.time)
            val key = "usage_internal_${dateString}_$appName"
            total += prefs.getLong(key, 0L)
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return total
    }

    
    private fun saveSettings() {
        val st = _state.value
        prefs.edit().apply {
            putBoolean("showTime", st.showTime)
            putString("clockType", st.clockType.name)
            putBoolean("is24Hour", st.is24HourFormat)
            putBoolean("showSeconds", st.showSeconds)
            putBoolean("showDate", st.showDate)
            putString("dateFormat", st.dateFormat.name)
            putBoolean("showWeekday", st.showWeekday)
            putString("weekdayFormat", st.weekdayFormat.name)
            putString("locationMethod", st.locationMethod.name)
            putString("weatherProvider", st.weatherProvider.name)
            putBoolean("showWeather", st.showWeather)
            putBoolean("enableT9Search", st.enableT9Search)
            putBoolean("t9SearchApps", st.t9SearchApps)
            putBoolean("t9SearchContacts", st.t9SearchContacts)
            putBoolean("showPinnedAppsSection", st.showPinnedAppsSection)
            putBoolean("showMessagesSection", st.showMessagesSection)
            putBoolean("showEmailSection", st.showEmailSection)
            putBoolean("showCalendarSection", st.showCalendarSection)
            putString("tempUnit", st.tempUnit.name)
            putString("weatherUpdateInterval", st.weatherUpdateInterval.name)
            putBoolean("weatherUpdateOnlyWhenClicked", st.weatherUpdateOnlyWhenClicked)
            putString("windSpeedUnit", st.windSpeedUnit.name)
            putString("weatherDailyLanguage", st.weatherDailyLanguage.name)
            
            // Convert messaging apps
            val mArray = org.json.JSONArray()
            st.messagingApps.forEach { mArray.put(it) }
            putString("messagingApps", mArray.toString())

            // Convert email apps
            val emArray = org.json.JSONArray()
            st.emailApps.forEach { emArray.put(it) }
            putString("emailApps", emArray.toString())
            
            val fArray = org.json.JSONArray()
            st.favoriteLocations.forEach { fArray.put(it) }
            putString("favoriteLocations", fArray.toString())

            if (st.selectedCalendarIds != null) {
                val calArray = org.json.JSONArray()
                st.selectedCalendarIds.forEach { calArray.put(it) }
                putString("selectedCalendarIds", calArray.toString())
            } else {
                remove("selectedCalendarIds")
            }
            putBoolean("isExtremeBatterySaver", st.isExtremeBatterySaver)
            if (st.extremeBatterySaverApp1 != null) putString("extremeBatterySaverApp1", st.extremeBatterySaverApp1) else remove("extremeBatterySaverApp1")
            if (st.extremeBatterySaverApp2 != null) putString("extremeBatterySaverApp2", st.extremeBatterySaverApp2) else remove("extremeBatterySaverApp2")
            
            apply()
        }
    }

    override fun onCleared() {
        try { getApplication<Application>().contentResolver.unregisterContentObserver(contactsObserver) } catch (_: Exception) { }
        super.onCleared()
    }

    /** Called when apps are installed/removed/updated. Debounced, and reloads only the app list. */
    fun loadApps() {
        appsReloadJob?.cancel()
        appsReloadJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1_500)
            val apps = repository.getInstalledApps(iconPackManager, _state.value.currentIconPack, _state.value.individualIconPacks)
            val pinnedIds = getPinnedAppIds()
            val pinnedApps = apps.filter { pinnedIds.contains(it.packageName) }.sortedBy { pinnedIds.indexOf(it.packageName) }
            _state.update { it.copy(
                allApps = apps,
                pinnedApps = pinnedApps,
                availableIconPacks = iconPackManager.getAvailableIconPacks()
            ) }
        }
    }

    private fun reloadContacts() {
        contactsReloadJob?.cancel()
        contactsReloadJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2_000)
            val fetchedContacts = repository.getContacts()
            _state.update { it.copy(contacts = fetchedContacts) }
        }
    }

    /** Full load: apps, contacts, calendars. Used at start-up and after permissions are granted. */
    fun refreshAll() {
        appsReloadJob?.cancel()
        appsReloadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val apps = repository.getInstalledApps(
                iconPackManager, 
                _state.value.currentIconPack, 
                _state.value.individualIconPacks
            )
            val pinnedIds = getPinnedAppIds()
            val pinnedApps = apps.filter { pinnedIds.contains(it.packageName) }.sortedBy { pinnedIds.indexOf(it.packageName) }
            
            val fetchedContacts = repository.getContacts()
            val fetchedCalendars = repository.getAvailableCalendars()
            val fetchedEvents = repository.getUpcomingEvents(_state.value.selectedCalendarIds)
            
            val messagingAppsStr = prefs.getString("messagingApps", null)
            val currentMsgApps = if (messagingAppsStr == null) {
                val detected = MessagingUtils.detectInstalledMessagingApps(apps, getApplication()).map { it.packageName }
                val initialList = (_state.value.messagingApps + detected).distinct()
                val array = JSONArray()
                initialList.forEach { array.put(it) }
                prefs.edit().putString("messagingApps", array.toString()).apply()
                initialList
            } else {
                _state.value.messagingApps
            }

            val emailAppsStr = prefs.getString("emailApps", null)
            val currentEmailApps = if (emailAppsStr == null) {
                val detected = MessagingUtils.detectInstalledEmailApps(apps, getApplication()).map { it.packageName }
                val initialList = (_state.value.emailApps + detected).distinct()
                val array = JSONArray()
                initialList.forEach { array.put(it) }
                prefs.edit().putString("emailApps", array.toString()).apply()
                initialList
            } else {
                _state.value.emailApps
            }

            _state.update { it.copy(
                allApps = apps,
                pinnedApps = pinnedApps,
                messagingApps = currentMsgApps,
                emailApps = currentEmailApps,
                contacts = fetchedContacts,
                availableCalendars = fetchedCalendars,
                todayEvents = fetchedEvents,
                availableIconPacks = iconPackManager.getAvailableIconPacks(),
                isLoading = false
            ) }
            lastCalendarLoad = System.currentTimeMillis()
        }
    }


    fun startWeatherLoop() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            var first = true
            while (true) {
                // "Only when clicked" still loads once at start-up so the home screen isn't blank.
                val ok = if (!_state.value.weatherUpdateOnlyWhenClicked || first) fetchWeather() else true
                first = false
                val interval = _state.value.weatherUpdateInterval.minutes * 60 * 1000L
                // On failure (offline, tunnel, flight mode) retry after 5 minutes instead of a full interval.
                kotlinx.coroutines.delay(if (ok) interval else minOf(interval, 5 * 60 * 1000L))
            }
        }
    }

    private val weatherMutex = kotlinx.coroutines.sync.Mutex()

    fun loadWeather() {
        viewModelScope.launch { fetchWeather() }
    }

    /** Fetches weather; keeps the last good forecast if the request fails. Returns success. */
    private suspend fun fetchWeather(): Boolean = weatherMutex.withLock {
            val weatherData = try {
                repository.getWeather(_state.value.tempUnit == TempUnit.FAHRENHEIT, _state.value.locationMethod == LocationMethod.GPS, null, _state.value.is24HourFormat)
            } catch (e: Exception) { null }
            if (weatherData == null) return@withLock false
            
_state.update { 
    it.copy(
        weather = weatherData?.info, 
        weatherLocationName = weatherData?.locationName, 
        hourlyForecast = weatherData?.hourly ?: emptyList(), 
        dailyForecast = weatherData?.daily ?: emptyList(),
        weatherCurrentTemp = weatherData?.currentTemp ?: 0.0,
        weatherCurrentSymbol = weatherData?.currentSymbol ?: "",
        weatherLastUpdated = System.currentTimeMillis(),
        weatherFeelsLike = weatherData?.feelsLike,
        weatherHumidity = weatherData?.humidity,
        weatherPrecipitation = weatherData?.precipitation,
        weatherWindSpeed = weatherData?.windSpeed
    ) 
}
            true
    }

    fun updateSearchQuery(query: String) {
        
_state.update { it.copy(searchQuery = query) }
    }
    
    fun appendT9(char: Char) {
        if (_state.value.enableT9Search) {
            _state.update { it.copy(t9Query = it.t9Query + char) }
        }
    }
    
    fun backspaceT9() {
        _state.update { 
            val newQuery = if (it.t9Query.isNotEmpty()) it.t9Query.dropLast(1) else ""
            it.copy(t9Query = newQuery) 
        }
    }

    fun setT9Mode(mode: T9SearchMode) {
        prefs.edit().putString("t9mode", mode.name).apply()
        _state.update { it.copy(t9SearchMode = mode, t9Query = "") }
    }

    fun switchT9Mode(mode: T9SearchMode, keepQuery: Boolean = true) {
        prefs.edit().putString("t9mode", mode.name).apply()
        _state.update { it.copy(t9SearchMode = mode, t9Query = if (keepQuery) it.t9Query else "") }
    }

    fun toggleClockDialog(show: Boolean) {
        _state.update { it.copy(showClockScreen = show) }
    }

    suspend fun getCallLogsForContact(contact: ContactInfo): List<CallLogEntry> {
        return repository.getCallLogsForContact(contact)
    }

    fun launchApp(appInfo: AppInfo) {
        val clockKeywords = listOf("deskclock", "sec.android.app.clockpackage")
        if (clockKeywords.any { appInfo.packageName.contains(it, ignoreCase = true) } || appInfo.name.equals("Clock", ignoreCase = true)) {
            _state.update { it.copy(showClockScreen = true) }
            return
        }
        repository.launchApp(appInfo)
    }
    
    fun uninstallApp(packageName: String) {
        repository.uninstallApp(packageName)
    }

    fun togglePinned(appInfo: AppInfo) {
        val currentPinned = _state.value.pinnedApps.toMutableList()
        val isPinned = currentPinned.any { it.packageName == appInfo.packageName }
        
        if (isPinned) {
            currentPinned.removeAll { it.packageName == appInfo.packageName }
        } else {
            currentPinned.add(appInfo)
        }
        
        savePinnedAppIds(currentPinned.map { it.packageName })
        
_state.update { it.copy(pinnedApps = currentPinned) }
    }

    fun movePinnedAppUp(appInfo: AppInfo) {
        val current = _state.value.pinnedApps.toMutableList()
        val index = current.indexOfFirst { it.packageName == appInfo.packageName }
        if (index > 0) {
            val temp = current[index]
            current[index] = current[index - 1]
            current[index - 1] = temp
            savePinnedAppIds(current.map { it.packageName })
            
_state.update { it.copy(pinnedApps = current) }
        }
    }

    fun movePinnedAppDown(appInfo: AppInfo) {
        val current = _state.value.pinnedApps.toMutableList()
        val index = current.indexOfFirst { it.packageName == appInfo.packageName }
        if (index != -1 && index < current.size - 1) {
            val temp = current[index]
            current[index] = current[index + 1]
            current[index + 1] = temp
            savePinnedAppIds(current.map { it.packageName })
            
_state.update { it.copy(pinnedApps = current) }
        }
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("theme", theme.name).apply()
        
_state.update { it.copy(currentTheme = theme) }
    }

    fun setFont(font: AppFont) {
        prefs.edit().putString("font", font.name).apply()
        
_state.update { it.copy(currentFont = font) }
    }



    
    fun setTextScale(scale: Float) {
        prefs.edit().putFloat("textScale", scale).apply()
        
_state.update { it.copy(textScale = scale) }
    }

    fun setIconScale(scale: Float) {
        prefs.edit().putFloat("iconScale", scale).apply()
        
_state.update { it.copy(iconScale = scale) }
    }



    fun setMessagingApps(apps: Collection<String>) {
        val list = apps.distinct()
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString("messagingApps", array.toString()).apply()
        _state.update { it.copy(messagingApps = list) }
    }

    fun toggleMessagingApp(packageName: String) {
        val current = _state.value.messagingApps.toMutableList()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        val array = JSONArray()
        current.forEach { array.put(it) }
        prefs.edit().putString("messagingApps", array.toString()).apply()
        
        _state.update { it.copy(messagingApps = current) }
    }

    fun moveMessagingAppUp(appInfo: AppInfo) {
        val current = _state.value.messagingApps.toMutableList()
        val visibleInstalledPkgs = _state.value.allApps
            .filter { current.contains(it.packageName) }
            .sortedBy { current.indexOf(it.packageName) }
            .map { it.packageName }

        val visibleIndex = visibleInstalledPkgs.indexOf(appInfo.packageName)
        if (visibleIndex > 0) {
            val prevPkg = visibleInstalledPkgs[visibleIndex - 1]
            val indexCurrent = current.indexOf(appInfo.packageName)
            val indexPrev = current.indexOf(prevPkg)
            if (indexCurrent != -1 && indexPrev != -1) {
                current[indexCurrent] = prevPkg
                current[indexPrev] = appInfo.packageName
                val array = JSONArray()
                current.forEach { array.put(it) }
                prefs.edit().putString("messagingApps", array.toString()).apply()
                _state.update { it.copy(messagingApps = current) }
            }
        }
    }

    fun moveMessagingAppDown(appInfo: AppInfo) {
        val current = _state.value.messagingApps.toMutableList()
        val visibleInstalledPkgs = _state.value.allApps
            .filter { current.contains(it.packageName) }
            .sortedBy { current.indexOf(it.packageName) }
            .map { it.packageName }

        val visibleIndex = visibleInstalledPkgs.indexOf(appInfo.packageName)
        if (visibleIndex != -1 && visibleIndex < visibleInstalledPkgs.size - 1) {
            val nextPkg = visibleInstalledPkgs[visibleIndex + 1]
            val indexCurrent = current.indexOf(appInfo.packageName)
            val indexNext = current.indexOf(nextPkg)
            if (indexCurrent != -1 && indexNext != -1) {
                current[indexCurrent] = nextPkg
                current[indexNext] = appInfo.packageName
                val array = JSONArray()
                current.forEach { array.put(it) }
                prefs.edit().putString("messagingApps", array.toString()).apply()
                _state.update { it.copy(messagingApps = current) }
            }
        }
    }

    fun setEmailApps(apps: Collection<String>) {
        val list = apps.distinct()
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString("emailApps", array.toString()).apply()
        _state.update { it.copy(emailApps = list) }
    }

    fun toggleEmailApp(packageName: String) {
        val current = _state.value.emailApps.toMutableList()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        val array = JSONArray()
        current.forEach { array.put(it) }
        prefs.edit().putString("emailApps", array.toString()).apply()
        
        _state.update { it.copy(emailApps = current) }
    }

    fun moveEmailAppUp(appInfo: AppInfo) {
        val current = _state.value.emailApps.toMutableList()
        val visibleInstalledPkgs = _state.value.allApps
            .filter { current.contains(it.packageName) }
            .sortedBy { current.indexOf(it.packageName) }
            .map { it.packageName }

        val visibleIndex = visibleInstalledPkgs.indexOf(appInfo.packageName)
        if (visibleIndex > 0) {
            val prevPkg = visibleInstalledPkgs[visibleIndex - 1]
            val indexCurrent = current.indexOf(appInfo.packageName)
            val indexPrev = current.indexOf(prevPkg)
            if (indexCurrent != -1 && indexPrev != -1) {
                current[indexCurrent] = prevPkg
                current[indexPrev] = appInfo.packageName
                val array = JSONArray()
                current.forEach { array.put(it) }
                prefs.edit().putString("emailApps", array.toString()).apply()
                _state.update { it.copy(emailApps = current) }
            }
        }
    }

    fun moveEmailAppDown(appInfo: AppInfo) {
        val current = _state.value.emailApps.toMutableList()
        val visibleInstalledPkgs = _state.value.allApps
            .filter { current.contains(it.packageName) }
            .sortedBy { current.indexOf(it.packageName) }
            .map { it.packageName }

        val visibleIndex = visibleInstalledPkgs.indexOf(appInfo.packageName)
        if (visibleIndex != -1 && visibleIndex < visibleInstalledPkgs.size - 1) {
            val nextPkg = visibleInstalledPkgs[visibleIndex + 1]
            val indexCurrent = current.indexOf(appInfo.packageName)
            val indexNext = current.indexOf(nextPkg)
            if (indexCurrent != -1 && indexNext != -1) {
                current[indexCurrent] = nextPkg
                current[indexNext] = appInfo.packageName
                val array = JSONArray()
                current.forEach { array.put(it) }
                prefs.edit().putString("emailApps", array.toString()).apply()
                _state.update { it.copy(emailApps = current) }
            }
        }
    }

    fun setGlobalIconPack(packageName: String?) {
        prefs.edit().putString("globalIconPack", packageName).apply()
        
_state.update { it.copy(currentIconPack = packageName) }
        loadApps()
    }

    fun getIconList(iconPackPackageName: String): List<String> = iconPackManager.getIconList(iconPackPackageName)
    fun getDrawableByName(iconPackPackageName: String, drawableName: String) = iconPackManager.getDrawableByName(iconPackPackageName, drawableName)

    fun setIndividualIconPack(appPackageName: String, iconPackPackageName: String) {
        val currentMap = _state.value.individualIconPacks.toMutableMap()
        currentMap[appPackageName] = iconPackPackageName
        saveIndividualIconPacks(currentMap)
        
_state.update { it.copy(individualIconPacks = currentMap) }
        loadApps()
    }

    fun clearIndividualIconPack(appPackageName: String) {
        val currentMap = _state.value.individualIconPacks.toMutableMap()
        currentMap.remove(appPackageName)
        saveIndividualIconPacks(currentMap)
        
_state.update { it.copy(individualIconPacks = currentMap) }
        loadApps()
    }

    private fun saveIndividualIconPacks(map: Map<String, String>) {
        val json = org.json.JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString("individualIconPacks", json.toString()).apply()
    }

    fun setCustomColor(type: String, color: Long?) {
        val editor = prefs.edit()
        if (color == null) editor.remove(type) else editor.putLong(type, color)
        editor.apply()
        _state.update {
            when (type) {
                "customBgColor" -> it.copy(customBgColor = color)
                "customBarColor" -> it.copy(customBarColor = color)
                "customTextColor" -> it.copy(customTextColor = color)
                "customAccentColor" -> it.copy(customAccentColor = color)
                "customHighlightColor" -> it.copy(customHighlightColor = color)
                else -> it
            }
        }
    }

    fun toggleShowLauncherStatusBar() {
        val newShow = !_state.value.showLauncherStatusBar
        prefs.edit().putBoolean("showLauncherStatusBar", newShow).apply()
        
_state.update { it.copy(showLauncherStatusBar = newShow) }
    }

    fun toggleShowAndroidStatusBar() {
        val newShow = !_state.value.showAndroidStatusBar
        prefs.edit().putBoolean("showAndroidStatusBar", newShow).apply()
        
_state.update { it.copy(showAndroidStatusBar = newShow) }
    }
    
    fun toggleHapticFeedback() {
        val newHaptic = !_state.value.hapticFeedbackEnabled
        prefs.edit().putBoolean("hapticFeedbackEnabled", newHaptic).apply()
        
_state.update { it.copy(hapticFeedbackEnabled = newHaptic) }
    }

    fun toggleSymbianSounds() {
        val newSounds = !_state.value.symbianSoundsEnabled
        prefs.edit().putBoolean("symbianSoundsEnabled", newSounds).apply()
        _state.update { it.copy(symbianSoundsEnabled = newSounds) }
    }

    fun setHapticIntensity(intensity: Float) {
        prefs.edit().putFloat("hapticIntensity", intensity).apply()
        
_state.update { it.copy(hapticIntensity = intensity) }
    }

    fun setUseIconPackForHub(use: Boolean) {
        prefs.edit().putBoolean("useIconPackForHub", use).apply()
        _state.update { it.copy(useIconPackForHub = use) }
    }

    fun setShowWallpaper(show: Boolean) {
        prefs.edit().putBoolean("showWallpaper", show).apply()
        _state.update { it.copy(showWallpaper = show) }
    }


    fun setWallpaperTransparency(transparency: Float) {
        prefs.edit().putFloat("wallpaperTransparency", transparency).apply()
        _state.update { it.copy(wallpaperTransparency = transparency) }
    }

    /** Reloads calendars and today's events. Throttled to once a minute unless [force]. */
    fun loadCalendarData(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCalendarLoad < 60_000L) return
        lastCalendarLoad = now
        viewModelScope.launch {
            val fetchedCalendars = repository.getAvailableCalendars()
            val fetchedEvents = repository.getUpcomingEvents(_state.value.selectedCalendarIds)
            _state.update { it.copy(
                availableCalendars = fetchedCalendars,
                todayEvents = fetchedEvents
            ) }
        }
    }

    fun toggleCalendarSelection(calId: Long) {
        val currentSelected = _state.value.selectedCalendarIds
        val allCalIds = _state.value.availableCalendars.map { it.id }.toSet()
        val baseSet = currentSelected ?: allCalIds
        val newSet = if (baseSet.contains(calId)) {
            baseSet - calId
        } else {
            baseSet + calId
        }
        _state.update { it.copy(selectedCalendarIds = newSet) }
        saveSettings()
        viewModelScope.launch {
            val events = repository.getUpcomingEvents(newSet)
            _state.update { it.copy(todayEvents = events) }
        }
    }

    fun selectAllCalendars() {
        _state.update { it.copy(selectedCalendarIds = null) }
        saveSettings()
        viewModelScope.launch {
            val events = repository.getUpcomingEvents(null)
            _state.update { it.copy(todayEvents = events) }
        }
    }

    fun deselectAllCalendars() {
        _state.update { it.copy(selectedCalendarIds = emptySet()) }
        saveSettings()
        viewModelScope.launch {
            _state.update { it.copy(todayEvents = emptyList()) }
        }
    }

    private fun loadSettings() {
        loadAlarms()
        loadWorldClocks()
        val themeStr = prefs.getString("theme", AppTheme.DEFAULT.name)
        val theme = runCatching { AppTheme.valueOf(themeStr!!) }.getOrDefault(AppTheme.DEFAULT)
        
        val customBgColor = if (prefs.contains("customBgColor")) prefs.getLong("customBgColor", 0) else null
        val customBarColor = if (prefs.contains("customBarColor")) prefs.getLong("customBarColor", 0) else null
        val customTextColor = if (prefs.contains("customTextColor")) prefs.getLong("customTextColor", 0) else null
        val customAccentColor = if (prefs.contains("customAccentColor")) prefs.getLong("customAccentColor", 0) else null
        val customHighlightColor = if (prefs.contains("customHighlightColor")) prefs.getLong("customHighlightColor", 0) else null

        val fontStr = prefs.getString("font", AppFont.NOKIA_PURE.name)
        val font = runCatching { AppFont.valueOf(fontStr!!) }.getOrDefault(AppFont.NOKIA_PURE)
        
        val t9ModeStr = prefs.getString("t9mode", T9SearchMode.APPS.name)
        val t9Mode = runCatching { T9SearchMode.valueOf(t9ModeStr!!) }.getOrDefault(T9SearchMode.APPS)

        val is24Hour = prefs.getBoolean("is24Hour", false)
        val dateFormatStr = prefs.getString("dateFormat", DateFormatOption.DD_MM_YYYY.name)
        val dateFormat = runCatching { DateFormatOption.valueOf(dateFormatStr!!) }.getOrDefault(DateFormatOption.DD_MM_YYYY)

        val tempUnitStr = prefs.getString("tempUnit", TempUnit.CELSIUS.name)
        val tempUnit = runCatching { TempUnit.valueOf(tempUnitStr!!) }.getOrDefault(TempUnit.CELSIUS)

        val showWeather = prefs.getBoolean("showWeather", true)
        val useGpsForWeather = false
        val showDate = prefs.getBoolean("showDate", true)
        val showLauncherStatusBar = prefs.getBoolean("showLauncherStatusBar", true)
        val showAndroidStatusBar = prefs.getBoolean("showAndroidStatusBar", true)
        val showWallpaper = prefs.getBoolean("showWallpaper", false)
        val wallpaperTransparency = prefs.getFloat("wallpaperTransparency", 0.5f)
        val showAnalogClock = true
        val hapticFeedbackEnabled = prefs.getBoolean("hapticFeedbackEnabled", true)
        val symbianSoundsEnabled = prefs.getBoolean("symbianSoundsEnabled", true)
        val hapticIntensity = prefs.getFloat("hapticIntensity", 0.5f)
        val textScale = prefs.getFloat("textScale", 1.0f)
        val iconScale = prefs.getFloat("iconScale", 1.0f)
        val showSeconds = prefs.getBoolean("showSeconds", false)

        val favLocsStr = prefs.getString("favoriteLocations", "[]")
        val favLocs = mutableListOf<String>()
        try {
            val array = org.json.JSONArray(favLocsStr)
            for (i in 0 until array.length()) {
                favLocs.add(array.getString(i))
            }
        } catch (e: Exception) {}

        val messagingAppsStr = prefs.getString("messagingApps", null)
        val msgApps = mutableListOf<String>()
        if (messagingAppsStr != null) {
            try {
                val array = JSONArray(messagingAppsStr)
                for (i in 0 until array.length()) {
                    msgApps.add(array.getString(i))
                }
            } catch (e: Exception) {}
        } else {
            msgApps.addAll(listOf("com.whatsapp", "com.google.android.apps.messaging", "org.telegram.messenger", "com.snapchat.android", "com.facebook.orca", "org.thoughtcrime.securesms"))
        }

        val emailAppsStr = prefs.getString("emailApps", null)
        val emailAppsList = mutableListOf<String>()
        if (emailAppsStr != null) {
            try {
                val array = JSONArray(emailAppsStr)
                for (i in 0 until array.length()) {
                    emailAppsList.add(array.getString(i))
                }
            } catch (e: Exception) {}
        } else {
            emailAppsList.addAll(listOf("com.google.android.gm", "com.microsoft.office.outlook", "net.thunderbird.android", "ch.protonmail.android", "com.fsck.k9"))
        }

        val globalIconPack = prefs.getString("globalIconPack", null)
        val individualPacksStr = prefs.getString("individualIconPacks", "{}")
        val individualPacks = mutableMapOf<String, String>()
        try {
            val json = org.json.JSONObject(individualPacksStr!!)
            json.keys().forEach { key ->
                individualPacks[key] = json.getString(key)
            }
        } catch(e: Exception) {}
        
        
        val showTime = prefs.getBoolean("showTime", true)
        val clockType = runCatching { ClockType.valueOf(prefs.getString("clockType", ClockType.DIGITAL.name)!!) }.getOrDefault(ClockType.DIGITAL)
        val showWeekday = prefs.getBoolean("showWeekday", true)
        val weekdayFormat = runCatching { WeekdayFormat.valueOf(prefs.getString("weekdayFormat", WeekdayFormat.SHORT.name)!!) }.getOrDefault(WeekdayFormat.SHORT)
        val locationMethod = runCatching { LocationMethod.valueOf(prefs.getString("locationMethod", LocationMethod.GPS.name)!!) }.getOrDefault(LocationMethod.GPS)
        val weatherProvider = runCatching { WeatherProvider.valueOf(prefs.getString("weatherProvider", WeatherProvider.MET_NORWAY.name)!!) }.getOrDefault(WeatherProvider.MET_NORWAY)
        val enableT9Search = prefs.getBoolean("enableT9Search", true)
        val t9SearchApps = prefs.getBoolean("t9SearchApps", true)
        val t9SearchContacts = prefs.getBoolean("t9SearchContacts", true)
        val showPinnedAppsSection = prefs.getBoolean("showPinnedAppsSection", true)
        val showMessagesSection = prefs.getBoolean("showMessagesSection", true)
        val showEmailSection = prefs.getBoolean("showEmailSection", true)
        val showCalendarSection = prefs.getBoolean("showCalendarSection", true)
        val selectedCalsStr = prefs.getString("selectedCalendarIds", null)
        val selectedCalendarIds: Set<Long>? = if (selectedCalsStr != null) {
            val set = mutableSetOf<Long>()
            try {
                val json = org.json.JSONArray(selectedCalsStr)
                for (i in 0 until json.length()) {
                    set.add(json.getLong(i))
                }
            } catch(e: Exception) {}
            set
        } else {
            null
        }
        val useIconPackForHub = prefs.getBoolean("useIconPackForHub", false)
        val weatherUpdateInterval = runCatching { WeatherUpdateInterval.valueOf(prefs.getString("weatherUpdateInterval", WeatherUpdateInterval.MIN_15.name)!!) }.getOrDefault(WeatherUpdateInterval.MIN_15)
        val weatherUpdateOnlyWhenClicked = prefs.getBoolean("weatherUpdateOnlyWhenClicked", false)
        val windSpeedUnit = runCatching { WindSpeedUnit.valueOf(prefs.getString("windSpeedUnit", WindSpeedUnit.MS.name)!!) }.getOrDefault(WindSpeedUnit.MS)
        val weatherDailyLanguage = runCatching { WeatherDailyLanguage.valueOf(prefs.getString("weatherDailyLanguage", WeatherDailyLanguage.NORWEGIAN.name)!!) }.getOrDefault(WeatherDailyLanguage.NORWEGIAN)
        val isExtremeBatterySaver = prefs.getBoolean("isExtremeBatterySaver", false)
        val extremeBatterySaverApp1 = prefs.getString("extremeBatterySaverApp1", null)
        val extremeBatterySaverApp2 = prefs.getString("extremeBatterySaverApp2", null)
        
        _state.update { it.copy(
            showTime = showTime,
            clockType = clockType,
            showWeekday = showWeekday,
            weekdayFormat = weekdayFormat,
            locationMethod = locationMethod,
            weatherProvider = weatherProvider,
            enableT9Search = enableT9Search,
            t9SearchApps = t9SearchApps,
            t9SearchContacts = t9SearchContacts,
            showPinnedAppsSection = showPinnedAppsSection,
            showMessagesSection = showMessagesSection,
            showEmailSection = showEmailSection,
            showCalendarSection = showCalendarSection,
            selectedCalendarIds = selectedCalendarIds,
            useIconPackForHub = useIconPackForHub,
            favoriteLocations = favLocs,

            currentTheme = theme, 
            customBgColor = customBgColor,
            customBarColor = customBarColor,
            customTextColor = customTextColor,
            customAccentColor = customAccentColor,
            customHighlightColor = customHighlightColor,
            currentFont = font, 
            textScale = textScale,
            iconScale = iconScale,
            t9SearchMode = t9Mode,
            is24HourFormat = is24Hour,
            showSeconds = showSeconds,
            dateFormat = dateFormat,
            tempUnit = tempUnit,
            windSpeedUnit = windSpeedUnit,
            weatherDailyLanguage = weatherDailyLanguage,
            showWeather = showWeather,
            
            showDate = showDate,
            showLauncherStatusBar = showLauncherStatusBar,
            showAndroidStatusBar = showAndroidStatusBar,
            showWallpaper = showWallpaper,
            wallpaperTransparency = wallpaperTransparency,
            
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            symbianSoundsEnabled = symbianSoundsEnabled,
            hapticIntensity = hapticIntensity,
            messagingApps = msgApps,
            emailApps = emailAppsList,
            currentIconPack = globalIconPack,
            individualIconPacks = individualPacks,
            isExtremeBatterySaver = isExtremeBatterySaver,
            extremeBatterySaverApp1 = extremeBatterySaverApp1,
            extremeBatterySaverApp2 = extremeBatterySaverApp2
        ) }
    }

    fun setExtremeBatterySaver(enabled: Boolean) {
        _state.update { it.copy(isExtremeBatterySaver = enabled) }
        prefs.edit().putBoolean("isExtremeBatterySaver", enabled).apply()
    }

    fun setExtremeBatterySaverApp(slot: Int, packageName: String?) {
        if (slot == 1) {
            _state.update { it.copy(extremeBatterySaverApp1 = packageName) }
            if (packageName != null) {
                prefs.edit().putString("extremeBatterySaverApp1", packageName).apply()
            } else {
                prefs.edit().remove("extremeBatterySaverApp1").apply()
            }
        } else if (slot == 2) {
            _state.update { it.copy(extremeBatterySaverApp2 = packageName) }
            if (packageName != null) {
                prefs.edit().putString("extremeBatterySaverApp2", packageName).apply()
            } else {
                prefs.edit().remove("extremeBatterySaverApp2").apply()
            }
        }
    }

    private fun getPinnedAppIds(): List<String> {
        val jsonStr = prefs.getString("pinned_apps", "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun savePinnedAppIds(ids: List<String>) {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        prefs.edit().putString("pinned_apps", array.toString()).apply()
    }

    fun importPkPass(uri: android.net.Uri, onComplete: (WalletPass?) -> Unit) {
        viewModelScope.launch {
            val pass = walletRepository.importPkPassUri(uri)
            onComplete(pass)
        }
    }

    fun importPassFromImage(uri: android.net.Uri, cardTitle: String? = null, onComplete: (WalletPass?) -> Unit) {
        viewModelScope.launch {
            val pass = walletRepository.importFromImageUri(uri, cardTitle)
            onComplete(pass)
        }
    }

    fun importPassFromBitmap(bitmap: android.graphics.Bitmap, cardTitle: String? = null, onComplete: (WalletPass?) -> Unit) {
        viewModelScope.launch {
            val pass = walletRepository.importFromBitmap(bitmap, cardTitle)
            onComplete(pass)
        }
    }

    fun decodeBarcodeFromUri(uri: android.net.Uri, onComplete: (BarcodeUtils.ScannedBarcodeResult?) -> Unit) {
        viewModelScope.launch {
            val result = walletRepository.decodeBarcodeFromUri(uri)
            onComplete(result)
        }
    }

    fun decodeBarcodeFromBitmap(bitmap: android.graphics.Bitmap, onComplete: (BarcodeUtils.ScannedBarcodeResult?) -> Unit) {
        viewModelScope.launch {
            val result = walletRepository.decodeBarcodeFromBitmap(bitmap)
            onComplete(result)
        }
    }

    fun addLoyaltyCard(
        organizationName: String,
        memberNumber: String,
        memberName: String = "",
        tier: String = "",
        points: String = "",
        bgColor: Long = 0xFF006241,
        barcodeFormat: BarcodeFormatType = BarcodeFormatType.QR_CODE,
        onComplete: ((WalletPass) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val pass = walletRepository.addLoyaltyCard(
                organizationName = organizationName,
                memberNumber = memberNumber,
                memberName = memberName,
                tier = tier,
                points = points,
                bgColor = bgColor,
                barcodeFormat = barcodeFormat
            )
            onComplete?.invoke(pass)
        }
    }

    fun addWalletPass(pass: WalletPass) {
        viewModelScope.launch {
            walletRepository.addPass(pass)
        }
    }

    fun deleteWalletPass(passId: String) {
        viewModelScope.launch {
            walletRepository.removePass(passId)
        }
    }

    fun clearWallet() {
        viewModelScope.launch {
            walletRepository.clearAllPasses()
        }
    }

    fun setWalletCategoryTab(tab: Int) {
        _state.update { it.copy(walletCategoryTab = (tab % 4 + 4) % 4) }
    }

    fun cycleWalletCategory() {
        _state.update { it.copy(walletCategoryTab = (it.walletCategoryTab + 1) % 4) }
    }
    
    fun cycleClockTab() {
        _state.update { it.copy(clockTab = (it.clockTab + 1) % 4) }
    }
    
    fun setWeatherTab(tab: Int) {
        _state.update { it.copy(weatherTab = (tab % 4 + 4) % 4) }
    }

    fun cycleWeatherTab() {
        _state.update { it.copy(weatherTab = (it.weatherTab + 1) % 4) }
    }
    
    fun setUsageTab(tab: Int) {
        _state.update { it.copy(usageTab = (tab % 4 + 4) % 4) }
    }

    fun cycleUsageTab() {
        _state.update { it.copy(usageTab = (it.usageTab + 1) % 4) }
    }
    
    private fun loadAlarms() {
        val str = prefs.getString("alarms", "[]") ?: "[]"
        val list = mutableListOf<AlarmItem>()
        try {
            val array = org.json.JSONArray(str)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(AlarmItem(
                    id = obj.getString("id"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    label = obj.optString("label", "Alarm"),
                    enabled = obj.optBoolean("enabled", true)
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        _state.update { it.copy(alarms = list) }
    }

    private fun saveAlarms(list: List<AlarmItem>) {
        val array = org.json.JSONArray()
        list.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("hour", it.hour)
            obj.put("minute", it.minute)
            obj.put("label", it.label)
            obj.put("enabled", it.enabled)
            array.put(obj)
        }
        prefs.edit().putString("alarms", array.toString()).apply()
        _state.update { it.copy(alarms = list) }
    }

    fun addAlarm(context: android.content.Context, alarm: AlarmItem) {
        val list = _state.value.alarms.toMutableList()
        list.add(alarm)
        saveAlarms(list)
        if (alarm.enabled) AlarmUtils.scheduleAlarm(context, alarm)
    }

    fun updateAlarm(context: android.content.Context, alarm: AlarmItem) {
        val list = _state.value.alarms.toMutableList()
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx != -1) {
            list[idx] = alarm
            saveAlarms(list)
            if (alarm.enabled) AlarmUtils.scheduleAlarm(context, alarm)
            else AlarmUtils.cancelAlarm(context, alarm.id)
        }
    }

    fun deleteAlarm(context: android.content.Context, id: String) {
        val list = _state.value.alarms.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx != -1) {
            list.removeAt(idx)
            saveAlarms(list)
            AlarmUtils.cancelAlarm(context, id)
        }
    }

    private fun loadWorldClocks() {
        val str = prefs.getString("world_clocks", null)
        val list = mutableListOf<WorldClockItem>()
        if (str != null) {
            try {
                val array = org.json.JSONArray(str)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(WorldClockItem(
                        id = obj.getString("id"),
                        city = obj.getString("city"),
                        timeZoneId = obj.getString("timeZoneId")
                    ))
                }
            } catch (e: Exception) { e.printStackTrace() }
        } else {
            list.add(WorldClockItem(city = "London", timeZoneId = "Europe/London"))
            list.add(WorldClockItem(city = "New York", timeZoneId = "America/New_York"))
            list.add(WorldClockItem(city = "Tokyo", timeZoneId = "Asia/Tokyo"))
            list.add(WorldClockItem(city = "Sydney", timeZoneId = "Australia/Sydney"))
        }
        _state.update { it.copy(worldClocks = list) }
    }

    private fun saveWorldClocks(list: List<WorldClockItem>) {
        val array = org.json.JSONArray()
        list.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("city", it.city)
            obj.put("timeZoneId", it.timeZoneId)
            array.put(obj)
        }
        prefs.edit().putString("world_clocks", array.toString()).apply()
        _state.update { it.copy(worldClocks = list) }
    }

    fun addWorldClock(item: WorldClockItem) {
        val list = _state.value.worldClocks.toMutableList()
        list.add(item)
        saveWorldClocks(list)
    }

    fun deleteWorldClock(id: String) {
        val list = _state.value.worldClocks.toMutableList()
        list.removeAll { it.id == id }
        saveWorldClocks(list)
    }

    fun setClockTab(tab: Int) {
        _state.update { it.copy(clockTab = tab) }
    }
}
