package com.golden.accountant.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.*
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.InvoiceMath
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.nav.Routes
import kotlinx.coroutines.launch

/** أسعار البيع لكل صنف بحسب العملة والوحدة وتاريخ السريان (آخر سعر ≤ تاريخ الفاتورة هو المعتمد). */
@Composable
fun ItemPricesScreen(db: AppDatabase, onBack: () -> Unit) {
    val allItems by db.items().observeAll().collectAsState(emptyList())
    var q by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<Item?>(null) }
    val shown = remember(q, allItems) { allItems.filter { q.isBlank() || it.name.contains(q.trim(), true) } }
    Scaffold(topBar = { GoldTopBar("أسعار البيع", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), label = { Text("بحث") }, singleLine = true)
            LazyColumn {
                items(shown, key = { it.id }) { it ->
                    Row(Modifier.fillMaxWidth().clickable { open = it }.padding(16.dp)) {
                        Text(it.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(money(it.salePrice), style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    open?.let { PriceDialog(db, it) { open = null } }
}

@Composable
private fun PriceDialog(db: AppDatabase, item: Item, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    val unitNames by db.core().observeUnits().collectAsState(emptyList())
    val prices by remember(item.id) { db.itemPrices().observeFor(item.id) }.collectAsState(emptyList())
    var units by remember { mutableStateOf(emptyList<ItemUnit>()) }
    var currency by remember { mutableLongStateOf(0L) }
    var unit by remember { mutableLongStateOf(item.baseUnitId) }
    var date by remember { mutableStateOf(todayIso()) }
    var price by remember { mutableStateOf("") }
    val canNew = Session.can(Routes.ITEM_PRICES, Action.NEW); val canDelete = Session.can(Routes.ITEM_PRICES, Action.DELETE)
    LaunchedEffect(item.id) { units = db.items().units(item.id) }
    val uName = { id: Long -> unitNames.firstOrNull { it.id == id }?.name ?: "" }
    val cName = { id: Long -> currencies.firstOrNull { it.id == id }?.name ?: "" }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(item.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canNew) {
                    CurrencyChips(currencies, currency) { currency = it }
                    if (units.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { units.forEach { u -> FilterChip(unit == u.unitId, { unit = u.unitId }, { Text(uName(u.unitId)) }) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DateButton(date) { date = it }
                        OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text("السعر") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Button(onClick = {
                        val p = InvoiceMath.parse(price)
                        if (p > 0) scope.launch { db.itemPrices().upsert(ItemPrice(itemId = item.id, currencyId = currency, unitId = unit, price = p, date = date)); price = "" }
                    }) { Text("إضافة سعر") }
                    HorizontalDivider()
                }
                Text("سجل الأسعار", style = MaterialTheme.typography.labelLarge)
                prices.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.date}  ${cName(p.currencyId)}  ${uName(p.unitId)}  →  ${money(p.price)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        if (canDelete) IconButton(onClick = { scope.launch { db.itemPrices().delete(p.id) } }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                if (prices.isEmpty()) Text("لا توجد أسعار مسجّلة؛ يُستخدم سعر البيع الأساسي للصنف.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
