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
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.nav.Routes
import kotlinx.coroutines.launch

private data class ALine(val item: Item, val qty: String = "")

private val adjNames = linkedMapOf(AdjType.SHORTAGE to "عجز", AdjType.SURPLUS to "زيادة", AdjType.DAMAGED to "تالف")

/** تسوية مخزنية بنوعها: عجز وتالف يُنقصان الرصيد، وزيادة تُضيفه. (الأثر على الأرباح عبر تقييم المخزون.) */
@Composable
fun AdjustScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val allItems by db.items().observeAll().collectAsState(emptyList())
    val history by remember { db.bills().observe(TrType.ADJUST, false) }.collectAsState(emptyList())
    val lines = remember { mutableStateListOf<ALine>() }
    var type by remember { mutableIntStateOf(AdjType.SHORTAGE) }
    var date by remember { mutableStateOf(todayIso()) }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Bill?>(null) }
    val canNew = Session.can(Routes.ADJUST, Action.NEW); val canDelete = Session.can(Routes.ADJUST, Action.DELETE)

    Scaffold(
        topBar = { GoldTopBar("تسوية مخزنية", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("أصناف: ${lines.size}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Button(enabled = canNew && lines.isNotEmpty(), onClick = {
                        scope.launch {
                            runCatching {
                                val sign = if (type == AdjType.SURPLUS) 1.0 else -1.0
                                val l = lines.map { x ->
                                    val q = InvoiceMath.parse(x.qty)
                                    if (q <= 0) throw PostingException("الكمية يجب أن تكون أكبر من صفر: ${x.item.name}")
                                    BillLine(itemId = x.item.id, unitId = x.item.baseUnitId, unitFactor = 1.0, qty = q * sign, price = 0.0)
                                }
                                BillRepository(db).save(Bill(trType = TrType.ADJUST, billType = BillType.CASH, adjType = type, date = date, time = nowTime(),
                                    remarks = (adjNames[type] ?: "") + (if (note.isBlank()) "" else " - $note"), userId = Session.userId, branchId = Session.branchId), l)
                            }.onSuccess { snack.showSnackbar("تم تسجيل التسوية"); lines.clear(); note = "" }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحفظ") }
                        }
                    }) { Text("اعتماد") }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { adjNames.forEach { (t, n) -> FilterChip(type == t, { type = t }, { Text(n) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateButton(date) { date = it }
                    OutlinedButton(onClick = { picking = true }) { Text("+ صنف") }
                }
            }
            item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات") }) }
            itemsIndexed(lines, key = { _, l -> l.item.id }) { idx, l ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(l.item.name, Modifier.weight(1.4f), fontWeight = FontWeight.Bold)
                        OutlinedTextField(l.qty, { lines[idx] = l.copy(qty = it) }, Modifier.weight(1f), label = { Text("الكمية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        IconButton(onClick = { lines.removeAt(idx) }) { Icon(Icons.Default.Delete, "إزالة", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)); Text("سجل التسويات", style = MaterialTheme.typography.titleSmall) }
                items(history, key = { "a${it.id}" }) { b ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = canDelete) { deleting = b }.padding(vertical = 8.dp)) {
                        Text("#${b.billNo}  ${b.date}", Modifier.weight(1f)); Text(b.remarks, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    if (picking) SimpleItemPicker(allItems.filter { it.isActive }, onPick = { i -> if (lines.none { it.item.id == i.id }) lines += ALine(i) }, onDismiss = { picking = false })
    deleting?.let { b ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("حذف تسوية #${b.billNo}؟") }, text = { Text("سيعود رصيد الأصناف كما كان.") },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch { runCatching { BillRepository(db).delete(b.id) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } },
        )
    }
}
