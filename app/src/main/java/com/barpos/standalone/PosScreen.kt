package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PosScreen(state: PosState, vm: PosViewModel) {
    val activeItems = remember(state.items) { state.items.filter { it.active } }
    val categories = remember(activeItems) {
        listOf<String?>(null) + activeItems.map { it.category }.distinct().sorted()
    }
    var category by remember { mutableStateOf<String?>(null) }
    val visibleItems = remember(activeItems, category) {
        if (category == null) activeItems else activeItems.filter { it.category == category }
    }

    val cartTotal = state.cart.sumOf { it.item.price * it.qty }
    val cartCount = state.cart.sumOf { it.qty }

    val workingTab = state.workingTab
    val tabItems = state.workingTabItems.filter { !it.voided }
    val tabTotal = tabItems.sumOf { it.priceAtTime * it.quantity }
    val tabCount = tabItems.sumOf { it.quantity }

    val onTab = workingTab != null
    val totalShown = if (onTab) tabTotal else cartTotal
    val countShown = if (onTab) tabCount else cartCount

    var voidDialogTabItem by remember { mutableStateOf<TabItem?>(null) }
    var confirmCancelTab by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(12.dp)) {

            if (onTab) {
                Surface(
                    color = BlueBtn,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { vm.closeWorkingTabView() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                 contentDescription = "חזרה", tint = Color.White)
                        }
                        Text(
                            "עובד על חשבון: ${workingTab!!.name}",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            workingTab.customerName ?: "",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { c ->
                    val active = c == category
                    Button(
                        onClick = { category = c },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) Accent else CardBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(c ?: "הכל", color = if (active) Color.Black else Color.White)
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleItems, key = { it.id }) { item ->
                    val outOfStock = item.trackStock && item.stockOnHand <= 0
                    Card(
                        modifier = Modifier
                            .height(96.dp).fillMaxWidth()
                            .clickable(enabled = !outOfStock) { vm.addToCart(item) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (outOfStock) Bg else CardBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                item.name,
                                color = if (outOfStock) Muted else Color.White, fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                            )
                            if (item.trackStock) {
                                Text(
                                    if (outOfStock) "אזל" else "מלאי: ${trim(item.stockOnHand)} ${item.unit}",
                                    color = if (outOfStock) Bad else
                                            if (item.stockOnHand < item.lowStockThreshold) Warning
                                            else Muted,
                                    fontSize = 10.sp,
                                )
                            }
                            Text(
                                formatMoney(item.price, state.currencySymbol),
                                color = if (outOfStock) Muted else Accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .width(360.dp).fillMaxHeight()
                .background(Panel).padding(14.dp)
        ) {
            Text(
                if (onTab) "פריטי חשבון" else "חשבון נוכחי",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!onTab && state.cart.isEmpty()) {
                    Text(
                        "החשבון ריק. בחר פריטים מהרשימה.",
                        color = Muted, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (onTab && tabItems.isEmpty()) {
                    Text(
                        "אין פריטים בחשבון עדיין.",
                        color = Muted, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (onTab) {
                    LazyColumn {
                        itemsIndexed(tabItems) { _, item ->
                            TabItemRow(
                                item, state.currencySymbol,
                                onVoid = { voidDialogTabItem = item },
                            )
                            HorizontalDivider(color = CardBg)
                        }
                    }
                } else {
                    LazyColumn {
                        itemsIndexed(state.cart) { idx, line ->
                            CartLineRow(line, state.currencySymbol) { vm.removeCartLine(idx) }
                            HorizontalDivider(color = CardBg)
                        }
                    }
                }
            }

            HorizontalDivider(color = CardBg, thickness = 2.dp,
                              modifier = Modifier.padding(vertical = 8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("פריטים", color = Muted)
                Text("$countShown", color = Color.White)
            }
            if (!state.taxIncluded && totalShown > 0) {
                val taxFraction = state.taxRatePct / 100.0
                val taxAmount = totalShown * taxFraction
                val withTax = totalShown + taxAmount
                Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ביניים", color = Muted)
                    Text(formatMoney(totalShown, state.currencySymbol), color = Color.White)
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("מע\"מ ${state.taxRatePct.toInt()}%", color = Muted)
                    Text(formatMoney(taxAmount, state.currencySymbol), color = Color.White)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("סה״כ", color = Color.White, fontSize = 18.sp)
                    Text(formatMoney(withTax, state.currencySymbol),
                         color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("סה״כ", color = Color.White, fontSize = 18.sp)
                    Text(formatMoney(totalShown, state.currencySymbol),
                         color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (onTab) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { vm.startCheckoutTab(workingTab!!.id, "cash") },
                        enabled = tabItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Good),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("סגור - מזומן", color = Color.White,
                             fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { vm.startCheckoutTab(workingTab!!.id, "credit") },
                        enabled = tabItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueBtn),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("סגור - אשראי", color = Color.White,
                             fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { confirmCancelTab = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Bad),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("בטל חשבון", color = Color.White) }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { vm.startCheckoutCart("cash") },
                        enabled = state.cart.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Good),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("מזומן", color = Color.White,
                             fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { vm.startCheckoutCart("credit") },
                        enabled = state.cart.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueBtn),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("אשראי", color = Color.White,
                             fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { vm.clearCart() },
                    enabled = state.cart.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("נקה חשבון", color = Color.White) }
            }
        }
    }

    // Pending payment dialogs
    val pending = state.pendingPayment
    if (pending != null) {
        if (pending.method == "cash") {
            CashPaymentDialog(
                pending = pending,
                currency = state.currencySymbol,
                onAmountChange = { vm.updatePendingCash(it) },
                onTipChange = { vm.updatePendingTip(it) },
                onConfirm = { vm.confirmPending() },
                onDismiss = { vm.cancelPending() },
            )
        } else if (pending.method == "credit") {
            CreditPaymentDialog(
                pending = pending,
                currency = state.currencySymbol,
                onTipChange = { vm.updatePendingTip(it) },
                onConfirm = { vm.confirmPending() },
                onDismiss = { vm.cancelPending() },
            )
        }
    }

    // Receipt success
    val lastReceipt = state.lastReceipt
    if (lastReceipt != null) {
        ReceiptSuccessDialog(
            receipt = lastReceipt,
            currency = state.currencySymbol,
            onPrintShare = { vm.shareLastReceipt() },
            onClose = { vm.dismissReceipt() },
        )
    }

    if (voidDialogTabItem != null) {
        VoidReasonDialog(
            title = "ביטול פריט: ${voidDialogTabItem!!.itemName}",
            onDismiss = { voidDialogTabItem = null },
            onConfirm = { reason ->
                vm.voidTabItem(voidDialogTabItem!!.id, reason)
                voidDialogTabItem = null
            },
        )
    }

    if (confirmCancelTab && workingTab != null) {
        VoidReasonDialog(
            title = "ביטול חשבון: ${workingTab.name}",
            onDismiss = { confirmCancelTab = false },
            onConfirm = { reason ->
                vm.cancelTab(workingTab.id, reason)
                confirmCancelTab = false
            },
        )
    }
}

@Composable
private fun CartLineRow(line: CartLine, currency: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.item.name, color = Color.White, fontSize = 14.sp)
            Text(
                "${line.qty} × ${formatMoney(line.item.price, currency)}",
                color = Muted, fontSize = 12.sp,
            )
        }
        Text(
            formatMoney(line.item.price * line.qty, currency),
            color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "הסר", tint = Bad)
        }
    }
}

@Composable
private fun TabItemRow(item: TabItem, currency: String, onVoid: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.itemName, color = Color.White, fontSize = 14.sp)
            Text(
                "${item.quantity} × ${formatMoney(item.priceAtTime, currency)}",
                color = Muted, fontSize = 12.sp,
            )
        }
        Text(
            formatMoney(item.priceAtTime * item.quantity, currency),
            color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onVoid) {
            Icon(Icons.Filled.Close, contentDescription = "בטל פריט", tint = Bad)
        }
    }
}

@Composable
internal fun VoidReasonDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("ציין סיבה (אופציונלי):", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("סיבה") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.ifBlank { null }) }) {
                Text("אישור", color = Bad)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

internal fun trim(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)
