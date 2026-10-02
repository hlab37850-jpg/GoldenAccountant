package com.golden.accountant.ui.accounts

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden.accountant.data.Account
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.StatementRow
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.report.PdfDoc
import com.golden.accountant.ui.report.ShareAction

private data class Row2(val row: StatementRow, val running: Double)

/** كشف حساب: رصيد افتتاحي قبل تاريخ البداية ثم الحركات برصيد تراكمي. accountId=0 يعني اختر حساباً. */
@Composable
fun StatementScreen(db: AppDatabase, accountId: Long, onBack: () -> Unit) {
    val accounts by db.accounts().observeLeaves().collectAsState(emptyList())
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    var account by remember { mutableStateOf<Account?>(null) }
    var currency by rememberSaveable { mutableLongStateOf(0L) }
    var from by rememberSaveable { mutableStateOf(todayIso().take(4) + "-01-01") }
    var to by rememberSaveable { mutableStateOf(todayIso()) }
    var picking by remember { mutableStateOf(false) }
    var opening by remember { mutableDoubleStateOf(0.0) }
    var rows by remember { mutableStateOf(emptyList<Row2>()) }

    LaunchedEffect(accountId) { if (accountId != 0L) account = db.accounts().byId(accountId) else picking = true }
    LaunchedEffect(account, currency, from, to) {
        val a = account ?: return@LaunchedEffect
        opening = db.journal().balanceBefore(a.id, currency, from)
        var run = opening
        rows = db.journal().statement(a.id, currency, from, to).map { r -> run += r.debit - r.credit; Row2(r, run) }
    }
    val totalDr = rows.sumOf { it.row.debit }; val totalCr = rows.sumOf { it.row.credit }
    val closing = rows.lastOrNull()?.running ?: opening

    val doc = account?.let { a ->
        PdfDoc("كشف حساب: ${a.name}", listOf("من $from إلى $to", "رصيد سابق: ${balanceText(opening)}"),
            listOf("التاريخ", "البيان", "مدين", "دائن", "الرصيد"), listOf(1.2f, 2.6f, 1.1f, 1.1f, 1.3f),
            rows.map { listOf(it.row.date, it.row.note, if (it.row.debit != 0.0) money(it.row.debit) else "", if (it.row.credit != 0.0) money(it.row.credit) else "", money(it.running)) },
            listOf("مدين ${money(totalDr)}   دائن ${money(totalCr)}", "الرصيد الختامي: ${balanceText(closing)}"))
    }
    Scaffold(topBar = { GoldTopBar("كشف حساب", onBack) { ShareAction(doc, "statement_${account?.id ?: 0}") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) { Text(account?.name ?: "اختر الحساب") }
                CurrencyChips(currencies, currency) { currency = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("من"); DateButton(from) { from = it }
                    Text("إلى"); DateButton(to) { to = it }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("رصيد سابق: ${balanceText(opening)}")
                        Text("مدين ${money(totalDr)}   دائن ${money(totalCr)}")
                        Text("الرصيد الختامي: ${balanceText(closing)}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Gold.Primary).padding(8.dp)) {
                listOf("التاريخ" to 1.1f, "البيان" to 2f, "مدين" to 1f, "دائن" to 1f, "الرصيد" to 1.2f).forEach { (t, w) ->
                    Text(t, Modifier.weight(w), color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            LazyColumn {
                itemsIndexed(rows) { _, r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(r.row.date.takeLast(5), Modifier.weight(1.1f), fontSize = 11.sp)
                        Text(r.row.note, Modifier.weight(2f), fontSize = 11.sp)
                        Text(if (r.row.debit != 0.0) money(r.row.debit) else "", Modifier.weight(1f), fontSize = 11.sp)
                        Text(if (r.row.credit != 0.0) money(r.row.credit) else "", Modifier.weight(1f), fontSize = 11.sp)
                        Text(money(r.running), Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
                if (rows.isEmpty() && account != null) item { Text("لا توجد حركات في هذه الفترة", Modifier.padding(24.dp)) }
            }
        }
    }
    if (picking) AccountPickerDialog("اختيار حساب", accounts, onPick = { account = it }, onDismiss = { picking = false })
}
