package com.golden.accountant.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.golden.accountant.domain.Session

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SALES = "sales_invoice"; const val SALES_BACK = "sales_return"; const val QUOTE = "quote"; const val SALES_LIST = "sales_list"
    const val PURCHASE = "purchase_invoice"; const val PURCHASE_BACK = "purchase_return"; const val PURCHASE_ORDER = "purchase_order"; const val PURCHASE_LIST = "purchase_list"
    const val RECEIPT = "receipt_voucher"; const val PAYMENT = "payment_voucher"; const val JOURNAL = "journal_entry"
    const val ACCOUNTS = "accounts_tree"; const val CUSTOMERS = "customers"; const val SUPPLIERS = "suppliers"; const val STATEMENT = "account_statement"
    const val ITEMS = "items"; const val UNITS = "units"; const val STOCKTAKE = "stocktake"; const val TRANSFER = "stock_transfer"
    const val TRIAL = "trial_balance"; const val PROFIT = "profit_loss"; const val SALES_REPORT = "sales_report"; const val STOCK_REPORT = "stock_report"
    const val USERS = "users"; const val CURRENCIES = "currencies"; const val TAXES = "taxes"; const val BRANCHES = "branches"
    const val BACKUP = "backup"; const val CLOSING = "closing_year"
}

/** part = رقم الجزء من خطة التسليم الذي يبني هذه الشاشة فعلياً. */
data class MenuItem(val route: String, val title: String, val icon: ImageVector, val part: Int)
data class MenuSection(val title: String, val icon: ImageVector, val items: List<MenuItem>)

object Menu {
    val sections: List<MenuSection> = listOf(
        MenuSection("المبيعات", Icons.Default.ShoppingCart, listOf(
            MenuItem(Routes.SALES, "فاتورة مبيعات", Icons.Default.Add, 3),
            MenuItem(Routes.SALES_BACK, "مرتجع مبيعات", Icons.Default.Refresh, 3),
            MenuItem(Routes.QUOTE, "عرض سعر", Icons.Default.Info, 3),
            MenuItem(Routes.SALES_LIST, "قائمة فواتير المبيعات", Icons.Default.List, 3),
        )),
        MenuSection("المشتريات", Icons.Default.Star, listOf(
            MenuItem(Routes.PURCHASE, "فاتورة مشتريات", Icons.Default.Add, 3),
            MenuItem(Routes.PURCHASE_BACK, "مرتجع مشتريات", Icons.Default.Refresh, 3),
            MenuItem(Routes.PURCHASE_ORDER, "طلب شراء", Icons.Default.Info, 3),
            MenuItem(Routes.PURCHASE_LIST, "قائمة فواتير المشتريات", Icons.Default.List, 3),
        )),
        MenuSection("السندات والقيود", Icons.Default.Email, listOf(
            MenuItem(Routes.RECEIPT, "سند قبض", Icons.Default.Add, 4),
            MenuItem(Routes.PAYMENT, "سند صرف", Icons.Default.Add, 4),
            MenuItem(Routes.JOURNAL, "قيد يومية", Icons.Default.Edit, 4),
        )),
        MenuSection("الحسابات", Icons.Default.AccountBox, listOf(
            MenuItem(Routes.ACCOUNTS, "شجرة الحسابات", Icons.Default.List, 4),
            MenuItem(Routes.CUSTOMERS, "العملاء", Icons.Default.Person, 4),
            MenuItem(Routes.SUPPLIERS, "الموردون", Icons.Default.Person, 4),
            MenuItem(Routes.STATEMENT, "كشف حساب", Icons.Default.DateRange, 4),
        )),
        MenuSection("المخزون", Icons.Default.Home, listOf(
            MenuItem(Routes.ITEMS, "الأصناف", Icons.Default.List, 5),
            MenuItem(Routes.UNITS, "الوحدات", Icons.Default.Build, 5),
            MenuItem(Routes.STOCKTAKE, "الجرد والتسوية", Icons.Default.Check, 5),
            MenuItem(Routes.TRANSFER, "تحويل مخزني", Icons.Default.Share, 6),
        )),
        MenuSection("التقارير", Icons.Default.Info, listOf(
            MenuItem(Routes.TRIAL, "ميزان المراجعة", Icons.Default.List, 5),
            MenuItem(Routes.PROFIT, "الأرباح والخسائر", Icons.Default.Star, 5),
            MenuItem(Routes.SALES_REPORT, "تقرير المبيعات", Icons.Default.DateRange, 5),
            MenuItem(Routes.STOCK_REPORT, "تقرير المخزون", Icons.Default.Home, 5),
        )),
    )

    val settings: List<MenuItem> = listOf(
        MenuItem(Routes.USERS, "المستخدمون والصلاحيات", Icons.Default.Lock, 6),
        MenuItem(Routes.CURRENCIES, "العملات وأسعار الصرف", Icons.Default.Refresh, 6),
        MenuItem(Routes.TAXES, "الضرائب", Icons.Default.Info, 6),
        MenuItem(Routes.BRANCHES, "الفروع", Icons.Default.Home, 6),
        MenuItem(Routes.BACKUP, "النسخ الاحتياطي والاستعادة", Icons.Default.Share, 6),
        MenuItem(Routes.CLOSING, "إقفال السنة المالية", Icons.Default.Lock, 6),
    )

    val allItems: List<MenuItem> get() = sections.flatMap { it.items } + settings

    /** الأقسام التي يحق للمستخدم الحالي رؤية شيء منها. */
    fun visibleSections(): List<MenuSection> = sections
        .map { sec -> sec.copy(items = sec.items.filter { Session.can(it.route) }) }
        .filter { it.items.isNotEmpty() }
}
