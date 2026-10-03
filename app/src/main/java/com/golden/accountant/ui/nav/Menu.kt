package com.golden.accountant.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.golden.accountant.domain.Session

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    // البلاطات الأربع
    const val SALES = "sales"; const val PURCHASE = "purchase"; const val VOUCHER = "voucher_home"; const val ACCOUNTS_LIST = "accounts_list"
    // عمليات مخزنية
    const val SUPPLY = "stock_supply"; const val ISSUE = "stock_issue"; const val TRANSFER = "stock_transfer"; const val ADJUST = "stock_adjust"
    const val WAREHOUSES = "warehouses"; const val STOCKTAKE = "stocktake"
    // قيود وحسابات
    const val JOURNAL = "journal_entry"; const val OPENING_ENTRY = "opening_entry"; const val ADD_ACCOUNT = "add_account"
    const val CASH_MOVEMENT = "cash_movement"; const val ACCOUNTS = "accounts_tree"; const val CLOSING = "closing_year"
    // أصناف
    const val ITEMS = "items"; const val ITEM_PRICES = "item_prices"; const val UNITS = "units"; const val QUOTE = "quote"; const val PURCHASE_ORDER = "purchase_order"
    // عملات
    const val CURRENCIES = "currencies"; const val RATES = "currency_rates"; const val ACCOUNT_LIMIT = "account_limit"
    // تقارير
    const val ITEM_MOVEMENT = "item_movement"; const val TRIAL = "trial_balance"; const val INCOME = "income_statement"; const val BALANCE_SHEET = "balance_sheet"; const val OTHER_REPORTS = "other_reports"
    const val SALES_REPORT = "sales_report"; const val STOCK_REPORT = "stock_report"; const val STATEMENT = "account_statement"
    const val CUSTOMERS = "customers"; const val SUPPLIERS = "suppliers"
    // نسخ احتياطي + إعدادات
    const val BACKUP_SAVE = "backup_save"; const val BACKUP_RESTORE = "backup_restore"
    const val USERS = "users"; const val TAXES = "taxes"
}

/** part: للتتبع فقط. */
data class MenuItem(val route: String, val title: String, val icon: ImageVector, val part: Int = 0)
data class MenuSection(val title: String, val icon: ImageVector, val items: List<MenuItem>)

/**
 * بنية القوائم مطابقة لجدول screens في القاعدة الأصلية:
 * أربع بلاطات رئيسية، خمس مجموعات، وعنصرا النسخ الاحتياطي (حفظ/استرجاع).
 */
object Menu {
    val tiles = listOf(
        MenuItem(Routes.SALES, "المبيعات", Icons.Default.ShoppingCart),
        MenuItem(Routes.PURCHASE, "المشتريات", Icons.Default.Star),
        MenuItem(Routes.VOUCHER, "قبض/صرف", Icons.Default.Email),
        MenuItem(Routes.ACCOUNTS_LIST, "الحسابات", Icons.Default.AccountBox),
    )

    val sections: List<MenuSection> = listOf(
        MenuSection("عمليات مخزنية", Icons.Default.Home, listOf(
            MenuItem(Routes.SUPPLY, "توريد مخزني", Icons.Default.Add),
            MenuItem(Routes.ISSUE, "صرف مخزني", Icons.Default.Refresh),
            MenuItem(Routes.TRANSFER, "تحويل مخزني", Icons.Default.Share),
            MenuItem(Routes.ADJUST, "تسوية مخزنية", Icons.Default.Build),
            MenuItem(Routes.WAREHOUSES, "إضافة مخزن", Icons.Default.Add),
            MenuItem(Routes.STOCKTAKE, "جرد مخزني", Icons.Default.Check),
        )),
        MenuSection("قيود وحسابات", Icons.Default.Edit, listOf(
            MenuItem(Routes.JOURNAL, "قيد يومي", Icons.Default.Edit),
            MenuItem(Routes.OPENING_ENTRY, "قيد إفتتاحي", Icons.Default.Edit),
            MenuItem(Routes.ADD_ACCOUNT, "إضافة حساب", Icons.Default.Add),
            MenuItem(Routes.CASH_MOVEMENT, "حركة الصندوق", Icons.Default.DateRange),
            MenuItem(Routes.ACCOUNTS, "دليل الحسابات", Icons.Default.List),
            MenuItem(Routes.CLOSING, "إقفال سنوي", Icons.Default.Lock),
        )),
        MenuSection("أصناف", Icons.Default.List, listOf(
            MenuItem(Routes.ITEMS, "الأصناف", Icons.Default.List),
            MenuItem(Routes.ITEM_PRICES, "أسعار البيع", Icons.Default.Star),
            MenuItem(Routes.UNITS, "وحدات الصنف", Icons.Default.Build),
            MenuItem(Routes.QUOTE, "فاتورة عرض سعر", Icons.Default.Info),
            MenuItem(Routes.PURCHASE_ORDER, "طلب شراء", Icons.Default.Info),
        )),
        MenuSection("العملات", Icons.Default.Refresh, listOf(
            MenuItem(Routes.CURRENCIES, "إضافة عملة", Icons.Default.Add),
            MenuItem(Routes.RATES, "سعر العملات", Icons.Default.Refresh),
            MenuItem(Routes.ACCOUNT_LIMIT, "سقف الحساب", Icons.Default.Warning),
        )),
        MenuSection("التقارير", Icons.Default.Info, listOf(
            MenuItem(Routes.ITEM_MOVEMENT, "حركة الأصناف", Icons.Default.List),
            MenuItem(Routes.TRIAL, "ميزان المراجعة", Icons.Default.List),
            MenuItem(Routes.INCOME, "قائمة الدخل", Icons.Default.Star),
            MenuItem(Routes.BALANCE_SHEET, "المركز المالي", Icons.Default.AccountBox),
            MenuItem(Routes.OTHER_REPORTS, "تقارير أخرى", Icons.Default.Info),
        )),
    )

    /** عنصرا النسخ الاحتياطي في القائمة الرئيسية (حفظ/استرجاع). */
    val backup = listOf(
        MenuItem(Routes.BACKUP_SAVE, "حفظ نسخة احتياطية", Icons.Default.Share),
        MenuItem(Routes.BACKUP_RESTORE, "استرجاع نسخة احتياطية", Icons.Default.Refresh),
    )

    /** محتويات «تقارير أخرى». */
    val otherReports = listOf(
        MenuItem(Routes.SALES_REPORT, "تقرير المبيعات والمشتريات", Icons.Default.DateRange),
        MenuItem(Routes.STOCK_REPORT, "تقرير المخزون", Icons.Default.Home),
        MenuItem(Routes.STATEMENT, "كشف حساب", Icons.Default.List),
        MenuItem(Routes.CUSTOMERS, "العملاء", Icons.Default.Person),
        MenuItem(Routes.SUPPLIERS, "الموردون", Icons.Default.Person),
    )

    val settings: List<MenuItem> = listOf(
        MenuItem(Routes.USERS, "المستخدمون والصلاحيات", Icons.Default.Lock),
        MenuItem(Routes.TAXES, "الضرائب", Icons.Default.Info),
    )

    val allItems: List<MenuItem> get() = tiles + sections.flatMap { it.items } + backup + otherReports + settings

    /** الأقسام التي يحق للمستخدم الحالي رؤية شيء منها. */
    fun visibleSections(): List<MenuSection> = sections
        .map { sec -> sec.copy(items = sec.items.filter { Session.can(it.route) }) }
        .filter { it.items.isNotEmpty() }
}
