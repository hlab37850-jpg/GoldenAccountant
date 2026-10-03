package com.golden.accountant.ui.invoice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.BillType
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.Session
import com.golden.accountant.domain.Settings
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.report.PdfDoc
import com.golden.accountant.ui.report.ShareAction

@Composable
fun InvoiceScreen(db: AppDatabase, kind: InvoiceKind, billId: Long, onBack: () -> Unit) {
    val vm: InvoiceViewModel = viewModel(
        key = "inv-${kind.route}-$billId",
        factory = viewModelFactory { initializer { InvoiceViewModel(db, kind, billId) } },
    )
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    LaunchedEffect(vm.leave) { if (vm.leave) onBack() }

    val allItems by vm.items.collectAsState()
    val parties by vm.parties.collectAsState()
    val units by vm.units.collectAsState()
    val taxes by vm.taxes.collectAsState()
    val currencies by vm.currencies.collectAsState()
    val warehouses by vm.warehouses.collectAsState()
    val cashAccounts by vm.cashAccounts.collectAsState()
    val accounts by vm.allAccounts.collectAsState()
    var showItems by remember { mutableStateOf(false) }
    var showParties by remember { mutableStateOf(false) }
    var showCounter by remember { mutableStateOf(false) }
    var showCash by remember { mutableStateOf(false) }
    var showCost by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val canSave = Session.can(kind.permRoute, if (vm.existingId > 0) Action.EDIT else Action.NEW)
    val canDelete = Session.can(kind.permRoute, Action.DELETE)
    val t = vm.totals
    val partyWord = if (kind.isSales) "العميل" else "المورد"

    val invoiceDoc = if (vm.existingId > 0 && vm.lines.isNotEmpty()) PdfDoc(
        "${kind.title} #${vm.billNo}",
        listOfNotNull("التاريخ: ${vm.date}", vm.party?.let { "$partyWord: ${it.name}" }, vm.counter?.let { "الحساب: ${it.name}" }, "النوع: ${if (vm.billType == BillType.CASH) "نقد" else "آجل"}"),
        listOf("الصنف", "الوحدة", "الكمية", "السعر", "المبلغ"), listOf(3f, 1f, 1f, 1.2f, 1.4f),
        vm.lines.map { l -> val b = l.toBillLine(); listOf(l.item.name, l.units.firstOrNull { it.unitId == l.unitId }?.name ?: "", l.qtyText, l.priceText, money(b.lineTotal)) },
        buildList {
            add("المجموع: ${money(t.subtotal)}"); if (t.discount > 0) add("الخصم: ${money(t.discount)}"); if (t.tax > 0) add("الضريبة: ${money(t.tax)}")
            if (t.extra > 0) add("رسوم: ${money(t.extra)}"); add("الإجمالي: ${money(t.total)}")
            if (vm.billType == BillType.CREDIT && kind.posts && !kind.isStock) { add("المدفوع: ${money(t.paid)}"); add("المتبقي: ${money(t.remaining)}") }
        },
    ) else null

    Scaffold(
        topBar = {
            GoldTopBar(kind.title + if (vm.billNo > 0) "  #${vm.billNo}" else "", onBack) {
                ShareAction(invoiceDoc, "${kind.route}_${vm.billNo}")
                if (vm.existingId > 0 && canDelete) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "حذف") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الإجمالي", style = MaterialTheme.typography.labelMedium)
                        Text(money(t.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold.Primary)
                    }
                    Button(onClick = vm::save, enabled = !vm.saving && canSave, shape = RoundedCornerShape(12.dp)) { Text(if (vm.existingId > 0) "تحديث" else "حفظ") }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // رأس الفاتورة: تاريخ، نقد/آجل، عملة وسعرها، مخزن
            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateButton(vm.date) { vm.date = it }
                    if (kind.posts && !kind.isStock) {
                        FilterChip(vm.billType == BillType.CASH, { vm.billType = BillType.CASH }, { Text("نقد") })
                        FilterChip(vm.billType == BillType.CREDIT, { vm.billType = BillType.CREDIT }, { Text("آجل") })
                    }
                }
                if (currencies.size > 1) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CurrencyChips(currencies, vm.currencyId) { vm.changeCurrency(it) }
                    if (vm.currencyId != 0L) OutlinedTextField(vm.rateText, { vm.rateText = it }, Modifier.width(120.dp), label = { Text("سعر الصرف") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (warehouses.size > 1) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("المخزن", style = MaterialTheme.typography.labelMedium)
                    warehouses.forEach { w -> FilterChip(vm.branchId == w.id, { if (vm.existingId == 0L) vm.branchId = w.id }, { Text(w.name) }) }
                }
            }
            // الطرف المقابل
            item {
                if (kind.isStock) PickerCard(vm.counter?.name ?: "اختر الحساب المقابل", onClick = { showCounter = true }, onClear = null)
                else if (kind.posts || kind.trType == com.golden.accountant.domain.TrType.QUOTE || kind.trType == com.golden.accountant.domain.TrType.PURCHASE_ORDER)
                    PickerCard(vm.party?.name ?: ("اختر $partyWord" + if (vm.billType == BillType.CASH) " (اختياري)" else ""), onClick = { showParties = true }, onClear = if (vm.party != null) ({ vm.party = null }) else null)
            }
            if (kind.isBack) item { OutlinedTextField(vm.refNo, { vm.refNo = it }, Modifier.fillMaxWidth(), label = { Text("رقم الفاتورة الأصلية") }, singleLine = true) }
            if (vm.showCashRow) item { PickerCard("الصندوق: ${vm.cashAccount?.name ?: "اختر"}", onClick = { showCash = true }, onClear = null) }

            // الأصناف
            itemsIndexed(vm.lines, key = { _, l -> l.key }) { idx, line ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Gold.Light)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(line.item.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { vm.removeLine(idx) }) { Icon(Icons.Default.Delete, "حذف السطر", tint = MaterialTheme.colorScheme.error) }
                        }
                        if (line.units.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            line.units.forEach { u -> FilterChip(line.unitId == u.unitId, { vm.changeUnit(idx, u) }, { Text(u.name) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("الكمية", line.qtyText, Modifier.weight(1f)) { v -> vm.updateLine(idx) { it.copy(qtyText = v) } }
                            NumField("السعر", line.priceText, Modifier.weight(1f)) { v -> vm.updateLine(idx) { it.copy(priceText = v) } }
                            NumField("خصم", line.discountText, Modifier.weight(0.8f)) { v -> vm.updateLine(idx) { it.copy(discountText = v) } }
                        }
                        if (Settings.showEndDate) OutlinedTextField(line.expiryText, { v -> vm.updateLine(idx) { it.copy(expiryText = v) } }, Modifier.fillMaxWidth(), label = { Text("تاريخ الانتهاء (yyyy-mm-dd)") }, singleLine = true)
                        Text("المبلغ: ${money(line.toBillLine().lineTotal)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { OutlinedButton(onClick = { showItems = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة صنف") } }

            // الإجماليات: خصم (نسبة/مبلغ)، ضريبة، رسوم، مدفوع
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SumRow("المجموع", money(t.subtotal))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumField("الخصم", vm.discountText, Modifier.weight(1f)) { vm.discountText = it }
                            FilterChip(vm.discountType == 0, { vm.discountType = 0 }, { Text("%") })
                            FilterChip(vm.discountType == 1, { vm.discountType = 1 }, { Text("مبلغ") })
                        }
                        if (t.discount > 0) SumRow("قيمة الخصم", money(t.discount))
                        if (Settings.vatEnabled && taxes.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("الضريبة", Modifier.padding(end = 4.dp))
                                taxes.forEach { tx -> FilterChip(vm.taxId == tx.id, { vm.applyTax(tx) }, { Text("${tx.name} ${tx.percent}%" + if (tx.percent > 0) (if (tx.included) " متضمن" else "") else "") }) }
                            }
                            if (vm.taxPercent > 0) SumRow(if (vm.taxIncluded) "منها ضريبة" else "قيمة الضريبة", money(t.tax))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumField("رسوم", vm.extraText, Modifier.weight(1f)) { vm.extraText = it }
                            OutlinedButton(onClick = { showCost = true }) { Text(vm.costAccount?.name ?: "حساب الرسوم") }
                        }
                        HorizontalDivider()
                        SumRow("الإجمالي", money(t.total), bold = true)
                        if (kind.posts && !kind.isStock && vm.billType == BillType.CREDIT) {
                            NumField("المدفوع", vm.paidText, Modifier.fillMaxWidth()) { vm.paidText = it }
                            SumRow("المتبقي على $partyWord", money(t.remaining), bold = true)
                        }
                        OutlinedTextField(vm.remarks, { vm.remarks = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات") })
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showItems) ItemPickerDialog(allItems, units, onPick = vm::addItem, onCreate = vm::createItem, onDismiss = { showItems = false })
    if (showParties) PartyPickerDialog("اختيار $partyWord", parties, onPick = { vm.party = it }, onCreate = vm::createParty, onDismiss = { showParties = false })
    if (showCounter) AccountPickerDialog("الحساب المقابل", accounts, onPick = { vm.counter = it }, onDismiss = { showCounter = false })
    if (showCash) AccountPickerDialog("الصندوق", cashAccounts, onPick = { vm.cashAccount = it }, onDismiss = { showCash = false })
    if (showCost) AccountPickerDialog("حساب الرسوم", accounts, onPick = { vm.costAccount = it }, onDismiss = { showCost = false })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف الفاتورة؟") }, text = { Text("سيتم حذف الفاتورة وقيودها المحاسبية نهائياً.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
    )
}

@Composable
private fun PickerCard(text: String, onClick: () -> Unit, onClear: (() -> Unit)?) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick), border = BorderStroke(1.dp, Gold.Light)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f))
            if (onClear != null) TextButton(onClick = onClear) { Text("مسح") }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) = OutlinedTextField(
    value, onChange, modifier, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
)

@Composable
private fun SumRow(label: String, value: String, bold: Boolean = false) = Row(Modifier.fillMaxWidth()) {
    Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
    Text(value, fontWeight = if (bold) FontWeight.Bold else null)
}
