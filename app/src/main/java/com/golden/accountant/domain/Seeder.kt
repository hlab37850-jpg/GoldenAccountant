package com.golden.accountant.domain

import com.golden.accountant.data.*

/** يزرع الشجرة المحاسبية والبيانات الأساسية عند أول تشغيل. */
object Seeder {
    suspend fun seedIfEmpty(db: AppDatabase) {
        if (db.accounts().count() > 0) return
        val n = Nature
        fun g(id: Long, name: String, parent: Long, nature: Int) = Account(id, name, parent, nature, isGroup = true, isSystem = true)
        fun a(id: Long, name: String, parent: Long, nature: Int) = Account(id, name, parent, nature, isGroup = false, isSystem = true)

        db.accounts().insertAll(listOf(
            g(Sys.ASSETS, "الأصول", 0, n.ASSET), g(Sys.LIABILITIES_EQUITY, "الالتزامات وحقوق الملكية", 0, n.LIABILITY),
            g(Sys.EXPENSES, "المصروفات", 0, n.EXPENSE), g(Sys.INCOME, "الإيرادات", 0, n.INCOME),
            g(Sys.FIXED_ASSETS, "أصول ثابتة", Sys.ASSETS, n.ASSET), g(Sys.CURRENT_ASSETS, "أصول متداولة", Sys.ASSETS, n.ASSET),
            g(Sys.EQUITY, "حقوق الملكية", Sys.LIABILITIES_EQUITY, n.EQUITY),
            g(Sys.CURRENT_LIAB, "التزامات متداولة", Sys.LIABILITIES_EQUITY, n.LIABILITY),
            g(Sys.FIXED_LIAB, "التزامات ثابتة", Sys.LIABILITIES_EQUITY, n.LIABILITY),
            g(Sys.COST_OF_ACTIVITY, "تكاليف النشاط", Sys.EXPENSES, n.EXPENSE),
            g(Sys.OPEX, "مصاريف تشغيلية وإدارية", Sys.EXPENSES, n.EXPENSE),
            g(Sys.REVENUE_ACTIVITY, "إيرادات النشاط", Sys.INCOME, n.INCOME), g(Sys.OTHER_REVENUE, "إيرادات أخرى", Sys.INCOME, n.INCOME),
            g(Sys.CASH_BOXES, "الصناديق", Sys.CURRENT_ASSETS, n.ASSET), g(Sys.BANKS, "البنوك", Sys.CURRENT_ASSETS, n.ASSET),
            g(Sys.CUSTOMERS, "العملاء", Sys.CURRENT_ASSETS, n.ASSET), g(Sys.OTHER_ASSETS, "أخرى", Sys.CURRENT_ASSETS, n.ASSET),
            g(Sys.INVENTORY_GROUP, "البضاعة", Sys.CURRENT_ASSETS, n.ASSET),
            g(Sys.CAPITAL_GROUP, "رأس المال", Sys.EQUITY, n.EQUITY), g(Sys.PROFIT_LOSS_GROUP, "الأرباح والخسائر", Sys.EQUITY, n.EQUITY),
            g(Sys.DRAWINGS_GROUP, "المسحوبات", Sys.EQUITY, n.EQUITY), g(Sys.PARTNERS_GROUP, "المساهمون", Sys.EQUITY, n.EQUITY),
            g(Sys.SUPPLIERS, "الموردون", Sys.CURRENT_LIAB, n.LIABILITY),
            // أوراق تشغيلية
            a(Sys.MAIN_CASH, "الصندوق الرئيسي", Sys.CASH_BOXES, n.ASSET),
            a(Sys.VAT, "ضريبة القيمة المضافة", Sys.OTHER_ASSETS, n.ASSET),
            a(Sys.INVENTORY, "مخزون البضاعة", Sys.INVENTORY_GROUP, n.ASSET),
            a(Sys.CAPITAL, "رأس المال", Sys.CAPITAL_GROUP, n.EQUITY),
            a(Sys.PURCHASES, "المشتريات", Sys.COST_OF_ACTIVITY, n.EXPENSE),
            a(Sys.SALES_RETURNS, "مردودات المبيعات", Sys.COST_OF_ACTIVITY, n.EXPENSE),
            a(Sys.DISCOUNT_ALLOWED, "الخصم المسموح به", Sys.COST_OF_ACTIVITY, n.EXPENSE),
            a(Sys.STOCK_ADJUST, "تسوية المخزون", Sys.COST_OF_ACTIVITY, n.EXPENSE),
            a(Sys.DAMAGED_ITEMS, "الأصناف التالفة", Sys.COST_OF_ACTIVITY, n.EXPENSE),
            a(Sys.ACTIVITY_EXPENSES, "مصاريف النشاط", Sys.OPEX, n.EXPENSE),
            a(Sys.ADMIN_EXPENSES, "مصاريف عمومية وإدارية", Sys.OPEX, n.EXPENSE),
            a(Sys.OTHER_EXPENSES, "مصاريف أخرى", Sys.OPEX, n.EXPENSE),
            a(Sys.FX_DIFF, "فروقات العملة", Sys.OPEX, n.EXPENSE),
            a(Sys.SALES, "المبيعات", Sys.REVENUE_ACTIVITY, n.INCOME),
            a(Sys.PURCHASE_RETURNS, "مردودات المشتريات", Sys.REVENUE_ACTIVITY, n.INCOME),
            a(Sys.DISCOUNT_EARNED, "الخصم المكتسب", Sys.REVENUE_ACTIVITY, n.INCOME),
            a(Sys.FEES_INCOME, "رسوم وإيرادات أخرى", Sys.OTHER_REVENUE, n.INCOME),
        ))
        db.core().insertCurrencies(listOf(
            Currency(0, "محلي", "YR", "فلس", isLocal = true), Currency(1, "دولار", "USD", "سنت"),
        ))
        db.core().insertUnits(listOf(
            UnitDef(0, "بدون", "."), UnitDef(1, "حبة", "حبة"), UnitDef(2, "كيلو", "ك"),
            UnitDef(3, "كرتون", "كرتون"), UnitDef(4, "كيس", "كيس"),
        ))
        db.core().insertTaxes(listOf(Tax(id = 1, name = "بدون ضريبة", percent = 0.0, isDefault = true)))
        db.core().insertBranches(listOf(Branch(id = 1, name = "الفرع الرئيسي")))
        // مستخدم مدير افتراضي: كلمة المرور تُضبط من شاشة الدخول (الجزء 6)
        db.core().insertUsers(listOf(AppUser(id = 1, userName = "admin", name = "المدير", pwdHash = "", cashAccountId = Sys.MAIN_CASH, isAdmin = true)))
    }

    /** يزرع صفوف الشاشات (اسم الشاشة = مسارها) ويضيف الجديد منها دون المساس بالموجود. */
    suspend fun syncScreens(db: AppDatabase, routes: List<String>) {
        val existing = db.screens().all()
        val have = existing.map { it.name }.toSet()
        var next = (existing.maxOfOrNull { it.id } ?: 0L) + 1
        db.screens().insertAll(routes.filter { it !in have }.map { Screen(next++, it) })
    }
}
