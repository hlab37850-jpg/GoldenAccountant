package com.golden.accountant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.*
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.InvoiceMath
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.nav.Routes
import kotlinx.coroutines.launch

/** سعر العملات: سجل أسعار الصرف لكل عملة أجنبية بتاريخ سريان. */
@Composable
fun RatesScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    val foreign = currencies.filter { !it.isLocal }
    var sel by rememberSaveable { mutableLongStateOf(-1L) }
    val cur = foreign.firstOrNull { it.id == sel } ?: foreign.firstOrNull()
    val rates by remember(cur?.id) { db.core().observeRates(cur?.id ?: -1L) }.collectAsState(emptyList())
    var date by remember { mutableStateOf(todayIso()) }
    var price by remember { mutableStateOf("") }
    val canNew = Session.can(Routes.RATES, Action.NEW); val canDelete = Session.can(Routes.RATES, Action.DELETE)

    Scaffold(topBar = { GoldTopBar("سعر العملات", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (foreign.isEmpty()) Text("أضف عملة أجنبية من شاشة «إضافة عملة» أولاً.")
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { foreign.forEach { c -> FilterChip(cur?.id == c.id, { sel = c.id }, { Text(c.name) }) } }
                Text("كم وحدة محلية تساوي 1 ${cur?.name ?: ""}", style = MaterialTheme.typography.bodySmall)
                if (canNew) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateButton(date) { date = it }
                    OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text("السعر") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Button(onClick = { val p = InvoiceMath.parse(price); val c = cur; if (p > 0 && c != null) scope.launch { db.core().insertRate(CurrencyRate(currencyId = c.id, fromDate = date, price = p)); price = "" } }) { Text("حفظ") }
                }
                HorizontalDivider()
                LazyColumn {
                    items(rates, key = { it.id }) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${r.fromDate}", Modifier.weight(1f)); Text(money(r.price), fontWeight = FontWeight.Bold, color = Gold.Primary)
                            if (canDelete) IconButton(onClick = { scope.launch { db.core().deleteRate(r.id) } }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** سقف الحساب لكل عملة: دائن (cr) = أعلى رصيد مدين مسموح، مدين (db) = أعلى رصيد دائن مسموح. 0 = بلا سقف. */
@Composable
fun AccountLimitScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val limits by db.limits().observeAll().collectAsState(emptyList())
    val accounts by db.accounts().observeLeaves().collectAsState(emptyList())
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    val names = remember(accounts) { accounts.associate { it.id to it.name } }
    var adding by remember { mutableStateOf(false) }
    val canNew = Session.can(Routes.ACCOUNT_LIMIT, Action.NEW); val canDelete = Session.can(Routes.ACCOUNT_LIMIT, Action.DELETE)

    Scaffold(
        topBar = { GoldTopBar("سقف الحساب", onBack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { adding = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "سقف جديد", tint = Color.White) } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(limits, key = { "${it.accountId}-${it.currencyId}" }) { l ->
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(names[l.accountId] ?: "?", fontWeight = FontWeight.Bold)
                        Text("${currencies.firstOrNull { it.id == l.currencyId }?.name ?: ""}  •  أعلى مدين ${money(l.cr)}  •  أعلى دائن ${money(l.db)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (canDelete) IconButton(onClick = { scope.launch { db.limits().delete(l.accountId, l.currencyId) } }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
            }
            if (limits.isEmpty()) item { Text("لا توجد أسقف. الحساب بلا سقف يقبل أي رصيد.", Modifier.padding(24.dp)) }
        }
    }

    if (adding) {
        var acc by remember { mutableStateOf<Account?>(null) }; var cur by remember { mutableLongStateOf(0L) }
        var cr by remember { mutableStateOf("") }; var dbt by remember { mutableStateOf("") }; var pick by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false }, title = { Text("سقف جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick = true }) { Text(acc?.name ?: "اختر الحساب") }
                    CurrencyChips(currencies, cur) { cur = it }
                    OutlinedTextField(cr, { cr = it }, label = { Text("أعلى رصيد مدين (عليه)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(dbt, { dbt = it }, label = { Text("أعلى رصيد دائن (له)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = { TextButton(onClick = { acc?.let { a -> scope.launch { db.limits().upsert(CusLimit(a.id, cur, InvoiceMath.parse(cr), InvoiceMath.parse(dbt))) } }; adding = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("إلغاء") } },
        )
        if (pick) AccountPickerDialog("اختيار الحساب", accounts, onPick = { acc = it }, onDismiss = { pick = false })
    }
}
