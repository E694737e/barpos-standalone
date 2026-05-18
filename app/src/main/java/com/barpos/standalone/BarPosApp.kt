package com.barpos.standalone

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BarPosApp : Application() {

    val db by lazy { AppDatabase.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            seedIfEmpty()
        }
    }

    private suspend fun seedIfEmpty() {
        val empCount = db.employees().count()
        if (empCount == 0) {
            db.employees().insert(
                Employee(name = "מנהל", pin = "1234", isManager = true, active = true)
            )
            db.employees().insert(
                Employee(name = "קופאי", pin = "1111", isManager = false, active = true)
            )
        }

        val itemCount = db.items().count()
        if (itemCount == 0) {
            val seed = listOf(
                Item(name = "בירה גולדסטאר", price = 25.0, category = "בירות", unit = "יח׳"),
                Item(name = "בירה הייניקן",   price = 28.0, category = "בירות", unit = "יח׳"),
                Item(name = "יין אדום כוס",   price = 35.0, category = "יין",    unit = "כוס"),
                Item(name = "יין לבן כוס",    price = 32.0, category = "יין",    unit = "כוס"),
                Item(name = "וודקה שוט",     price = 30.0, category = "אלכוהול", unit = "שוט"),
                Item(name = "וויסקי שוט",    price = 40.0, category = "אלכוהול", unit = "שוט"),
                Item(name = "צ'יפס",          price = 18.0, category = "אוכל",   unit = "מנה"),
                Item(name = "המבורגר",        price = 65.0, category = "אוכל",   unit = "מנה"),
                Item(name = "סלט יווני",       price = 42.0, category = "אוכל",   unit = "מנה"),
                Item(name = "קולה",            price = 12.0, category = "שתייה קלה", unit = "יח׳"),
                Item(name = "מים",             price = 8.0,  category = "שתייה קלה", unit = "יח׳"),
                Item(name = "אספרסו",          price = 10.0, category = "חמים",  unit = "כוס"),
            )
            seed.forEach { db.items().insert(it) }
        }

        val settingsCount = db.settings().count()
        if (settingsCount == 0) {
            db.settings().put(Setting(SettingKeys.BUSINESS_NAME, "BarPOS"))
            db.settings().put(Setting(SettingKeys.BUSINESS_ADDRESS, ""))
            db.settings().put(Setting(SettingKeys.BUSINESS_PHONE, ""))
            db.settings().put(Setting(SettingKeys.BUSINESS_TAX_ID, ""))
            db.settings().put(Setting(SettingKeys.TAX_RATE, "17"))
            db.settings().put(Setting(SettingKeys.CURRENCY_SYMBOL, "₪"))
            db.settings().put(Setting(SettingKeys.TAX_INCLUDED, "true"))
            db.settings().put(Setting(SettingKeys.SHOW_CLOCK, "true"))
            db.settings().put(Setting(SettingKeys.RECEIPT_FOOTER, "תודה ולהתראות!"))
            db.settings().put(Setting(SettingKeys.DEFAULT_UNIT, "יח׳"))
            db.settings().put(Setting(SettingKeys.LOW_STOCK_THRESHOLD, "5"))
            db.settings().put(Setting(SettingKeys.TABLE_COUNT, "12"))
        }
    }
}
