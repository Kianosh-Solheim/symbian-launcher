package com.example

import android.content.BroadcastReceiver
import android.content.Context
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.os.Build
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private fun formatDailyDateLabel(dateMillis: Long?, timeStr: String, isToday: Boolean, isNorwegian: Boolean): String {
    val date = if (dateMillis != null && dateMillis > 0) {
        java.util.Date(dateMillis)
    } else {
        runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(timeStr)
        }.getOrNull() ?: java.util.Date()
    }
    
    val cal = java.util.Calendar.getInstance().apply { time = date }
    val dayNum = cal.get(java.util.Calendar.DAY_OF_MONTH)
    
    val norwegianMonths = listOf("jan", "feb", "mar", "apr", "mai", "jun", "jul", "aug", "sep", "okt", "nov", "des")
    val norwegianDays = listOf("Søn", "Man", "Tir", "Ons", "Tor", "Fre", "Lør")
    
    val englishMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val englishDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    
    val monthIdx = cal.get(java.util.Calendar.MONTH).coerceIn(0, 11)
    val dayOfWeekIdx = (cal.get(java.util.Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
    
    return if (isNorwegian) {
        val monthStr = norwegianMonths[monthIdx]
        if (isToday) {
            "I dag $dayNum. $monthStr"
        } else {
            val dayName = norwegianDays[dayOfWeekIdx]
            "$dayName $dayNum. $monthStr"
        }
    } else {
        val monthStr = englishMonths[monthIdx]
        if (isToday) {
            "Today $dayNum $monthStr"
        } else {
            val dayName = englishDays[dayOfWeekIdx]
            "$dayName $dayNum $monthStr"
        }
    }
}

private fun formatDailyPrecipitation(precipMm: Double?, isNorwegian: Boolean): String {
    if (precipMm == null || precipMm < 0.1) return ""
    return if (precipMm >= 9.95) {
        "${kotlin.math.round(precipMm).toInt()} mm"
    } else {
        val formatted = String.format(java.util.Locale.US, "%.1f", precipMm)
        val withComma = if (isNorwegian) formatted.replace('.', ',') else formatted
        "$withComma mm"
    }
}

private fun formatDailyWindSpeed(windSpeedVal: Double?, unit: WindSpeedUnit): String {
    if (windSpeedVal == null) return "--"
    return when (unit) {
        WindSpeedUnit.MPH -> "${kotlin.math.round(windSpeedVal * 2.23694).toInt()} mph"
        WindSpeedUnit.KMH -> "${kotlin.math.round(windSpeedVal * 3.6).toInt()} km/h"
        WindSpeedUnit.MS -> "${kotlin.math.round(windSpeedVal).toInt()} m/s"
    }
}

@Composable
fun WeatherForecastScreen(
    state: LauncherState, 
    viewModel: LauncherViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSettingsClick: () -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val tabs = listOf("Today", "Hourly", "Daily", "Faves")
    val currentTab = tabs.getOrElse(state.weatherTab) { "Today" }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
    ) {
        // Official MET warnings (farevarsler): one line each, press to read the advice.
        if (state.weatherWarnings.isNotEmpty()) {
            var expanded by remember { mutableStateOf<String?>(null) }
            state.weatherWarnings.forEach { w ->
                val src = remember { MutableInteractionSource() }
                val foc by src.collectIsFocusedAsState()
                val fmt = Formatters.get("EEE HH:mm")
                val textScroll = rememberScrollState()
                val scope = rememberCoroutineScope()
                val isOpen = expanded == w.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        // While a warning is open, Up/Down scroll its text; at either end focus moves on as normal.
                        .onPreviewKeyEvent { e ->
                            if (!isOpen || e.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                            when (e.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (textScroll.value < textScroll.maxValue) {
                                    scope.launch { textScroll.animateScrollBy(140f) }; true } else false
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> if (textScroll.value > 0) {
                                    scope.launch { textScroll.animateScrollBy(-140f) }; true } else false
                                else -> false
                            }
                        }
                        .symbianHighlight(foc, unfocusedColor = warningColor(w.color).copy(alpha = 0.25f))
                        .clickable(interactionSource = src, indication = LocalIndication.current) { expanded = if (expanded == w.id) null else w.id }
                        .padding(8.dp)
                ) {
                    Text("⚠ ${w.event}", color = if (foc) Color.Black else warningColor(w.color), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${w.area} · ${if (w.start > 0) fmt.format(w.start) else ""}–${if (w.end > 0) fmt.format(w.end) else ""}",
                        color = if (foc) Color.Black else Color.White, fontSize = 12.sp)
                    if (isOpen) {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp)) {
                            Column(modifier = Modifier.verticalScroll(textScroll).padding(end = 6.dp)) {
                                listOf(w.description, w.consequences, w.instruction).filter { it.isNotBlank() }.forEach {
                                    Text(it, color = if (foc) Color.Black else Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            // Scroll indicator, shown only when there's more text than fits.
                            if (textScroll.maxValue > 0) {
                                val frac = textScroll.value.toFloat() / textScroll.maxValue
                                Box(
                                    modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().width(3.dp)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.3f)
                                            .offset(y = (170.dp * 0.7f) * frac)
                                            .background(if (foc) Color.Black else Color.White)
                                    )
                                }
                            }
                        }
                        if (textScroll.maxValue > 0 && textScroll.value < textScroll.maxValue) {
                            Text("▼ more", color = if (foc) Color.Black else Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Top row with Tabs and Settings
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabs
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val isSelected = state.weatherTab == index
                    
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = state.currentFont.toFontFamily(),
                        modifier = Modifier
                            .symbianHighlight(isFocused)
                            .focusable(interactionSource = interactionSource)
                            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setWeatherTab(index)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }
            
            val intSource = remember { MutableInteractionSource() }
            val isFoc by intSource.collectIsFocusedAsState()
            
            androidx.compose.material3.IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .symbianHighlight(isFoc)
                    .focusable(interactionSource = intSource)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                    contentDescription = "Weather Settings",
                    tint = Color.White
                )
            }
        }

        // Last updated underneath the tabs (with language toggle for Daily view)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lastUpdated = state.weatherLastUpdated
            if (lastUpdated != null) {
                val diff = (System.currentTimeMillis() - lastUpdated) / 60000
                val timeText = if (diff < 1) "Just now" else "$diff min ago"
                Text(
                    text = "Last updated: $timeText",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = state.currentFont.toFontFamily()
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            
            if (currentTab == "Daily") {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.clickable {
                        val next = if (state.weatherDailyLanguage == WeatherDailyLanguage.NORWEGIAN) WeatherDailyLanguage.ENGLISH else WeatherDailyLanguage.NORWEGIAN
                        viewModel.updateState { it.copy(weatherDailyLanguage = next) }
                    }
                ) {
                    Text(
                        text = if (state.weatherDailyLanguage == WeatherDailyLanguage.NORWEGIAN) "🇳🇴 Norsk (Yr)" else "🇬🇧 English",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        Text(
            text = state.weatherLocationName ?: "Unknown Location",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = state.currentFont.toFontFamily(),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        
        when (currentTab) {
            "Today" -> {
                val tempUnit = if (state.tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                    RealisticWeatherIcon(
                        symbolCode = state.weatherCurrentSymbol,
                        size = 72.dp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column {
                        Text(
                            text = "${kotlin.math.round(state.weatherCurrentTemp).toInt()}$tempUnit",
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.currentFont.toFontFamily()
                        )
                        Text(
                            text = state.weatherCurrentSymbol.ifEmpty { "Unknown" },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontFamily = state.currentFont.toFontFamily()
                        )
                    }
                }
                
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp), color = Color.White.copy(alpha = 0.3f))
                
                val feelsLikeUnit = if (state.tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"
                val feelsLikeText = state.weatherFeelsLike?.let { "${kotlin.math.round(it).toInt()}$feelsLikeUnit" } ?: "--"
                val humidityText = state.weatherHumidity?.let { "${it.toInt()}%" } ?: "--"
                val precipText = state.weatherPrecipitation?.let { "$it mm" } ?: "0 mm"
                
                val windSpeedVal = state.weatherWindSpeed
                val windText = if (windSpeedVal != null) {
                    when (state.windSpeedUnit) {
                        WindSpeedUnit.MPH -> "${(windSpeedVal * 2.23694).toInt()} mph"
                        WindSpeedUnit.KMH -> "${(windSpeedVal * 3.6).toInt()} km/h"
                        WindSpeedUnit.MS -> "${String.format(java.util.Locale.US, "%.1f", windSpeedVal)} m/s"
                    }
                } else "--"
                
                val details = listOf(
                    "Feels Like" to feelsLikeText,
                    "Humidity" to humidityText,
                    "Precipitation" to precipText,
                    "Wind" to windText
                )
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    details.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, color = Color.White, fontSize = 18.sp, fontFamily = state.currentFont.toFontFamily())
                            Text(text = value, color = Color.White, fontSize = 18.sp, fontFamily = state.currentFont.toFontFamily())
                        }
                    }
                }
            }
            "Hourly" -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(state.hourlyForecast) { forecast ->
                        val intSource = remember { MutableInteractionSource() }
                        val isFoc by intSource.collectIsFocusedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianHighlight(isFoc)
                                .focusable(interactionSource = intSource)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = forecast.time, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            
                            RealisticWeatherIcon(
                                symbolCode = forecast.symbolCode,
                                size = 30.dp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            
                            Text(text = "${forecast.temperature.toInt()}°", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp).padding(start = 8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(64.dp), horizontalArrangement = Arrangement.End) {
                                Text(text = "${forecast.humidity?.toInt() ?: 0}%", color = Color.White.copy(alpha=0.8f), fontSize = 16.sp)
                                Icon(imageVector = androidx.compose.material.icons.Icons.Default.WaterDrop, contentDescription = null, tint = Color.White.copy(alpha=0.8f), modifier = Modifier.size(16.dp).padding(start = 2.dp))
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
                    }
                }
            }
            "Daily" -> {
                val isNorwegian = state.weatherDailyLanguage == WeatherDailyLanguage.NORWEGIAN
                val nightLabel = "00"
                val morningLabel = "06"
                val afternoonLabel = "12"
                val eveningLabel = "18"
                val tempLabel = if (isNorwegian) "Temp" else "Temp"
                val precipLabel = if (isNorwegian) "Nedb" else "Rain"
                
                val windLabel = if (isNorwegian) "Vind" else "Wind"

                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = nightLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = morningLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = afternoonLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = eveningLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(text = tempLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                            Text(text = precipLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(42.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(text = windLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 10.sp)
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(state.dailyForecast) { index, forecast ->
                            val intSource = remember { MutableInteractionSource() }
                            val isFoc by intSource.collectIsFocusedAsState()
                            
                            var isExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }
                            
                            val dayLabel = formatDailyDateLabel(
                                dateMillis = forecast.dateMillis,
                                timeStr = forecast.time,
                                isToday = forecast.isToday,
                                isNorwegian = isNorwegian
                            )
                            val precipText = formatDailyPrecipitation(
                                precipMm = forecast.precipitation,
                                isNorwegian = isNorwegian
                            )
                            val windUnitStr = when (state.windSpeedUnit) {
                                WindSpeedUnit.MPH -> "mph"
                                WindSpeedUnit.KMH -> "km/h"
                                WindSpeedUnit.MS -> "m/s"
                            }
                            val windText = if (forecast.windSpeed == null) "--" else when (state.windSpeedUnit) {
                                WindSpeedUnit.MPH -> "${kotlin.math.round(forecast.windSpeed * 2.23694).toInt()} $windUnitStr"
                                WindSpeedUnit.KMH -> "${kotlin.math.round(forecast.windSpeed * 3.6).toInt()} $windUnitStr"
                                WindSpeedUnit.MS -> "${kotlin.math.round(forecast.windSpeed).toInt()} $windUnitStr"
                            }

                            val primaryTextColor = Color.White
                            val secondaryTextColor = Color.White.copy(alpha = 0.85f)

                            val prevForecast = state.dailyForecast.getOrNull(index - 1)
                            val highTemp = forecast.highTemp?.toInt() ?: 0
                            val lowTemp = forecast.lowTemp?.toInt() ?: 0
                            val prevHigh = prevForecast?.highTemp?.toInt()
                            val prevLow = prevForecast?.lowTemp?.toInt()

                            val highIcon = when {
                                prevHigh == null -> null
                                highTemp > prevHigh -> androidx.compose.material.icons.Icons.Default.KeyboardArrowUp
                                highTemp < prevHigh -> androidx.compose.material.icons.Icons.Default.KeyboardArrowDown
                                else -> null
                            }

                            val lowIcon = when {
                                prevLow == null -> null
                                lowTemp > prevLow -> androidx.compose.material.icons.Icons.Default.KeyboardArrowUp
                                lowTemp < prevLow -> androidx.compose.material.icons.Icons.Default.KeyboardArrowDown
                                else -> null
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .symbianHighlight(isFoc)
                                    .focusable(interactionSource = intSource)
                                    .clickable { isExpanded = !isExpanded }
                            ) {
                                Text(
                                    text = dayLabel,
                                    color = primaryTextColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (!forecast.nightSymbol.isNullOrEmpty()) {
                                            RealisticWeatherIcon(symbolCode = forecast.nightSymbol, size = 28.dp)
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (!forecast.morningSymbol.isNullOrEmpty()) {
                                            RealisticWeatherIcon(symbolCode = forecast.morningSymbol, size = 28.dp)
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (!forecast.afternoonSymbol.isNullOrEmpty()) {
                                            RealisticWeatherIcon(symbolCode = forecast.afternoonSymbol, size = 28.dp)
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (!forecast.eveningSymbol.isNullOrEmpty()) {
                                            RealisticWeatherIcon(symbolCode = forecast.eveningSymbol, size = 28.dp)
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }
                                    }

                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "${highTemp}°", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (highIcon != null) {
                                                    Icon(imageVector = highIcon, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "${lowTemp}°", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (lowIcon != null) {
                                                    Icon(imageVector = lowIcon, contentDescription = null, tint = Color(0xFF42A5F5), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                                        if (precipText.isNotEmpty()) {
                                            Text(text = precipText, color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }

                                    Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.CenterEnd) {
                                        Text(
                                            text = windText,
                                            color = secondaryTextColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                            lineHeight = 12.sp,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                }
                                
                                androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                    ) {
                                        val dayHourly = state.hourlyForecast.filter { 
                                            val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }
                                            val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = forecast.dateMillis ?: 0L }
                                            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR) &&
                                            cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR)
                                        }
                                        
                                        if (dayHourly.isNotEmpty()) {
                                            val tidLabel = if (isNorwegian) "Tid" else "Time"
                                            val varLabel = if (isNorwegian) "Vær" else "Weather"
                                            val temLabel = if (isNorwegian) "Temp." else "Temp"
                                            val nedLabel = if (isNorwegian) "Nedbør mm" else "Precip mm"
                                            val vinLabel = if (isNorwegian) "Vind m/s" else "Wind m/s"
                                            
                                            // Header
                                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(text = tidLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.width(40.dp))
                                                Text(text = varLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                Text(text = temLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                Text(text = nedLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                Text(text = vinLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                            }
                                            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(bottom = 4.dp))
                                            
                                            val intervals = listOf(
                                                Triple("00-06", forecast.nightSymbol, dayHourly.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }.get(java.util.Calendar.HOUR_OF_DAY) in 0..5 }),
                                                Triple("06-12", forecast.morningSymbol, dayHourly.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }.get(java.util.Calendar.HOUR_OF_DAY) in 6..11 }),
                                                Triple("12-18", forecast.afternoonSymbol, dayHourly.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }.get(java.util.Calendar.HOUR_OF_DAY) in 12..17 }),
                                                Triple("18-00", forecast.eveningSymbol, dayHourly.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }.get(java.util.Calendar.HOUR_OF_DAY) in 18..23 })
                                            )
                                            
                                            intervals.forEach { (timeStr, symbol, hours) ->
                                                if (hours.isNotEmpty()) {
                                                    val maxT = hours.maxOfOrNull { it.temperature }?.toInt() ?: 0
                                                    val sumP = hours.sumOf { it.precipitation ?: 0.0 }
                                                    val avgW = hours.mapNotNull { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0
                                                    
                                                    val pText = if (sumP > 0) String.format("%.1f", sumP) else "0"
                                                    val wText = when (state.windSpeedUnit) {
                                                        WindSpeedUnit.MPH -> "${kotlin.math.round(avgW * 2.23694).toInt()}"
                                                        WindSpeedUnit.KMH -> "${kotlin.math.round(avgW * 3.6).toInt()}"
                                                        WindSpeedUnit.MS -> "${kotlin.math.round(avgW).toInt()}"
                                                    }
                                                    
                                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(text = timeStr, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                                                        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                                            if (!symbol.isNullOrEmpty()) {
                                                                RealisticWeatherIcon(symbolCode = symbol, size = 24.dp)
                                                            }
                                                        }
                                                        Text(text = "${maxT}°", color = Color(0xFFEF5350), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                        
                                                        // Bar chart for temp instead of just numbers? The user requested "using a small bar chart for temperatures"
                                                        // Let's add a tiny Row to draw the bar chart!
                                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(text = pText, color = Color(0xFF29B6F6), fontSize = 12.sp, modifier = Modifier.padding(end=4.dp))
                                                                // Tiny bar visualization
                                                                Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color.White.copy(alpha=0.2f), androidx.compose.foundation.shape.CircleShape)) {
                                                                    val fraction = (maxT.toFloat() + 20f) / 50f // Arbitrary scale from -20 to +30
                                                                    Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFFEF5350), androidx.compose.foundation.shape.CircleShape))
                                                                }
                                                            }
                                                        }
                                                        
                                                        Row(modifier = Modifier.width(50.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                                            Text(text = wText, color = Color.White, fontSize = 12.sp)
                                                            Icon(imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp).padding(start = 2.dp))
                                                        }
                                                    }
                                                    androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                                                }
                                            }
                                        } else {
                                            Text(text = if (isNorwegian) "Ingen timevarsel tilgjengelig" else "No hourly forecast available", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                        }
                                    }
                                }
                                
                                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
            "Faves" -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    if (state.favoriteLocations.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(text = "No favorite locations", color = Color.White.copy(alpha=0.7f), fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(state.favoriteLocations) { loc ->
                            Text(text = loc, color = Color.White, modifier = Modifier.padding(16.dp))
                            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}
