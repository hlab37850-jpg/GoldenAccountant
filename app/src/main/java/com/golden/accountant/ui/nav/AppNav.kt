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
import com.golden.accountant.data.Party
import com.golden.accountant.domain.Session
import com.golden.accountant.domain.TrType
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.accounts.AccountsListScreen
import com.golden.accountant.ui.accounts.AccountsTreeScreen
import com.golden.accountant.ui.accounts.PartiesScreen
import com.golden.accountant.ui.accounts.StatementScreen
import com.golden.accountant.ui.common.CollapsibleSection
import com.golden.accountant.ui.common.MenuRow
import com.golden.accountant.ui.common.PlaceholderScreen
import com.golden.accountant.ui.home.HomeScreen
import com.golden.accountant.ui.invoice.BillListScreen
import com.golden.accountant.ui.invoice.InvoiceKind
import com.golden.accountant.ui.invoice.InvoiceKinds
import com.golden.accountant.ui.invoice.InvoiceScreen
import com.golden.accountant.ui.items.*
import com.golden.accountant.ui.journal.JournalEntryScreen
import com.golden.accountant.ui.journal.JournalListScreen
import com.golden.accountant.ui.report.*
import com.golden.accountant.ui.settings.*
import com.golden.accountant.ui.voucher.VoucherScreen
import com.golden.accountant.ui.voucher.VouchersListScreen
import kotlinx.coroutines.launch

private const val EDIT_ROUTE = "bill/{k}/{id}"
private const val NEW_ROUTE = "new/{k}"
private const val JOURNAL_NEW = "journal_new"
private const val OPENING_NEW = "opening_new"

/** قوائم المستندات: مسار القائمة → أنواع المستندات التي تعرضها. */
private val billLists: Map<String, List<InvoiceKind>> = mapOf(
    Routes.SALES to listOf(InvoiceKinds.SALES, InvoiceKinds.SALES_BACK),
    Routes.PURCHASE to listOf(InvoiceKinds.PURCHASE, InvoiceKinds.PURCHASE_BACK),
    Routes.QUOTE to listOf(InvoiceKinds.QUOTE),
    Routes.PURCHASE_ORDER to listOf(InvoiceKinds.PURCHASE_ORDER),
    Routes.SUPPLY to listOf(InvoiceKinds.SUPPLY),
    Routes.ISSUE to listOf(InvoiceKinds.ISSUE),
)

@Composable
fun AppNav(db: AppDatabase, real: Map<String, @Composable (onBack: () -> Unit) -> Unit> = emptyMap()) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // المسارات المتفرعة تُحكم بصلاحية أصلها؛ ولا تُفتح شاشة بلا صلاحية عرض
    val go: (String) -> Unit = { target ->
        scope.launch { drawer.close() }
        val parts = target.split("/")
        val base = when (parts[0]) {
            "bill", "new" -> InvoiceKinds.byRoute(parts.getOrNull(1))?.permRoute ?: target
            "voucher", "vouchers" -> Routes.VOUCHER
            "statement" -> Routes.STATEMENT
            JOURNAL_NEW -> Routes.JOURNAL
            OPENING_NEW -> Routes.OPENING_ENTRY
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
                    Menu.tiles.filter { Session.can(it.route) }.forEach { MenuRow(it, go) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Menu.visibleSections().forEach { CollapsibleSection(it, go) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Menu.backup.filter { Session.can(it.route) }.forEach { MenuRow(it, go) }
                    MenuRow(MenuItemSettings, go)
                }
            }
        },
    ) {
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen(db, go) { scope.launch { drawer.open() } } }
            composable(Routes.SETTINGS) { SettingsScreen(db, go, back) }

            // فاتورة جديدة / فتح فاتورة موجودة
            composable(NEW_ROUTE, arguments = listOf(navArgument("k") { type = NavType.StringType })) { e ->
                val kind = InvoiceKinds.byRoute(e.arguments?.getString("k"))
                if (kind != null) InvoiceScreen(db, kind, 0L, back) else PlaceholderScreen("فاتورة", 3, back)
            }
            composable(EDIT_ROUTE, arguments = listOf(navArgument("k") { type = NavType.StringType }, navArgument("id") { type = NavType.LongType })) { e ->
                val kind = InvoiceKinds.byRoute(e.arguments?.getString("k"))
                val id = e.arguments?.getLong("id") ?: 0L
                if (kind != null) InvoiceScreen(db, kind, id, back) else PlaceholderScreen("فاتورة", 3, back)
            }
            // كشف حساب لحساب محدد، وسندات (نموذج/قائمة/تعديل)، وقيود جديدة
            composable("statement/{a}", arguments = listOf(navArgument("a") { type = NavType.LongType })) { e -> StatementScreen(db, e.arguments?.getLong("a") ?: 0L, back) }
            composable("voucher/{t}/{id}", arguments = listOf(navArgument("t") { type = NavType.IntType }, navArgument("id") { type = NavType.LongType })) { e ->
                val t = e.arguments?.getInt("t") ?: TrType.RECEIPT
                VoucherScreen(db, t, e.arguments?.getLong("id") ?: 0L, onList = { go("vouchers/$t") }, onBack = back)
            }
            composable("vouchers/{t}", arguments = listOf(navArgument("t") { type = NavType.IntType })) { e ->
                val t = e.arguments?.getInt("t") ?: TrType.RECEIPT
                VouchersListScreen(db, t, onNew = { go("voucher/$t/0") }, onOpen = { go("voucher/$t/$it") }, onBack = back)
            }
            composable(JOURNAL_NEW) { JournalEntryScreen(db, opening = false, onBack = back) }
            composable(OPENING_NEW) { JournalEntryScreen(db, opening = true, onBack = back) }

            Menu.allItems.forEach { item ->
                composable(item.route) {
                    val screen = real[item.route]
                    val family = billLists[item.route]
                    when {
                        family != null -> BillListScreen(db, family, item.title, onNew = { go("new/${it.route}") }, onOpen = { k, id -> go("bill/${k.route}/$id") }, onBack = back)
                        item.route == Routes.VOUCHER -> VoucherScreen(db, TrType.RECEIPT, 0L, onList = { t -> go("vouchers/$t") }, onBack = back)
                        item.route == Routes.ACCOUNTS_LIST || item.route == Routes.ADD_ACCOUNT -> AccountsListScreen(db, openAdd = item.route == Routes.ADD_ACCOUNT, onStatement = { go("statement/$it") }, onBack = back)
                        item.route == Routes.JOURNAL -> JournalListScreen(db, opening = false, onNew = { go(JOURNAL_NEW) }, onBack = back)
                        item.route == Routes.OPENING_ENTRY -> JournalListScreen(db, opening = true, onNew = { go(OPENING_NEW) }, onBack = back)
                        item.route == Routes.CASH_MOVEMENT -> StatementScreen(db, Session.cashAccountId, back)
                        item.route == Routes.ACCOUNTS -> AccountsTreeScreen(db, onStatement = { go("statement/$it") }, onBack = back)
                        item.route == Routes.CUSTOMERS -> PartiesScreen(db, Party.KIND_CUSTOMER, item.title, onStatement = { go("statement/$it") }, onBack = back)
                        item.route == Routes.SUPPLIERS -> PartiesScreen(db, Party.KIND_SUPPLIER, item.title, onStatement = { go("statement/$it") }, onBack = back)
                        item.route == Routes.STATEMENT -> StatementScreen(db, 0L, back)
                        item.route == Routes.ITEMS -> ItemsScreen(db, back)
                        item.route == Routes.ITEM_PRICES -> ItemPricesScreen(db, back)
                        item.route == Routes.UNITS -> UnitsScreen(db, back)
                        item.route == Routes.STOCKTAKE -> StocktakeScreen(db, back)
                        item.route == Routes.ADJUST -> AdjustScreen(db, back)
                        item.route == Routes.TRANSFER -> TransferScreen(db, back)
                        item.route == Routes.WAREHOUSES -> BranchesScreen(db, back)
                        item.route == Routes.CURRENCIES -> CurrenciesScreen(db, back)
                        item.route == Routes.RATES -> RatesScreen(db, back)
                        item.route == Routes.ACCOUNT_LIMIT -> AccountLimitScreen(db, back)
                        item.route == Routes.ITEM_MOVEMENT -> ItemMovementScreen(db, back)
                        item.route == Routes.TRIAL -> TrialBalanceScreen(db, back)
                        item.route == Routes.INCOME -> ProfitLossScreen(db, back)
                        item.route == Routes.BALANCE_SHEET -> BalanceSheetScreen(db, back)
                        item.route == Routes.OTHER_REPORTS -> OtherReportsScreen(go, back)
                        item.route == Routes.SALES_REPORT -> SalesReportScreen(db, back)
                        item.route == Routes.STOCK_REPORT -> StockReportScreen(db, back)
                        item.route == Routes.BACKUP_SAVE || item.route == Routes.BACKUP_RESTORE -> BackupScreen(db, back)
                        item.route == Routes.CLOSING -> ClosingScreen(db, back)
                        item.route == Routes.USERS -> UsersScreen(db, back)
                        item.route == Routes.TAXES -> TaxesScreen(db, back)
                        screen != null -> screen(back)
                        else -> PlaceholderScreen(item.title, item.part, back)
                    }
                }
            }
        }
    }
}

private val MenuItemSettings = MenuItem(Routes.SETTINGS, "الإعدادات", Icons.Default.Settings)
