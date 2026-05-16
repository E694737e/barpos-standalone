package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Tab as M3Tab
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun SalesReportScreen(state: PosState, vm: PosViewModel) {
    val tabs = listOf("דוח מכירות", "דוח סוף יום", "דוח טיפים")
    var selectedTab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        TabRow(selectedTabIndex = selectedTab, containerColor = Panel, contentColor = Accent) {
            tabs.forEachIndexed { i, t ->
                M3Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> SalesByPeriod(state, vm)
                1 -> EndOfDayReport(state, vm)
                2 -> TipsReport(state, vm)
            }
        }
    }
}

private enum class Range(val title: String) {
    Today("היום"),
    Yesterday("אתמול"),
    Week("השבוע (7 ימים)"),
    Month("החודש (30 ימים)"),
}

@Composable
private fun SalesByPeriod(state: PosState, vm: PosViewModel) {
    var range by remember { mutableStateOf(Range.Today) }
    var report by remember { mutableStateOf<SalesReportData?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        val (since, until) = when (range) {
            Range.Today -> startOfDayMillis() to startOfDayAddingDays(1)
            Range.Yesterday -> startOfDayAddingDays(-1) to startOfDayMillis()
            Range.Week  -> startOfDayAddingDays(-6) to startOfDayAddingDays(1)
            Range.Month -> startOfDayAddingDays(-29) to startOfDayAddingDays(1)
        }
        scope.launch { report = vm.salesInRange(since, until) }
    }
    LaunchedEffect(range, state.recentTxs.size) { reload() }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("דוח מכירות", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.values().forEach { r ->
                Button(
                    onClick = { range = r },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (r == range) Accent else CardBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(r.title, color = if (r == range) Color.Black else Color.White,
                         fontSize = 13.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = ::reload) { Text("רענן", color = Muted) }
        }
        Spacer(Modifier.height(12.dp))
        val r = report
        if (r == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("סה״כ מכירות", formatMoney(r.totalSales, state.currencySymbol),
                        Modifier.weight(1f))
            SummaryCard("עסקאות", r.txCount.toString(), Modifier.weight(1f))
            SummaryCard("ממוצע", formatMoney(
                if (r.txCount == 0) 0.0 else r.totalSales / r.txCount,
                state.currencySymbol), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("טיפים", formatMoney(r.totalTips, state.currencySymbol),
                        Modifier.weight(1f))
            SummaryCard("זיכויים", formatMoney(r.refundsTotal, state.currencySymbol),
                        Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        SectionCard("פילוח לפי אמצעי תשלום") {
            if (r.byMethod.isEmpty()) Text("אין נתונים", color = Muted)
            else r.byMethod.forEach { (m, sum) ->
                KvRow(paymentMethodLabel(m), formatMoney(sum, state.currencySymbol))
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("פילוח לפי עובד") {
            if (r.byEmployee.isEmpty()) Text("אין נתונים", color = Muted)
            else r.byEmployee.forEach { (id, sum) ->
                val name = state.employees.firstOrNull { it.id == id }?.name ?: "עובד #$id"
                KvRow(name, formatMoney(sum, state.currencySymbol))
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("פילוח לפי קטגוריה") {
            if (r.byCategory.isEmpty()) Text("אין נתונים", color = Muted)
            else r.byCategory.entries.sortedByDescending { it.value }.forEach { (c, sum) ->
                KvRow(c, formatMoney(sum, state.currencySymbol))
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("פריטים פופולריים") {
            if (r.topItems.isEmpty()) Text("אין נתונים", color = Muted)
            else r.topItems.forEach { (name, qty, sum) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, modifier = Modifier.weight(1f))
                    Text("$qty יח׳", color = Muted, fontSize = 13.sp,
                         modifier = Modifier.padding(horizontal = 12.dp))
                    Text(formatMoney(sum, state.currencySymbol),
                         color = Accent, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = Bg)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EndOfDayReport(state: PosState, vm: PosViewModel) {
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<SalesReportData?>(null) }

    LaunchedEffect(state.recentTxs.size) {
        scope.launch {
            report = vm.salesInRange(startOfDayMillis(), startOfDayAddingDays(1))
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("דוח סוף יום (Z-Report)",
             color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "סיכום מכירות מתחילת היום עד עכשיו",
            color = Muted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        Text(formatDate(System.currentTimeMillis()),
             color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        val r = report
        if (r == null) {
            CircularProgressIndicator(color = Accent)
            return@Column
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                BigKvRow("סה״כ עסקאות", r.txCount.toString())
                BigKvRow("סה״כ מכירות", formatMoney(r.totalSales, state.currencySymbol))
                BigKvRow("טיפים", formatMoney(r.totalTips, state.currencySymbol))
                BigKvRow("זיכויים שניתנו",
                         "${r.refundsCount} (${formatMoney(r.refundsTotal, state.currencySymbol)})")
                HorizontalDivider(color = CardBg, modifier = Modifier.padding(vertical = 8.dp))
                Text("תקבולים נטו",
                     color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                BigKvRow("הכנסה נטו", formatMoney(r.totalSales - r.refundsTotal,
                                                    state.currencySymbol),
                         valueColor = Good)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("פילוח לפי אמצעי תשלום") {
            if (r.byMethod.isEmpty()) Text("אין נתונים", color = Muted)
            else r.byMethod.forEach { (m, sum) ->
                KvRow(paymentMethodLabel(m), formatMoney(sum, state.currencySymbol))
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("פילוח לפי עובד") {
            if (r.byEmployee.isEmpty()) Text("אין נתונים", color = Muted)
            else r.byEmployee.forEach { (id, sum) ->
                val name = state.employees.firstOrNull { it.id == id }?.name ?: "עובד #$id"
                KvRow(name, formatMoney(sum, state.currencySymbol))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TipsReport(state: PosState, vm: PosViewModel) {
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf(Range.Today) }
    var report by remember { mutableStateOf<SalesReportData?>(null) }

    LaunchedEffect(range, state.recentTxs.size) {
        val (since, until) = when (range) {
            Range.Today -> startOfDayMillis() to startOfDayAddingDays(1)
            Range.Yesterday -> startOfDayAddingDays(-1) to startOfDayMillis()
            Range.Week  -> startOfDayAddingDays(-6) to startOfDayAddingDays(1)
            Range.Month -> startOfDayAddingDays(-29) to startOfDayAddingDays(1)
        }
        scope.launch { report = vm.salesInRange(since, until) }
    }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("דוח טיפים", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.values().forEach { r ->
                Button(
                    onClick = { range = r },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (r == range) Accent else CardBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(r.title, color = if (r == range) Color.Black else Color.White,
                         fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        val r = report
        if (r == null) {
            CircularProgressIndicator(color = Accent)
            return@Column
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("סה״כ טיפים", color = Muted, fontSize = 14.sp)
                Text(formatMoney(r.totalTips, state.currencySymbol),
                     color = Good, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("טיפים לפי עובד") {
            if (r.tipsByEmployee.isEmpty()) Text("אין טיפים בתקופה", color = Muted)
            else r.tipsByEmployee.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .forEach { (id, sum) ->
                    val name = state.employees.firstOrNull { it.id == id }?.name ?: "עובד #$id"
                    KvRow(name, formatMoney(sum, state.currencySymbol))
                }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
private fun KvRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White)
        Text(value, color = Accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BigKvRow(label: String, value: String, valueColor: Color = Accent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
