package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun TabsScreen(
    state: PosState,
    vm: PosViewModel,
    openTab: (Long) -> Unit,
) {
    var showNewDialog by remember { mutableStateOf(false) }
    val tabs = state.openTabs

    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "חשבונות פתוחים (${tabs.size})",
                color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { showNewDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("+ חשבון חדש", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (tabs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "אין חשבונות פתוחים כעת. לחץ \"חשבון חדש\" כדי לפתוח.",
                    color = Muted, fontSize = 16.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab, currency = state.currencySymbol,
                        onClick = { openTab(tab.id) },
                    )
                }
            }
        }
    }

    if (showNewDialog) {
        NewTabDialog(
            onDismiss = { showNewDialog = false },
            onCreate = { name, cust ->
                vm.createTab(name, cust)
                showNewDialog = false
                openTab(-1L)  // signal: navigate to POS; workingTab will be set async
            },
        )
    }
}

@Composable
private fun TabCard(
    tab: Tab, currency: String,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val db = remember { (ctx.applicationContext as BarPosApp).db }
    val itemsFlow = remember(tab.id) { db.tabs().observeItems(tab.id) }
    val items by itemsFlow.collectAsState(initial = emptyList())
    val active = items.filter { !it.voided }
    val sum = active.sumOf { it.priceAtTime * it.quantity }
    val count = active.sumOf { it.quantity }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.height(120.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tab.name, color = Color.White,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text("$count פריטים", color = Muted, fontSize = 13.sp)
            }
            if (!tab.customerName.isNullOrBlank()) {
                Text(tab.customerName!!, color = Muted, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "נפתח ${formatDateTime(tab.createdAt)}",
                    color = Muted, fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatMoney(sum, currency),
                    color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NewTabDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, customer: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("חשבון חדש") },
        text = {
            Column {
                Text("שולחן / מספר חשבון:", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("שולחן 1 / חשבון א'") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text("שם לקוח (אופציונלי):", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customer, onValueChange = { customer = it },
                    label = { Text("שם") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate(name.ifBlank { "אורח" }, customer.ifBlank { null })
            }) { Text("פתח חשבון") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
