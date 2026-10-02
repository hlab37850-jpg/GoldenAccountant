package com.golden.accountant.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.Party
import com.golden.accountant.domain.AccountRepository
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.nav.Routes
import com.golden.accountant.domain.InvoiceMath
import com.golden.accountant.domain.PartyRepository
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun PartiesScreen(db: AppDatabase, kind: Int, title: String, onStatement: (Long) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val parties by db.parties().observeByKinds(listOf(kind, Party.KIND_BOTH)).collectAsState(emptyList())
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    var currency by rememberSaveable { mutableLongStateOf(0L) }
    val balances by remember(currency) { db.accounts().balances(currency) }.collectAsState(emptyList())
    val bal = remember(balances) { balances.associate { it.accountId to it.balance } }
    var q by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Party?>(null) }
    var creating by remember { mutableStateOf(false) }
    val route = if (kind == Party.KIND_SUPPLIER) Routes.SUPPLIERS else Routes.CUSTOMERS
    val canNew = Session.can(route, Action.NEW); val canEdit = Session.can(route, Action.EDIT); val canDelete = Session.can(route, Action.DELETE)
    val shown = remember(q, parties) { parties.filter { q.isBlank() || it.name.contains(q.trim(), true) || it.phone.contains(q.trim()) } }
    val debit = shown.sumOf { (bal[it.accountId] ?: 0.0).coerceAtLeast(0.0) }
    val credit = shown.sumOf { (-(bal[it.accountId] ?: 0.0)).coerceAtLeast(0.0) }

    Scaffold(
        topBar = { GoldTopBar(title, onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { creating = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "جديد", tint = Color.White) } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("بحث بالاسم أو الهاتف") }, singleLine = true)
                CurrencyChips(currencies, currency) { currency = it }
                Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("إجمالي عليهم: ${money(debit)}", Modifier.weight(1f))
                        Text("إجمالي لهم: ${money(credit)}")
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { p ->
                    val b = bal[p.accountId] ?: 0.0
                    Row(Modifier.fillMaxWidth().clickable { onStatement(p.accountId) }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            if (p.phone.isNotBlank()) Text(p.phone, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(balanceText(b), color = if (b > 0) MaterialTheme.colorScheme.error else Gold.Primary, fontWeight = FontWeight.Bold)
                        if (canEdit) IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "تعديل") }
                    }
                    HorizontalDivider()
                }
                if (shown.isEmpty()) item { Text("لا توجد نتائج", Modifier.padding(24.dp)) }
            }
        }
    }

    if (creating) PartyEditDialog(null, onDismiss = { creating = false },
        onSave = { n, ph, ad, lim, _ ->
            scope.launch {
                runCatching { PartyRepository(db).create(n, kind, ph, ad, lim) }
                    .onFailure { snack.showSnackbar("تعذر الحفظ: الاسم مستخدم أو فارغ") }
            }
        }, onDelete = null)
    editing?.let { p ->
        PartyEditDialog(p, onDismiss = { editing = null },
            onSave = { n, ph, ad, lim, _ ->
                scope.launch { runCatching { AccountRepository(db).updateParty(p.copy(name = n.trim(), phone = ph, address = ad, creditLimit = lim)) }.onFailure { snack.showSnackbar("تعذر الحفظ: الاسم مستخدم") } }
            },
            onDelete = if (!canDelete) null else { { scope.launch { runCatching { AccountRepository(db).deleteParty(p) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } } } })
    }
}

@Composable
private fun PartyEditDialog(
    p: Party?, onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Unit) -> Unit, onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(p?.name ?: "") }
    var phone by remember { mutableStateOf(p?.phone ?: "") }
    var address by remember { mutableStateOf(p?.address ?: "") }
    var limit by remember { mutableStateOf(if ((p?.creditLimit ?: 0.0) > 0) p!!.creditLimit.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p == null) "إضافة" else "تعديل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(address, { address = it }, label = { Text("العنوان") }, singleLine = true)
                OutlinedTextField(limit, { limit = it }, label = { Text("سقف الدين (0 = بلا سقف)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, phone, address, InvoiceMath.parse(limit), Unit); onDismiss() }) { Text("حفظ") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        },
    )
}
