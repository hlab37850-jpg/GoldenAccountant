package com.golden.accountant.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import com.golden.accountant.data.*

/** يزرع شجرة الحسابات والبيانات الأساسية بنفس محتوى القاعدة الأصلية. */
object Seeder {
    suspend fun seedIfEmpty(db: AppDatabase) {
        if (db.accounts().count() > 0) return
        val n = Nature
        fun g(id: Long, name: String, parent: Long, nature: Int) = Account(id, name, parent, nature, isGroup = true, isSystem = true)
        fun a(id: Long, name: String, parent: Long, nature: Int, type: Int) = Account(id, name, parent, nature, isGroup = false, isSystem = true, type = type)

        db.accounts().insertAll(listOf(
            g(1, "اصول", 0, n.ASSET), g(2, "التزامات وحقوق الملكية", 0, n.LIABILITY), g(3, "مصروفات", 0, n.EXPENSE), g(4, "ايرادات", 0, n.INCOME),
            g(11, "اصول ثابتة", 1, n.ASSET), g(12, "اصول متداولة", 1, n.ASSET),
            g(21, "حقوق الملكية", 2, n.EQUITY), g(22, "التزمات متداولة", 2, n.LIABILITY), g(23, "التزامات ثابتة", 2, n.LIABILITY),
            g(31, "تكاليف النشاط", 3, n.EXPENSE), g(32, "مصاريف تشغيلية وإدارية", 3, n.EXPENSE),
            g(41, "ايرادات النشاط", 4, n.INCOME), g(42, "إيرادات أخرى", 4, n.INCOME),
            g(121, "الصناديق", 12, n.ASSET), g(122, "البنوك", 12, n.ASSET), g(123, "العملاء", 12, n.ASSET), g(124, "اخرى", 12, n.ASSET), g(125, "البضاعة", 12, n.ASSET),
            g(211, "راس المال", 21, n.EQUITY), g(212, "الأرباح والخسائر", 21, n.EQUITY), g(213, "المسحوبات", 21, n.EQUITY), g(214, "المساهمين", 21, n.EQUITY),
            g(221, "الموردون", 22, n.LIABILITY),
            g(311, "المشتريات", 31, n.EXPENSE), g(312, "مردودات مبيعات", 31, n.EXPENSE), g(313, "الخصم المسموح به", 31, n.EXPENSE), g(314, "تسوية المخزون", 31, n.EXPENSE), g(315, "الأصناف التالفة", 31, n.EXPENSE),
            g(321, "مصاريف النشاط", 32, n.EXPENSE), g(322, "مصاريف عمومية وادارية", 32, n.EXPENSE), g(323, "مصاريف أخرى", 32, n.EXPENSE),
            g(411, "المبيعات", 41, n.INCOME), g(412, "مردودات مشتريات", 41, n.INCOME), g(413, "الخصم المكتسب", 41, n.INCOME),
            // الحسابات الفرعية النظامية
            a(Sys.SALES_CREDIT, "مبيعات آجل", 411, n.INCOME, AccType.TRADE), a(Sys.SALES_CASH, "مبيعات نقدي", 411, n.INCOME, AccType.TRADE),
            a(Sys.PURCHASE_CREDIT, "مشتريات آجل", 311, n.EXPENSE, AccType.TRADE), a(Sys.PURCHASE_CASH, "مشتريات نقدي", 311, n.EXPENSE, AccType.TRADE),
            a(Sys.SALES_RET_CREDIT, "مردودات مبيعات آجل", 312, n.EXPENSE, AccType.TRADE), a(Sys.SALES_RET_CASH, "مردودات مبيعات نقدي", 312, n.EXPENSE, AccType.TRADE),
            a(Sys.PURCHASE_RET_CREDIT, "مردودات مشتريات آجل", 412, n.INCOME, AccType.TRADE), a(Sys.PURCHASE_RET_CASH, "مردودات مشتريات نقدي", 412, n.INCOME, AccType.TRADE),
            a(Sys.CASH, "الصندوق", 121, n.ASSET, AccType.CASH),
            a(Sys.DISCOUNT_ALLOWED, "الخصم المسموح به", 313, n.EXPENSE, AccType.EXPENSE), a(Sys.DISCOUNT_EARNED, "الخصم المكتسب", 413, n.INCOME, AccType.REVENUE),
            a(Sys.CAPITAL, "رأس المال", 211, n.EQUITY, AccType.OTHER), a(Sys.OPENING_STOCK, "بضاعة أول المدة", 125, n.ASSET, AccType.OTHER),
            a(Sys.STOCK_SHORTAGE, "عجز و زيادة البضاعة", 314, n.EXPENSE, AccType.OTHER), a(Sys.STOCK_DAMAGED, "البضاعة التالفة", 314, n.EXPENSE, AccType.OTHER),
            a(Sys.PROFIT_LOSS, "ح/الارباح والخسائر", 212, n.EQUITY, AccType.OTHER), a(Sys.VAT, "الضريبة", 22, n.LIABILITY, AccType.OTHER),
            a(Sys.OTHER_FEES, "رسوم أخرى", 323, n.EXPENSE, AccType.OTHER), a(Sys.TRANSPORT, "اجور نقل", 322, n.EXPENSE, AccType.OTHER),
            a(Sys.FX_DIFF, "فوارق بيع وشراء العملات", 42, n.INCOME, AccType.OTHER), a(Sys.STOCK_SETTLE, "تسوية المخزون-صرف وتوريد", 314, n.EXPENSE, AccType.OTHER),
        ))
        db.core().insertCurrencies(listOf(Currency(0, "محلي", "YR", "فلس", isLocal = true), Currency(1, "دولار", "USD", "سنت")))
        db.core().insertUnits(listOf(UnitDef(0, "بدون", "."), UnitDef(1, "حبة", "حبة"), UnitDef(2, "كيلو", "ك"), UnitDef(3, "كرتون", "كرتون"), UnitDef(4, "كيس", "كيس")))
        db.core().insertTaxes(listOf(Tax(id = 1, name = "بدون", percent = 0.0, isDefault = true), Tax(id = 2, name = "ض.قيمة مضافة", percent = 5.0, included = false)))
        db.core().insertBranches(listOf(Branch(id = 1, name = "المخزن الرئيسي")))
        db.core().insertUsers(listOf(AppUser(id = 1, userName = "admin", name = "مدير النظام", pwdHash = "", cashAccountId = Sys.CASH, isAdmin = true)))
        db.conf().set(SysConf(Settings.VAT_ENABLE, "0")); db.conf().set(SysConf(Settings.SHOW_END_DATE, "0"))
    }

    /** يزرع صفوف الشاشات (اسم الشاشة = مسارها) ويضيف الجديد منها دون المساس بالموجود. */
    suspend fun syncScreens(db: AppDatabase, routes: List<String>) {
        val existing = db.screens().all()
        val have = existing.map { it.name }.toSet()
        var next = (existing.maxOfOrNull { it.id } ?: 0L) + 1
        db.screens().insertAll(routes.filter { it !in have }.map { Screen(next++, it) })
    }
}

/** إعدادات النظام (sys_conf): مخزّنة في القاعدة ومقروءة كحالة Compose. */
object Settings {
    const val VAT_ENABLE = "VAT enable"; const val SHOW_END_DATE = "show_end_date"
    var vatEnabled by androidx.compose.runtime.mutableStateOf(false)
    var showEndDate by androidx.compose.runtime.mutableStateOf(false)

    suspend fun load(db: AppDatabase) {
        vatEnabled = db.conf().get(VAT_ENABLE) == "1"; showEndDate = db.conf().get(SHOW_END_DATE) == "1"
    }
    suspend fun setVat(db: AppDatabase, on: Boolean) { db.conf().set(SysConf(VAT_ENABLE, if (on) "1" else "0")); vatEnabled = on }
    suspend fun setShowEndDate(db: AppDatabase, on: Boolean) { db.conf().set(SysConf(SHOW_END_DATE, if (on) "1" else "0")); showEndDate = on }
}
