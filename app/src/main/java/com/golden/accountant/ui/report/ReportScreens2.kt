package com.golden.accountant.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden.accountant.data.*
import com.golden.accountant.domain.*
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.items.SimpleItemPicker
import com.golden.accountant.ui.nav.Menu
import com.golden.accountant.ui.common.MenuRow
import kotlinx.coroutines.flow.first

private fun yearStart() = todayIso().take(4) + "-01-01"

private fun trName(t: Int, back: Boolean) = when (t) {
    TrType.SALE -> if (back) "مرتجع بيع" else "بيع"; TrType.PURCHASE -> if (back) "مرتجع شراء" else "شراء"
    TrType.SUPPLY -> "توريد"; TrType.ISSUE -> "صرف"; TrType.TRANSFER -> "تحويل"; TrType.ADJUST -> "تسوية"; else -> "حركة"
}

/** حركة الأصناف: رصيد سابق ثم كل حركة (داخل/خارج) برصيد تراكمي لصنف واحد في فترة. */
@Composable
fun ItemMovementScreen(db: AppDatabase, onBack: () -> Unit) {
    val allItems by db.items().observeAll().collectAsState(emptyList())
    var item by remember { mutableStateOf<Item?>(null) }
    var picking by remember { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf(yearStart()) }
    var to by rememberSaveable { mutableStateOf(todayIso()) }
    var opening by remember { mutableDoubleStateOf(0.0) }
    var rows by remember { mutableStateOf(emptyList<Triple<ItemMove, Double, Double>>()) }   // (حركة، أثرها على الرصيد، الرصيد بعدها)

    LaunchedEffect(item, from, to) {
        val i = item ?: return@LaunchedEffect
        opening = Money.r(i.openingQty + db.items().qtyBefore(i.id, from))
        var run = opening
        rows = db.items().itemMoves(i.id, from, to).map { m ->
            val eff = when {
                m.trType == TrType.SUPPLY || (m.trType == TrType.PURCHASE && !m.isBack) || (m.trType == TrType.SALE && m.isBack) -> m.qty
                m.trType == TrType.ISSUE || (m.trType == TrType.SALE && !m.isBack) || (m.trType == TrType.PURCHASE && m.isBack) -> -m.qty
                m.trType == TrType.ADJUST -> m.qty
                else -> 0.0   // التحويل لا يغيّر الإجمالي
            }
            run = Money.r(run + eff); Triple(m, eff, run)
        }
    }
    val doc = item?.let { i ->
        PdfDoc("حركة صنف: ${i.name}", listOf("من $from إلى $to", "رصيد سابق: ${money(opening)}"), listOf("التاريخ", "الحركة", "داخل", "خارج", "الرصيد"), listOf(1.3f, 2f, 1f, 1f, 1.2f),
            rows.map { (m, e, r) -> listOf(m.date, "${trName(m.trType, m.isBack)} #${m.billNo}", if (e > 0) money(e) else "", if (e < 0) money(-e) else "", money(r)) }, listOf("الرصيد الختامي: ${money(rows.lastOrNull()?.third ?: opening)}"))
    }

    Scaffold(topBar = { GoldTopBar("حركة الأصناف", onBack) { ShareAction(doc, "item_movement") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) { Text(item?.name ?: "اختر الصنف") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("من"); DateButton(from) { from = it }; Text("إلى"); DateButton(to) { to = it } }
                if (item != null) Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) { Text("رصيد سابق: ${money(opening)}   |   الرصيد الختامي: ${money(rows.lastOrNull()?.third ?: opening)}", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.fillMaxWidth().background(Gold.Primary).padding(8.dp)) {
                listOf("التاريخ" to 1.3f, "الحركة" to 2f, "داخل" to 1f, "خارج" to 1f, "الرصيد" to 1.2f).forEach { (t, w) -> Text(t, Modifier.weight(w), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            LazyColumn {
                items(rows.size) { i ->
                    val (m, e, r) = rows[i]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(m.date.takeLast(5), Modifier.weight(1.3f), fontSize = 11.sp); Text("${trName(m.trType, m.isBack)} #${m.billNo}", Modifier.weight(2f), fontSize = 11.sp)
                        Text(if (e > 0) money(e) else "", Modifier.weight(1f), fontSize = 11.sp); Text(if (e < 0) money(-e) else "", Modifier.weight(1f), fontSize = 11.sp)
                        Text(money(r), Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
                if (item != null && rows.isEmpty()) item { Text("لا توجد حركات في هذه الفترة", Modifier.padding(24.dp)) }
            }
        }
    }
    if (picking) SimpleItemPicker(allItems, onPick = { item = it }, onDismiss = { picking = false })
}

/**
 * المركز المالي حتى تاريخ: الأصول = أرصدة الأصول بالقيد + قيمة المخزون الحالية (بدل قيد بضاعة أول المدة)،
 * والخصوم وحقوق الملكية من القيود + صافي الربح التراكمي. يتحقق من تساوي الطرفين.
 */
@Composable
fun BalanceSheetScreen(db: AppDatabase, onBack: () -> Unit) {
    var asOf by rememberSaveable { mutableStateOf(todayIso()) }
    var assets by remember { mutableStateOf(emptyList<Pair<String, Double>>()) }
    var liab by remember { mutableStateOf(emptyList<Pair<String, Double>>()) }
    var equity by remember { mutableStateOf(emptyList<Pair<String, Double>>()) }

    LaunchedEffect(asOf) {
        val accounts = db.accounts().observeAll().first()
        val totals = db.journal().periodTotals(0L, "0000-01-01", asOf).associate { it.accountId to (it.debit to it.credit) }
        val items = db.items().all()
        val closing = Valuation.totalValue(Valuation.positions(items, db.items().movementRowsUpTo(asOf)))
        val opening = Valuation.totalValue(Valuation.positions(items, db.items().movementRowsBefore("0000-01-02")))
        val pl = ProfitLoss.compute(totals, accounts, opening, closing)
        val leaves = accounts.filter { !it.isGroup }
        fun bal(a: Account) = (totals[a.id]?.let { it.first - it.second } ?: 0.0)
        assets = leaves.filter { it.nature == Nature.ASSET && it.id != Sys.OPENING_STOCK && Math.abs(bal(it)) > 0.00005 }.map { it.name to Money.r(bal(it)) } +
            listOf("مخزون البضاعة (بالتكلفة)" to Money.r(closing))
        liab = leaves.filter { it.nature == Nature.LIABILITY && Math.abs(bal(it)) > 0.00005 }.map { it.name to Money.r(-bal(it)) }
        equity = leaves.filter { it.nature == Nature.EQUITY && Math.abs(bal(it)) > 0.00005 }.map { it.name to Money.r(-bal(it)) } +
            listOf((if (pl.netProfit >= 0) "صافي الربح" else "صافي الخسارة") to pl.netProfit)
    }
    val tA = assets.sumOf { it.second }; val tL = liab.sumOf { it.second }; val tE = equity.sumOf { it.second }
    val ok = Math.abs(tA - (tL + tE)) < 0.01
    val doc = PdfDoc("المركز المالي", listOf("بتاريخ $asOf", "بالعملة المحلية"), listOf("البند", "المبلغ"), listOf(3f, 1.5f),
        listOf(listOf("الأصول", "")) + assets.map { listOf("  ${it.first}", money(it.second)) } + listOf(listOf("الالتزامات", "")) + liab.map { listOf("  ${it.first}", money(it.second)) } +
            listOf(listOf("حقوق الملكية", "")) + equity.map { listOf("  ${it.first}", money(it.second)) },
        listOf("إجمالي الأصول: ${money(tA)}", "الالتزامات + حقوق الملكية: ${money(tL + tE)}", if (ok) "المركز متوازن" else "تنبيه: المركز غير متوازن"))

    Scaffold(topBar = { GoldTopBar("المركز المالي", onBack) { ShareAction(doc, "balance_sheet") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("حتى تاريخ"); DateButton(asOf) { asOf = it } }
            Text("بالعملة المحلية. المخزون مُقيَّم بالمتوسط المرجح.", style = MaterialTheme.typography.bodySmall)
            Section("الأصول", assets, tA); Section("الالتزامات", liab, tL); Section("حقوق الملكية", equity, tE)
            Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                Column(Modifier.padding(14.dp)) {
                    Text("الأصول ${money(tA)}", fontWeight = FontWeight.Bold)
                    Text("الالتزامات + حقوق الملكية ${money(tL + tE)}", fontWeight = FontWeight.Bold)
                    Text(if (ok) "المركز المالي متوازن" else "غير متوازن! راجع القيود", color = if (ok) Gold.Primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, rows: List<Pair<String, Double>>, total: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Gold.Primary)
            rows.forEach { (n, v) -> Row(Modifier.fillMaxWidth()) { Text(n, Modifier.weight(1f)); Text(money(v)) } }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth()) { Text("الإجمالي", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(money(total), fontWeight = FontWeight.Bold) }
        }
    }
}

/** تقارير أخرى: بقية التقارير والقوائم. */
@Composable
fun OtherReportsScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) = Scaffold(topBar = { GoldTopBar("تقارير أخرى", onBack) }) { pad ->
    Column(Modifier.fillMaxSize().padding(pad).padding(8.dp)) {
        Menu.otherReports.filter { Session.can(it.route) }.forEach { MenuRow(it, onNavigate); HorizontalDivider() }
    }
}
