package com.golden.accountant.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
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
import com.golden.accountant.data.Account
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.JournalLine
import com.golden.accountant.data.JournalSummary
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.InvoiceMath
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.nav.Routes
import com.golden.accountant.domain.JournalRepository
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun JournalListScreen(db: AppDatabase, opening: Boolean, onNew: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val allEntries by db.journal().observeManual().collectAsState(emptyList())
    val entries = remember(allEntries, opening) { allEntries.filter { (it.kind == com.golden.accountant.domain.TrType.OPENING) == opening } }
    val accounts by db.accounts().observeAll().collectAsState(emptyList())
    val names = remember(accounts) { accounts.associate { it.id to it.name } }
    var open by remember { mutableStateOf<JournalSummary?>(null) }
    val permRoute = if (opening) Routes.OPENING_ENTRY else Routes.JOURNAL
    val canNew = Session.can(permRoute, Action.NEW); val canDelete = Session.can(permRoute, Action.DELETE)
    var openLines by remember { mutableStateOf(emptyList<JournalLine>()) }
    LaunchedEffect(open) { openLines = open?.let { db.journal().linesOf(it.id) } ?: emptyList() }

    Scaffold(
        topBar = { GoldTopBar(if (opening) "القيود الافتتاحية" else "قيود اليومية", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = onNew, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "قيد جديد", tint = Color.White) } },
    ) { pad ->
        if (entries.isEmpty()) Box(Modifier.fillMaxSize().padding(pad).imePadding(), contentAlignment = Alignment.Center) { Text("لا توجد قيود يدوية") }
        else LazyColumn(Modifier.fillMaxSize().padding(pad).imePadding()) {
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().clickable { open = e }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(e.note, fontWeight = FontWeight.Bold); Text(e.date, style = MaterialTheme.typography.bodySmall) }
                    Text(money(e.total), color = Gold.Primary, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
        }
    }

    open?.let { e ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text("${e.note} — ${e.date}") },
            text = {
                Column {
                    openLines.forEach { l ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(names[l.accountId] ?: "?", Modifier.weight(1f))
                            Text(if (l.debit > 0) "مدين ${money(l.debit)}" else "دائن ${money(l.credit)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("إغلاق") } },
            dismissButton = {
                if (canDelete) TextButton(onClick = {
                    val id = e.id; open = null
                    scope.launch { runCatching { JournalRepository(db).delete(id) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

private data class JLine(val key: Int, val account: Account? = null, val debit: String = "", val credit: String = "")

@Composable
fun JournalEntryScreen(db: AppDatabase, opening: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val leaves by db.accounts().observeLeaves().collectAsState(emptyList())
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    var date by remember { mutableStateOf(todayIso()) }
    var note by remember { mutableStateOf("") }
    var currency by remember { mutableLongStateOf(0L) }
    var seq by remember { mutableIntStateOf(2) }
    val lines = remember { mutableStateListOf(JLine(0), JLine(1)) }
    var pickFor by remember { mutableStateOf(-1) }

    val dr = lines.sumOf { InvoiceMath.parse(it.debit) }; val cr = lines.sumOf { InvoiceMath.parse(it.credit) }
    val diff = Math.round((dr - cr) * 10000.0) / 10000.0
    val balanced = diff == 0.0 && dr > 0

    Scaffold(
        topBar = { GoldTopBar(if (opening) "قيد افتتاحي جديد" else "قيد يومية جديد", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مدين ${money(dr)}   دائن ${money(cr)}", style = MaterialTheme.typography.bodySmall)
                        Text(if (balanced) "القيد متوازن" else "الفرق: ${money(diff)}", color = if (balanced) Gold.Primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    Button(enabled = balanced, onClick = {
                        scope.launch {
                            val data = lines.filter { it.account != null || it.debit.isNotBlank() || it.credit.isNotBlank() }
                                .map { JournalLine(accountId = it.account?.id ?: 0L, debit = InvoiceMath.parse(it.debit), credit = InvoiceMath.parse(it.credit)) }
                            runCatching { JournalRepository(db).saveManual(date, note, currency, data, opening) }
                                .onSuccess { onBack() }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحفظ") }
                        }
                    }) { Text("حفظ القيد") }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).imePadding().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateButton(date) { date = it }
                    CurrencyChips(currencies, currency) { currency = it }
                }
            }
            item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("البيان") }) }
            itemsIndexed(lines, key = { _, l -> l.key }) { idx, l ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { pickFor = idx }, Modifier.weight(1f)) { Text(l.account?.name ?: "اختر الحساب") }
                            if (lines.size > 2) IconButton(onClick = { lines.removeAt(idx) }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(l.debit, { v -> lines[idx] = l.copy(debit = v, credit = if (v.isNotBlank()) "" else l.credit) }, Modifier.weight(1f), label = { Text("مدين") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(l.credit, { v -> lines[idx] = l.copy(credit = v, debit = if (v.isNotBlank()) "" else l.debit) }, Modifier.weight(1f), label = { Text("دائن") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { lines += JLine(seq++) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة سطر") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (pickFor >= 0) AccountPickerDialog("اختيار حساب", leaves, onPick = { a -> lines[pickFor] = lines[pickFor].copy(account = a) }, onDismiss = { pickFor = -1 })
}
