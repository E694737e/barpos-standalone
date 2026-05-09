package com.barpos.standalone

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
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

private val Bg      = Color(0xFF0B0E15)
private val Panel   = Color(0xFF171A23)
private val Accent  = Color(0xFFFBBF24)
private val Muted   = Color(0xFF94A3B8)
private val Good    = Color(0xFF22C55E)
private val Bad     = Color(0xFFEF4444)
private val CardBg  = Color(0xFF1F2740)
private val BlueBtn = Color(0xFF3B82F6)

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

private enum class Screen { Login, Pos, Admin }

@Composable
private fun AppRoot() {
    val app = LocalContext.current.applicationContext as Application
    val vm: PosViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    )
    val state by vm.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.Login) }

    Scaffold(containerColor = Bg) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Bg)) {
            when (screen) {
                Screen.Login -> LoginScreen { emp ->
                    vm.setActiveEmployee(emp)
                    screen = Screen.Pos
                }
                Screen.Pos -> PosScreen(
                    state = state,
                    onAddItem = vm::addToCart,
                    onRemoveLine = vm::removeLine,
                    onClearCart = vm::clearCart,
                    onCheckout = vm::checkout,
                    onLogout = { vm.logout(); screen = Screen.Login },
                    onAdmin = { screen = Screen.Admin },
                )
                Screen.Admin -> AdminScreen(
                    state = state,
                    onBack = { screen = Screen.Pos },
                    onAddItem = vm::createItem,
                    onDeleteItem = vm::deleteItem,
                    onAddEmployee = vm::createEmployee,
                    onDeleteEmployee = vm::deleteEmployee,
                )
            }
        }
    }
}

data class CartLine(val item: Item, val qty: Int)

data class PosState(
    val items: List<Item> = emptyList(),
    val employees: List<Employee> = emptyList(),
    val cart: List<CartLine> = emptyList(),
    val activeEmployee: Employee? = null,
    val todayTotal: Double = 0.0,
    val todayCount: Int = 0,
)

class PosViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as BarPosApp).db
    private val _state = MutableStateFlow(PosState())
    val state: StateFlow<PosState> = _state

    init {
        viewModelScope.launch {
            db.items().observeAll().collect { list ->
                _state.update { it.copy(items = list) }
            }
        }
        viewModelScope.launch {
            db.employees().observeActive().collect { list ->
                _state.update { it.copy(employees = list) }
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
        _state.update { it.copy(activeEmployee = null, cart = emptyList()) }
    }

    fun addToCart(item: Item) {
        val cart = _state.value.cart.toMutableList()
        val idx = cart.indexOfFirst { it.item.id == item.id }
        if (idx >= 0) cart[idx] = cart[idx].copy(qty = cart[idx].qty + 1)
        else cart.add(CartLine(item, 1))
        _state.update { it.copy(cart = cart) }
    }

    fun removeLine(idx: Int) {
        val cart = _state.value.cart.toMutableList()
        if (idx in cart.indices) cart.removeAt(idx)
        _state.update { it.copy(cart = cart) }
    }

    fun clearCart() { _state.update { it.copy(cart = emptyList()) } }

    fun checkout(method: String) {
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

    fun createItem(name: String, price: Double, category: String) {
        viewModelScope.launch {
            db.items().insert(Item(name = name, price = price, category = category, active = true))
        }
    }

    fun deleteItem(id: Long) { viewModelScope.launch { db.items().softDelete(id) } }

    fun createEmployee(name: String, pin: String, isManager: Boolean) {
        viewModelScope.launch {
            db.employees().insert(Employee(name = name, pin = pin, isManager = isManager, active = true))
        }
    }

    fun deleteEmployee(id: Long) { viewModelScope.launch { db.employees().softDelete(id) } }
}

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
private fun NumPad(onDigit: (String) -> Unit, onBack: () -> Unit, onOk: () -> Unit) {
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
                            text = ch,
                            fontSize = 22.sp,
                            color = if (ch == "→") Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosScreen(
    state: PosState,
    onAddItem: (Item) -> Unit,
    onRemoveLine: (Int) -> Unit,
    onClearCart: () -> Unit,
    onCheckout: (String) -> Unit,
    onLogout: () -> Unit,
    onAdmin: () -> Unit,
) {
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

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("BarPOS", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text("שלום ${state.activeEmployee?.name ?: ""}",
                     color = Muted, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "היום: ${formatMoney(state.todayTotal)} (${state.todayCount} עסקאות)",
                    color = Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.width(12.dp))
                if (state.activeEmployee?.isManager == true) {
                    IconButton(onClick = onAdmin) {
                        Icon(Icons.Filled.Settings, contentDescription = "ניהול", tint = Muted)
                    }
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "התנתק", tint = Muted)
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
                    Card(
                        modifier = Modifier
                            .height(96.dp).fillMaxWidth()
                            .clickable { onAddItem(item) },
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                item.name,
                                color = Color.White, fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                formatMoney(item.price),
                                color = Accent, fontSize = 16.sp,
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
                "חשבון נוכחי",
                color = Color.White, fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.cart.isEmpty()) {
                    Text(
                        "החשבון ריק. בחר פריטים מהרשימה.",
                        color = Muted, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(state.cart) { idx, line ->
                            CartLineRow(line) { onRemoveLine(idx) }
                            HorizontalDivider(color = CardBg)
                        }
                    }
                }
            }

            HorizontalDivider(
                color = CardBg, thickness = 2.dp,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("פריטים", color = Muted)
                Text("$cartCount", color = Color.White)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("סה״כ", color = Color.White, fontSize = 18.sp)
                Text(
                    formatMoney(cartTotal),
                    color = Accent, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { onCheckout("cash") },
                    enabled = state.cart.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Good),
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("מזומן", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onCheckout("credit") },
                    enabled = state.cart.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueBtn),
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("אשראי", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = onClearCart,
                enabled = state.cart.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("נקה חשבון", color = Color.White)
            }
        }
    }
}

@Composable
private fun CartLineRow(line: CartLine, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.item.name, color = Color.White, fontSize = 14.sp)
            Text(
                "${line.qty} × ${formatMoney(line.item.price)}",
                color = Muted, fontSize = 12.sp,
            )
        }
        Text(
            formatMoney(line.item.price * line.qty),
            color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "הסר", tint = Bad)
        }
    }
}

@Composable
private fun AdminScreen(
    state: PosState,
    onBack: () -> Unit,
    onAddItem: (String, Double, String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onAddEmployee: (String, String, Boolean) -> Unit,
    onDeleteEmployee: (Long) -> Unit,
) {
    var showAddItem by remember { mutableStateOf(false) }
    var showAddEmp by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("מוצרים", "עובדים")

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה",
                     tint = Color.White)
            }
            Text("ניהול", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Accent) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }

        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> {
                Button(
                    onClick = { showAddItem = true },
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
                            formatMoney(item.price), color = Accent,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        if (item.active) {
                            IconButton(onClick = { onDeleteItem(item.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "מחק", tint = Bad)
                            }
                        }
                    }
                    HorizontalDivider(color = CardBg)
                }
            }
            1 -> {
                Button(
                    onClick = { showAddEmp = true },
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
                                e.name + if (e.isManager) " (מנהל)" else "",
                                color = Color.White,
                            )
                            Text("PIN: ${e.pin}", color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDeleteEmployee(e.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "מחק", tint = Bad)
                        }
                    }
                    HorizontalDivider(color = CardBg)
                }
            }
        }
    }

    if (showAddItem) {
        AddItemDialog(
            onDismiss = { showAddItem = false },
            onSave = { name, price, cat ->
                onAddItem(name, price, cat)
                showAddItem = false
            },
        )
    }
    if (showAddEmp) {
        AddEmployeeDialog(
            onDismiss = { showAddEmp = false },
            onSave = { name, pin, isMgr ->
                onAddEmployee(name, pin, isMgr)
                showAddEmp = false
            },
        )
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

private fun formatMoney(d: Double): String = "₪%.2f".format(d)

private fun startOfDayMillis(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
