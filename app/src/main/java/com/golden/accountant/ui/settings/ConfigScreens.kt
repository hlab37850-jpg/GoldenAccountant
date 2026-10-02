package com.golden.accountant.ui.settings

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

// ------------------------------------------------------------------ العملات وأسعار الصرف
@Composable
fun CurrenciesScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    var open by remember { mutableStateOf<Currency?>(null) }
    var creating by remember { mutableStateOf(false) }
    val canNew = Session.can(Routes.CURRENCIES, Action.NEW)

    Scaffold(
        topBar = { GoldTopBar("العملات وأسعار الصرف", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { creating = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "عملة جديدة", tint = Color.White) } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(currencies, key = { it.id }) { c ->
                val rate by produceState<Double?>(null, c.id) { value = db.core().rateOn(c.id, todayIso()) }
                Row(Modifier.fillMaxWidth().clickable { open = c }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name + if (c.isLocal) "  (المحلية)" else "", fontWeight = FontWeight.Bold)
                        Text(c.code, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!c.isLocal) Text(rate?.let { "1 = ${money(it)}" } ?: "بلا سعر!", color = if (rate == null) MaterialTheme.colorScheme.error else Gold.Primary)
                }
                HorizontalDivider()
            }
        }
    }

    if (creating) CurrencyDialog(null, db, onDismiss = { creating = false }) { n, code ->
        scope.launch { runCatching { db.core().upsertCurrency(Currency(db.core().nextCurrencyId(), n.trim(), code.trim())) }.onFailure { snack.showSnackbar("تعذر الحفظ") } }
    }
    open?.let { c ->
        CurrencyDialog(c, db, onDismiss = { open = null }) { n, code ->
            if (n.isNotBlank()) scope.launch { db.core().upsertCurrency(c.copy(name = n.trim(), code = code.trim())) }
        }
    }
}

@Composable
private fun CurrencyDialog(c: Currency?, db: AppDatabase, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(c?.name ?: "") }
    var code by remember { mutableStateOf(c?.code ?: "") }
    var date by remember { mutableStateOf(todayIso()) }
    var price by remember { mutableStateOf("") }
    val rates by remember(c?.id) { db.core().observeRates(c?.id ?: -1L) }.collectAsState(emptyList())
    val canEdit = Session.can(Routes.CURRENCIES, Action.EDIT)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c == null) "عملة جديدة" else c.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, enabled = c?.isLocal != true)
                OutlinedTextField(code, { code = it }, label = { Text("الرمز") }, singleLine = true, enabled = c?.isLocal != true)
                if (c != null && !c.isLocal) {
                    HorizontalDivider()
                    Text("أسعار الصرف (مقابل المحلية)", style = MaterialTheme.typography.labelLarge)
                    rates.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${r.fromDate}  →  ${money(r.price)}", Modifier.weight(1f))
                            if (canEdit) IconButton(onClick = { scope.launch { db.core().deleteRate(r.id) } }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                    if (canEdit) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DateButton(date) { date = it }
                            OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text("السعر") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                        Button(onClick = {
                            val p = InvoiceMath.parse(price)
                            if (p > 0) scope.launch { db.core().insertRate(CurrencyRate(currencyId = c.id, fromDate = date, price = p)); price = "" }
                        }) { Text("إضافة سعر") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, code); onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

// ------------------------------------------------------------------ الضرائب
@Composable
fun TaxesScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val taxes by db.core().observeAllTaxes().collectAsState(emptyList())
    var editing by remember { mutableStateOf<Tax?>(null) }
    var creating by remember { mutableStateOf(false) }
    val canNew = Session.can(Routes.TAXES, Action.NEW); val canEdit = Session.can(Routes.TAXES, Action.EDIT)

    Scaffold(
        topBar = { GoldTopBar("الضرائب", onBack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { creating = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "ضريبة جديدة", tint = Color.White) } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(taxes, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = t }.padding(16.dp)) {
                    Text(t.name + (if (t.isDefault) "  (افتراضية)" else "") + (if (!t.isActive) "  (معطّلة)" else ""), Modifier.weight(1f))
                    Text("${t.percent}%", fontWeight = FontWeight.Bold, color = Gold.Primary)
                }
                HorizontalDivider()
            }
        }
    }
    suspend fun save(t: Tax) { if (t.isDefault) db.core().clearDefaultTax(); db.core().upsertTax(t) }
    if (creating) TaxDialog(null, onDismiss = { creating = false }) { scope.launch { save(it) } }
    editing?.let { t -> TaxDialog(t, onDismiss = { editing = null }) { scope.launch { save(it) } } }
}

@Composable
private fun TaxDialog(t: Tax?, onDismiss: () -> Unit, onSave: (Tax) -> Unit) {
    var name by remember { mutableStateOf(t?.name ?: "") }
    var pct by remember { mutableStateOf(t?.percent?.toString() ?: "") }
    var def by remember { mutableStateOf(t?.isDefault ?: false) }
    var active by remember { mutableStateOf(t?.isActive ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (t == null) "ضريبة جديدة" else "تعديل الضريبة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(pct, { pct = it }, label = { Text("النسبة %") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(def, { def = it }); Spacer(Modifier.width(8.dp)); Text("الافتراضية في الفواتير الجديدة") }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, { active = it }); Spacer(Modifier.width(8.dp)); Text("فعّالة") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = InvoiceMath.parse(pct)
                if (name.isNotBlank() && p in 0.0..100.0) onSave((t ?: Tax(name = "", percent = 0.0)).copy(name = name.trim(), percent = p, isDefault = def, isActive = active))
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

// ------------------------------------------------------------------ الفروع
@Composable
fun BranchesScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val branches by db.core().observeBranches().collectAsState(emptyList())
    var editing by remember { mutableStateOf<Branch?>(null) }
    var creating by remember { mutableStateOf(false) }
    val canNew = Session.can(Routes.BRANCHES, Action.NEW); val canEdit = Session.can(Routes.BRANCHES, Action.EDIT)

    Scaffold(
        topBar = { GoldTopBar("الفروع", onBack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { creating = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "فرع جديد", tint = Color.White) } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(branches, key = { it.id }) { b ->
                Column(Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = b }.padding(16.dp)) {
                    Text(b.name, fontWeight = FontWeight.Bold)
                    if (b.address.isNotBlank() || b.phone.isNotBlank()) Text("${b.address}  ${b.phone}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
    if (creating) BranchDialog(null, onDismiss = { creating = false }) { scope.launch { db.core().upsertBranch(it) } }
    editing?.let { b -> BranchDialog(b, onDismiss = { editing = null }) { scope.launch { db.core().upsertBranch(it) } } }
}

@Composable
private fun BranchDialog(b: Branch?, onDismiss: () -> Unit, onSave: (Branch) -> Unit) {
    var name by remember { mutableStateOf(b?.name ?: "") }
    var address by remember { mutableStateOf(b?.address ?: "") }
    var phone by remember { mutableStateOf(b?.phone ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (b == null) "فرع جديد" else "تعديل الفرع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("العنوان") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave((b ?: Branch(name = "")).copy(name = name.trim(), address = address.trim(), phone = phone.trim())); onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
