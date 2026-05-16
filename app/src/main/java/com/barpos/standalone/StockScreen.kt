package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Tab as M3Tab
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StockScreen(state: PosState, vm: PosViewModel) {
    val tabs = listOf("מצב מלאי", "כניסות מלאי", "התראות")
    var selected by remember { mutableStateOf(0) }
    var restockItem by remember { mutableStateOf<Item?>(null) }
    var adjustItem by remember { mutableStateOf<Item?>(null) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("ניהול מלאי",
                 color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        TabRow(selectedTabIndex = selected, containerColor = Panel, contentColor = Accent) {
            tabs.forEachIndexed { i, t ->
                M3Tab(selected == i, onClick = { selected = i }, text = { Text(t) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> StockStatus(state, onRestock = { restockItem = it }, onAdjust = { adjustItem = it })
                1 -> StockMovements(state)
                2 -> StockAlerts(state, onRestock = { restockItem = it })
            }
        }
    }

    if (restockItem != null) {
        RestockDialog(
            item = restockItem!!,
            currency = state.currencySymbol,
            onDismiss = { restockItem = null },
            onConfirm = { qty, note ->
                vm.restockItem(restockItem!!.id, qty, note)
                restockItem = null
            },
        )
    }
    if (adjustItem != null) {
        AdjustStockDialog(
            item = adjustItem!!,
            onDismiss = { adjustItem = null },
            onConfirm = { newQty, note ->
                vm.adjustStock(adjustItem!!.id, newQty, note)
                adjustItem = null
            },
        )
    }
}

@Composable
private fun StockStatus(
    state: PosState,
    onRestock: (Item) -> Unit,
    onAdjust: (Item) -> Unit,
) {
    val tracked = state.items.filter { it.active && it.trackStock }
    if (tracked.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("אין פריטים עם מעקב מלאי. הפעל מעקב מלאי בעריכת פריט.",
                 color = Muted, modifier = Modifier.padding(20.dp))
        }
        return
    }
    LazyColumn(
        Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val byCategory = tracked.groupBy { it.category }
        byCategory.forEach { (cat, list) ->
            item {
                Text(cat, color = Accent, fontWeight = FontWeight.Bold,
                     modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            items(list, key = { it.id }) { item ->
                val low = item.stockOnHand <= item.lowStockThreshold
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color.White,
                                 fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${trim(item.stockOnHand)} ${item.unit}",
                                color = if (item.stockOnHand <= 0) Bad
                                        else if (low) Warning
                                        else Good,
                                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            )
                            Text("מינימום: ${trim(item.lowStockThreshold)} ${item.unit}",
                                 color = Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Button(
                                onClick = { onRestock(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = Good),
                                shape = RoundedCornerShape(8.dp),
                            ) { Text("+ מלאי", color = Color.White, fontSize = 13.sp) }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { onAdjust(item) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Muted,
                                ),
                            ) { Text("התאם", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StockMovements(state: PosState) {
    if (state.recentStockMovements.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("אין תנועות מלאי", color = Muted)
        }
        return
    }
    LazyColumn(
        Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.recentStockMovements, key = { it.id }) { mv ->
            val empName = if (mv.employeeId != null)
                state.employees.firstOrNull { it.id == mv.employeeId }?.name else null
            val color = if (mv.change > 0) Good else Bad
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(mv.itemName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "${reasonLabel(mv.reason)}${if (empName != null) " • $empName" else ""}",
                            color = Muted, fontSize = 12.sp,
                        )
                        Text(formatDateTime(mv.createdAt), color = Muted, fontSize = 11.sp)
                        if (!mv.note.isNullOrBlank()) {
                            Text("הערה: ${mv.note}", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Text(
                        if (mv.change > 0) "+${trim(mv.change)}" else trim(mv.change),
                        color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StockAlerts(state: PosState, onRestock: (Item) -> Unit) {
    val low = state.items.filter {
        it.active && it.trackStock && it.stockOnHand <= it.lowStockThreshold
    }
    if (low.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("✓ כל המלאי מעל המינימום", color = Good, fontSize = 16.sp)
        }
        return
    }
    LazyColumn(
        Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(low, key = { it.id }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                        val text = if (item.stockOnHand <= 0)
                            "אזל לחלוטין"
                        else
                            "נשארו ${trim(item.stockOnHand)} ${item.unit} (מינימום: ${trim(item.lowStockThreshold)})"
                        Text(text,
                             color = if (item.stockOnHand <= 0) Bad else Warning,
                             fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onRestock(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = Good),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("הוסף מלאי", color = Color.White) }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun RestockDialog(
    item: Item, currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String?) -> Unit,
) {
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוספת מלאי: ${item.name}") },
        text = {
            Column {
                Text("מלאי נוכחי: ${trim(item.stockOnHand)} ${item.unit}",
                     color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it },
                    label = { Text("כמות להוספה (${item.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("הערה (אופציונלי)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toDoubleOrNull() ?: 0.0
                if (q > 0) onConfirm(q, note.ifBlank { null })
            }) { Text("הוסף", color = Good, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun AdjustStockDialog(
    item: Item,
    onDismiss: () -> Unit,
    onConfirm: (Double, String?) -> Unit,
) {
    var qty by remember { mutableStateOf(trim(item.stockOnHand)) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("התאמת מלאי: ${item.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it },
                    label = { Text("מלאי חדש (${item.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("סיבה (ספירת מלאי, פחת וכו')") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toDoubleOrNull()
                if (q != null && q >= 0) onConfirm(q, note.ifBlank { null })
            }) { Text("התאם") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

private fun reasonLabel(reason: String): String = when (reason) {
    "restock" -> "כניסת מלאי"
    "sale" -> "מכירה"
    "void" -> "ביטול"
    "adjust" -> "התאמה"
    "event_start" -> "פתיחת אירוע"
    else -> reason
}
