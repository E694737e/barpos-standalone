package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
internal fun RefundsScreen(state: PosState, vm: PosViewModel) {
    val tabs = listOf("עסקאות לזיכוי", "זיכויים שניתנו")
    var selected by remember { mutableStateOf(0) }
    var selectedTx by remember { mutableStateOf<Tx?>(null) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("זיכויים",
                 color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            val totalRefunds = state.refundTxs.sumOf { -it.total }
            Text("סה״כ זיכויים: ${formatMoney(totalRefunds, state.currencySymbol)}",
                 color = Muted, fontSize = 13.sp)
        }

        TabRow(selectedTabIndex = selected, containerColor = Panel, contentColor = Accent) {
            tabs.forEachIndexed { i, t ->
                M3Tab(selected = selected == i, onClick = { selected = i }, text = { Text(t) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> CompletedListForRefund(state, onSelect = { selectedTx = it })
                1 -> GivenRefunds(state)
            }
        }
    }

    if (selectedTx != null) {
        RefundDialog(
            tx = selectedTx!!,
            state = state,
            onDismiss = { selectedTx = null },
            onConfirm = { amount, reason, method ->
                vm.refundTransaction(selectedTx!!.id, amount, reason, method)
                selectedTx = null
            },
        )
    }
}

@Composable
private fun CompletedListForRefund(state: PosState, onSelect: (Tx) -> Unit) {
    if (state.completedTxs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("אין עסקאות זמינות לזיכוי", color = Muted)
        }
        return
    }
    LazyColumn(
        Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.completedTxs, key = { it.id }) { tx ->
            val empName = state.employees.firstOrNull { it.id == tx.employeeId }?.name ?: ""
            // Has this tx already been refunded? (sum of refunds for this tx)
            val refundsForThis = state.refundTxs
                .filter { it.refundOfTxId == tx.id }
                .sumOf { -it.total }
            val available = tx.total - refundsForThis
            Card(
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = available > 0.01) { onSelect(tx) },
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("עסקה #${tx.id}",
                             color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(formatMoney(tx.total, state.currencySymbol),
                             color = Accent, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${formatDateTime(tx.createdAt)} • ${paymentMethodLabel(tx.paymentMethod)} • $empName",
                        color = Muted, fontSize = 12.sp,
                    )
                    if (refundsForThis > 0) {
                        Text(
                            "כבר זוכה: ${formatMoney(refundsForThis, state.currencySymbol)} | ניתן עוד: ${formatMoney(available, state.currencySymbol)}",
                            color = if (available > 0.01) Warning else Bad,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun GivenRefunds(state: PosState) {
    if (state.refundTxs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("עדיין לא ניתנו זיכויים", color = Muted)
        }
        return
    }
    LazyColumn(
        Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.refundTxs, key = { it.id }) { tx ->
            val empName = state.employees.firstOrNull { it.id == tx.employeeId }?.name ?: ""
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("זיכוי #${tx.id}",
                             color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(formatMoney(-tx.total, state.currencySymbol),
                             color = Bad, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${formatDateTime(tx.createdAt)} • ${paymentMethodLabel(tx.paymentMethod)} • $empName",
                        color = Muted, fontSize = 12.sp,
                    )
                    if (tx.refundOfTxId != null) {
                        Text("עבור עסקה #${tx.refundOfTxId}", color = Muted, fontSize = 12.sp)
                    }
                    if (!tx.refundReason.isNullOrBlank()) {
                        Text("סיבה: ${tx.refundReason}", color = Warning, fontSize = 13.sp,
                             modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun RefundDialog(
    tx: Tx,
    state: PosState,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, reason: String, method: String) -> Unit,
) {
    val refundsForThis = state.refundTxs
        .filter { it.refundOfTxId == tx.id }
        .sumOf { -it.total }
    val available = (tx.total - refundsForThis).coerceAtLeast(0.0)

    var amountText by remember { mutableStateOf(trim2(available)) }
    var reason by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(tx.paymentMethod) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("זיכוי עסקה #${tx.id}", color = Accent, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("עסקה מקורית:", color = Muted, fontSize = 13.sp)
                Text(formatMoney(tx.total, state.currencySymbol),
                     color = Color.White, fontWeight = FontWeight.Bold)
                Text("(ניתן לזכות עד ${formatMoney(available, state.currencySymbol)})",
                     color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("סכום זיכוי") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("שיטת החזר:", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("cash" to "מזומן", "credit" to "אשראי").forEach { (k, label) ->
                        OutlinedButton(
                            onClick = { method = k },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (method == k) Accent else Color.Transparent,
                                contentColor = if (method == k) Color.Black else Color.White,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("סיבה") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = amountText.toDoubleOrNull() ?: 0.0
                    if (a in 0.01..(available + 0.01)) {
                        onConfirm(a, reason, method)
                    }
                },
            ) { Text("בצע זיכוי", color = Bad, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

private fun trim2(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)
