package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EventsScreen(state: PosState, vm: PosViewModel) {
    val active = state.activeEvent
    var showNewDialog by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ניהול אירועים",
                 color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (active == null) {
                Button(
                    onClick = { showNewDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("+ פתח אירוע חדש", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (active != null) {
            ActiveEventCard(active, state, vm, onEnd = { confirmEnd = true })
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("אין אירוע פעיל כעת", color = Muted)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("היסטוריית אירועים",
             color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        val history = state.allEvents.filter { it.id != active?.id }
        if (history.isEmpty()) {
            Text("אין אירועים בהיסטוריה", color = Muted, fontSize = 13.sp)
        } else {
            history.forEach { ev ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ev.name, color = Color.White,
                                 fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.weight(1f))
                            Text(eventStatusLabel(ev.status),
                                 color = if (ev.status == "ended") Muted else Bad,
                                 fontSize = 12.sp)
                        }
                        Text("התחיל: ${formatDateTime(ev.startedAt)}",
                             color = Muted, fontSize = 12.sp)
                        if (ev.endedAt != null) {
                            Text("הסתיים: ${formatDateTime(ev.endedAt)}",
                                 color = Muted, fontSize = 12.sp)
                        }
                        if (!ev.description.isNullOrBlank()) {
                            Text(ev.description, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showNewDialog) {
        NewEventDialog(
            onDismiss = { showNewDialog = false },
            onCreate = { name, desc ->
                vm.startEvent(name, desc)
                showNewDialog = false
            },
        )
    }

    if (confirmEnd && active != null) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("סיום אירוע: ${active.name}") },
            text = { Text("האם אתה בטוח? לאחר סיום האירוע לא תוכל להוסיף אליו פריטים.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.endActiveEvent()
                    confirmEnd = false
                }) { Text("סיים אירוע", color = Bad, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("ביטול") }
            },
        )
    }
}

@Composable
private fun ActiveEventCard(
    event: Event,
    state: PosState,
    vm: PosViewModel,
    onEnd: () -> Unit,
) {
    val ctx = LocalContext.current
    val db = remember { (ctx.applicationContext as BarPosApp).db }
    var snapshots by remember(event.id) { mutableStateOf<List<EventStockSnapshot>>(emptyList()) }
    LaunchedEffect(event.id) {
        snapshots = db.events().snapshotsFor(event.id)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("פעיל: ${event.name}",
                         color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("התחיל: ${formatDateTime(event.startedAt)}",
                         color = Muted, fontSize = 12.sp)
                    if (!event.description.isNullOrBlank()) {
                        Text(event.description, color = Color.White, fontSize = 13.sp,
                             modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Button(
                    onClick = onEnd,
                    colors = ButtonDefaults.buttonColors(containerColor = Bad),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("סיים אירוע", color = Color.White) }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBg)
            Spacer(Modifier.height(12.dp))

            Text("מצב מלאי באירוע",
                 color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            if (snapshots.isEmpty()) {
                Text("לא נקלט מלאי התחלתי לאירוע זה", color = Muted, fontSize = 13.sp)
            } else {
                snapshots.forEach { snap ->
                    val currentItem = state.items.firstOrNull { it.id == snap.itemId }
                    val current = currentItem?.stockOnHand ?: 0.0
                    val sold = (snap.initialQty - current).coerceAtLeast(0.0)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(snap.itemName, color = Color.White, fontSize = 13.sp)
                            Text("התחלתי: ${trim(snap.initialQty)} ${snap.unit}",
                                 color = Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("נמכר: ${trim(sold)}",
                                 color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("נשאר: ${trim(current)}",
                                 color = if (current <= 0) Bad else Good, fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = Bg)
                }
            }
        }
    }
}

@Composable
private fun NewEventDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("אירוע חדש") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("שם האירוע") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("תיאור (אופציונלי)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "פתיחת אירוע תצלם תמונת מצב של המלאי הנוכחי. " +
                            "תוכל לראות בכל רגע כמה נמכר באירוע.",
                    color = Muted, fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onCreate(name, desc.ifBlank { null })
            }) { Text("התחל אירוע", color = Accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

private fun eventStatusLabel(s: String): String = when (s) {
    "active" -> "פעיל"
    "ended" -> "הסתיים"
    "cancelled" -> "בוטל"
    else -> s
}
