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
import com.golden.accountant.ui.common.CurrencyChips
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.GoldTopBar
import com.golden.accountant.ui.report.PdfDoc
import com.golden.accountant.ui.report.ShareAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)

@OptIn(ExperimentalMaterial3Api::class)
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
    val canSave = Session.can(kind.route, if (vm.existingId > 0) Action.EDIT else Action.NEW)
    val canDelete = Session.can(kind.route, Action.DELETE)
    var showItems by remember { mutableStateOf(false) }
    var showParties by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val t = vm.totals
    val partyWord = if (kind.isSales) "العميل" else "المورد"
    val invoiceDoc = if (vm.existingId > 0 && vm.lines.isNotEmpty()) PdfDoc(
        "${kind.title} #${vm.billNo}",
        listOfNotNull("التاريخ: ${vm.date}", vm.party?.let { "$partyWord: ${it.name}" }, "النوع: ${if (vm.billType == BillType.CASH) "نقد" else "آجل"}"),
        listOf("الصنف", "الوحدة", "الكمية", "السعر", "المبلغ"), listOf(3f, 1f, 1f, 1.2f, 1.4f),
        vm.lines.map { l -> val b = l.toBillLine(); listOf(l.item.name, l.units.firstOrNull { it.unitId == l.unitId }?.name ?: "", l.qtyText, l.priceText, money(b.lineTotal)) },
        buildList {
            add("المجموع: ${money(t.subtotal)}"); if (t.discount > 0) add("الخصم: ${money(t.discount)}"); if (t.tax > 0) add("الضريبة: ${money(t.tax)}")
            if (t.extra > 0) add("رسوم: ${money(t.extra)}"); add("الإجمالي: ${money(t.total)}")
            if (vm.billType == BillType.CREDIT && kind.posts) { add("المدفوع: ${money(t.paid)}"); add("المتبقي: ${money(t.remaining)}") }
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
                    Button(onClick = vm::save, enabled = !vm.saving && canSave, shape = RoundedCornerShape(12.dp)) {
                        Text(if (vm.existingId > 0) "تحديث" else "حفظ الفاتورة")
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // التاريخ ونوع الفاتورة
            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDate = true }) { Text(vm.date) }
                    CurrencyChips(currencies, vm.currencyId) { vm.currencyId = it }
                    if (kind.posts) {
                        FilterChip(vm.billType == BillType.CASH, { vm.billType = BillType.CASH }, { Text("نقد") })
                        FilterChip(vm.billType == BillType.CREDIT, { vm.billType = BillType.CREDIT }, { Text("آجل") })
                    }
                }
            }
            // الطرف
            item {
                OutlinedCard(Modifier.fillMaxWidth().clickable { showParties = true }, border = BorderStroke(1.dp, Gold.Light)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.party?.name ?: "اختر $partyWord" + if (vm.billType == BillType.CASH) " (اختياري)" else "", Modifier.weight(1f))
                        if (vm.party != null) TextButton(onClick = { vm.party = null }) { Text("مسح") }
                    }
                }
            }
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
                        Text("المبلغ: ${money(line.toBillLine().lineTotal)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showItems = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة صنف")
                }
            }
            // الإجماليات
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SumRow("المجموع", money(t.subtotal))
                        NumField("الخصم", vm.discountText, Modifier.fillMaxWidth()) { vm.discountText = it }
                        if (taxes.size > 1 || vm.taxPercent > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("الضريبة", Modifier.padding(end = 4.dp))
                            taxes.forEach { tx -> FilterChip(vm.taxPercent == tx.percent, { vm.taxPercent = tx.percent }, { Text("${tx.name} ${tx.percent}%") }) }
                        }
                        if (vm.taxPercent > 0) SumRow("قيمة الضريبة", money(t.tax))
                        NumField("رسوم إضافية", vm.extraText, Modifier.fillMaxWidth()) { vm.extraText = it }
                        HorizontalDivider()
                        SumRow("الإجمالي", money(t.total), bold = true)
                        if (kind.posts && vm.billType == BillType.CREDIT) {
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
    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it)) }
                    showDate = false
                }) { Text("تم") }
            },
        ) { DatePicker(state) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف الفاتورة؟") }, text = { Text("سيتم حذف الفاتورة وقيودها المحاسبية نهائياً.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
    )
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) = OutlinedTextField(
    value, onChange, modifier, label = { Text(label) }, singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
)

@Composable
private fun SumRow(label: String, value: String, bold: Boolean = false) = Row(Modifier.fillMaxWidth()) {
    Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
    Text(value, fontWeight = if (bold) FontWeight.Bold else null)
}
