package com.barpos.standalone

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val category: String = "כללי",
    val active: Boolean = true,
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
    val total: Double,
    val paymentMethod: String,
    val employeeId: Long,
    val createdAt: Long = System.currentTimeMillis(),
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
}

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE active = 1")
    fun observeActive(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE pin = :pin AND active = 1 LIMIT 1")
    suspend fun findByPin(pin: String): Employee?

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

    @Query("SELECT COALESCE(SUM(total), 0) FROM transactions WHERE createdAt >= :since")
    suspend fun sumSince(since: Long): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT * FROM transaction_items WHERE transactionId = :txId")
    suspend fun itemsFor(txId: Long): List<TxItem>
}

@Database(
    entities = [Item::class, Employee::class, Tx::class, TxItem::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun employees(): EmployeeDao
    abstract fun transactions(): TxDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barpos.db"
                ).build()
                INSTANCE = db
                db
            }
        }
    }
}
