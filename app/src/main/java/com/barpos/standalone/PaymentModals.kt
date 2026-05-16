package com.barpos.standalone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Represents a payment in progress that the user is still configuring.
 * Source can be "cart" (direct register) or tab (an open tab id).
 */
data class PendingPayment(
    val method: String,
    val sourceKind: String,   // "cart" or "tab"
    val sourceTabId: Long? = null,
    val baseSubtotal: Double, // amount before tip
    val tax: Double = 0.0,
    val tip: Double = 0.0,
    val cashReceived: Double = 0.0,
) {
    val total: Double get() = baseSubtotal + tax + tip
    val change: Double get() = (cashReceived - total).coerceAtLeast(0.0)
}

@Composable
internal fun CashPaymentDialog(
    pending: PendingPayment,
    currency: String,
    onAmountChange: (Double) -> Unit,
    onTipChange: (Double) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember(pending.baseSubtotal) {
        mutableStateOf(if (pending.cashReceived > 0) trimMoney(pending.cashReceived) else "")
    }
    var tipText by remember(pending.baseSubtotal) {
        mutableStateOf(if (pending.tip > 0) trimMoney(pending.tip) else "")
    }
    val total = pending.total
    val received = amountText.toDoubleOrNull() ?: 0.0
    val change = (received - total).coerceAtLeast(0.0)
    val insufficient = received in 0.001..(total - 0.001)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("תשלום במזומן", color = Accent, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("סכום ביניים", color = Muted)
                    Text(formatMoney(pending.baseSubtotal, currency), color = Color.White)
                }
                if (pending.tax > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("מע\"מ", color = Muted)
                        Text(formatMoney(pending.tax, currency), color = Color.White)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = tipText,
                    onValueChange = {
                        tipText = it
                        onTipChange(it.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("טיפ (אופציונלי)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("סה״כ לתשלום", color = Color.White,
                         fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        formatMoney(total, currency),
                        color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        onAmountChange(it.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("סכום שהתקבל") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = insufficient,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (insufficient) {
                    Text(
                        "הסכום נמוך מהחיוב",
                        color = Bad, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("עודף", color = Color.White, fontSize = 15.sp)
                    Text(
                        formatMoney(change, currency),
                        color = Good, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = received >= total - 0.001,
            ) { Text("אשר תשלום", color = Good, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        },
    )
}

@Composable
internal fun CreditPaymentDialog(
    pending: PendingPayment,
    currency: String,
    onTipChange: (Double) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var customTipText by remember(pending.baseSubtotal) { mutableStateOf("") }
    val tipPercents = listOf(0, 10, 12, 15, 20)
    var selectedPct by remember(pending.baseSubtotal) { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("תשלום באשראי", color = Accent, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("סכום ביניים", color = Muted)
                    Text(formatMoney(pending.baseSubtotal, currency), color = Color.White)
                }
                if (pending.tax > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("מע\"מ", color = Muted)
                        Text(formatMoney(pending.tax, currency), color = Color.White)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("טיפ", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                // Percentage buttons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tipPercents.forEach { pct ->
                        val isSel = selectedPct == pct && customTipText.isEmpty()
                        OutlinedButton(
                            onClick = {
                                selectedPct = pct
                                customTipText = ""
                                onTipChange(pending.baseSubtotal * pct / 100.0)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) Accent else Color.Transparent,
                                contentColor = if (isSel) Color.Black else Color.White,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            Text(if (pct == 0) "ללא" else "$pct%", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customTipText,
                    onValueChange = {
                        customTipText = it
                        val v = it.toDoubleOrNull() ?: 0.0
                        onTipChange(v)
                        selectedPct = -1
                    },
                    label = { Text("טיפ מותאם ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("טיפ", color = Muted)
                    Text(formatMoney(pending.tip, currency), color = Good)
                }
                HorizontalDivider(color = CardBg, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("סה״כ לחיוב",
                         color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        formatMoney(pending.total, currency),
                        color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "הערה: באנדרואיד אין סליקה אוטומטית. אחרי \"אשר חיוב\" - השתמש במסוף האשראי הפיזי שלך לחיוב הסכום הסופי.",
                    color = Muted, fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("אשר חיוב", color = BlueBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        },
    )
}

@Composable
internal fun ReceiptSuccessDialog(
    receipt: ReceiptSummary,
    currency: String,
    onPrintShare: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("תשלום בוצע ✓", color = Good, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("חשבונית #${receipt.txId}", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                receipt.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${line.quantity} × ${line.itemName}",
                             color = Color.White, fontSize = 13.sp)
                        Text(formatMoney(line.priceAtTime * line.quantity, currency),
                             color = Color.White, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = CardBg, modifier = Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ביניים", color = Muted)
                    Text(formatMoney(receipt.subtotal, currency), color = Color.White)
                }
                if (receipt.tax > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("מע\"מ", color = Muted)
                        Text(formatMoney(receipt.tax, currency), color = Color.White)
                    }
                }
                if (receipt.tip > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("טיפ", color = Muted)
                        Text(formatMoney(receipt.tip, currency), color = Good)
                    }
                }
                HorizontalDivider(color = CardBg, modifier = Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("סה״כ", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(formatMoney(receipt.total, currency),
                         color = Accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                if (receipt.paymentMethod == "cash" && receipt.cashReceived > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("התקבל", color = Muted)
                        Text(formatMoney(receipt.cashReceived, currency), color = Color.White)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("עודף", color = Muted)
                        Text(formatMoney(receipt.changeGiven, currency), color = Good)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPrintShare) {
                Text("הדפס / שתף PDF", color = Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("סגור") }
        },
    )
}

data class ReceiptSummary(
    val txId: Long,
    val subtotal: Double,
    val tax: Double,
    val tip: Double,
    val total: Double,
    val paymentMethod: String,
    val cashReceived: Double,
    val changeGiven: Double,
    val createdAt: Long,
    val lines: List<TxItem>,
    val employeeName: String,
)

private fun trimMoney(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)
