package com.golden.accountant.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.golden.accountant.data.Account
import com.golden.accountant.data.Currency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)
fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
fun nowTime(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

/** نص الرصيد: موجب = عليه (مدين)، سالب = له (دائن). */
fun balanceText(b: Double): String = when {
    b > 0.00005 -> "عليه ${money(b)}"
    b < -0.00005 -> "له ${money(-b)}"
    else -> "0.00"
}

@Composable
fun CurrencyChips(currencies: List<Currency>, selected: Long, onSelect: (Long) -> Unit) {
    if (currencies.size < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencies.forEach { c -> FilterChip(selected == c.id, { onSelect(c.id) }, { Text(c.name) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier) { Text(date) }
    if (show) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it)))
                    }
                    show = false
                }) { Text("تم") }
            },
        ) { DatePicker(state) }
    }
}

/** اختيار حساب من قائمة جاهزة مع بحث. */
@Composable
fun AccountPickerDialog(title: String, accounts: List<Account>, onPick: (Account) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, accounts) { accounts.filter { q.isBlank() || it.name.contains(q.trim(), true) } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("بحث") }, singleLine = true)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(shown, key = { it.id }) { a ->
                        Text(a.name, Modifier.fillMaxWidth().clickable { onPick(a); onDismiss() }.padding(vertical = 12.dp))
                        HorizontalDivider()
                    }
                    if (shown.isEmpty()) item { Text("لا توجد نتائج", Modifier.padding(12.dp)) }
                }
            }
        }
    }
}
