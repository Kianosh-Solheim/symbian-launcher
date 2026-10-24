package com.example

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Which clock app should the launcher open?" — shown once, and from Settings › Time & Date. */
@Composable
fun ClockChooserDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val choices = remember { ClockApps.choices(context) }
    val current = remember { ClockApps.chosen(context) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Clock app") },
        text = {
            Column {
                Text("Which app should open when you select the time or the Clock icon?", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                choices.forEachIndexed { i, c ->
                    val src = remember { MutableInteractionSource() }
                    val foc by src.collectIsFocusedAsState()
                    Column(
                        Modifier.fillMaxWidth()
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .symbianHighlight(foc)
                            .focusable(interactionSource = src)
                            .clickable(interactionSource = src, indication = LocalIndication.current) {
                                ClockApps.choose(context, c.pkg)
                                onDone()
                                if (c.pkg == ClockApps.SYMBIAN_CLOCK && !c.installed) ClockApps.open(context, LauncherState())
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text((if (c.pkg == current) "● " else "") + c.label, fontWeight = FontWeight.Bold,
                            color = if (foc) Color.Black else androidx.compose.material3.LocalContentColor.current)
                        if (c.pkg == ClockApps.SYMBIAN_CLOCK) Text(
                            if (c.installed) "Alarms, timer, stopwatch and world clocks in the launcher's style"
                            else "Not installed yet: selecting it opens the download page",
                            fontSize = 12.sp, color = if (foc) Color.DarkGray else androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.7f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Later") } }
    )
}
