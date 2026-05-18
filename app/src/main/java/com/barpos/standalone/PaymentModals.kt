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
    val method: String,                 // "cash" | "credit" | "split"
    val sourceKind: String,
    val sourceTabId: Long? = null,
    val baseSubtotal: Double,
    val tax: Double = 0.0,
    val tip: Double = 0.0,
    val cashReceived: Double = 0.0,
    val splitCashAmount: Double = 0.0,  // for split: amount to be paid in cash
    val splitCreditAmount: Double = 0.0,
    val splitCashReceived: Double = 0.0, // for split: amount of cash received (>= splitCashAmount)
) {
    val total: Double get() = baseSubtotal + tax + tip
    val change: Double get() = (cashReceived - total).coerceAtLeast(0.0)
    val splitChange: Double get() = (splitCashReceived - splitCashAmount).coerceAtLeast(0.0)
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
internal fun SplitPaymentDialog(
    pending: PendingPayment,
    currency: String,
    onSplitChange: (cashAmount: Double, creditAmount: Double) -> Unit,
    onCashReceivedChange: (Double) -> Unit,
    onTipChange: (Double) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tipText by remember(pending.baseSubtotal) {
        mutableStateOf(if (pending.tip > 0) trimMoney2(pending.tip) else "")
    }
    var cashAmountText by remember(pending.baseSubtotal) {
        mutableStateOf(if (pending.splitCashAmount > 0) trimMoney2(pending.splitCashAmount) else "")
    }
    var creditAmountText by remember(pending.baseSubtotal) {
        mutableStateOf(if (pending.splitCreditAmount > 0) trimMoney2(pending.splitCreditAmount) else "")
    }
    var cashReceivedText by remember(pending.baseSubtotal) {
        mutableStateOf(if (pending.splitCashReceived > 0) trimMoney2(pending.splitCashReceived) else "")
    }
    // Track which side the user last edited - to auto-fill the other side
    var lastEdited by remember { mutableStateOf("cash") }

    val total = pending.total
    val cash = cashAmountText.toDoubleOrNull() ?: 0.0
    val credit = creditAmountText.toDoubleOrNull() ?: 0.0
    val sumOfSplit = cash + credit
    val balanced = kotlin.math.abs(sumOfSplit - total) < 0.01
    val cashReceived = cashReceivedText.toDoubleOrNull() ?: 0.0
    val cashShortfall = cashReceived in 0.001..(cash - 0.001)
    val splitChange = (cashReceived - cash).coerceAtLeast(0.0)

    fun applySplit(c: Double, cr: Double) {
        onSplitChange(c, cr)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פיצול תשלום", color = Accent, fontWeight = FontWeight.Bold) },
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
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("סה\u05ea\u05db לחיוב", color = Color.White,
                         fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        formatMoney(total, currency),
                        color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
                }
                HorizontalDivider(color = CardBg, modifier = Modifier.padding(vertical = 8.dp))

                Text("חלוקה בין אמצעי תשלום", color = Color.White,
                     fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "שנה אחד מהשדות - השני יתעדכן אוטומטית",
                    color = Muted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = cashAmountText,
                    onValueChange = {
                        cashAmountText = it
                        lastEdited = "cash"
                        val c = it.toDoubleOrNull() ?: 0.0
                        val cr = (total - c).coerceAtLeast(0.0)
                        creditAmountText = if (cr == 0.0) "" else trimMoney2(cr)
                        applySplit(c, cr)
                    },
                    label = { Text("סכום במזומן") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = creditAmountText,
                    onValueChange = {
                        creditAmountText = it
                        lastEdited = "credit"
                        val cr = it.toDoubleOrNull() ?: 0.0
                        val c = (total - cr).coerceAtLeast(0.0)
                        cashAmountText = if (c == 0.0) "" else trimMoney2(c)
                        applySplit(c, cr)
                    },
                    label = { Text("סכום באשראי") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Quick presets
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val presets = listOf(
                        "50/50" to 0.5,
                        "70 מזומן" to (70.0 / total).coerceAtMost(1.0),
                        "100 מזומן" to (100.0 / total).coerceAtMost(1.0),
                        "200 מזומן" to (200.0 / total).coerceAtMost(1.0),
                    )
                    presets.forEach { (label, fraction) ->
                        OutlinedButton(
                            onClick = {
                                val c: Double
                                val cr: Double
                                if (label == "50/50") {
                                    c = total / 2.0
                                    cr = total - c
                                } else {
                                    val targetCash = label.substringBefore(" ").toDoubleOrNull() ?: 0.0
                                    c = targetCash.coerceAtMost(total)
                                    cr = (total - c).coerceAtLeast(0.0)
                                }
                                cashAmountText = trimMoney2(c)
                                creditAmountText = if (cr == 0.0) "" else trimMoney2(cr)
                                applySplit(c, cr)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                        ) {
                            Text(label, fontSize = 10.sp)
                        }
                    }
                }

                if (!balanced) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "הסכומים לא מסתכמים לסכום הכולל. " +
                                "הפרש: ${formatMoney(total - sumOfSplit, currency)}",
                        color = Warning, fontSize = 12.sp,
                    )
                }

                HorizontalDivider(color = CardBg, modifier = Modifier.padding(vertical = 8.dp))

                // Cash received input (only if cash > 0)
                if (cash > 0.001) {
                    Text("מזומן שהתקבל", color = Color.White,
                         fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = cashReceivedText,
                        onValueChange = {
                            cashReceivedText = it
                            onCashReceivedChange(it.toDoubleOrNull() ?: 0.0)
                        },
                        label = { Text("סכום מזומן שהתקבל") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = cashShortfall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (cashShortfall) {
                        Text(
                            "הסכום במזומן נמוך מהחיוב במזומן",
                            color = Bad, fontSize = 12.sp,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("עודף", color = Color.White)
                        Text(formatMoney(splitChange, currency),
                             color = Good, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            val canConfirm = balanced && (cash <= 0.001 || cashReceived >= cash - 0.001)
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
            ) { Text("אשר תשלום מפוצל",
                    color = if (canConfirm) Good else Muted,
                    fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        },
    )
}

private fun trimMoney2(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)

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
                } else if (receipt.paymentMethod == "split") {
                    HorizontalDivider(color = CardBg, modifier = Modifier.padding(vertical = 4.dp))
                    Text("חלוקה:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  מזומן", color = Muted)
                        Text(formatMoney(receipt.splitCashAmount, currency), color = Color.White)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  אשראי", color = Muted)
                        Text(formatMoney(receipt.splitCreditAmount, currency), color = Color.White)
                    }
                    if (receipt.splitCashReceived > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("  מזומן שהתקבל", color = Muted)
                            Text(formatMoney(receipt.splitCashReceived, currency), color = Color.White)
                        }
                        if (receipt.splitChangeGiven > 0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("  עודף", color = Muted)
                                Text(formatMoney(receipt.splitChangeGiven, currency), color = Good)
                            }
                        }
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
    val splitCashAmount: Double = 0.0,
    val splitCreditAmount: Double = 0.0,
    val splitCashReceived: Double = 0.0,
    val splitChangeGiven: Double = 0.0,
    val createdAt: Long,
    val lines: List<TxItem>,
    val employeeName: String,
)

private fun trimMoney(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)
