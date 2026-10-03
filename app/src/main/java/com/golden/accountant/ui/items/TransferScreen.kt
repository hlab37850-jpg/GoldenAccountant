package com.golden.accountant.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.golden.accountant.domain.*
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.nav.Routes
import kotlinx.coroutines.launch

private data class TLine(val item: Item, val qty: String = "")

/** تحويل مخزني بين مخزنين (بالوحدة الأساسية). يُمنع إن كان رصيد المخزن المرسل لا يكفي. */
@Composable
fun TransferScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val branches by db.core().observeBranches().collectAsState(emptyList())
    val allItems by db.items().observeAll().collectAsState(emptyList())
    val history by remember { db.bills().observe(TrType.TRANSFER, false) }.collectAsState(emptyList())
    var from by remember { mutableLongStateOf(Session.branchId) }
    var to by remember { mutableLongStateOf(0L) }
    var date by remember { mutableStateOf(todayIso()) }
    var note by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<TLine>() }
    var picking by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf(emptyMap<Long, Double>()) }
    var deleting by remember { mutableStateOf<Bill?>(null) }
    val canNew = Session.can(Routes.TRANSFER, Action.NEW); val canDelete = Session.can(Routes.TRANSFER, Action.DELETE)
    val names = remember(branches) { branches.associate { it.id to it.name } }

    // رصيد المخزن المرسل (الافتتاحي للمخزن 1) — يُحدَّث عند تغيير المخزن وبعد كل تحويل
    LaunchedEffect(from, history.size) {
        val m = db.items().stockByBranch(from).associate { it.itemId to it.qty }
        available = allItems.associate { it.id to Money.r((if (it.openingBranchId == from) it.openingQty else 0.0) + (m[it.id] ?: 0.0)) }
    }

    Scaffold(
        topBar = { GoldTopBar("تحويل مخزني", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("أصناف: ${lines.size}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Button(enabled = canNew && lines.isNotEmpty() && to != 0L, onClick = {
                        scope.launch {
                            runCatching {
                                if (from == to) throw PostingException("اختر مخزنين مختلفين")
                                val l = lines.map { BillLine(itemId = it.item.id, unitId = it.item.baseUnitId, unitFactor = 1.0, qty = InvoiceMath.parse(it.qty), price = 0.0) }
                                l.forEachIndexed { i, x ->
                                    if (x.qty <= 0) throw PostingException("الكمية يجب أن تكون أكبر من صفر: ${lines[i].item.name}")
                                    if (x.qty > (available[x.itemId] ?: 0.0)) throw PostingException("رصيد ${lines[i].item.name} في ${names[from]} لا يكفي (${money(available[x.itemId] ?: 0.0)})")
                                }
                                BillRepository(db).save(Bill(trType = TrType.TRANSFER, billType = BillType.CASH, branchId = from, toBranchId = to, date = date, time = nowTime(), remarks = note.ifBlank { "تحويل مخزني" }, userId = Session.userId), l)
                            }.onSuccess { snack.showSnackbar("تم التحويل"); lines.clear(); note = "" }
                                .onFailure { snack.showSnackbar(it.message ?: "تعذر الحفظ") }
                        }
                    }) { Text("تنفيذ التحويل") }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("من مخزن", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.forEach { b -> FilterChip(from == b.id, { from = b.id; lines.clear() }, { Text(b.name) }) } }
                Text("إلى مخزن", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.filter { it.id != from }.forEach { b -> FilterChip(to == b.id, { to = b.id }, { Text(b.name) }) } }
                if (branches.size < 2) Text("أضف مخزناً ثانياً من شاشة المخازن أولاً.", color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateButton(date) { date = it }
                    OutlinedButton(onClick = { picking = true }) { Text("+ صنف") }
                }
            }
            item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات") }) }
            itemsIndexed(lines, key = { _, l -> l.item.id }) { idx, l ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1.4f)) { Text(l.item.name, fontWeight = FontWeight.Bold); Text("المتاح: ${money(available[l.item.id] ?: 0.0)}", style = MaterialTheme.typography.bodySmall) }
                        OutlinedTextField(l.qty, { lines[idx] = l.copy(qty = it) }, Modifier.weight(1f), label = { Text("الكمية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        IconButton(onClick = { lines.removeAt(idx) }) { Icon(Icons.Default.Delete, "إزالة", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)); Text("سجل التحويلات", style = MaterialTheme.typography.titleSmall) }
                items(history, key = { "t${it.id}" }) { b ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = canDelete) { deleting = b }.padding(vertical = 8.dp)) {
                        Text("#${b.billNo}  ${b.date}", Modifier.weight(1f)); Text("${names[b.branchId] ?: "?"} ← ${names[b.toBranchId] ?: "?"}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (picking) SimpleItemPicker(allItems.filter { it.isActive }, onPick = { i -> if (lines.none { it.item.id == i.id }) lines += TLine(i) }, onDismiss = { picking = false })
    deleting?.let { b ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("حذف التحويل #${b.billNo}؟") }, text = { Text("سترجع الكميات إلى المخزن المرسل.") },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch { runCatching { BillRepository(db).delete(b.id) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } },
        )
    }
}
