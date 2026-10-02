package com.golden.accountant.ui.items

import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.golden.accountant.data.*
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.InvoiceMath
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.nav.Routes
import com.golden.accountant.domain.ItemRepository
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import kotlinx.coroutines.launch

private fun num(v: Double) = if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
fun ItemsScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val allItems by db.items().observeAll().collectAsState(emptyList())
    val units by db.core().observeUnits().collectAsState(emptyList())
    val moves by db.items().movementByItem().collectAsState(emptyList())
    val unitNames = remember(units) { units.associate { it.id to it.name } }
    val onHand = remember(allItems, moves) { val m = moves.associate { it.itemId to it.qty }; allItems.associate { it.id to Money.r(it.openingQty + (m[it.id] ?: 0.0)) } }
    var q by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Item?>(null) }
    var creating by remember { mutableStateOf(false) }
    val canNew = Session.can(Routes.ITEMS, Action.NEW); val canEdit = Session.can(Routes.ITEMS, Action.EDIT); val canDelete = Session.can(Routes.ITEMS, Action.DELETE)
    val shown = remember(q, allItems) { allItems.filter { q.isBlank() || it.name.contains(q.trim(), true) || it.barcode == q.trim() } }

    Scaffold(
        topBar = { GoldTopBar("الأصناف (${allItems.size})", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { creating = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "صنف جديد", tint = Color.White) } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), label = { Text("بحث بالاسم أو الباركود") }, singleLine = true)
            LazyColumn {
                items(shown, key = { it.id }) { it ->
                    val qty = onHand[it.id] ?: 0.0
                    Row(Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = it }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(it.name + if (!it.isActive) "  (معطّل)" else "", fontWeight = FontWeight.Bold)
                            Text("سعر البيع ${money(it.salePrice)} / ${unitNames[it.baseUnitId] ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(num(qty).ifEmpty { "0" }, color = if (qty < 0) MaterialTheme.colorScheme.error else Gold.Primary, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
                if (shown.isEmpty()) item { Text("لا توجد أصناف", Modifier.padding(24.dp)) }
            }
        }
    }

    if (creating) ItemEditDialog(null, units, db, onDismiss = { creating = false }, onSave = { i, u -> scope.launch { runCatching { ItemRepository(db).save(i, u) }.onFailure { snack.showSnackbar(it.message ?: "اسم الصنف مستخدم") } } }, onDelete = null)
    editing?.let { item ->
        ItemEditDialog(item, units, db, onDismiss = { editing = null },
            onSave = { i, u -> scope.launch { runCatching { ItemRepository(db).save(i, u) }.onFailure { snack.showSnackbar(it.message ?: "اسم الصنف مستخدم") } } },
            onDelete = if (!canDelete) null else { { scope.launch { runCatching { ItemRepository(db).delete(item) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } } } })
    }
}

private data class UnitRow(val key: Int, val unitId: Long, val factor: String, val price: String)

@Composable
private fun ItemEditDialog(
    item: Item?, units: List<UnitDef>, db: AppDatabase, onDismiss: () -> Unit,
    onSave: (Item, List<ItemUnit>) -> Unit, onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var barcode by remember { mutableStateOf(item?.barcode ?: "") }
    var base by remember { mutableLongStateOf(item?.baseUnitId ?: units.firstOrNull { it.id != 0L }?.id ?: 0L) }
    var salePrice by remember { mutableStateOf(num(item?.salePrice ?: 0.0)) }
    var openQty by remember { mutableStateOf(num(item?.openingQty ?: 0.0)) }
    var openCost by remember { mutableStateOf(num(item?.openingCost ?: 0.0)) }
    var active by remember { mutableStateOf(item?.isActive ?: true) }
    var seq by remember { mutableIntStateOf(0) }
    val extras = remember { mutableStateListOf<UnitRow>() }
    val real = remember(units) { units.filter { it.id != 0L } }

    LaunchedEffect(item?.id) {
        if (item != null) db.items().units(item.id).filter { it.unitId != item.baseUnitId }.forEach { extras += UnitRow(seq++, it.unitId, num(it.factor), num(it.salePrice)) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (item == null) "صنف جديد" else "تعديل الصنف", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم الصنف") }, singleLine = true)
                OutlinedTextField(barcode, { barcode = it }, Modifier.fillMaxWidth(), label = { Text("الباركود (اختياري)") }, singleLine = true)
                Text("الوحدة الأساسية", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { real.forEach { u -> FilterChip(base == u.id, { base = u.id }, { Text(u.name) }) } }
                OutlinedTextField(salePrice, { salePrice = it }, Modifier.fillMaxWidth(), label = { Text("سعر البيع بالوحدة الأساسية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(openQty, { openQty = it }, Modifier.weight(1f), label = { Text("كمية افتتاحية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(openCost, { openCost = it }, Modifier.weight(1f), label = { Text("تكلفة الوحدة") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }

                Text("وحدات إضافية (مثال: كرتون = 12 حبة)", style = MaterialTheme.typography.labelMedium)
                extras.forEachIndexed { i, r ->
                    Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                real.filter { it.id != base }.forEach { u -> FilterChip(r.unitId == u.id, { extras[i] = r.copy(unitId = u.id) }, { Text(u.name) }) }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { extras.removeAt(i) }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(r.factor, { extras[i] = r.copy(factor = it) }, Modifier.weight(1f), label = { Text("= كم من الأساسية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                OutlinedTextField(r.price, { extras[i] = r.copy(price = it) }, Modifier.weight(1f), label = { Text("سعر البيع") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { extras += UnitRow(seq++, real.firstOrNull { it.id != base }?.id ?: 0L, "", "") }) { Text("+ وحدة") }

                Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, { active = it }); Spacer(Modifier.width(8.dp)); Text("الصنف فعّال") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val base0 = item ?: Item(name = "")
                        onSave(
                            base0.copy(name = name.trim(), barcode = barcode.trim().ifEmpty { null }, baseUnitId = base, salePrice = InvoiceMath.parse(salePrice),
                                openingQty = InvoiceMath.parse(openQty), openingCost = InvoiceMath.parse(openCost), isActive = active,
                                openingDate = base0.openingDate.ifEmpty { todayIso() }),
                            extras.filter { it.unitId != 0L }.map { ItemUnit(item?.id ?: 0L, it.unitId, InvoiceMath.parse(it.factor), InvoiceMath.parse(it.price)) },
                        )
                        onDismiss()
                    }) { Text("حفظ") }
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
