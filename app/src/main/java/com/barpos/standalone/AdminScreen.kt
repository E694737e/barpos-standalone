package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
internal fun AdminScreen(state: PosState, vm: PosViewModel) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("מוצרים", "עובדים", "הגדרות עסק", "הגדרות מערכת")

    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp)) {
        Text("ניהול", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Panel,
                         contentColor = Accent, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, t ->
                M3Tab(tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> ProductsTab(state, vm)
                1 -> EmployeesTab(state, vm)
                2 -> BusinessSettingsTab(state, vm)
                3 -> SystemSettingsTab(state, vm)
            }
        }
    }
}

@Composable
private fun ProductsTab(state: PosState, vm: PosViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Item?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Button(
            onClick = { showAdd = true },
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(8.dp),
        ) { Text("+ הוסף מוצר", color = Color.Black) }

        Spacer(Modifier.height(10.dp))
        val byCat = state.items.groupBy { it.category }
        byCat.forEach { (cat, list) ->
            Text(cat, color = Accent, fontWeight = FontWeight.Bold,
                 modifier = Modifier.padding(vertical = 6.dp))
            list.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name + if (!item.active) " (לא פעיל)" else "",
                            color = if (item.active) Color.White else Muted,
                        )
                        val extras = buildList {
                            add(item.unit)
                            if (item.trackStock) add("מלאי: ${trim(item.stockOnHand)}")
                        }.joinToString(" • ")
                        Text(extras, color = Muted, fontSize = 12.sp)
                    }
                    Text(
                        formatMoney(item.price, state.currencySymbol), color = Accent,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = { editItem = item }) {
                        Text("ערוך", color = Muted, fontSize = 12.sp)
                    }
                    if (item.active) {
                        IconButton(onClick = { vm.deleteItem(item.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "מחק", tint = Bad)
                        }
                    }
                }
                HorizontalDivider(color = CardBg)
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showAdd) {
        ItemDialog(
            initial = null, defaultUnit = state.defaultUnit,
            onDismiss = { showAdd = false },
            onSave = { name, price, cat, unit, track, stock, threshold ->
                vm.createItem(name, price, cat, unit, track, stock, threshold)
                showAdd = false
            },
        )
    }
    if (editItem != null) {
        ItemDialog(
            initial = editItem, defaultUnit = state.defaultUnit,
            onDismiss = { editItem = null },
            onSave = { name, price, cat, unit, track, stock, threshold ->
                vm.updateItem(editItem!!.copy(
                    name = name, price = price, category = cat,
                    unit = unit, trackStock = track,
                    stockOnHand = stock, lowStockThreshold = threshold,
                ))
                editItem = null
            },
        )
    }
}

@Composable
private fun EmployeesTab(state: PosState, vm: PosViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Button(
            onClick = { showAdd = true },
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(8.dp),
        ) { Text("+ הוסף עובד", color = Color.Black) }

        Spacer(Modifier.height(10.dp))
        state.employees.forEach { e ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        e.name + (if (e.isManager) " (מנהל)" else "") + (if (!e.active) " (לא פעיל)" else ""),
                        color = if (e.active) Color.White else Muted,
                    )
                    Text("PIN: ${e.pin}", color = Muted, fontSize = 12.sp)
                }
                if (e.active) {
                    IconButton(onClick = { vm.deleteEmployee(e.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "מחק", tint = Bad)
                    }
                }
            }
            HorizontalDivider(color = CardBg)
        }
    }

    if (showAdd) {
        AddEmployeeDialog(
            onDismiss = { showAdd = false },
            onSave = { name, pin, isMgr ->
                vm.createEmployee(name, pin, isMgr)
                showAdd = false
            },
        )
    }
}

@Composable
private fun BusinessSettingsTab(state: PosState, vm: PosViewModel) {
    var name by remember(state.businessName) { mutableStateOf(state.businessName) }
    var address by remember(state.businessAddress) { mutableStateOf(state.businessAddress) }
    var phone by remember(state.businessPhone) { mutableStateOf(state.businessPhone) }
    var taxId by remember(state.businessTaxId) { mutableStateOf(state.businessTaxId) }
    var taxRate by remember(state.taxRatePct) { mutableStateOf(state.taxRatePct.toString()) }
    var currency by remember(state.currencySymbol) { mutableStateOf(state.currencySymbol) }
    var taxIncluded by remember(state.taxIncluded) { mutableStateOf(state.taxIncluded) }
    var footer by remember(state.receiptFooter) { mutableStateOf(state.receiptFooter) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().padding(end = 12.dp),
    ) {
        SettingsCard("פרטי העסק (יודפסו בחשבוניות)") {
            OutlinedTextField(name, { name = it; saved = false }, label = { Text("שם העסק") },
                              singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(address, { address = it; saved = false }, label = { Text("כתובת") },
                              singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(phone, { phone = it; saved = false }, label = { Text("טלפון") },
                              singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(taxId, { taxId = it; saved = false },
                              label = { Text("ע.מ./ע.ר./ח.פ.") },
                              singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        SettingsCard("מטבע ומס") {
            OutlinedTextField(currency, { currency = it; saved = false },
                              label = { Text("סמל מטבע") },
                              singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(taxRate, { taxRate = it; saved = false },
                              label = { Text("מע\"מ %") },
                              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                              singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = taxIncluded,
                         onCheckedChange = { taxIncluded = it; saved = false })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("מע\"מ כלול במחיר", color = Color.White, fontSize = 14.sp)
                    Text(if (taxIncluded)
                            "המחירים שמוצגים כוללים מע\"מ"
                         else "המחירים שמוצגים לפני מע\"מ - יתווסף בנפרד",
                         color = Muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsCard("חשבונית") {
            OutlinedTextField(footer, { footer = it; saved = false },
                              label = { Text("טקסט תחתון בחשבונית") },
                              modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("ייכתב בתחתית כל חשבונית, למשל \"תודה ולהתראות!\"",
                 color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val tax = taxRate.toDoubleOrNull() ?: 0.0
                    vm.saveAllSettings(mapOf(
                        SettingKeys.BUSINESS_NAME to name.ifBlank { "BarPOS" },
                        SettingKeys.BUSINESS_ADDRESS to address,
                        SettingKeys.BUSINESS_PHONE to phone,
                        SettingKeys.BUSINESS_TAX_ID to taxId,
                        SettingKeys.TAX_RATE to tax.toString(),
                        SettingKeys.CURRENCY_SYMBOL to currency.ifBlank { "₪" },
                        SettingKeys.TAX_INCLUDED to taxIncluded.toString(),
                        SettingKeys.RECEIPT_FOOTER to footer,
                    ))
                    saved = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("שמור הגדרות", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            if (saved) {
                Spacer(Modifier.width(12.dp))
                Text("ההגדרות נשמרו", color = Good, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SystemSettingsTab(state: PosState, vm: PosViewModel) {
    var showClock by remember(state.showClock) { mutableStateOf(state.showClock) }
    var defaultUnit by remember(state.defaultUnit) { mutableStateOf(state.defaultUnit) }
    var lowStock by remember(state.lowStockThreshold) {
        mutableStateOf(state.lowStockThreshold.toString())
    }
    var tableCount by remember(state.tableCount) {
        mutableStateOf(state.tableCount.toString())
    }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().padding(end = 12.dp),
    ) {
        SettingsCard("שעון ותצוגה") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showClock, onCheckedChange = { showClock = it; saved = false })
                Spacer(Modifier.width(8.dp))
                Text("הצג שעון בכותרת", color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsCard("ברירות מחדל מוצרים") {
            OutlinedTextField(defaultUnit, { defaultUnit = it; saved = false },
                              label = { Text("יחידת מידה ברירת מחדל") },
                              singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(lowStock, { lowStock = it; saved = false },
                              label = { Text("רף מלאי נמוך ברירת מחדל") },
                              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                              singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        SettingsCard("שולחנות") {
            OutlinedTextField(tableCount, { tableCount = it; saved = false },
                              label = { Text("מספר שולחנות בעסק") },
                              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                              singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("ייצור גריד של שולחנות בכרטיסיית \"שולחנות\". כל שולחן עם מספר.",
                 color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        SettingsCard("גרסה ותחזוקה") {
            Text("גרסה: 2.1.0", color = Color.White, fontSize = 13.sp)
            Text("בסיס נתונים: גרסה 5", color = Muted, fontSize = 12.sp)
            Text("מסלול נתונים: אחסון פנימי של האפליקציה",
                 color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    vm.saveAllSettings(mapOf(
                        SettingKeys.SHOW_CLOCK to showClock.toString(),
                        SettingKeys.DEFAULT_UNIT to defaultUnit.ifBlank { "יח׳" },
                        SettingKeys.LOW_STOCK_THRESHOLD to (lowStock.toDoubleOrNull() ?: 5.0).toString(),
                        SettingKeys.TABLE_COUNT to (tableCount.toIntOrNull() ?: 12).toString(),
                    ))
                    saved = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("שמור הגדרות", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            if (saved) {
                Spacer(Modifier.width(12.dp))
                Text("ההגדרות נשמרו", color = Good, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White,
                 fontSize = 16.sp, fontWeight = FontWeight.Bold,
                 modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun ItemDialog(
    initial: Item?,
    defaultUnit: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String, price: Double, category: String, unit: String,
        trackStock: Boolean, stock: Double, lowStockThreshold: Double,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "כללי") }
    var unit by remember { mutableStateOf(initial?.unit ?: defaultUnit) }
    var trackStock by remember { mutableStateOf(initial?.trackStock ?: false) }
    var stock by remember { mutableStateOf(trim(initial?.stockOnHand ?: 0.0)) }
    var threshold by remember { mutableStateOf(trim(initial?.lowStockThreshold ?: 0.0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "מוצר חדש" else "ערוך מוצר") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("שם") },
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(price, { price = it }, label = { Text("מחיר") },
                                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(category, { category = it }, label = { Text("קטגוריה") },
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(unit, { unit = it }, label = { Text("יחידת מידה (יח׳, ק״ג, ליטר וכו')") },
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(trackStock, { trackStock = it })
                    Text("עקוב אחרי מלאי", color = Color.White, fontSize = 14.sp)
                }
                if (trackStock) {
                    OutlinedTextField(stock, { stock = it }, label = { Text("מלאי נוכחי") },
                                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                      modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(threshold, { threshold = it },
                                      label = { Text("רף מלאי נמוך (התראה)") },
                                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                      modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.toDoubleOrNull() ?: return@TextButton
                if (name.isNotBlank()) {
                    onSave(
                        name, p, category.ifBlank { "כללי" }, unit.ifBlank { defaultUnit },
                        trackStock,
                        stock.toDoubleOrNull() ?: 0.0,
                        threshold.toDoubleOrNull() ?: 0.0,
                    )
                }
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun AddEmployeeDialog(onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isMgr by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עובד חדש") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("שם") },
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pin, { pin = it }, label = { Text("קוד PIN") },
                                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(isMgr, { isMgr = it })
                    Text("מנהל", color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && pin.isNotBlank()) onSave(name, pin, isMgr)
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
