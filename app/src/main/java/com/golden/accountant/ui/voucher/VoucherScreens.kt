package com.golden.accountant.ui.voucher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.Account
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.Voucher
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.InvoiceMath
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.nav.Routes
import com.golden.accountant.domain.Sys
import com.golden.accountant.domain.TrType
import com.golden.accountant.domain.VoucherRepository
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import kotlinx.coroutines.launch

private fun title(type: Int) = if (type == TrType.RECEIPT) "سند قبض" else "سند صرف"
private fun fmtNum(v: Double) = if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** نموذج سند قبض/صرف. id=0 جديد؛ غير ذلك تعديل. */
@Composable
fun VoucherScreen(db: AppDatabase, type: Int, id: Long, onList: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val leaves by db.accounts().observeLeaves().collectAsState(emptyList())
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    val repo = remember { VoucherRepository(db) }

    var date by remember { mutableStateOf(todayIso()) }
    var account by remember { mutableStateOf<Account?>(null) }
    var cash by remember { mutableStateOf<Account?>(null) }
    var amount by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }
    var currency by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf("") }
    var no by remember { mutableIntStateOf(0) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickCash by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var currentBalance by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(id) {
        cash = db.accounts().byId(Session.cashAccountId)
        if (id > 0) {
            val v = db.vouchers().byId(id) ?: run { onBack(); return@LaunchedEffect }
            date = v.date; account = db.accounts().byId(v.accountId); cash = db.accounts().byId(v.cashAccountId)
            amount = fmtNum(v.amount); discount = fmtNum(v.discount); currency = v.currencyId; note = v.note; no = v.voucherNo
        } else no = db.vouchers().nextNo(type)
    }
    LaunchedEffect(account, currency) { account?.let { currentBalance = db.accounts().balance(it.id, currency) } }

    val cashAccounts = remember(leaves) { leaves.filter { it.parentId == Sys.CASH_BOXES || it.parentId == Sys.BANKS } }
    val amt = InvoiceMath.parse(amount); val disc = InvoiceMath.parse(discount)
    val route = if (type == TrType.RECEIPT) Routes.RECEIPT else Routes.PAYMENT
    val canSave = Session.can(route, if (id > 0) Action.EDIT else Action.NEW)
    val canDelete = Session.can(route, Action.DELETE)

    Scaffold(
        topBar = {
            GoldTopBar(title(type) + if (no > 0) "  #$no" else "", onBack) {
                IconButton(onClick = onList) { Icon(Icons.Default.List, "قائمة السندات") }
                if (id > 0 && canDelete) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "حذف") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DateButton(date) { date = it }
            CurrencyChips(currencies, currency) { currency = it }
            OutlinedButton(onClick = { pickAccount = true }, Modifier.fillMaxWidth()) {
                Text(account?.name ?: if (type == TrType.RECEIPT) "استلمنا من (اختر الحساب)" else "دفعنا إلى (اختر الحساب)")
            }
            if (account != null) Text("رصيد الحساب الحالي: ${balanceText(currentBalance)}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { pickCash = true }, Modifier.fillMaxWidth()) { Text("الصندوق/البنك: ${cash?.name ?: "اختر"}") }
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("المبلغ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(discount, { discount = it }, Modifier.fillMaxWidth(), label = { Text(if (type == TrType.RECEIPT) "خصم مسموح به" else "خصم مكتسب") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (disc > 0) Text("يُسدَّد من الحساب: ${money(amt + disc)}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("البيان") })
            Button(
                onClick = {
                    val a = account; val c = cash
                    scope.launch {
                        if (a == null || c == null) { snack.showSnackbar("اختر الحساب والصندوق"); return@launch }
                        runCatching {
                            repo.save(Voucher(id = id, type = type, voucherNo = if (id > 0) no else 0, date = date, time = nowTime(), accountId = a.id, cashAccountId = c.id, amount = amt, discount = disc, currencyId = currency, note = note.trim(), userId = Session.userId))
                        }.onSuccess { sid ->
                            if (id > 0) { onBack() } else {
                                snack.showSnackbar("تم حفظ ${title(type)} رقم ${db.vouchers().byId(sid)?.voucherNo}")
                                amount = ""; discount = ""; note = ""; account = null; no = db.vouchers().nextNo(type)
                            }
                        }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحفظ") }
                    }
                },
                Modifier.fillMaxWidth(), enabled = canSave,
            ) { Text(if (id > 0) "تحديث" else "حفظ") }
        }
    }

    if (pickAccount) AccountPickerDialog("اختيار الحساب", leaves.filter { it.id != cash?.id }, onPick = { account = it }, onDismiss = { pickAccount = false })
    if (pickCash) AccountPickerDialog("الصندوق أو البنك", cashAccounts, onPick = { cash = it }, onDismiss = { pickCash = false })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف السند؟") }, text = { Text("سيُحذف السند وقيده المحاسبي نهائياً.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { runCatching { repo.delete(id) }.onSuccess { onBack() }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
    )
}

@Composable
fun VouchersListScreen(db: AppDatabase, type: Int, onNew: () -> Unit, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val vouchers by remember(type) { db.vouchers().observe(type) }.collectAsState(emptyList())
    val accounts by db.accounts().observeAll().collectAsState(emptyList())
    val names = remember(accounts) { accounts.associate { it.id to it.name } }
    Scaffold(
        topBar = { GoldTopBar("قائمة " + title(type), onBack) },
        floatingActionButton = { FloatingActionButton(onClick = onNew, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "جديد", tint = Color.White) } },
    ) { pad ->
        if (vouchers.isEmpty()) Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("لا توجد سندات") }
        else LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(vouchers, key = { it.id }) { v ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(v.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${v.voucherNo}  ${names[v.accountId] ?: ""}", fontWeight = FontWeight.Bold)
                        Text(v.date + if (v.note.isNotBlank()) "  •  ${v.note}" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(money(v.amount), color = Gold.Primary, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
        }
    }
}
