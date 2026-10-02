package com.golden.accountant.ui.invoice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.Bill
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.GoldTopBar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import java.util.Locale

/** قائمة فواتير عائلة واحدة (مبيعات أو مشتريات) مع تبديل بين الفاتورة والمرتجع وعرض السعر/الطلب. */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun BillListScreen(
    db: AppDatabase, family: List<InvoiceKind>, title: String,
    onNew: (InvoiceKind) -> Unit, onOpen: (InvoiceKind, Long) -> Unit, onBack: () -> Unit,
) {
    var kind by remember { mutableStateOf(family.first()) }
    val flow = remember(kind) { db.bills().observe(kind.trType, kind.isBack) }
    val bills by flow.collectAsState(initial = emptyList())
    val parties by db.parties().observeAll().collectAsState(initial = emptyList())
    val names = remember(parties) { parties.associate { it.accountId to it.name } }

    Scaffold(
        topBar = { GoldTopBar(title, onBack) },
        floatingActionButton = { FloatingActionButton(onClick = { onNew(kind) }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "جديد", tint = androidx.compose.ui.graphics.Color.White) } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                family.forEach { k -> FilterChip(kind == k, { kind = k }, { Text(k.title) }) }
            }
            if (bills.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد مستندات") }
            } else {
                Text("العدد: ${bills.size}   الإجمالي: ${money(bills.sumOf { it.total })}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
                LazyColumn {
                    items(bills, key = { it.id }) { b -> BillRow(b, names[b.partyAccountId]) { onOpen(kind, b.id) } }
                }
            }
        }
    }
}

private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)

@Composable
private fun BillRow(b: Bill, partyName: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("#${b.billNo}  ${partyName ?: "نقدي"}", fontWeight = FontWeight.Bold)
            Text(b.date, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(b.total), color = Gold.Primary, fontWeight = FontWeight.Bold)
            if (b.paid < b.total && b.billType == 2) Text("متبقي ${money(b.total - b.paid)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider()
}
