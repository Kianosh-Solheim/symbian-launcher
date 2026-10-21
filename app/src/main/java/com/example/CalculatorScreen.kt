package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import kotlin.math.*

enum class CalculatorMode(val title: String) {
    STANDARD("Standard"),
    SCIENTIFIC("Scientific"),
    CONVERTER("Converter")
}

data class CalcHistoryItem(
    val expression: String,
    val result: String
)

@Composable
fun CalculatorScreen(
    state: LauncherState,
    haptic: HapticFeedback,
    onBack: () -> Unit
) {
    var mode by remember { mutableStateOf(CalculatorMode.STANDARD) }
    var displayText by remember { mutableStateOf("0") }
    var expressionText by remember { mutableStateOf("") }
    var currentOp by remember { mutableStateOf<String?>(null) }
    var firstOperand by remember { mutableStateOf<Double?>(null) }
    var isNewNumber by remember { mutableStateOf(true) }
    var isRadMode by remember { mutableStateOf(false) }
    var memoryValue by remember { mutableStateOf(0.0) }
    var hasMemory by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val historyList = remember { mutableStateListOf<CalcHistoryItem>() }

    val clipboardManager = LocalClipboardManager.current

    // Converter States
    var convType by remember { mutableStateOf("Currency") }
    var convFromUnit by remember { mutableStateOf(0) }
    var convToUnit by remember { mutableStateOf(1) }

    val formatter = remember { DecimalFormat("#,###.########") }

    fun playClickSound() {
        if (state.symbianSoundsEnabled) {
            SymbianSoundManager.playClick()
        }
    }

    fun formatNumber(value: Double): String {
        return if (value.isNaN() || value.isInfinite()) {
            "Error"
        } else if (value == value.toLong().toDouble() && abs(value) < 1e12) {
            DecimalFormat("#,###").format(value.toLong())
        } else {
            formatter.format(value)
        }
    }

    fun onDigit(d: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        if (isNewNumber || displayText == "0" || displayText == "Error") {
            displayText = d
            isNewNumber = false
        } else {
            val clean = displayText.replace(",", "").replace("-", "")
            if (clean.length < 15) {
                displayText += d
            }
        }
    }

    fun onDot() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        if (isNewNumber) {
            displayText = "0."
            isNewNumber = false
        } else if (!displayText.contains(".")) {
            displayText += "."
        }
    }

    fun onClear() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        displayText = "0"
        expressionText = ""
        firstOperand = null
        currentOp = null
        isNewNumber = true
    }

    fun onClearEntry() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        displayText = "0"
        isNewNumber = true
    }

    fun onBackspace() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        if (!isNewNumber && displayText.length > 1 && displayText != "Error") {
            displayText = displayText.dropLast(1)
        } else {
            displayText = "0"
            isNewNumber = true
        }
    }

    fun onToggleSign() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        val num = displayText.toDoubleOrNull() ?: return
        displayText = formatNumber(-num)
    }

    fun onPercent() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        val num = displayText.toDoubleOrNull() ?: return
        val res = if (firstOperand != null) {
            firstOperand!! * (num / 100.0)
        } else {
            num / 100.0
        }
        displayText = formatNumber(res)
    }

    fun calculateResult(op: String, a: Double, b: Double): Double {
        return when (op) {
            "+" -> a + b
            "-" -> a - b
            "×" -> a * b
            "÷" -> if (b != 0.0) a / b else Double.NaN
            "^" -> a.pow(b)
            else -> b
        }
    }

    fun onOperator(op: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        val currentVal = displayText.toDoubleOrNull() ?: 0.0

        if (firstOperand != null && currentOp != null && !isNewNumber) {
            val result = calculateResult(currentOp!!, firstOperand!!, currentVal)
            firstOperand = result
            displayText = formatNumber(result)
            expressionText = "${formatNumber(result)} $op"
        } else {
            firstOperand = currentVal
            expressionText = "${formatNumber(currentVal)} $op"
        }
        currentOp = op
        isNewNumber = true
    }

    fun onEquals() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        val op = currentOp ?: return
        val first = firstOperand ?: return
        val second = displayText.toDoubleOrNull() ?: return

        val res = calculateResult(op, first, second)
        val formattedRes = formatNumber(res)
        val fullExpr = "${formatNumber(first)} $op ${formatNumber(second)}"

        historyList.add(0, CalcHistoryItem(fullExpr, formattedRes))
        expressionText = "$fullExpr ="
        displayText = formattedRes
        firstOperand = null
        currentOp = null
        isNewNumber = true
    }

    fun onScientificFunc(func: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        val num = displayText.toDoubleOrNull() ?: return
        val res = when (func) {
            "√" -> if (num >= 0) sqrt(num) else Double.NaN
            "x²" -> num * num
            "1/x" -> if (num != 0.0) 1.0 / num else Double.NaN
            "|x|" -> abs(num)
            "sin" -> if (isRadMode) sin(num) else sin(Math.toRadians(num))
            "cos" -> if (isRadMode) cos(num) else cos(Math.toRadians(num))
            "tan" -> if (isRadMode) tan(num) else tan(Math.toRadians(num))
            "ln" -> if (num > 0) ln(num) else Double.NaN
            "log" -> if (num > 0) log10(num) else Double.NaN
            "π" -> Math.PI
            "e" -> Math.E
            "n!" -> {
                if (num in 0.0..20.0 && num == num.toLong().toDouble()) {
                    var f = 1.0
                    for (i in 1..num.toInt()) f *= i
                    f
                } else Double.NaN
            }
            else -> num
        }
        expressionText = "$func(${formatNumber(num)})"
        displayText = formatNumber(res)
        isNewNumber = true
        historyList.add(0, CalcHistoryItem(expressionText, displayText))
    }

    // Memory operations
    fun onMemoryClear() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        memoryValue = 0.0
        hasMemory = false
    }

    fun onMemoryRecall() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        if (hasMemory) {
            displayText = formatNumber(memoryValue)
            isNewNumber = true
        }
    }

    fun onMemoryAdd() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        val num = displayText.toDoubleOrNull() ?: 0.0
        memoryValue += num
        hasMemory = true
        isNewNumber = true
    }

    fun onMemorySubtract() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playClickSound()
        val num = displayText.toDoubleOrNull() ?: 0.0
        memoryValue -= num
        hasMemory = true
        isNewNumber = true
    }

    // Currency & Unit conversions
    val currencyRates = listOf("USD" to 1.0, "EUR" to 0.92, "GBP" to 0.79, "JPY" to 155.0, "CAD" to 1.36, "AUD" to 1.52, "NOK" to 10.65)
    val lengthUnits = listOf("Meters (m)" to 1.0, "Kilometers (km)" to 0.001, "Feet (ft)" to 3.28084, "Miles (mi)" to 0.000621371, "Inches (in)" to 39.3701)
    val weightUnits = listOf("Kilograms (kg)" to 1.0, "Grams (g)" to 1000.0, "Pounds (lb)" to 2.20462, "Ounces (oz)" to 35.274)
    val tempUnits = listOf("Celsius (°C)", "Fahrenheit (°F)", "Kelvin (K)")

    fun getConvertedValue(): String {
        val input = displayText.toDoubleOrNull() ?: 0.0
        return when (convType) {
            "Currency" -> {
                val fromRate = currencyRates.getOrNull(convFromUnit)?.second ?: 1.0
                val toRate = currencyRates.getOrNull(convToUnit)?.second ?: 1.0
                val usdVal = input / fromRate
                val targetVal = usdVal * toRate
                "${formatNumber(targetVal)} ${currencyRates.getOrNull(convToUnit)?.first ?: ""}"
            }
            "Length" -> {
                val fromFactor = lengthUnits.getOrNull(convFromUnit)?.second ?: 1.0
                val toFactor = lengthUnits.getOrNull(convToUnit)?.second ?: 1.0
                val meterVal = input / fromFactor
                val targetVal = meterVal * toFactor
                "${formatNumber(targetVal)} ${lengthUnits.getOrNull(convToUnit)?.first?.split(" ")?.first() ?: ""}"
            }
            "Weight" -> {
                val fromFactor = weightUnits.getOrNull(convFromUnit)?.second ?: 1.0
                val toFactor = weightUnits.getOrNull(convToUnit)?.second ?: 1.0
                val kgVal = input / fromFactor
                val targetVal = kgVal * toFactor
                "${formatNumber(targetVal)} ${weightUnits.getOrNull(convToUnit)?.first?.split(" ")?.first() ?: ""}"
            }
            "Temp" -> {
                val cVal = when (convFromUnit) {
                    0 -> input // C
                    1 -> (input - 32.0) * 5.0 / 9.0 // F to C
                    else -> input - 273.15 // K to C
                }
                val target = when (convToUnit) {
                    0 -> cVal
                    1 -> (cVal * 9.0 / 5.0) + 32.0
                    else -> cVal + 273.15
                }
                "${formatNumber(target)} ${tempUnits.getOrNull(convToUnit) ?: ""}"
            }
            else -> "0"
        }
    }

    val firstButtonFocusRequester = remember { FocusRequester() }
    LaunchedEffect(mode) {
        try { firstButtonFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val nativeKey = keyEvent.nativeKeyEvent
                    val nativeKeyCode = nativeKey.keyCode
                    val code = nativeKey.unicodeChar
                    val char = if (code > 0) code.toChar() else null

                    when {
                        // Let D-pad navigation and D-pad center fall through to focused buttons
                        nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                        nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                        nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                        nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ||
                        nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER -> false

                        // Physical keyboard digits
                        nativeKeyCode in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                            val digit = (nativeKeyCode - android.view.KeyEvent.KEYCODE_0).toString()
                            onDigit(digit)
                            true
                        }
                        nativeKeyCode in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> {
                            val digit = (nativeKeyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0).toString()
                            onDigit(digit)
                            true
                        }
                        char in '0'..'9' -> { onDigit(char.toString()); true }

                        // Operators and punctuation
                        char == '.' || char == ',' || nativeKeyCode == android.view.KeyEvent.KEYCODE_PERIOD || nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_DOT || nativeKeyCode == android.view.KeyEvent.KEYCODE_COMMA -> { onDot(); true }
                        char == '+' || nativeKeyCode == android.view.KeyEvent.KEYCODE_PLUS || nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ADD -> { onOperator("+"); true }
                        char == '-' || nativeKeyCode == android.view.KeyEvent.KEYCODE_MINUS || nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> { onOperator("-"); true }
                        char == '*' || char == 'x' || char == 'X' || nativeKeyCode == android.view.KeyEvent.KEYCODE_STAR || nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> { onOperator("×"); true }
                        char == '/' || nativeKeyCode == android.view.KeyEvent.KEYCODE_SLASH || nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_DIVIDE -> { onOperator("÷"); true }
                        char == '%' -> { onPercent(); true }
                        char == '^' -> { onOperator("^"); true }
                        char == '=' || nativeKeyCode == android.view.KeyEvent.KEYCODE_EQUALS || nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_EQUALS -> { onEquals(); true }
                        char == 'c' || char == 'C' || nativeKeyCode == android.view.KeyEvent.KEYCODE_CLEAR -> { onClear(); true }
                        nativeKeyCode == android.view.KeyEvent.KEYCODE_DEL || nativeKeyCode == android.view.KeyEvent.KEYCODE_FORWARD_DEL -> { onBackspace(); true }
                        nativeKeyCode == android.view.KeyEvent.KEYCODE_BACK || nativeKeyCode == android.view.KeyEvent.KEYCODE_ESCAPE -> { onBack(); true }
                        keyEvent.key == Key.Zero || keyEvent.key == Key.NumPad0 -> { onDigit("0"); true }
                        keyEvent.key == Key.One || keyEvent.key == Key.NumPad1 -> { onDigit("1"); true }
                        keyEvent.key == Key.Two || keyEvent.key == Key.NumPad2 -> { onDigit("2"); true }
                        keyEvent.key == Key.Three || keyEvent.key == Key.NumPad3 -> { onDigit("3"); true }
                        keyEvent.key == Key.Four || keyEvent.key == Key.NumPad4 -> { onDigit("4"); true }
                        keyEvent.key == Key.Five || keyEvent.key == Key.NumPad5 -> { onDigit("5"); true }
                        keyEvent.key == Key.Six || keyEvent.key == Key.NumPad6 -> { onDigit("6"); true }
                        keyEvent.key == Key.Seven || keyEvent.key == Key.NumPad7 -> { onDigit("7"); true }
                        keyEvent.key == Key.Eight || keyEvent.key == Key.NumPad8 -> { onDigit("8"); true }
                        keyEvent.key == Key.Nine || keyEvent.key == Key.NumPad9 -> { onDigit("9"); true }
                        keyEvent.key == Key.Period || keyEvent.key == Key.NumPadDot -> { onDot(); true }
                        keyEvent.key == Key.Plus || keyEvent.key == Key.NumPadAdd -> { onOperator("+"); true }
                        keyEvent.key == Key.Minus || keyEvent.key == Key.NumPadSubtract -> { onOperator("-"); true }
                        keyEvent.key == Key.Multiply || keyEvent.key == Key.NumPadMultiply -> { onOperator("×"); true }
                        keyEvent.key == Key.Slash || keyEvent.key == Key.NumPadDivide -> { onOperator("÷"); true }
                        keyEvent.key == Key.Equals || keyEvent.key == Key.NumPadEquals -> { onEquals(); true }
                        keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete -> { onBackspace(); true }
                        keyEvent.key == Key.Escape || keyEvent.key == Key.Back -> { onBack(); true }
                        else -> false
                    }
                } else false
            }
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
    ) {
        // Mode Selector Tabs (Standard / Scientific / Converter) & History button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CalculatorMode.values().forEach { m ->
                    val isSelected = mode == m
                    val tabInteractionSource = remember { MutableInteractionSource() }
                    val isTabFocused by tabInteractionSource.collectIsFocusedAsState()

                    LaunchedEffect(isTabFocused) {
                        if (isTabFocused) playClickSound()
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isTabFocused) Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFCA28)))
                                else if (isSelected) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                                else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .border(
                                width = if (isTabFocused) 2.dp else 0.dp,
                                color = if (isTabFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                interactionSource = tabInteractionSource,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playClickSound()
                                mode = m
                            }
                            .focusable(interactionSource = tabInteractionSource)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = m.title,
                            color = if (isTabFocused || isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected || isTabFocused) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = state.currentFont.toFontFamily()
                        )
                    }
                }
            }

            // History toggle button
            val historyInteractionSource = remember { MutableInteractionSource() }
            val isHistoryFocused by historyInteractionSource.collectIsFocusedAsState()
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    playClickSound()
                    showHistory = !showHistory
                },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isHistoryFocused) Color(0xFFFFD54F) else Color.Transparent)
                    .focusable(interactionSource = historyInteractionSource)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = if (isHistoryFocused) Color.Black else if (showHistory) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // History overlay if active
        if (showHistory) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E252B))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (historyList.isEmpty()) {
                    Text(
                        "No calculations yet",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontFamily = state.currentFont.toFontFamily(),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(historyList) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        displayText = item.result
                                        isNewNumber = true
                                        showHistory = false
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    item.expression,
                                    color = Color.LightGray,
                                    fontSize = 13.sp,
                                    fontFamily = state.currentFont.toFontFamily()
                                )
                                Text(
                                    "= ${item.result}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = state.currentFont.toFontFamily()
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Retro Nokia / Symbian LCD Display Bezel
        SymbianLcdDisplay(
            expression = expressionText,
            display = displayText,
            op = currentOp,
            hasMemory = hasMemory,
            isRadMode = isRadMode,
            fontFamily = state.currentFont.toFontFamily(),
            converterResult = if (mode == CalculatorMode.CONVERTER) getConvertedValue() else null
        )

        // Hardware keypad indicator banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Type numbers (0-9) on phone keyboard",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = state.currentFont.toFontFamily()
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Operator Keypads (Without 0-9 digits)
        when (mode) {
            CalculatorMode.STANDARD -> {
                OperatorsOnlyKeypad(
                    fontFamily = state.currentFont.toFontFamily(),
                    firstFocusRequester = firstButtonFocusRequester,
                    onDot = ::onDot,
                    onClear = ::onClear,
                    onClearEntry = ::onClearEntry,
                    onBackspace = ::onBackspace,
                    onOperator = ::onOperator,
                    onEquals = ::onEquals,
                    onToggleSign = ::onToggleSign,
                    onPercent = ::onPercent,
                    onFunc = ::onScientificFunc,
                    onMemoryClear = ::onMemoryClear,
                    onMemoryRecall = ::onMemoryRecall,
                    onMemoryAdd = ::onMemoryAdd,
                    onMemorySubtract = ::onMemorySubtract,
                    haptic = haptic,
                    playSound = ::playClickSound
                )
            }
            CalculatorMode.SCIENTIFIC -> {
                ScientificOperatorsKeypad(
                    fontFamily = state.currentFont.toFontFamily(),
                    firstFocusRequester = firstButtonFocusRequester,
                    isRad = isRadMode,
                    onToggleRad = {
                        isRadMode = !isRadMode
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playClickSound()
                    },
                    onDot = ::onDot,
                    onClear = ::onClear,
                    onBackspace = ::onBackspace,
                    onOperator = ::onOperator,
                    onEquals = ::onEquals,
                    onToggleSign = ::onToggleSign,
                    onPercent = ::onPercent,
                    onFunc = ::onScientificFunc,
                    haptic = haptic,
                    playSound = ::playClickSound
                )
            }
            CalculatorMode.CONVERTER -> {
                ConverterControlKeypad(
                    fontFamily = state.currentFont.toFontFamily(),
                    firstFocusRequester = firstButtonFocusRequester,
                    convType = convType,
                    onSelectType = { convType = it },
                    fromIndex = convFromUnit,
                    toIndex = convToUnit,
                    onSelectFrom = { convFromUnit = it },
                    onSelectTo = { convToUnit = it },
                    onDot = ::onDot,
                    onClear = ::onClear,
                    onBackspace = ::onBackspace,
                    onToggleSign = ::onToggleSign,
                    onCopyResult = {
                        val converted = getConvertedValue()
                        clipboardManager.setText(AnnotatedString(converted))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        playClickSound()
                    },
                    haptic = haptic,
                    playSound = ::playClickSound
                )
            }
        }
    }
}

@Composable
fun SymbianLcdDisplay(
    expression: String,
    display: String,
    op: String?,
    hasMemory: Boolean,
    isRadMode: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    converterResult: String? = null
) {
    // Outer Metallic Bevel Frame
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF455A64), Color(0xFF263238), Color(0xFF1E272C))
                )
            )
            .border(1.5.dp, Color(0xFF90A4AE).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        // Inner LCD screen (Symbian Tinted Display with retro glass gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF8C997E),
                            Color(0xFF7E8C70),
                            Color(0xFF6F7D61)
                        )
                    )
                )
                .border(1.dp, Color(0xFF333D29), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                // Top status badges in LCD
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hasMemory) {
                            Text("M", color = Color(0xFF2A3620), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
                        }
                        if (isRadMode) {
                            Text("RAD", color = Color(0xFF2A3620), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
                        } else {
                            Text("DEG", color = Color(0xFF2A3620), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
                        }
                    }

                    // Expression / History string
                    Text(
                        text = if (expression.isNotEmpty()) expression else " ",
                        color = Color(0xFF37462B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Main Digits Display
                Text(
                    text = display,
                    color = Color(0xFF16200F),
                    fontSize = if (display.length > 12) 22.sp else if (display.length > 8) 26.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color(0xFF6A775C).copy(alpha = 0.6f),
                            offset = Offset(1.5f, 1.5f),
                            blurRadius = 1f
                        )
                    )
                )

                // If Converter Mode result is provided
                if (converterResult != null) {
                    HorizontalDivider(
                        color = Color(0xFF4A573C).copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Converted:",
                            color = Color(0xFF37462B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = converterResult,
                            color = Color(0xFF16200F),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SymbianCalcButton(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF37474F),
    textColor: Color = Color.White,
    fontSize: Int = 18,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    haptic: HapticFeedback,
    playSound: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            playSound()
        }
    }

    val scale by animateFloatAsState(
        if (isPressed) 0.94f else if (isFocused) 1.05f else 1.0f,
        label = "pressScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(48.dp)
            .shadow(if (isFocused) 6.dp else if (isPressed) 1.dp else 3.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isFocused) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFCA28), Color(0xFFFF8F00))
                    )
                } else if (isPressed) {
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(red = backgroundColor.red * 0.7f, green = backgroundColor.green * 0.7f, blue = backgroundColor.blue * 0.7f),
                            backgroundColor
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(red = min(1f, backgroundColor.red * 1.25f), green = min(1f, backgroundColor.green * 1.25f), blue = min(1f, backgroundColor.blue * 1.25f)),
                            backgroundColor,
                            backgroundColor.copy(red = backgroundColor.red * 0.75f, green = backgroundColor.green * 0.75f, blue = backgroundColor.blue * 0.75f)
                        )
                    )
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                playSound()
                onClick()
            }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        // Bevel top shine line
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(1.5.dp)
                .align(Alignment.TopCenter)
                .background(if (isFocused) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = if (isPressed) 0.1f else 0.4f))
        )

        Text(
            text = text,
            color = if (isFocused) Color(0xFF101418) else textColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily,
            style = TextStyle(
                shadow = if (isFocused) Shadow(color = Color.White.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 1f)
                else Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(1f, 1f), blurRadius = 2f)
            )
        )
    }
}

/**
 * Standard Mode Keypad - Focuses purely on operators, memory, and utility keys (No 0-9 digits)
 */
@Composable
fun OperatorsOnlyKeypad(
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    firstFocusRequester: FocusRequester? = null,
    onDot: () -> Unit,
    onClear: () -> Unit,
    onClearEntry: () -> Unit,
    onBackspace: () -> Unit,
    onOperator: (String) -> Unit,
    onEquals: () -> Unit,
    onToggleSign: () -> Unit,
    onPercent: () -> Unit,
    onFunc: (String) -> Unit,
    onMemoryClear: () -> Unit,
    onMemoryRecall: () -> Unit,
    onMemoryAdd: () -> Unit,
    onMemorySubtract: () -> Unit,
    haptic: HapticFeedback,
    playSound: () -> Unit
) {
    val clearColor = Color(0xFFC62828) // Red
    val fnColor = Color(0xFF37474F) // Dark Slate
    val memColor = Color(0xFF455A64) // Blue Grey
    val opColor = Color(0xFFFF8F00) // Deep Orange
    val eqColor = Color(0xFF2E7D32) // Forest Green

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Clear & Delete Controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val firstMod = if (firstFocusRequester != null) Modifier.weight(1f).focusRequester(firstFocusRequester) else Modifier.weight(1f)
            SymbianCalcButton("C", firstMod, clearColor, Color.White, fontSize = 18, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onClear)
            SymbianCalcButton("CE", Modifier.weight(1f), clearColor.copy(alpha = 0.85f), Color.White, fontSize = 16, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onClearEntry)
            SymbianCalcButton("⌫", Modifier.weight(1f), fnColor, Color.White, fontSize = 18, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onBackspace)
            SymbianCalcButton("%", Modifier.weight(1f), fnColor, Color.White, fontSize = 18, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onPercent)
        }

        // Row 2: Arithmetic Operators
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbianCalcButton("÷", Modifier.weight(1f), opColor, Color.White, fontSize = 24, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("÷") }
            SymbianCalcButton("×", Modifier.weight(1f), opColor, Color.White, fontSize = 24, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("×") }
            SymbianCalcButton("-", Modifier.weight(1f), opColor, Color.White, fontSize = 26, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("-") }
            SymbianCalcButton("+", Modifier.weight(1f), opColor, Color.White, fontSize = 24, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("+") }
        }

        // Row 3: Math Functions & Decimal
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbianCalcButton("±", Modifier.weight(1f), fnColor, Color.White, fontSize = 20, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onToggleSign)
            SymbianCalcButton(".", Modifier.weight(1f), fnColor, Color.White, fontSize = 24, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onDot)
            SymbianCalcButton("√", Modifier.weight(1f), fnColor, Color.White, fontSize = 18, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("√") }
            SymbianCalcButton("x²", Modifier.weight(1f), fnColor, Color.White, fontSize = 16, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("x²") }
        }

        // Row 4: Powers & Memory
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbianCalcButton("1/x", Modifier.weight(1f), fnColor, Color.White, fontSize = 15, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("1/x") }
            SymbianCalcButton("xʸ", Modifier.weight(1f), fnColor, Color.White, fontSize = 15, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("^") }
            SymbianCalcButton("π", Modifier.weight(1f), fnColor, Color.White, fontSize = 17, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("π") }
            SymbianCalcButton("MC", Modifier.weight(1f), memColor, Color(0xFFFFD54F), fontSize = 14, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onMemoryClear)
        }

        // Row 5: Memory Recall/Add & Prominent Equals
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbianCalcButton("MR", Modifier.weight(1f), memColor, Color(0xFFFFD54F), fontSize = 14, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onMemoryRecall)
            SymbianCalcButton("M+", Modifier.weight(1f), memColor, Color(0xFFFFD54F), fontSize = 14, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onMemoryAdd)
            SymbianCalcButton("M-", Modifier.weight(1f), memColor, Color(0xFFFFD54F), fontSize = 14, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onMemorySubtract)
            SymbianCalcButton("=", Modifier.weight(1f), eqColor, Color.White, fontSize = 28, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onEquals)
        }
    }
}

/**
 * Scientific Mode Keypad - Focuses purely on scientific functions and operators (No 0-9 digits)
 */
@Composable
fun ScientificOperatorsKeypad(
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    firstFocusRequester: FocusRequester? = null,
    isRad: Boolean,
    onToggleRad: () -> Unit,
    onDot: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onOperator: (String) -> Unit,
    onEquals: () -> Unit,
    onToggleSign: () -> Unit,
    onPercent: () -> Unit,
    onFunc: (String) -> Unit,
    haptic: HapticFeedback,
    playSound: () -> Unit
) {
    val clearColor = Color(0xFFC62828)
    val sciColor = Color(0xFF283593) // Indigo
    val opColor = Color(0xFFFF8F00) // Deep Orange
    val eqColor = Color(0xFF2E7D32) // Green

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Scientific Row 1: RAD/DEG, sin, cos, tan, π
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val firstMod = if (firstFocusRequester != null) Modifier.weight(1f).focusRequester(firstFocusRequester) else Modifier.weight(1f)
            SymbianCalcButton(if (isRad) "RAD" else "DEG", firstMod, sciColor, Color(0xFFFFE082), fontSize = 12, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onToggleRad)
            SymbianCalcButton("sin", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("sin") }
            SymbianCalcButton("cos", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("cos") }
            SymbianCalcButton("tan", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("tan") }
            SymbianCalcButton("π", Modifier.weight(1f), sciColor, Color.White, fontSize = 16, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("π") }
        }

        // Scientific Row 2: ln, log, e, n!, xʸ
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SymbianCalcButton("ln", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("ln") }
            SymbianCalcButton("log", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("log") }
            SymbianCalcButton("e", Modifier.weight(1f), sciColor, Color.White, fontSize = 14, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("e") }
            SymbianCalcButton("n!", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("n!") }
            SymbianCalcButton("xʸ", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("^") }
        }

        // Scientific Row 3: √, x², 1/x, |x|, %
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SymbianCalcButton("√", Modifier.weight(1f), sciColor, Color.White, fontSize = 16, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("√") }
            SymbianCalcButton("x²", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("x²") }
            SymbianCalcButton("1/x", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("1/x") }
            SymbianCalcButton("|x|", Modifier.weight(1f), sciColor, Color.White, fontSize = 13, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onFunc("|x|") }
            SymbianCalcButton("%", Modifier.weight(1f), sciColor, Color.White, fontSize = 15, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onPercent)
        }

        // Row 4: C, ⌫, ±, ., ÷
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SymbianCalcButton("C", Modifier.weight(1f), clearColor, Color.White, fontSize = 16, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onClear)
            SymbianCalcButton("⌫", Modifier.weight(1f), Color(0xFF37474F), Color.White, fontSize = 16, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onBackspace)
            SymbianCalcButton("±", Modifier.weight(1f), Color(0xFF37474F), Color.White, fontSize = 18, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onToggleSign)
            SymbianCalcButton(".", Modifier.weight(1f), Color(0xFF37474F), Color.White, fontSize = 22, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onDot)
            SymbianCalcButton("÷", Modifier.weight(1f), opColor, Color.White, fontSize = 20, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("÷") }
        }

        // Row 5: ×, -, +, =
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SymbianCalcButton("×", Modifier.weight(1f), opColor, Color.White, fontSize = 20, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("×") }
            SymbianCalcButton("-", Modifier.weight(1f), opColor, Color.White, fontSize = 22, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("-") }
            SymbianCalcButton("+", Modifier.weight(1f), opColor, Color.White, fontSize = 20, fontFamily = fontFamily, haptic = haptic, playSound = playSound) { onOperator("+") }
            SymbianCalcButton("=", Modifier.weight(2f), eqColor, Color.White, fontSize = 24, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onEquals)
        }
    }
}

/**
 * Converter Controls Keypad - Category selector, unit pickers, and operation keys (No 0-9 digits)
 */
@Composable
fun ConverterControlKeypad(
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    firstFocusRequester: FocusRequester? = null,
    convType: String,
    onSelectType: (String) -> Unit,
    fromIndex: Int,
    toIndex: Int,
    onSelectFrom: (Int) -> Unit,
    onSelectTo: (Int) -> Unit,
    onDot: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onToggleSign: () -> Unit,
    onCopyResult: () -> Unit,
    haptic: HapticFeedback,
    playSound: () -> Unit
) {
    val types = listOf("Currency", "Length", "Weight", "Temp")
    val currentUnits: List<String> = when (convType) {
        "Currency" -> listOf("USD ($)", "EUR (€)", "GBP (£)", "JPY (¥)", "CAD ($)", "AUD ($)", "NOK (kr)")
        "Length" -> listOf("Meters", "Kilometers", "Feet", "Miles", "Inches")
        "Weight" -> listOf("Kilograms", "Grams", "Pounds", "Ounces")
        else -> listOf("Celsius (°C)", "Fahrenheit (°F)", "Kelvin (K)")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Converter Category Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            types.forEachIndexed { index, t ->
                val isSel = convType == t
                val tabInteraction = remember { MutableInteractionSource() }
                val isTabFocused by tabInteraction.collectIsFocusedAsState()

                LaunchedEffect(isTabFocused) {
                    if (isTabFocused) playSound()
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (index == 0 && firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isTabFocused) Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFCA28)))
                            else if (isSel) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                            else Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF263238)))
                        )
                        .border(
                            width = if (isTabFocused) 2.dp else 1.dp,
                            color = if (isTabFocused) Color.White else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable(interactionSource = tabInteraction, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            playSound()
                            onSelectType(t)
                            onSelectFrom(0)
                            onSelectTo(1)
                        }
                        .focusable(interactionSource = tabInteraction)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t,
                        color = if (isTabFocused || isSel) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }
        }

        // Units Picker (From -> To with swap)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF263238))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // From Unit
            val fromInteraction = remember { MutableInteractionSource() }
            val isFromFocused by fromInteraction.collectIsFocusedAsState()

            LaunchedEffect(isFromFocused) {
                if (isFromFocused) playSound()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isFromFocused) Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFCA28)))
                        else Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF263238)))
                    )
                    .border(
                        width = if (isFromFocused) 2.dp else 1.dp,
                        color = if (isFromFocused) Color.White else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable(interactionSource = fromInteraction, indication = null) {
                        val next = (fromIndex + 1) % currentUnits.size
                        onSelectFrom(next)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playSound()
                    }
                    .focusable(interactionSource = fromInteraction)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "FROM (Tap)",
                        color = if (isFromFocused) Color(0xFF333333) else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = fontFamily
                    )
                    Text(
                        currentUnits.getOrElse(fromIndex) { "Unit" },
                        color = if (isFromFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val swapInteraction = remember { MutableInteractionSource() }
            val isSwapFocused by swapInteraction.collectIsFocusedAsState()

            IconButton(
                onClick = {
                    val temp = fromIndex
                    onSelectFrom(toIndex)
                    onSelectTo(temp)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    playSound()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSwapFocused) Color(0xFFFFD54F) else Color.Transparent)
                    .focusable(interactionSource = swapInteraction)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Swap",
                    tint = if (isSwapFocused) Color.Black else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // To Unit
            val toInteraction = remember { MutableInteractionSource() }
            val isToFocused by toInteraction.collectIsFocusedAsState()

            LaunchedEffect(isToFocused) {
                if (isToFocused) playSound()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isToFocused) Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFCA28)))
                        else Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF263238)))
                    )
                    .border(
                        width = if (isToFocused) 2.dp else 1.dp,
                        color = if (isToFocused) Color.White else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable(interactionSource = toInteraction, indication = null) {
                        val next = (toIndex + 1) % currentUnits.size
                        onSelectTo(next)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playSound()
                    }
                    .focusable(interactionSource = toInteraction)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "TO (Tap)",
                        color = if (isToFocused) Color(0xFF333333) else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = fontFamily
                    )
                    Text(
                        currentUnits.getOrElse(toIndex) { "Unit" },
                        color = if (isToFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Operation Buttons: C, ⌫, ±, ., Copy
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbianCalcButton("C", Modifier.weight(1f), Color(0xFFC62828), Color.White, fontSize = 18, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onClear)
            SymbianCalcButton("⌫", Modifier.weight(1f), Color(0xFF37474F), Color.White, fontSize = 18, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onBackspace)
            SymbianCalcButton("±", Modifier.weight(1f), Color(0xFF37474F), Color.White, fontSize = 20, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onToggleSign)
            SymbianCalcButton(".", Modifier.weight(1f), Color(0xFF37474F), Color.White, fontSize = 24, fontFamily = fontFamily, haptic = haptic, playSound = playSound, onClick = onDot)
        }

        // Copy Result Button
        SymbianCalcButton(
            text = "Copy Converted Result",
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF2E7D32),
            textColor = Color.White,
            fontSize = 14,
            fontFamily = fontFamily,
            haptic = haptic,
            playSound = playSound,
            onClick = onCopyResult
        )
    }
}
