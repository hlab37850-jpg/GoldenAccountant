package com.golden.accountant.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.CollapsibleSection
import com.golden.accountant.ui.common.MenuRow
import com.golden.accountant.ui.common.PlaceholderScreen
import com.golden.accountant.data.Party
import com.golden.accountant.domain.TrType
import com.golden.accountant.ui.accounts.AccountsTreeScreen
import com.golden.accountant.ui.accounts.PartiesScreen
import com.golden.accountant.ui.accounts.StatementScreen
import com.golden.accountant.ui.items.ItemsScreen
import com.golden.accountant.ui.items.StocktakeScreen
import com.golden.accountant.ui.items.UnitsScreen
import com.golden.accountant.ui.report.ProfitLossScreen
import com.golden.accountant.ui.report.SalesReportScreen
import com.golden.accountant.ui.report.StockReportScreen
import com.golden.accountant.ui.report.TrialBalanceScreen
import com.golden.accountant.ui.items.TransferScreen
import com.golden.accountant.ui.settings.BackupScreen
import com.golden.accountant.ui.settings.BranchesScreen
import com.golden.accountant.ui.settings.ClosingScreen
import com.golden.accountant.ui.settings.CurrenciesScreen
import com.golden.accountant.ui.settings.TaxesScreen
import com.golden.accountant.ui.settings.UsersScreen
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.home.HomeScreen
import com.golden.accountant.ui.journal.JournalEntryScreen
import com.golden.accountant.ui.journal.JournalListScreen
import com.golden.accountant.ui.voucher.VoucherScreen
import com.golden.accountant.ui.voucher.VouchersListScreen
import com.golden.accountant.ui.invoice.BillListScreen
import com.golden.accountant.ui.invoice.InvoiceKinds
import com.golden.accountant.ui.invoice.InvoiceScreen
import com.golden.accountant.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val EDIT_ROUTE = "bill/{k}/{id}"
private const val JOURNAL_NEW = "journal_new"

/**
 * التنقل الرئيسي. `real` يربط مساراً بشاشته الحقيقية؛ أي مسار غير موجود فيه
 * يظهر كشاشة مؤقتة. الأجزاء 4–6 تضيف شاشاتها هنا.
 */
@Composable
fun AppNav(db: AppDatabase, real: Map<String, @Composable (onBack: () -> Unit) -> Unit> = emptyMap()) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    val ctx = androidx.compose.ui.platform.LocalContext.current
    // المسارات المتفرعة (bill/.., voucher/..) تُحكم بصلاحية أصلها؛ هنا نمنع فتح أي شاشة بلا صلاحية عرض
    val go: (String) -> Unit = { target ->
        scope.launch { drawer.close() }
        val base = when {
            target.startsWith("bill/") -> target.split("/").getOrNull(1) ?: target
            target.startsWith("voucher/") || target.startsWith("vouchers/") -> if (target.split("/").getOrNull(1) == "5") Routes.RECEIPT else Routes.PAYMENT
            target.startsWith("statement/") -> Routes.STATEMENT
            target == "journal_new" -> Routes.JOURNAL
            else -> target
        }
        if (target == Routes.HOME || target == Routes.SETTINGS || Session.can(base)) { if (target != Routes.HOME) nav.navigate(target) { launchSingleTop = true } }
        else android.widget.Toast.makeText(ctx, "ليست لديك صلاحية فتح هذه الشاشة", android.widget.Toast.LENGTH_SHORT).show()
    }
    val back: () -> Unit = { nav.popBackStack() }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = route == Routes.HOME || drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text("المحاسب الذهبي", style = MaterialTheme.typography.titleLarge, color = Gold.Primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    Menu.visibleSections().forEach { CollapsibleSection(it, go) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    MenuRow(MenuItemSettings, go)
                }
            }
        },
    ) {
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen(db, go) { scope.launch { drawer.open() } } }
            composable(Routes.SETTINGS) { SettingsScreen(db, go, back) }

            // تعديل فاتورة موجودة: bill/{kindRoute}/{id}
            composable(EDIT_ROUTE, arguments = listOf(navArgument("k") { type = NavType.StringType }, navArgument("id") { type = NavType.LongType })) { e ->
                val kind = InvoiceKinds.byRoute(e.arguments?.getString("k"))
                val id = e.arguments?.getLong("id") ?: 0L
                if (kind != null) InvoiceScreen(db, kind, id, back) else PlaceholderScreen("فاتورة", 3, back)
            }

            // كشف حساب لحساب محدد، وسندات (نموذج/قائمة/تعديل)، وقيد جديد
            composable("statement/{a}", arguments = listOf(navArgument("a") { type = NavType.LongType })) { e ->
                StatementScreen(db, e.arguments?.getLong("a") ?: 0L, back)
            }
            composable("voucher/{t}/{id}", arguments = listOf(navArgument("t") { type = NavType.IntType }, navArgument("id") { type = NavType.LongType })) { e ->
                val t = e.arguments?.getInt("t") ?: TrType.RECEIPT
                VoucherScreen(db, t, e.arguments?.getLong("id") ?: 0L, onList = { go("vouchers/$t") }, onBack = back)
            }
            composable("vouchers/{t}", arguments = listOf(navArgument("t") { type = NavType.IntType })) { e ->
                val t = e.arguments?.getInt("t") ?: TrType.RECEIPT
                VouchersListScreen(db, t, onNew = { go(if (t == TrType.RECEIPT) Routes.RECEIPT else Routes.PAYMENT) }, onOpen = { go("voucher/$t/$it") }, onBack = back)
            }
            composable(JOURNAL_NEW) { JournalEntryScreen(db, back) }

            Menu.allItems.forEach { item ->
                composable(item.route) {
                    val invoice = InvoiceKinds.byRoute(item.route)
                    val screen = real[item.route]
                    when {
                        invoice != null -> InvoiceScreen(db, invoice, 0L, back)
                        item.route == Routes.SALES_LIST -> BillListScreen(
                            db, InvoiceKinds.salesFamily, item.title,
                            onNew = { go(it.route) }, onOpen = { k, id -> go("bill/${k.route}/$id") }, onBack = back,
                        )
                        item.route == Routes.PURCHASE_LIST -> BillListScreen(
                            db, InvoiceKinds.purchaseFamily, item.title,
                            onNew = { go(it.route) }, onOpen = { k, id -> go("bill/${k.route}/$id") }, onBack = back,
                        )
                        item.route == Routes.RECEIPT -> VoucherScreen(db, TrType.RECEIPT, 0L, onList = { go("vouchers/${TrType.RECEIPT}") }, onBack = back)
                        item.route == Routes.PAYMENT -> VoucherScreen(db, TrType.PAYMENT, 0L, onList = { go("vouchers/${TrType.PAYMENT}") }, onBack = back)
                        item.route == Routes.JOURNAL -> JournalListScreen(db, onNew = { go(JOURNAL_NEW) }, onBack = back)
                        item.route == Routes.ACCOUNTS -> AccountsTreeScreen(db, onStatement = { go("statement/$it") }, onBack = back)
                        item.route == Routes.CUSTOMERS -> PartiesScreen(db, Party.KIND_CUSTOMER, item.title, onStatement = { go("statement/$it") }, onBack = back)
                        item.route == Routes.SUPPLIERS -> PartiesScreen(db, Party.KIND_SUPPLIER, item.title, onStatement = { go("statement/$it") }, onBack = back)
                        item.route == Routes.STATEMENT -> StatementScreen(db, 0L, back)
                        item.route == Routes.ITEMS -> ItemsScreen(db, back)
                        item.route == Routes.UNITS -> UnitsScreen(db, back)
                        item.route == Routes.STOCKTAKE -> StocktakeScreen(db, back)
                        item.route == Routes.TRIAL -> TrialBalanceScreen(db, back)
                        item.route == Routes.PROFIT -> ProfitLossScreen(db, back)
                        item.route == Routes.SALES_REPORT -> SalesReportScreen(db, back)
                        item.route == Routes.STOCK_REPORT -> StockReportScreen(db, back)
                        item.route == Routes.USERS -> UsersScreen(db, back)
                        item.route == Routes.CURRENCIES -> CurrenciesScreen(db, back)
                        item.route == Routes.TAXES -> TaxesScreen(db, back)
                        item.route == Routes.BRANCHES -> BranchesScreen(db, back)
                        item.route == Routes.BACKUP -> BackupScreen(db, back)
                        item.route == Routes.CLOSING -> ClosingScreen(db, back)
                        item.route == Routes.TRANSFER -> TransferScreen(db, back)
                        screen != null -> screen(back)
                        else -> PlaceholderScreen(item.title, item.part, back)
                    }
                }
            }
        }
    }
}

private val MenuItemSettings = MenuItem(Routes.SETTINGS, "الإعدادات", Icons.Default.Settings, 2)
