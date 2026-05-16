package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AdminScreen(state: PosState, vm: PosViewModel) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("מוצרים", "עובדים", "הגדרות עסק")

    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ניהול", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        TabRow(selectedTabIndex = tab, containerColor = Panel, contentColor = Accent) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> ProductsTab(state, vm)
                1 -> EmployeesTab(state, vm)
                2 -> SettingsTab(state, vm)
            }
        }
    }
}

@Composable
private fun ProductsTab(state: PosState, vm: PosViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Button(
            onClick = { showAdd = true },
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(8.dp),
        ) { Text("+ הוסף מוצר", color = Color.Black) }

        Spacer(Modifier.height(10.dp))
        state.items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name + if (!item.active) " (לא פעיל)" else "",
                        color = if (item.active) Color.White else Muted,
                    )
                    Text(item.category, color = Muted, fontSize = 12.sp)
                }
                Text(
                    formatMoney(item.price, state.currencySymbol), color = Accent,
                    modifier = Modifier.padding(end = 12.dp),
                )
                if (item.active) {
                    IconButton(onClick = { vm.deleteItem(item.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "מחק", tint = Bad)
                    }
                }
            }
            HorizontalDivider(color = CardBg)
        }
    }

    if (showAdd) {
        AddItemDialog(
            onDismiss = { showAdd = false },
            onSave = { name, price, cat ->
                vm.createItem(name, price, cat)
                showAdd = false
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
private fun SettingsTab(state: PosState, vm: PosViewModel) {
    var businessName by remember(state.businessName) { mutableStateOf(state.businessName) }
    var taxRate by remember(state.taxRatePct) { mutableStateOf(state.taxRatePct.toString()) }
    var currency by remember(state.currencySymbol) { mutableStateOf(state.currencySymbol) }
    var taxIncluded by remember(state.taxIncluded) { mutableStateOf(state.taxIncluded) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().padding(end = 12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "פרטי העסק",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = businessName, onValueChange = { businessName = it; saved = false },
                    label = { Text("שם העסק") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = currency, onValueChange = { currency = it; saved = false },
                    label = { Text("סמל מטבע (₪, $, € וכו')") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "מע\"מ",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = taxRate, onValueChange = { taxRate = it; saved = false },
                    label = { Text("שיעור מע\"מ (אחוזים)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = taxIncluded,
                        onCheckedChange = { taxIncluded = it; saved = false },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "מע\"מ כלול במחיר המוצרים",
                            color = Color.White, fontSize = 14.sp,
                        )
                        Text(
                            if (taxIncluded)
                                "המחיר שמוצג בקופה הוא כולל מע\"מ"
                            else
                                "המחיר שמוצג הוא לפני מע\"מ - מע\"מ יחושב בנפרד",
                            color = Muted, fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val tax = taxRate.toDoubleOrNull() ?: 0.0
                    vm.saveBusinessSettings(
                        name = businessName.ifBlank { "BarPOS" },
                        taxPct = tax,
                        currency = currency.ifBlank { "₪" },
                        taxIncluded = taxIncluded,
                    )
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AddItemDialog(onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("כללי") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מוצר חדש") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("שם") },
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(price, { price = it }, label = { Text("מחיר") },
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(category, { category = it }, label = { Text("קטגוריה") },
                                  modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.toDoubleOrNull() ?: return@TextButton
                if (name.isNotBlank()) onSave(name, p, category.ifBlank { "כללי" })
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
