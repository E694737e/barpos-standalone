package com.barpos.standalone

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ============ Entities ============

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val category: String = "כללי",
    val active: Boolean = true,
    val unit: String = "יח׳",
    val stockOnHand: Double = 0.0,
    val lowStockThreshold: Double = 0.0,
    val trackStock: Boolean = false,
)

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pin: String,
    val isManager: Boolean = false,
    val active: Boolean = true,
)

@Entity(tableName = "transactions")
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val total: Double,                  // total charged (subtotal + tip)
    val subtotal: Double = 0.0,         // amount before tip
    val tip: Double = 0.0,              // tip amount
    val tax: Double = 0.0,              // tax amount (if tax not included in prices)
    val paymentMethod: String,          // "cash" | "credit" | "split" | "refund_*"
    val cashReceived: Double = 0.0,     // for cash payments
    val changeGiven: Double = 0.0,
    val splitCashAmount: Double = 0.0,  // for split: portion paid in cash (incl. share of tip)
    val splitCreditAmount: Double = 0.0, // for split: portion paid in credit
    val splitCashReceived: Double = 0.0, // for split: amount of cash physically received
    val splitChangeGiven: Double = 0.0,
    val employeeId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "completed",
    val voidedAt: Long? = null,
    val voidReason: String? = null,
    val tabId: Long? = null,
    val refundOfTxId: Long? = null,
    val refundReason: String? = null,
)

@Entity(tableName = "transaction_items")
data class TxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val itemId: Long,
    val itemName: String,
    val priceAtTime: Double,
    val quantity: Int,
)

@Entity(tableName = "tabs")
data class Tab(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val customerName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: String = "open",
    val cancelReason: String? = null,
    val createdByEmployeeId: Long,
)

@Entity(tableName = "tab_items")
data class TabItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tabId: Long,
    val itemId: Long,
    val itemName: String,
    val priceAtTime: Double,
    val quantity: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val voided: Boolean = false,
    val voidReason: String? = null,
    val voidedAt: Long? = null,
)

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "stock_movements")
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val itemName: String,
    val change: Double,                 // positive for restock, negative for usage
    val reason: String,                 // "restock", "sale", "adjust", "void"
    val note: String? = null,
    val employeeId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val status: String = "active",      // active | ended | cancelled
    val createdByEmployeeId: Long,
)

@Entity(tableName = "event_stock_snapshots")
data class EventStockSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val initialQty: Double,
)

// ============ DAOs ============

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE active = 1 ORDER BY category, name")
    fun observeActive(): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY category, name")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Query("UPDATE items SET active = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Query("UPDATE items SET stockOnHand = stockOnHand + :delta WHERE id = :id AND trackStock = 1")
    suspend fun adjustStock(id: Long, delta: Double)

    @Query("UPDATE items SET stockOnHand = :qty WHERE id = :id")
    suspend fun setStock(id: Long, qty: Double)
}

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE active = 1")
    fun observeActive(): Flow<List<Employee>>

    @Query("SELECT * FROM employees")
    fun observeAll(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE pin = :pin AND active = 1 LIMIT 1")
    suspend fun findByPin(pin: String): Employee?

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun get(id: Long): Employee?

    @Query("SELECT COUNT(*) FROM employees")
    suspend fun count(): Int

    @Insert
    suspend fun insert(emp: Employee): Long

    @Update
    suspend fun update(emp: Employee)

    @Query("UPDATE employees SET active = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)
}

@Dao
interface TxDao {
    @Insert
    suspend fun insertTx(tx: Tx): Long

    @Insert
    suspend fun insertItems(items: List<TxItem>)

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<Tx>>

    @Query("SELECT * FROM transactions WHERE status = 'completed' AND createdAt >= :since AND createdAt < :until ORDER BY createdAt DESC")
    suspend fun listInRange(since: Long, until: Long): List<Tx>

    @Query("SELECT * FROM transactions WHERE status = 'voided' ORDER BY voidedAt DESC")
    fun observeVoided(): Flow<List<Tx>>

    @Query("SELECT * FROM transactions WHERE refundOfTxId IS NOT NULL ORDER BY createdAt DESC")
    fun observeRefunds(): Flow<List<Tx>>

    @Query("SELECT * FROM transactions WHERE status = 'completed' AND refundOfTxId IS NULL ORDER BY createdAt DESC LIMIT :limit")
    fun observeCompleted(limit: Int = 200): Flow<List<Tx>>

    @Query("SELECT COALESCE(SUM(total), 0) FROM transactions WHERE status = 'completed' AND createdAt >= :since")
    suspend fun sumSince(since: Long): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'completed' AND createdAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(total), 0) FROM transactions WHERE status = 'completed' AND createdAt >= :since AND createdAt < :until")
    suspend fun sumInRange(since: Long, until: Long): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'completed' AND createdAt >= :since AND createdAt < :until")
    suspend fun countInRange(since: Long, until: Long): Int

    @Query("SELECT * FROM transaction_items WHERE transactionId = :txId")
    suspend fun itemsFor(txId: Long): List<TxItem>

    @Query("SELECT * FROM transaction_items WHERE transactionId IN (:txIds)")
    suspend fun itemsForAll(txIds: List<Long>): List<TxItem>

    @Query("UPDATE transactions SET status = 'voided', voidedAt = :ts, voidReason = :reason WHERE id = :id")
    suspend fun voidTx(id: Long, ts: Long, reason: String)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Tx?
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE status = 'open' ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<Tab>>

    @Query("SELECT * FROM tabs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Tab>>

    @Query("SELECT * FROM tabs WHERE status = 'cancelled' ORDER BY closedAt DESC")
    fun observeCancelled(): Flow<List<Tab>>

    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun get(id: Long): Tab?

    @Insert
    suspend fun insert(tab: Tab): Long

    @Update
    suspend fun update(tab: Tab)

    @Query("UPDATE tabs SET status = 'closed', closedAt = :ts WHERE id = :id")
    suspend fun closeTab(id: Long, ts: Long)

    @Query("UPDATE tabs SET status = 'cancelled', closedAt = :ts, cancelReason = :reason WHERE id = :id")
    suspend fun cancelTab(id: Long, ts: Long, reason: String?)

    @Query("SELECT * FROM tab_items WHERE tabId = :tabId ORDER BY addedAt ASC")
    fun observeItems(tabId: Long): Flow<List<TabItem>>

    @Query("SELECT * FROM tab_items WHERE tabId = :tabId AND voided = 0")
    suspend fun activeItemsFor(tabId: Long): List<TabItem>

    @Insert
    suspend fun insertItem(item: TabItem): Long

    @Query("UPDATE tab_items SET voided = 1, voidReason = :reason, voidedAt = :ts WHERE id = :id")
    suspend fun voidItem(id: Long, ts: Long, reason: String?)

    @Query("SELECT * FROM tab_items WHERE voided = 1 ORDER BY voidedAt DESC")
    fun observeVoidedItems(): Flow<List<TabItem>>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<Setting>>

    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: Setting)

    @Query("SELECT COUNT(*) FROM settings")
    suspend fun count(): Int
}

@Dao
interface StockDao {
    @Insert
    suspend fun insertMovement(movement: StockMovement): Long

    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE itemId = :itemId ORDER BY createdAt DESC")
    fun observeForItem(itemId: Long): Flow<List<StockMovement>>
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE status = 'active' LIMIT 1")
    fun observeActive(): Flow<Event?>

    @Query("SELECT * FROM events ORDER BY startedAt DESC LIMIT :limit")
    fun observeAll(limit: Int = 100): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: Long): Event?

    @Insert
    suspend fun insert(e: Event): Long

    @Update
    suspend fun update(e: Event)

    @Query("UPDATE events SET status = 'ended', endedAt = :ts WHERE id = :id")
    suspend fun endEvent(id: Long, ts: Long)

    @Insert
    suspend fun insertSnapshot(s: EventStockSnapshot): Long

    @Query("SELECT * FROM event_stock_snapshots WHERE eventId = :eventId")
    suspend fun snapshotsFor(eventId: Long): List<EventStockSnapshot>

    @Query("SELECT * FROM event_stock_snapshots WHERE eventId = :eventId")
    fun observeSnapshotsFor(eventId: Long): Flow<List<EventStockSnapshot>>
}

// ============ Database ============

@Database(
    entities = [
        Item::class, Employee::class, Tx::class, TxItem::class,
        Tab::class, TabItem::class, Setting::class,
        StockMovement::class, Event::class, EventStockSnapshot::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun employees(): EmployeeDao
    abstract fun transactions(): TxDao
    abstract fun tabs(): TabDao
    abstract fun settings(): SettingsDao
    abstract fun stockMovements(): StockDao
    abstract fun events(): EventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barpos.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}

// ============ Settings keys ============

object SettingKeys {
    const val BUSINESS_NAME = "business_name"
    const val BUSINESS_ADDRESS = "business_address"
    const val BUSINESS_PHONE = "business_phone"
    const val BUSINESS_TAX_ID = "business_tax_id"   // ע.מ. או ע.ר. או ח.פ.
    const val TAX_RATE = "tax_rate"
    const val CURRENCY_SYMBOL = "currency_symbol"
    const val TAX_INCLUDED = "tax_included"
    const val SHOW_CLOCK = "show_clock"
    const val RECEIPT_FOOTER = "receipt_footer"
    const val DEFAULT_UNIT = "default_unit"
    const val LOW_STOCK_THRESHOLD = "low_stock_threshold"
}
