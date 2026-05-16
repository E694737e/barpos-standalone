package com.barpos.standalone

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============ Theme colors ============

internal val Bg      = Color(0xFF0B0E15)
internal val Panel   = Color(0xFF171A23)
internal val Accent  = Color(0xFFFBBF24)
internal val Muted   = Color(0xFF94A3B8)
internal val Good    = Color(0xFF22C55E)
internal val Bad     = Color(0xFFEF4444)
internal val CardBg  = Color(0xFF1F2740)
internal val BlueBtn = Color(0xFF3B82F6)
internal val Warning = Color(0xFFF59E0B)

// ============ Activity ============

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Accent,
                        onPrimary = Color.Black,
                        background = Bg,
                        surface = Panel,
                        onBackground = Color.White,
                        onSurface = Color.White,
                    )
                ) {
                    AppRoot()
                }
            }
        }
    }
}

// ============ Top-level navigation tabs ============

internal enum class TopTab(val title: String, val managerOnly: Boolean = false) {
    Pos("קופה"),
    Tabs("חשבונות"),
    Voids("ביטולים"),
    Sales("דוח מכירות", managerOnly = true),
    Admin("ניהול", managerOnly = true),
}

// ============ Root composable ============

@Composable
private fun AppRoot() {
    val app = LocalContext.current.applicationContext as Application
    val vm: PosViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    )
    val state by vm.state.collectAsState()

    if (state.activeEmployee == null) {
        LoginScreen { emp -> vm.setActiveEmployee(emp) }
        return
    }

    var topTab by remember { mutableStateOf(TopTab.Pos) }
    val isManager = state.activeEmployee?.isManager == true
    val tabs = TopTab.values().filter { !it.managerOnly || isManager }

    Scaffold(containerColor = Bg) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Bg)) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.businessName.ifBlank { "BarPOS" },
                    color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                Text("שלום ${state.activeEmployee?.name ?: ""}", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "היום: ${formatMoney(state.todayTotal, state.currencySymbol)} (${state.todayCount})",
                    color = Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { vm.logout() }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "התנתק", tint = Muted)
                }
            }

            // Tab bar
            TabRow(
                selectedTabIndex = tabs.indexOf(topTab).coerceAtLeast(0),
                containerColor = Panel,
                contentColor = Accent,
            ) {
                tabs.forEach { t ->
                    Tab(
                        selected = topTab == t,
                        onClick = { topTab = t },
                        text = { Text(t.title, fontSize = 15.sp) },
                    )
                }
            }

            // Content
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (topTab) {
                    TopTab.Pos   -> PosScreen(state = state, vm = vm)
                    TopTab.Tabs  -> TabsScreen(state = state, vm = vm, openTab = { tabId ->
                        if (state.workingTab?.id != tabId) {
                            vm.openTabForEditing(tabId)
                        }
                        topTab = TopTab.Pos
                    })
                    TopTab.Voids -> VoidsScreen(state = state, vm = vm)
                    TopTab.Sales -> SalesReportScreen(state = state, vm = vm)
                    TopTab.Admin -> AdminScreen(state = state, vm = vm)
                }
            }
        }
    }
}

// ============ State + ViewModel ============

data class CartLine(val item: Item, val qty: Int)

data class PosState(
    val items: List<Item> = emptyList(),
    val employees: List<Employee> = emptyList(),
    val cart: List<CartLine> = emptyList(),                 // for direct cash register
    val activeEmployee: Employee? = null,
    val todayTotal: Double = 0.0,
    val todayCount: Int = 0,
    val openTabs: List<Tab> = emptyList(),
    val workingTab: Tab? = null,                            // currently editing this tab
    val workingTabItems: List<TabItem> = emptyList(),
    val voidedTxs: List<Tx> = emptyList(),
    val cancelledTabs: List<Tab> = emptyList(),
    val voidedTabItems: List<TabItem> = emptyList(),
    val recentTxs: List<Tx> = emptyList(),
    val businessName: String = "BarPOS",
    val taxRatePct: Double = 17.0,
    val currencySymbol: String = "₪",
    val taxIncluded: Boolean = true,
)

class PosViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as BarPosApp).db
    private val _state = MutableStateFlow(PosState())
    val state: StateFlow<PosState> = _state
    private var workingTabJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            db.items().observeAll().collect { list ->
                _state.update { it.copy(items = list) }
            }
        }
        viewModelScope.launch {
            db.employees().observeAll().collect { list ->
                _state.update { it.copy(employees = list) }
            }
        }
        viewModelScope.launch {
            db.tabs().observeOpen().collect { list ->
                _state.update { it.copy(openTabs = list) }
            }
        }
        viewModelScope.launch {
            db.transactions().observeVoided().collect { list ->
                _state.update { it.copy(voidedTxs = list) }
            }
        }
        viewModelScope.launch {
            db.tabs().observeCancelled().collect { list ->
                _state.update { it.copy(cancelledTabs = list) }
            }
        }
        viewModelScope.launch {
            db.tabs().observeVoidedItems().collect { list ->
                _state.update { it.copy(voidedTabItems = list) }
            }
        }
        viewModelScope.launch {
            db.transactions().observeRecent(200).collect { list ->
                _state.update { it.copy(recentTxs = list) }
            }
        }
        viewModelScope.launch {
            db.settings().observeAll().collect { list ->
                val map = list.associate { it.key to it.value }
                _state.update {
                    it.copy(
                        businessName = map[SettingKeys.BUSINESS_NAME] ?: "BarPOS",
                        taxRatePct = (map[SettingKeys.TAX_RATE] ?: "17").toDoubleOrNull() ?: 17.0,
                        currencySymbol = map[SettingKeys.CURRENCY_SYMBOL] ?: "₪",
                        taxIncluded = (map[SettingKeys.TAX_INCLUDED] ?: "true") == "true",
                    )
                }
            }
        }
        refreshTotals()
    }

    private fun refreshTotals() {
        viewModelScope.launch {
            val midnight = startOfDayMillis()
            val sum = db.transactions().sumSince(midnight)
            val count = db.transactions().countSince(midnight)
            _state.update { it.copy(todayTotal = sum, todayCount = count) }
        }
    }

    fun setActiveEmployee(e: Employee) {
        _state.update { it.copy(activeEmployee = e) }
    }

    fun logout() {
        workingTabJob?.cancel()
        workingTabJob = null
        _state.update {
            it.copy(
                activeEmployee = null,
                cart = emptyList(),
                workingTab = null,
                workingTabItems = emptyList(),
            )
        }
    }

    // ----- Cash register cart -----

    fun addToCart(item: Item) {
        // If working on a tab, add to tab instead
        val tab = _state.value.workingTab
        if (tab != null) {
            viewModelScope.launch {
                db.tabs().insertItem(
                    TabItem(
                        tabId = tab.id, itemId = item.id, itemName = item.name,
                        priceAtTime = item.price, quantity = 1,
                    )
                )
                refreshWorkingTab()
            }
            return
        }
        val cart = _state.value.cart.toMutableList()
        val idx = cart.indexOfFirst { it.item.id == item.id }
        if (idx >= 0) cart[idx] = cart[idx].copy(qty = cart[idx].qty + 1)
        else cart.add(CartLine(item, 1))
        _state.update { it.copy(cart = cart) }
    }

    fun removeCartLine(idx: Int) {
        val cart = _state.value.cart.toMutableList()
        if (idx in cart.indices) cart.removeAt(idx)
        _state.update { it.copy(cart = cart) }
    }

    fun clearCart() { _state.update { it.copy(cart = emptyList()) } }

    fun checkoutCart(method: String) {
        val cart = _state.value.cart
        val emp = _state.value.activeEmployee ?: return
        if (cart.isEmpty()) return
        val total = cart.sumOf { it.item.price * it.qty }
        viewModelScope.launch {
            val txId = db.transactions().insertTx(
                Tx(total = total, paymentMethod = method, employeeId = emp.id)
            )
            db.transactions().insertItems(cart.map { line ->
                TxItem(
                    transactionId = txId, itemId = line.item.id,
                    itemName = line.item.name, priceAtTime = line.item.price,
                    quantity = line.qty,
                )
            })
            _state.update { it.copy(cart = emptyList()) }
            refreshTotals()
        }
    }

    // ----- Tabs -----

    fun openTabForEditing(tabId: Long) {
        viewModelScope.launch {
            val tab = db.tabs().get(tabId) ?: return@launch
            workingTabJob?.cancel()
            _state.update { it.copy(workingTab = tab, cart = emptyList()) }
            refreshWorkingTab()
            workingTabJob = viewModelScope.launch {
                db.tabs().observeItems(tabId).collect { items ->
                    if (_state.value.workingTab?.id == tabId) {
                        _state.update { it.copy(workingTabItems = items) }
                    }
                }
            }
        }
    }

    fun closeWorkingTabView() {
        workingTabJob?.cancel()
        workingTabJob = null
        _state.update { it.copy(workingTab = null, workingTabItems = emptyList()) }
    }

    private suspend fun refreshWorkingTab() {
        val tab = _state.value.workingTab ?: return
        val items = db.tabs().activeItemsFor(tab.id)
        _state.update { it.copy(workingTabItems = items) }
    }

    fun createTab(name: String, customerName: String?) {
        val emp = _state.value.activeEmployee ?: return
        viewModelScope.launch {
            val id = db.tabs().insert(
                Tab(
                    name = name.ifBlank { "אורח" },
                    customerName = customerName,
                    createdByEmployeeId = emp.id,
                )
            )
            openTabForEditing(id)
        }
    }

    fun voidTabItem(itemId: Long, reason: String?) {
        viewModelScope.launch {
            db.tabs().voidItem(itemId, System.currentTimeMillis(), reason)
            refreshWorkingTab()
        }
    }

    fun cancelTab(tabId: Long, reason: String?) {
        viewModelScope.launch {
            db.tabs().cancelTab(tabId, System.currentTimeMillis(), reason)
            if (_state.value.workingTab?.id == tabId) {
                _state.update { it.copy(workingTab = null, workingTabItems = emptyList()) }
            }
        }
    }

    fun closeTab(tabId: Long, paymentMethod: String) {
        val emp = _state.value.activeEmployee ?: return
        viewModelScope.launch {
            val items = db.tabs().activeItemsFor(tabId)
            if (items.isEmpty()) return@launch
            val total = items.sumOf { it.priceAtTime * it.quantity }
            val txId = db.transactions().insertTx(
                Tx(total = total, paymentMethod = paymentMethod,
                   employeeId = emp.id, tabId = tabId)
            )
            db.transactions().insertItems(items.map { it ->
                TxItem(
                    transactionId = txId, itemId = it.itemId,
                    itemName = it.itemName, priceAtTime = it.priceAtTime,
                    quantity = it.quantity,
                )
            })
            db.tabs().closeTab(tabId, System.currentTimeMillis())
            if (_state.value.workingTab?.id == tabId) {
                _state.update { it.copy(workingTab = null, workingTabItems = emptyList()) }
            }
            refreshTotals()
        }
    }

    // ----- Voids -----

    fun voidTransaction(txId: Long, reason: String) {
        viewModelScope.launch {
            db.transactions().voidTx(txId, System.currentTimeMillis(), reason)
            refreshTotals()
        }
    }

    // ----- Admin -----

    fun createItem(name: String, price: Double, category: String) {
        viewModelScope.launch {
            db.items().insert(Item(name = name, price = price, category = category, active = true))
        }
    }

    fun updateItem(item: Item) {
        viewModelScope.launch { db.items().update(item) }
    }

    fun deleteItem(id: Long) { viewModelScope.launch { db.items().softDelete(id) } }

    fun createEmployee(name: String, pin: String, isManager: Boolean) {
        viewModelScope.launch {
            db.employees().insert(Employee(name = name, pin = pin,
                                           isManager = isManager, active = true))
        }
    }

    fun updateEmployee(emp: Employee) {
        viewModelScope.launch { db.employees().update(emp) }
    }

    fun deleteEmployee(id: Long) { viewModelScope.launch { db.employees().softDelete(id) } }

    fun saveBusinessSettings(name: String, taxPct: Double, currency: String, taxIncluded: Boolean) {
        viewModelScope.launch {
            db.settings().put(Setting(SettingKeys.BUSINESS_NAME, name))
            db.settings().put(Setting(SettingKeys.TAX_RATE, taxPct.toString()))
            db.settings().put(Setting(SettingKeys.CURRENCY_SYMBOL, currency))
            db.settings().put(Setting(SettingKeys.TAX_INCLUDED, taxIncluded.toString()))
        }
    }

    // ----- Sales report -----

    suspend fun salesInRange(sinceMs: Long, untilMs: Long): SalesReportData {
        val txs = db.transactions().listInRange(sinceMs, untilMs)
        val items = if (txs.isNotEmpty()) {
            db.transactions().itemsForAll(txs.map { it.id })
        } else emptyList()
        val byEmp = txs.groupBy { it.employeeId }
            .mapValues { e -> e.value.sumOf { it.total } }
        val byMethod = txs.groupBy { it.paymentMethod }
            .mapValues { e -> e.value.sumOf { it.total } }
        val byCategory = items.groupBy { txItemCategory(it) }
            .mapValues { e -> e.value.sumOf { it.priceAtTime * it.quantity } }
        val topItems = items.groupBy { it.itemName }
            .map { (name, list) ->
                Triple(name, list.sumOf { it.quantity },
                       list.sumOf { it.priceAtTime * it.quantity })
            }
            .sortedByDescending { it.third }
            .take(20)
        return SalesReportData(
            txCount = txs.size,
            totalSales = txs.sumOf { it.total },
            byEmployee = byEmp,
            byMethod = byMethod,
            byCategory = byCategory,
            topItems = topItems,
        )
    }

    private fun txItemCategory(it: TxItem): String {
        val cat = _state.value.items.firstOrNull { i -> i.id == it.itemId }?.category
        return cat ?: "ללא קטגוריה"
    }
}

data class SalesReportData(
    val txCount: Int,
    val totalSales: Double,
    val byEmployee: Map<Long, Double>,
    val byMethod: Map<String, Double>,
    val byCategory: Map<String, Double>,
    val topItems: List<Triple<String, Int, Double>>,
)

// ============ Login ============

@Composable
private fun LoginScreen(onLoggedIn: (Employee) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { (ctx.applicationContext as BarPosApp).db }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (pin.isBlank()) return
        scope.launch {
            val e = db.employees().findByPin(pin)
            if (e == null) {
                error = "קוד שגוי"
                pin = ""
            } else {
                error = null
                onLoggedIn(e)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(380.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("BarPOS", color = Accent, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("קופה רושמת — גרסה עצמאית", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))

                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (pin.isEmpty()) "הזן קוד" else "•".repeat(pin.length),
                        color = if (pin.isEmpty()) Muted else Accent,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))
                NumPad(
                    onDigit = { d -> if (pin.length < 8) pin += d },
                    onBack = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onOk = ::submit,
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error!!, color = Bad, fontSize = 14.sp)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "דמו: 1234 (מנהל) או 1111 (קופאי)",
                    color = Muted, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun NumPad(onDigit: (String) -> Unit, onBack: () -> Unit, onOk: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "→"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch ->
                    val color = when (ch) {
                        "⌫" -> Bad
                        "→" -> Accent
                        else -> CardBg
                    }
                    val onClick: () -> Unit = when (ch) {
                        "⌫" -> onBack
                        "→" -> onOk
                        else -> { { onDigit(ch) } }
                    }
                    Button(
                        onClick = onClick,
                        modifier = Modifier.size(width = 86.dp, height = 64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = ch, fontSize = 22.sp,
                            color = if (ch == "→") Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ============ Helpers ============

internal fun formatMoney(d: Double, currency: String = "₪"): String =
    "$currency%.2f".format(d)

internal fun startOfDayMillis(at: Long = System.currentTimeMillis()): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = at
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

internal fun startOfDayAddingDays(days: Int): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    cal.add(java.util.Calendar.DAY_OF_YEAR, days)
    return cal.timeInMillis
}

internal fun formatDateTime(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("he", "IL"))
    return sdf.format(java.util.Date(ms))
}

internal fun formatDate(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("he", "IL"))
    return sdf.format(java.util.Date(ms))
}

internal fun paymentMethodLabel(method: String): String = when (method) {
    "cash" -> "מזומן"
    "credit" -> "אשראי"
    else -> method
}
