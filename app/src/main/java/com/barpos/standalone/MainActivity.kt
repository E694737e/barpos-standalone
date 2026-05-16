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
import androidx.compose.material3.Tab as M3Tab
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
import kotlinx.coroutines.delay
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
    Refunds("זיכויים"),
    Sales("דוחות", managerOnly = true),
    Stock("מלאי", managerOnly = true),
    Events("אירועים", managerOnly = true),
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

    // Live clock
    var clockText by remember { mutableStateOf(currentTimeText()) }
    if (state.showClock) {
        LaunchedEffect(Unit) {
            while (true) {
                clockText = currentTimeText()
                delay(1000)
            }
        }
    }

    Scaffold(containerColor = Bg) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Bg)) {
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
                if (state.showClock) {
                    Text(clockText, color = Color.White, fontSize = 14.sp,
                         fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    "היום: ${formatMoney(state.todayTotal, state.currencySymbol)} (${state.todayCount})",
                    color = Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { vm.logout() }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "התנתק", tint = Muted)
                }
            }

            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(topTab).coerceAtLeast(0),
                containerColor = Panel,
                contentColor = Accent,
                edgePadding = 0.dp,
            ) {
                tabs.forEach { t ->
                    M3Tab(
                        selected = topTab == t,
                        onClick = { topTab = t },
                        text = { Text(t.title, fontSize = 14.sp) },
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (topTab) {
                    TopTab.Pos   -> PosScreen(state = state, vm = vm)
                    TopTab.Tabs  -> TabsScreen(state = state, vm = vm, openTab = { tabId ->
                        if (state.workingTab?.id != tabId) {
                            vm.openTabForEditing(tabId)
                        }
                        topTab = TopTab.Pos
                    })
                    TopTab.Voids   -> VoidsScreen(state = state, vm = vm)
                    TopTab.Refunds -> RefundsScreen(state = state, vm = vm)
                    TopTab.Sales   -> SalesReportScreen(state = state, vm = vm)
                    TopTab.Stock   -> StockScreen(state = state, vm = vm)
                    TopTab.Events  -> EventsScreen(state = state, vm = vm)
                    TopTab.Admin   -> AdminScreen(state = state, vm = vm)
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
    val cart: List<CartLine> = emptyList(),
    val activeEmployee: Employee? = null,
    val todayTotal: Double = 0.0,
    val todayCount: Int = 0,
    val openTabs: List<Tab> = emptyList(),
    val workingTab: Tab? = null,
    val workingTabItems: List<TabItem> = emptyList(),
    val voidedTxs: List<Tx> = emptyList(),
    val cancelledTabs: List<Tab> = emptyList(),
    val voidedTabItems: List<TabItem> = emptyList(),
    val recentTxs: List<Tx> = emptyList(),
    val completedTxs: List<Tx> = emptyList(),
    val refundTxs: List<Tx> = emptyList(),
    val activeEvent: Event? = null,
    val allEvents: List<Event> = emptyList(),
    val recentStockMovements: List<StockMovement> = emptyList(),
    val pendingPayment: PendingPayment? = null,
    val lastReceipt: ReceiptSummary? = null,
    val businessName: String = "BarPOS",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val businessTaxId: String = "",
    val receiptFooter: String = "תודה ולהתראות!",
    val taxRatePct: Double = 17.0,
    val currencySymbol: String = "₪",
    val taxIncluded: Boolean = true,
    val showClock: Boolean = true,
    val defaultUnit: String = "יח׳",
    val lowStockThreshold: Double = 5.0,
)

class PosViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as BarPosApp).db
    private val appContext = app
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
            db.transactions().observeRefunds().collect { list ->
                _state.update { it.copy(refundTxs = list) }
            }
        }
        viewModelScope.launch {
            db.transactions().observeCompleted(300).collect { list ->
                _state.update { it.copy(completedTxs = list) }
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
            db.transactions().observeRecent(300).collect { list ->
                _state.update { it.copy(recentTxs = list) }
            }
        }
        viewModelScope.launch {
            db.events().observeActive().collect { e ->
                _state.update { it.copy(activeEvent = e) }
            }
        }
        viewModelScope.launch {
            db.events().observeAll(100).collect { list ->
                _state.update { it.copy(allEvents = list) }
            }
        }
        viewModelScope.launch {
            db.stockMovements().observeRecent(200).collect { list ->
                _state.update { it.copy(recentStockMovements = list) }
            }
        }
        viewModelScope.launch {
            db.settings().observeAll().collect { list ->
                val map = list.associate { it.key to it.value }
                _state.update {
                    it.copy(
                        businessName = map[SettingKeys.BUSINESS_NAME] ?: "BarPOS",
                        businessAddress = map[SettingKeys.BUSINESS_ADDRESS] ?: "",
                        businessPhone = map[SettingKeys.BUSINESS_PHONE] ?: "",
                        businessTaxId = map[SettingKeys.BUSINESS_TAX_ID] ?: "",
                        receiptFooter = map[SettingKeys.RECEIPT_FOOTER] ?: "תודה ולהתראות!",
                        taxRatePct = (map[SettingKeys.TAX_RATE] ?: "17").toDoubleOrNull() ?: 17.0,
                        currencySymbol = map[SettingKeys.CURRENCY_SYMBOL] ?: "₪",
                        taxIncluded = (map[SettingKeys.TAX_INCLUDED] ?: "true") == "true",
                        showClock = (map[SettingKeys.SHOW_CLOCK] ?: "true") == "true",
                        defaultUnit = map[SettingKeys.DEFAULT_UNIT] ?: "יח׳",
                        lowStockThreshold = (map[SettingKeys.LOW_STOCK_THRESHOLD] ?: "5").toDoubleOrNull() ?: 5.0,
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
                pendingPayment = null,
                lastReceipt = null,
            )
        }
    }

    // ----- Cart -----

    fun addToCart(item: Item) {
        val tab = _state.value.workingTab
        if (tab != null) {
            viewModelScope.launch {
                db.tabs().insertItem(
                    TabItem(
                        tabId = tab.id, itemId = item.id, itemName = item.name,
                        priceAtTime = item.price, quantity = 1,
                    )
                )
                if (item.trackStock) {
                    db.items().adjustStock(item.id, -1.0)
                    db.stockMovements().insertMovement(
                        StockMovement(
                            itemId = item.id, itemName = item.name,
                            change = -1.0, reason = "sale",
                            employeeId = _state.value.activeEmployee?.id,
                        )
                    )
                }
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

    // ----- Payment flow (cart) -----

    fun startCheckoutCart(method: String) {
        val cart = _state.value.cart
        if (cart.isEmpty()) return
        val subtotal = cart.sumOf { it.item.price * it.qty }
        val (base, tax) = computeBaseAndTax(subtotal)
        _state.update {
            it.copy(pendingPayment = PendingPayment(
                method = method, sourceKind = "cart",
                baseSubtotal = base, tax = tax,
            ))
        }
    }

    fun startCheckoutTab(tabId: Long, method: String) {
        viewModelScope.launch {
            val items = db.tabs().activeItemsFor(tabId)
            if (items.isEmpty()) return@launch
            val subtotal = items.sumOf { it.priceAtTime * it.quantity }
            val (base, tax) = computeBaseAndTax(subtotal)
            _state.update {
                it.copy(pendingPayment = PendingPayment(
                    method = method, sourceKind = "tab", sourceTabId = tabId,
                    baseSubtotal = base, tax = tax,
                ))
            }
        }
    }

    private fun computeBaseAndTax(subtotal: Double): Pair<Double, Double> {
        // If tax included in prices, subtotal IS final. Tax is shown for informational purposes only.
        // If tax NOT included, tax must be added on top.
        val s = _state.value
        return if (s.taxIncluded) {
            subtotal to 0.0
        } else {
            subtotal to (subtotal * s.taxRatePct / 100.0)
        }
    }

    fun updatePendingTip(tip: Double) {
        val p = _state.value.pendingPayment ?: return
        _state.update { it.copy(pendingPayment = p.copy(tip = tip)) }
    }

    fun updatePendingCash(amount: Double) {
        val p = _state.value.pendingPayment ?: return
        _state.update { it.copy(pendingPayment = p.copy(cashReceived = amount)) }
    }

    fun cancelPending() {
        _state.update { it.copy(pendingPayment = null) }
    }

    fun confirmPending() {
        val p = _state.value.pendingPayment ?: return
        val emp = _state.value.activeEmployee ?: return
        viewModelScope.launch {
            val cashReceived = if (p.method == "cash") p.cashReceived else 0.0
            val changeGiven = if (p.method == "cash") p.change else 0.0
            val tx = Tx(
                total = p.total,
                subtotal = p.baseSubtotal,
                tip = p.tip,
                tax = p.tax,
                paymentMethod = p.method,
                cashReceived = cashReceived,
                changeGiven = changeGiven,
                employeeId = emp.id,
                tabId = if (p.sourceKind == "tab") p.sourceTabId else null,
            )
            val txId = db.transactions().insertTx(tx)
            val lines: List<TxItem> = if (p.sourceKind == "cart") {
                val cart = _state.value.cart
                cart.map { line ->
                    TxItem(
                        transactionId = txId, itemId = line.item.id,
                        itemName = line.item.name, priceAtTime = line.item.price,
                        quantity = line.qty,
                    )
                }
            } else {
                val items = db.tabs().activeItemsFor(p.sourceTabId ?: -1)
                items.map { it ->
                    TxItem(
                        transactionId = txId, itemId = it.itemId,
                        itemName = it.itemName, priceAtTime = it.priceAtTime,
                        quantity = it.quantity,
                    )
                }
            }
            db.transactions().insertItems(lines)
            if (p.sourceKind == "tab" && p.sourceTabId != null) {
                db.tabs().closeTab(p.sourceTabId, System.currentTimeMillis())
            }
            // For cart sales (not tab), deduct stock now
            if (p.sourceKind == "cart") {
                for (line in _state.value.cart) {
                    if (line.item.trackStock) {
                        db.items().adjustStock(line.item.id, -line.qty.toDouble())
                        db.stockMovements().insertMovement(
                            StockMovement(
                                itemId = line.item.id, itemName = line.item.name,
                                change = -line.qty.toDouble(), reason = "sale",
                                employeeId = emp.id,
                            )
                        )
                    }
                }
            }
            val receipt = ReceiptSummary(
                txId = txId,
                subtotal = p.baseSubtotal, tax = p.tax, tip = p.tip, total = p.total,
                paymentMethod = p.method,
                cashReceived = cashReceived, changeGiven = changeGiven,
                createdAt = tx.createdAt,
                lines = lines,
                employeeName = emp.name,
            )
            _state.update {
                it.copy(
                    pendingPayment = null,
                    cart = if (p.sourceKind == "cart") emptyList() else it.cart,
                    workingTab = if (p.sourceKind == "tab" && it.workingTab?.id == p.sourceTabId) null else it.workingTab,
                    workingTabItems = if (p.sourceKind == "tab" && it.workingTab?.id == p.sourceTabId) emptyList() else it.workingTabItems,
                    lastReceipt = receipt,
                )
            }
            refreshTotals()
        }
    }

    fun dismissReceipt() {
        _state.update { it.copy(lastReceipt = null) }
    }

    fun shareLastReceipt() {
        val r = _state.value.lastReceipt ?: return
        val s = _state.value
        val uri = ReceiptUtil.generateReceiptPdf(
            appContext, r, s.currencySymbol,
            s.businessName, s.businessAddress, s.businessPhone, s.businessTaxId,
            s.receiptFooter,
        ) ?: return
        ReceiptUtil.shareReceipt(appContext, uri)
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
            // Return stock back
            val items = _state.value.workingTabItems
            val voidedItem = items.firstOrNull { it.id == itemId }
            if (voidedItem != null) {
                val product = db.items().get(voidedItem.itemId)
                if (product != null && product.trackStock) {
                    db.items().adjustStock(product.id, voidedItem.quantity.toDouble())
                    db.stockMovements().insertMovement(
                        StockMovement(
                            itemId = product.id, itemName = product.name,
                            change = voidedItem.quantity.toDouble(),
                            reason = "void",
                            employeeId = _state.value.activeEmployee?.id,
                            note = reason,
                        )
                    )
                }
            }
            refreshWorkingTab()
        }
    }

    fun cancelTab(tabId: Long, reason: String?) {
        viewModelScope.launch {
            // Return stock for all active items
            val activeItems = db.tabs().activeItemsFor(tabId)
            for (ti in activeItems) {
                val prod = db.items().get(ti.itemId)
                if (prod != null && prod.trackStock) {
                    db.items().adjustStock(prod.id, ti.quantity.toDouble())
                    db.stockMovements().insertMovement(
                        StockMovement(
                            itemId = prod.id, itemName = prod.name,
                            change = ti.quantity.toDouble(),
                            reason = "void",
                            employeeId = _state.value.activeEmployee?.id,
                            note = reason,
                        )
                    )
                }
            }
            db.tabs().cancelTab(tabId, System.currentTimeMillis(), reason)
            if (_state.value.workingTab?.id == tabId) {
                _state.update { it.copy(workingTab = null, workingTabItems = emptyList()) }
            }
        }
    }

    // ----- Voids / Refunds -----

    fun voidTransaction(txId: Long, reason: String) {
        viewModelScope.launch {
            db.transactions().voidTx(txId, System.currentTimeMillis(), reason)
            refreshTotals()
        }
    }

    fun refundTransaction(originalTxId: Long, amount: Double, reason: String, method: String) {
        val emp = _state.value.activeEmployee ?: return
        viewModelScope.launch {
            val original = db.transactions().get(originalTxId) ?: return@launch
            val refundMethod = "refund_${method.ifBlank { original.paymentMethod }}"
            val refundTx = Tx(
                total = -amount,  // negative
                subtotal = -amount,
                tip = 0.0, tax = 0.0,
                paymentMethod = refundMethod,
                employeeId = emp.id,
                tabId = original.tabId,
                refundOfTxId = originalTxId,
                refundReason = reason,
            )
            val rid = db.transactions().insertTx(refundTx)
            // Copy original items (informational)
            val origItems = db.transactions().itemsFor(originalTxId)
            db.transactions().insertItems(origItems.map { ti ->
                TxItem(
                    transactionId = rid, itemId = ti.itemId,
                    itemName = ti.itemName,
                    priceAtTime = -ti.priceAtTime,
                    quantity = ti.quantity,
                )
            })
            refreshTotals()
        }
    }

    // ----- Admin -----

    fun createItem(
        name: String, price: Double, category: String,
        unit: String = "יח׳", trackStock: Boolean = false,
        initialStock: Double = 0.0, lowStockThreshold: Double = 0.0,
    ) {
        viewModelScope.launch {
            val id = db.items().insert(Item(
                name = name, price = price, category = category, active = true,
                unit = unit, trackStock = trackStock,
                stockOnHand = initialStock, lowStockThreshold = lowStockThreshold,
            ))
            if (trackStock && initialStock > 0) {
                db.stockMovements().insertMovement(
                    StockMovement(
                        itemId = id, itemName = name,
                        change = initialStock, reason = "restock",
                        note = "Initial stock",
                        employeeId = _state.value.activeEmployee?.id,
                    )
                )
            }
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

    fun saveAllSettings(map: Map<String, String>) {
        viewModelScope.launch {
            map.forEach { (k, v) -> db.settings().put(Setting(k, v)) }
        }
    }

    // ----- Stock -----

    fun restockItem(itemId: Long, qty: Double, note: String?) {
        viewModelScope.launch {
            val item = db.items().get(itemId) ?: return@launch
            db.items().adjustStock(itemId, qty)
            db.stockMovements().insertMovement(
                StockMovement(
                    itemId = itemId, itemName = item.name,
                    change = qty, reason = "restock", note = note,
                    employeeId = _state.value.activeEmployee?.id,
                )
            )
        }
    }

    fun adjustStock(itemId: Long, newQty: Double, note: String?) {
        viewModelScope.launch {
            val item = db.items().get(itemId) ?: return@launch
            val delta = newQty - item.stockOnHand
            db.items().setStock(itemId, newQty)
            db.stockMovements().insertMovement(
                StockMovement(
                    itemId = itemId, itemName = item.name,
                    change = delta, reason = "adjust", note = note,
                    employeeId = _state.value.activeEmployee?.id,
                )
            )
        }
    }

    // ----- Events -----

    fun startEvent(name: String, description: String?) {
        val emp = _state.value.activeEmployee ?: return
        viewModelScope.launch {
            val active = db.events().get(_state.value.activeEvent?.id ?: -1)
            if (active != null && active.status == "active") return@launch
            val eventId = db.events().insert(
                Event(name = name, description = description, createdByEmployeeId = emp.id)
            )
            // Snapshot current stock
            val items = db.items().observeActive().let {
                _state.value.items.filter { item -> item.active && item.trackStock }
            }
            for (item in items) {
                db.events().insertSnapshot(EventStockSnapshot(
                    eventId = eventId, itemId = item.id, itemName = item.name,
                    unit = item.unit, initialQty = item.stockOnHand,
                ))
            }
        }
    }

    fun endActiveEvent() {
        val ev = _state.value.activeEvent ?: return
        viewModelScope.launch {
            db.events().endEvent(ev.id, System.currentTimeMillis())
        }
    }

    // ----- Reports -----

    suspend fun salesInRange(sinceMs: Long, untilMs: Long): SalesReportData {
        val txs = db.transactions().listInRange(sinceMs, untilMs)
        val items = if (txs.isNotEmpty()) {
            db.transactions().itemsForAll(txs.map { it.id })
        } else emptyList()
        val nonRefund = txs.filter { it.refundOfTxId == null }
        val refunds = txs.filter { it.refundOfTxId != null }
        val byEmp = nonRefund.groupBy { it.employeeId }
            .mapValues { e -> e.value.sumOf { it.total } }
        val byMethod = nonRefund.groupBy { it.paymentMethod }
            .mapValues { e -> e.value.sumOf { it.total } }
        val byCategory = items.filter { it.priceAtTime > 0 }
            .groupBy { txItemCategory(it) }
            .mapValues { e -> e.value.sumOf { it.priceAtTime * it.quantity } }
        val topItems = items.filter { it.priceAtTime > 0 }
            .groupBy { it.itemName }
            .map { (name, list) ->
                Triple(name, list.sumOf { it.quantity },
                       list.sumOf { it.priceAtTime * it.quantity })
            }
            .sortedByDescending { it.third }
            .take(20)
        val tipsTotal = nonRefund.sumOf { it.tip }
        val tipsByEmp = nonRefund.groupBy { it.employeeId }
            .mapValues { e -> e.value.sumOf { it.tip } }
        return SalesReportData(
            txCount = nonRefund.size,
            totalSales = nonRefund.sumOf { it.total },
            totalTips = tipsTotal,
            byEmployee = byEmp,
            byMethod = byMethod,
            byCategory = byCategory,
            topItems = topItems,
            tipsByEmployee = tipsByEmp,
            refundsTotal = -refunds.sumOf { it.total },
            refundsCount = refunds.size,
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
    val totalTips: Double,
    val byEmployee: Map<Long, Double>,
    val byMethod: Map<String, Double>,
    val byCategory: Map<String, Double>,
    val topItems: List<Triple<String, Int, Double>>,
    val tipsByEmployee: Map<Long, Double>,
    val refundsTotal: Double,
    val refundsCount: Int,
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

internal fun currentTimeText(): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale("he", "IL"))
    return sdf.format(java.util.Date())
}

internal fun paymentMethodLabel(method: String): String = when (method) {
    "cash" -> "מזומן"
    "credit" -> "אשראי"
    "refund_cash" -> "החזר מזומן"
    "refund_credit" -> "החזר אשראי"
    else -> if (method.startsWith("refund_")) "החזר ${method.substringAfter("refund_")}" else method
}
