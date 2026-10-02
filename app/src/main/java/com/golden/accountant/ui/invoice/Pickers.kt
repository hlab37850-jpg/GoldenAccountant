package com.golden.accountant.ui.invoice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.golden.accountant.data.Item
import com.golden.accountant.data.Party
import com.golden.accountant.data.UnitDef
import com.golden.accountant.domain.InvoiceMath

@Composable
fun ItemPickerDialog(allItems: List<Item>, units: List<UnitDef>, onPick: (Item) -> Unit, onCreate: (String, Double, Long) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val shown = remember(q, allItems) { allItems.filter { it.isActive && (q.isBlank() || it.name.contains(q.trim(), true) || it.barcode == q.trim()) } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
                Text("اختيار صنف", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("بحث بالاسم أو الباركود") }, singleLine = true)
                if (adding) {
                    QuickAddItem(units, onSave = { n, p, u -> onCreate(n, p, u); onDismiss() }, onCancel = { adding = false })
                } else {
                    TextButton(onClick = { adding = true }) { Text("+ صنف جديد") }
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(shown, key = { it.id }) { it ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(it); onDismiss() }.padding(vertical = 10.dp)) {
                                Text(it.name)
                                if (it.salePrice > 0) Text("السعر: ${it.salePrice}", style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                        }
                        if (shown.isEmpty()) item { Text("لا توجد نتائج", Modifier.padding(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddItem(units: List<UnitDef>, onSave: (String, Double, Long) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var unit by remember { mutableLongStateOf(units.firstOrNull { it.id != 0L }?.id ?: 0L) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم الصنف") }, singleLine = true)
        OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("سعر البيع") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            units.forEach { u -> FilterChip(selected = unit == u.id, onClick = { unit = u.id }, label = { Text(u.name) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(name, InvoiceMath.parse(price), unit) }) { Text("حفظ وإضافة") }
            TextButton(onClick = onCancel) { Text("رجوع") }
        }
    }
}

@Composable
fun PartyPickerDialog(title: String, parties: List<Party>, onPick: (Party) -> Unit, onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val shown = remember(q, parties) { parties.filter { q.isBlank() || it.name.contains(q.trim(), true) || it.phone.contains(q.trim()) } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (adding) {
                    var name by remember { mutableStateOf("") }
                    var phone by remember { mutableStateOf("") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("الاسم") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("الهاتف (اختياري)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onCreate(name, phone); onDismiss() }) { Text("حفظ") }
                        TextButton(onClick = { adding = false }) { Text("رجوع") }
                    }
                } else {
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("بحث") }, singleLine = true)
                    TextButton(onClick = { adding = true }) { Text("+ جديد") }
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(shown, key = { it.id }) { p ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(p); onDismiss() }.padding(vertical = 10.dp)) {
                                Text(p.name)
                                if (p.phone.isNotBlank()) Text(p.phone, style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                        }
                        if (shown.isEmpty()) item { Text("لا توجد نتائج", Modifier.padding(12.dp)) }
                    }
                }
            }
        }
    }
}
