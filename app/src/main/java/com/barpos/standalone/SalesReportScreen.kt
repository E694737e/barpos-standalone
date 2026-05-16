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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class Range(val title: String) {
    Today("היום"),
    Week("השבוע (7 ימים)"),
    Month("החודש (30 ימים)"),
}

@Composable
internal fun SalesReportScreen(state: PosState, vm: PosViewModel) {
    var range by remember { mutableStateOf(Range.Today) }
    var report by remember { mutableStateOf<SalesReportData?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        val (since, until) = when (range) {
            Range.Today -> startOfDayMillis() to startOfDayAddingDays(1)
            Range.Week  -> startOfDayAddingDays(-6) to startOfDayAddingDays(1)
            Range.Month -> startOfDayAddingDays(-29) to startOfDayAddingDays(1)
        }
        scope.launch {
            report = vm.salesInRange(since, until)
        }
    }

    LaunchedEffect(range, state.recentTxs.size) { reload() }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("דוח מכירות", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.values().forEach { r ->
                Button(
                    onClick = { range = r },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (r == range) Accent else CardBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(r.title, color = if (r == range) Color.Black else Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = ::reload) { Text("רענן", color = Muted) }
        }

        Spacer(Modifier.height(12.dp))

        if (report == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            return@Column
        }

        val r = report!!
        // Summary cards
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(
                title = "סה״כ מכירות",
                value = formatMoney(r.totalSales, state.currencySymbol),
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "מס׳ עסקאות",
                value = r.txCount.toString(),
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "ממוצע לעסקה",
                value = formatMoney(
                    if (r.txCount == 0) 0.0 else r.totalSales / r.txCount,
                    state.currencySymbol
                ),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("פילוח לפי אמצעי תשלום") {
            if (r.byMethod.isEmpty()) {
                Text("אין נתונים", color = Muted)
            } else {
                r.byMethod.forEach { (method, sum) ->
                    KeyValueRow(paymentMethodLabel(method),
                                formatMoney(sum, state.currencySymbol))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("פילוח לפי עובד") {
            if (r.byEmployee.isEmpty()) {
                Text("אין נתונים", color = Muted)
            } else {
                r.byEmployee.forEach { (empId, sum) ->
                    val empName = state.employees.firstOrNull { it.id == empId }?.name
                        ?: "עובד #$empId"
                    KeyValueRow(empName, formatMoney(sum, state.currencySymbol))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("פילוח לפי קטגוריה") {
            if (r.byCategory.isEmpty()) {
                Text("אין נתונים", color = Muted)
            } else {
                r.byCategory.entries
                    .sortedByDescending { it.value }
                    .forEach { (cat, sum) ->
                        KeyValueRow(cat, formatMoney(sum, state.currencySymbol))
                    }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("פריטים פופולריים") {
            if (r.topItems.isEmpty()) {
                Text("אין נתונים", color = Muted)
            } else {
                r.topItems.forEach { (name, qty, sum) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, color = Color.White, modifier = Modifier.weight(1f))
                        Text("$qty יח׳", color = Muted, fontSize = 13.sp,
                             modifier = Modifier.padding(horizontal = 12.dp))
                        Text(formatMoney(sum, state.currencySymbol),
                             color = Accent, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Bg)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryCard(
    title: String, value: String, modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.White,
                 fontSize = 16.sp, fontWeight = FontWeight.Bold,
                 modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White)
        Text(value, color = Accent, fontWeight = FontWeight.Bold)
    }
}
