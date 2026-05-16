package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Tab as M3Tab
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VoidsScreen(state: PosState, vm: PosViewModel) {
    val tabs = listOf("עסקאות שבוטלו", "חשבונות שבוטלו", "פריטים שבוטלו")
    var selected by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ביטולים",
                color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            val totalVoided = state.voidedTxs.sumOf { it.total }
            Text(
                "סה״כ בוטל: ${formatMoney(totalVoided, state.currencySymbol)}",
                color = Muted, fontSize = 13.sp,
            )
        }

        TabRow(selectedTabIndex = selected, containerColor = Panel, contentColor = Accent) {
            tabs.forEachIndexed { i, t ->
                M3Tab(selected = selected == i, onClick = { selected = i }, text = { Text(t) })
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                0 -> VoidedTransactions(state)
                1 -> CancelledTabs(state)
                2 -> VoidedItems(state)
            }
        }
    }
}

@Composable
private fun VoidedTransactions(state: PosState) {
    if (state.voidedTxs.isEmpty()) {
        EmptyVoidsHint("לא קיימות עסקאות שבוטלו.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.voidedTxs, key = { it.id }) { tx ->
            val empName = state.employees.firstOrNull { it.id == tx.employeeId }?.name ?: ""
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("עסקה #${tx.id}",
                             color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(formatMoney(tx.total, state.currencySymbol),
                             color = Bad, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "המקור: ${formatDateTime(tx.createdAt)} • ${paymentMethodLabel(tx.paymentMethod)} • $empName",
                        color = Muted, fontSize = 12.sp,
                    )
                    if (tx.voidedAt != null) {
                        Text("בוטלה: ${formatDateTime(tx.voidedAt)}",
                             color = Muted, fontSize = 12.sp)
                    }
                    if (!tx.voidReason.isNullOrBlank()) {
                        Text("סיבה: ${tx.voidReason}",
                             color = Warning, fontSize = 13.sp,
                             modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CancelledTabs(state: PosState) {
    if (state.cancelledTabs.isEmpty()) {
        EmptyVoidsHint("לא קיימים חשבונות שבוטלו.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.cancelledTabs, key = { it.id }) { tab ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tab.name, color = Color.White, fontWeight = FontWeight.Bold)
                        if (!tab.customerName.isNullOrBlank()) {
                            Text(" — ${tab.customerName}", color = Muted, fontSize = 13.sp)
                        }
                    }
                    Text("נפתח: ${formatDateTime(tab.createdAt)}",
                         color = Muted, fontSize = 12.sp)
                    if (tab.closedAt != null) {
                        Text("בוטל: ${formatDateTime(tab.closedAt)}",
                             color = Muted, fontSize = 12.sp)
                    }
                    if (!tab.cancelReason.isNullOrBlank()) {
                        Text("סיבה: ${tab.cancelReason}",
                             color = Warning, fontSize = 13.sp,
                             modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VoidedItems(state: PosState) {
    if (state.voidedTabItems.isEmpty()) {
        EmptyVoidsHint("לא קיימים פריטים שבוטלו מחשבונות.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.voidedTabItems, key = { it.id }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.itemName, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${item.quantity} × ${formatMoney(item.priceAtTime, state.currencySymbol)}",
                            color = Bad, fontSize = 13.sp,
                        )
                    }
                    Text(
                        "נוסף: ${formatDateTime(item.addedAt)}" +
                                (item.voidedAt?.let { " • בוטל: ${formatDateTime(it)}" } ?: ""),
                        color = Muted, fontSize = 12.sp,
                    )
                    if (!item.voidReason.isNullOrBlank()) {
                        Text("סיבה: ${item.voidReason}",
                             color = Warning, fontSize = 13.sp,
                             modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyVoidsHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 15.sp)
    }
}
