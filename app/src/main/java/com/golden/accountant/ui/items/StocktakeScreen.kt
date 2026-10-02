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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.golden.accountant.data.*
import com.golden.accountant.domain.BillRepository
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.InvoiceMath
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.nav.Routes
import com.golden.accountant.domain.StockService
import com.golden.accountant.domain.TrType
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import kotlinx.coroutines.launch

private data class CountLine(val item: Item, val system: Double, val actual: String = "") {
    val counted: Double? get() = actual.takeIf { it.isNotBlank() }?.let { InvoiceMath.parse(it) }
    val diff: Double get() = counted?.let { Money.r(it - system) } ?: 0.0
}

/**
 * الجرد والتسوية: تُدخل الكمية الفعلية فيُسجَّل مستند تسوية بالفرق فقط.
 * التسوية تغيّر الكمية ولا تُنتج قيداً محاسبياً؛ أثرها على الأرباح يظهر عبر تقييم المخزون في التقارير.
 */
@Composable
fun StocktakeScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val allItems by db.items().observeAll().collectAsState(emptyList())
    val history by remember { db.bills().observe(TrType.ADJUST, false) }.collectAsState(emptyList())
    val lines = remember { mutableStateListOf<CountLine>() }
    var date by remember { mutableStateOf(todayIso()) }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Bill?>(null) }
    val stock = remember { StockService(db) }
    val canNew = Session.can(Routes.STOCKTAKE, Action.NEW); val canDelete = Session.can(Routes.STOCKTAKE, Action.DELETE)
    val changed = lines.filter { it.counted != null && it.diff != 0.0 }

    Scaffold(
        topBar = { GoldTopBar("الجرد والتسوية", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("أصناف بها فرق: ${changed.size}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Button(enabled = canNew && changed.isNotEmpty(), onClick = {
                        scope.launch {
                            val bill = Bill(trType = TrType.ADJUST, billType = BillType.CASH, date = date, time = nowTime(), remarks = note.ifBlank { "جرد وتسوية" }, userId = Session.userId, branchId = Session.branchId)
                            val l = changed.map { BillLine(itemId = it.item.id, unitId = it.item.baseUnitId, unitFactor = 1.0, qty = it.diff, price = 0.0) }
                            runCatching { BillRepository(db).save(bill, l) }
                                .onSuccess { snack.showSnackbar("تم تسجيل التسوية"); lines.clear(); note = "" }
                                .onFailure { snack.showSnackbar(it.message ?: "تعذر الحفظ") }
                        }
                    }) { Text("اعتماد التسوية") }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateButton(date) { date = it }
                    OutlinedButton(onClick = { picking = true }) { Text("+ صنف") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val have = lines.map { it.item.id }.toSet()
                            allItems.filter { it.isActive && it.id !in have }.forEach { lines += CountLine(it, stock.onHand(it.id)) }
                        }
                    }) { Text("كل الأصناف") }
                }
            }
            item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات") }) }
            itemsIndexed(lines, key = { _, l -> l.item.id }) { idx, l ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1.4f)) {
                            Text(l.item.name, fontWeight = FontWeight.Bold)
                            Text("بالنظام: ${money(l.system)}", style = MaterialTheme.typography.bodySmall)
                            if (l.counted != null) Text("الفرق: ${money(l.diff)}", style = MaterialTheme.typography.bodySmall,
                                color = if (l.diff == 0.0) Gold.Primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        OutlinedTextField(l.actual, { lines[idx] = l.copy(actual = it) }, Modifier.weight(1f), label = { Text("الفعلي") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        IconButton(onClick = { lines.removeAt(idx) }) { Icon(Icons.Default.Delete, "إزالة", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)); Text("سجل التسويات السابقة", style = MaterialTheme.typography.titleSmall) }
                items(history, key = { "h${it.id}" }) { b ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = canDelete) { deleting = b }.padding(vertical = 8.dp)) {
                        Text("#${b.billNo}  ${b.date}", Modifier.weight(1f)); Text(b.remarks, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (picking) SimpleItemPicker(allItems.filter { it.isActive }, onPick = { item ->
        if (lines.none { it.item.id == item.id }) scope.launch { lines += CountLine(item, stock.onHand(item.id)) }
    }, onDismiss = { picking = false })

    deleting?.let { b ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("حذف تسوية #${b.billNo}؟") }, text = { Text("سيعود رصيد الأصناف كما كان قبل التسوية.") },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch { runCatching { BillRepository(db).delete(b.id) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
internal fun SimpleItemPicker(all: List<Item>, onPick: (Item) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, all) { all.filter { q.isBlank() || it.name.contains(q.trim(), true) || it.barcode == q.trim() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), label = { Text("بحث") }, singleLine = true)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(shown, key = { it.id }) { i ->
                        Text(i.name, Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 12.dp))
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
