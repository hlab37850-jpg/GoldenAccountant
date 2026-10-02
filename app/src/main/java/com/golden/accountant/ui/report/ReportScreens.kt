package com.golden.accountant.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden.accountant.data.*
import com.golden.accountant.domain.ProfitLoss
import com.golden.accountant.domain.Valuation
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import kotlinx.coroutines.flow.first

@Composable
fun ShareAction(doc: PdfDoc?, fileName: String) {
    val ctx = LocalContext.current
    IconButton(enabled = doc != null, onClick = { doc?.let { PdfExport.export(ctx, it, fileName) } }) { Icon(Icons.Default.Share, "مشاركة PDF") }
}

@Composable
private fun PeriodRow(from: String, to: String, onFrom: (String) -> Unit, onTo: (String) -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("من"); DateButton(from, onChange = onFrom); Text("إلى"); DateButton(to, onChange = onTo)
    }

@Composable
private fun KV(label: String, value: Double, bold: Boolean = false, indent: Boolean = false) = Row(Modifier.fillMaxWidth().padding(start = if (indent) 16.dp else 0.dp, top = 3.dp, bottom = 3.dp)) {
    Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
    Text(money(value), fontWeight = if (bold) FontWeight.Bold else null)
}

private fun yearStart() = todayIso().take(4) + "-01-01"

// ------------------------------------------------------------------ ميزان المراجعة
@Composable
fun TrialBalanceScreen(db: AppDatabase, onBack: () -> Unit) {
    val accounts by db.accounts().observeAll().collectAsState(emptyList())
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    var currency by rememberSaveable { mutableLongStateOf(0L) }
    val balances by remember(currency) { db.accounts().balances(currency) }.collectAsState(emptyList())

    val rows = remember(accounts, balances) {
        val names = accounts.associateBy { it.id }
        balances.filter { Math.abs(it.balance) > 0.00005 && names[it.accountId]?.isGroup == false }
            .map { names[it.accountId]!!.name to it.balance }.sortedBy { it.first }
    }
    val dr = rows.sumOf { it.second.coerceAtLeast(0.0) }; val cr = rows.sumOf { (-it.second).coerceAtLeast(0.0) }
    val ok = Math.abs(dr - cr) < 0.005
    val doc = PdfDoc(
        "ميزان المراجعة", listOf("بتاريخ ${todayIso()}", "العملة: ${currencies.firstOrNull { it.id == currency }?.name ?: ""}"),
        listOf("الحساب", "مدين", "دائن"), listOf(3f, 1.2f, 1.2f),
        rows.map { listOf(it.first, if (it.second > 0) money(it.second) else "", if (it.second < 0) money(-it.second) else "") },
        listOf("الإجمالي: مدين ${money(dr)}   دائن ${money(cr)}", if (ok) "الميزان متوازن" else "تنبيه: الميزان غير متوازن"),
    )

    Scaffold(topBar = { GoldTopBar("ميزان المراجعة", onBack) { ShareAction(doc, "trial_balance") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.padding(12.dp)) { CurrencyChips(currencies, currency) { currency = it } }
            Row(Modifier.fillMaxWidth().background(Gold.Primary).padding(8.dp)) {
                Text("الحساب", Modifier.weight(3f), color = Color.White, fontWeight = FontWeight.Bold)
                Text("مدين", Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold)
                Text("دائن", Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(rows) { (name, b) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(name, Modifier.weight(3f), fontSize = 13.sp)
                        Text(if (b > 0) money(b) else "", Modifier.weight(1.2f), fontSize = 13.sp)
                        Text(if (b < 0) money(-b) else "", Modifier.weight(1.2f), fontSize = 13.sp)
                    }
                    HorizontalDivider()
                }
                if (rows.isEmpty()) item { Text("لا توجد حركات", Modifier.padding(24.dp)) }
            }
            Surface(color = Gold.Light) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("مدين ${money(dr)}   دائن ${money(cr)}", fontWeight = FontWeight.Bold)
                    Text(if (ok) "الميزان متوازن" else "الميزان غير متوازن!", color = if (ok) Gold.Primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ الأرباح والخسائر
@Composable
fun ProfitLossScreen(db: AppDatabase, onBack: () -> Unit) {
    var from by rememberSaveable { mutableStateOf(yearStart()) }
    var to by rememberSaveable { mutableStateOf(todayIso()) }
    var res by remember { mutableStateOf<ProfitLoss.Result?>(null) }

    LaunchedEffect(from, to) {
        val accounts = db.accounts().observeAll().first()
        val totals = db.journal().periodTotals(0L, from, to).associate { it.accountId to (it.debit to it.credit) }
        val items = db.items().all()
        val opening = Valuation.totalValue(Valuation.positions(items, db.items().movementRowsBefore(from)))
        val closing = Valuation.totalValue(Valuation.positions(items, db.items().movementRowsUpTo(to)))
        res = ProfitLoss.compute(totals, accounts, opening, closing)
    }
    val r = res
    val doc = r?.let { x ->
        PdfDoc("الأرباح والخسائر", listOf("من $from إلى $to", "بالعملة المحلية"), listOf("البند", "المبلغ"), listOf(3f, 1.5f),
            buildList {
                add(listOf("المبيعات", money(x.sales))); add(listOf("مردودات المبيعات", money(x.salesReturns))); add(listOf("خصم مسموح به", money(x.discountAllowed)))
                add(listOf("صافي المبيعات", money(x.netSales)))
                add(listOf("مخزون أول المدة", money(x.openingInventory))); add(listOf("صافي المشتريات", money(x.netPurchases))); add(listOf("مخزون آخر المدة", money(x.closingInventory)))
                add(listOf("تكلفة المبيعات", money(x.cogs)))
                if (x.otherCosts != 0.0) add(listOf("تكاليف أخرى (تسوية/تالف)", money(x.otherCosts)))
                add(listOf("مجمل الربح", money(x.grossProfit))); add(listOf("إيرادات أخرى", money(x.otherIncome)))
                x.expenses.forEach { add(listOf("مصروف: ${it.label}", money(it.amount))) }
                add(listOf("إجمالي المصروفات", money(x.totalExpenses)))
            },
            listOf("صافي الربح: ${money(x.netProfit)}"))
    }

    Scaffold(topBar = { GoldTopBar("الأرباح والخسائر", onBack) { ShareAction(doc, "profit_loss") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PeriodRow(from, to, { from = it }, { to = it })
            Text("التقرير بالعملة المحلية. المخزون مُقيَّم بالمتوسط المرجح للتكلفة.", style = MaterialTheme.typography.bodySmall)
            if (r == null) CircularProgressIndicator() else {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        KV("المبيعات", r.sales); KV("(-) مردودات المبيعات", r.salesReturns, indent = true); KV("(-) خصم مسموح به", r.discountAllowed, indent = true)
                        KV("صافي المبيعات", r.netSales, bold = true); HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KV("مخزون أول المدة", r.openingInventory); KV("(+) صافي المشتريات", r.netPurchases, indent = true); KV("(-) مخزون آخر المدة", r.closingInventory, indent = true)
                        KV("تكلفة المبيعات", r.cogs, bold = true)
                        if (r.otherCosts != 0.0) KV("تكاليف أخرى (تسوية/تالف)", r.otherCosts)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KV("مجمل الربح", r.grossProfit, bold = true); KV("(+) إيرادات أخرى", r.otherIncome, indent = true)
                        r.expenses.forEach { KV("(-) ${it.label}", it.amount, indent = true) }
                        KV("إجمالي المصروفات", r.totalExpenses)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(if (r.netProfit >= 0) "صافي الربح" else "صافي الخسارة", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(money(r.netProfit), fontWeight = FontWeight.Bold, color = if (r.netProfit >= 0) Gold.Primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ تقرير المبيعات
@Composable
fun SalesReportScreen(db: AppDatabase, onBack: () -> Unit) {
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    var currency by rememberSaveable { mutableLongStateOf(0L) }
    var from by rememberSaveable { mutableStateOf(yearStart()) }
    var to by rememberSaveable { mutableStateOf(todayIso()) }
    var sales by remember { mutableStateOf<BillSummary?>(null) }
    var back by remember { mutableStateOf<BillSummary?>(null) }
    var purchases by remember { mutableStateOf<BillSummary?>(null) }
    var pback by remember { mutableStateOf<BillSummary?>(null) }
    var top by remember { mutableStateOf(emptyList<TopItem>()) }

    LaunchedEffect(currency, from, to) {
        sales = db.bills().summary(1, false, currency, from, to); back = db.bills().summary(1, true, currency, from, to)
        purchases = db.bills().summary(2, false, currency, from, to); pback = db.bills().summary(2, true, currency, from, to)
        top = db.items().topSold(currency, from, to)
    }
    val net = (sales?.total ?: 0.0) - (back?.total ?: 0.0)
    val doc = if (sales == null) null else PdfDoc(
        "تقرير المبيعات والمشتريات", listOf("من $from إلى $to"),
        listOf("البند", "العدد", "المجموع", "الخصم", "الضريبة", "الصافي"), listOf(2.2f, 0.8f, 1.3f, 1.1f, 1.1f, 1.4f),
        listOf("مبيعات" to sales, "مرتجع مبيعات" to back, "مشتريات" to purchases, "مرتجع مشتريات" to pback).map { (n, s) ->
            listOf(n, "${s?.count ?: 0}", money(s?.subtotal ?: 0.0), money(s?.discount ?: 0.0), money(s?.tax ?: 0.0), money(s?.total ?: 0.0))
        } + top.map { listOf("• ${it.name}", "", money(it.qty), "", "", money(it.amount)) },
        listOf("صافي المبيعات بعد المرتجع: ${money(net)}"),
    )

    Scaffold(topBar = { GoldTopBar("تقرير المبيعات", onBack) { ShareAction(doc, "sales_report") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PeriodRow(from, to, { from = it }, { to = it })
            CurrencyChips(currencies, currency) { currency = it }
            listOf("المبيعات" to sales, "مرتجع المبيعات" to back, "المشتريات" to purchases, "مرتجع المشتريات" to pback).forEach { (n, s) ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("$n  (${s?.count ?: 0} مستند)", fontWeight = FontWeight.Bold, color = Gold.Primary)
                        KV("المجموع", s?.subtotal ?: 0.0); KV("الخصم", s?.discount ?: 0.0); KV("الضريبة", s?.tax ?: 0.0); KV("الصافي", s?.total ?: 0.0, bold = true)
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) { Box(Modifier.padding(12.dp)) { KV("صافي المبيعات بعد المرتجع", net, bold = true) } }
            if (top.isNotEmpty()) {
                Text("أكثر الأصناف مبيعاً", style = MaterialTheme.typography.titleSmall)
                top.forEach { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(it.name, Modifier.weight(1f)); Text("${money(it.qty)}  |  ${money(it.amount)}", fontSize = 12.sp) } }
            }
        }
    }
}

// ------------------------------------------------------------------ تقرير المخزون
@Composable
fun StockReportScreen(db: AppDatabase, onBack: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var data by remember { mutableStateOf<List<Triple<Item, Double, Double>>?>(null) }
    val moves by db.items().movementByItem().collectAsState(emptyList())   // يعيد الحساب عند أي حركة
    val branches by db.core().observeBranches().collectAsState(emptyList())
    var branch by rememberSaveable { mutableLongStateOf(0L) }                  // 0 = كل الفروع

    LaunchedEffect(moves, branch) {
        val items = db.items().all()
        val pos = Valuation.positions(items, db.items().movementRowsUpTo("9999-12-31")).associateBy { it.itemId }
        // كمية الفرع: الافتتاحي للفرع 1 + حركات الفرع (التحويل يخصم ويضيف). التكلفة المتوسطة عامة.
        val perBranch = if (branch != 0L) db.items().stockByBranch(branch).associate { it.itemId to it.qty } else emptyMap()
        data = items.map {
            val qty = if (branch == 0L) pos[it.id]?.qty ?: 0.0 else Money.r((if (branch == 1L) it.openingQty else 0.0) + (perBranch[it.id] ?: 0.0))
            Triple(it, qty, pos[it.id]?.avgCost ?: 0.0)
        }
    }
    val shown = remember(q, data) { data.orEmpty().filter { q.isBlank() || it.first.name.contains(q.trim(), true) } }
    val totalValue = shown.sumOf { Money.r(it.second.coerceAtLeast(0.0) * it.third) }
    val doc = data?.let {
        PdfDoc("تقرير المخزون", listOf("بتاريخ ${todayIso()}"), listOf("الصنف", "الكمية", "متوسط التكلفة", "القيمة"), listOf(3f, 1f, 1.3f, 1.4f),
            shown.map { (i, qty, c) -> listOf(i.name, money(qty), money(c), money(qty.coerceAtLeast(0.0) * c)) }, listOf("إجمالي قيمة المخزون: ${money(totalValue)}"))
    }

    Scaffold(topBar = { GoldTopBar("تقرير المخزون", onBack) { ShareAction(doc, "stock_report") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), label = { Text("بحث") }, singleLine = true)
            if (branches.size > 1) Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(branch == 0L, { branch = 0L }, { Text("كل الفروع") })
                branches.forEach { b -> FilterChip(branch == b.id, { branch = b.id }, { Text(b.name) }) }
            }
            Row(Modifier.fillMaxWidth().background(Gold.Primary).padding(8.dp)) {
                listOf("الصنف" to 3f, "الكمية" to 1f, "التكلفة" to 1.3f, "القيمة" to 1.4f).forEach { (t, w) -> Text(t, Modifier.weight(w), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.first.id }) { (i, qty, c) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(i.name, Modifier.weight(3f), fontSize = 12.sp)
                        Text(money(qty), Modifier.weight(1f), fontSize = 12.sp, color = if (qty < 0) MaterialTheme.colorScheme.error else Color.Unspecified)
                        Text(money(c), Modifier.weight(1.3f), fontSize = 12.sp)
                        Text(money(qty.coerceAtLeast(0.0) * c), Modifier.weight(1.4f), fontSize = 12.sp)
                    }
                    HorizontalDivider()
                }
                if (data == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else if (shown.isEmpty()) item { Text("لا توجد أصناف", Modifier.padding(24.dp)) }
            }
            Surface(color = Gold.Light) { Text("إجمالي قيمة المخزون: ${money(totalValue)}", Modifier.fillMaxWidth().padding(12.dp), fontWeight = FontWeight.Bold) }
        }
    }
}
