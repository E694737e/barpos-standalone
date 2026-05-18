package com.barpos.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TablesScreen(
    state: PosState,
    openTable: (Int) -> Unit,
) {
    val tableCount = state.tableCount.coerceAtLeast(1)
    // Map of tableNumber -> open Tab
    val tabsByTable: Map<Int, Tab> = remember(state.openTabs) {
        state.openTabs
            .filter { it.tableNumber != null }
            .associateBy { it.tableNumber!! }
    }
    val occupiedCount = tabsByTable.size
    val freeCount = tableCount - occupiedCount

    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "שולחנות",
                color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBadge("פנויים", "$freeCount", Good)
                StatBadge("תפוסים", "$occupiedCount", Warning)
                StatBadge("סה״כ", "$tableCount", Muted)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items((1..tableCount).toList()) { tableNum ->
                val tab = tabsByTable[tableNum]
                TableCard(
                    tableNumber = tableNum,
                    tab = tab,
                    currency = state.currencySymbol,
                    onClick = { openTable(tableNum) },
                )
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = color,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(width = 28.dp, height = 24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun TableCard(
    tableNumber: Int,
    tab: Tab?,
    currency: String,
    onClick: () -> Unit,
) {
    val occupied = tab != null
    val borderColor = if (occupied) Warning else Color.Transparent
    val bg = if (occupied) CardBg else Panel

    val ctx = LocalContext.current
    val db = remember { (ctx.applicationContext as BarPosApp).db }

    // Live total for occupied tables
    var totalText by remember(tab?.id) { mutableStateOf<String?>(null) }
    var itemCount by remember(tab?.id) { mutableStateOf(0) }
    LaunchedEffect(tab?.id) {
        if (tab != null) {
            val items = db.tabs().activeItemsFor(tab.id)
            val sum = items.sumOf { it.priceAtTime * it.quantity }
            totalText = formatMoney(sum, currency)
            itemCount = items.sumOf { it.quantity }
        } else {
            totalText = null
            itemCount = 0
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable { onClick() }
            .then(if (occupied) Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp))
                  else Modifier),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "שולחן $tableNumber",
                color = if (occupied) Accent else Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )

            if (occupied) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        totalText ?: "...",
                        color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "$itemCount פריטים",
                        color = Muted, fontSize = 12.sp,
                    )
                }
                Surface(
                    color = Warning, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "תפוס - לחץ להמשיך",
                        color = Color.White, fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            } else {
                Text(
                    "ריק",
                    color = Muted, fontSize = 14.sp,
                )
                Surface(
                    color = Good, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "פנוי - לחץ לפתיחה",
                        color = Color.White, fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
