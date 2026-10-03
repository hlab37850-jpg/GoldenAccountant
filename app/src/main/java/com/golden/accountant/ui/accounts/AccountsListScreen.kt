package com.golden.accountant.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.Account
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.domain.AccType
import com.golden.accountant.domain.AccountRepository
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.nav.Routes
import kotlinx.coroutines.launch

val accTypeNames = linkedMapOf(
    AccType.CUSTOMER to "عملاء", AccType.SUPPLIER to "موردون", AccType.TRADE to "مبيعات ومشتريات",
    AccType.CASH to "نقدية", AccType.EXPENSE to "مصروفات", AccType.REVENUE to "إيرادات", AccType.OTHER to "أخرى",
)

/** شاشة «الحسابات»: كل الحسابات الفرعية بأنواعها وأرصدتها، مع فلترة بالنوع وإضافة حساب بنوعه. */
@Composable
fun AccountsListScreen(db: AppDatabase, openAdd: Boolean, onStatement: (Long) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val leaves by db.accounts().observeLeaves().collectAsState(emptyList())
    val groups by db.accounts().observeAll().collectAsState(emptyList())
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    var currency by rememberSaveable { mutableLongStateOf(0L) }
    val balances by remember(currency) { db.accounts().balances(currency) }.collectAsState(emptyList())
    val bal = remember(balances) { balances.associate { it.accountId to it.balance } }
    var typeFilter by rememberSaveable { mutableIntStateOf(-1) }
    var q by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(openAdd) }
    val canNew = Session.can(if (openAdd) Routes.ADD_ACCOUNT else Routes.ACCOUNTS_LIST, Action.NEW) || Session.can(Routes.ADD_ACCOUNT, Action.NEW)
    val shown = remember(leaves, q, typeFilter) { leaves.filter { (typeFilter == -1 || it.type == typeFilter) && (q.isBlank() || it.name.contains(q.trim(), true)) } }

    Scaffold(
        topBar = { GoldTopBar(if (openAdd) "إضافة حساب" else "الحسابات", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { adding = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "حساب جديد", tint = Color.White) } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("بحث") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(typeFilter == -1, { typeFilter = -1 }, { Text("الكل") })
                    accTypeNames.forEach { (t, n) -> FilterChip(typeFilter == t, { typeFilter = t }, { Text(n) }) }
                }
                CurrencyChips(currencies, currency) { currency = it }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { a ->
                    val b = bal[a.id] ?: 0.0
                    Row(Modifier.fillMaxWidth().clickable { onStatement(a.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.Bold)
                            Text(accTypeNames[a.type] ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(balanceText(b), color = if (b > 0) MaterialTheme.colorScheme.error else Gold.Primary, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
                if (shown.isEmpty()) item { Text("لا توجد حسابات", Modifier.padding(24.dp)) }
            }
        }
    }

    if (adding) AddAccountDialog(groups.filter { it.isGroup }, onDismiss = { adding = false }) { name, type, parent ->
        scope.launch { runCatching { AccountRepository(db).addAccount(name, type, parent) }.onFailure { snack.showSnackbar(it.message ?: "الاسم مستخدم") } }
    }
}

/** الحساب الأب يُقترح تلقائياً بحسب النوع ويمكن تغييره. */
@Composable
fun AddAccountDialog(groups: List<Account>, onDismiss: () -> Unit, onSave: (String, Int, Account) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(AccType.CUSTOMER) }
    var parent by remember { mutableStateOf<Account?>(null) }
    var pick by remember { mutableStateOf(false) }
    val defaultParentId = defaultParentFor(type)
    val effective = parent ?: groups.firstOrNull { it.id == defaultParentId }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("حساب جديد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الحساب") }, singleLine = true)
                Text("النوع", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accTypeNames.forEach { (t, n) -> FilterChip(type == t, { type = t; parent = null }, { Text(n) }) }
                }
                OutlinedButton(onClick = { pick = true }) { Text("تحت: ${effective?.name ?: "اختر"}") }
            }
        },
        confirmButton = { TextButton(onClick = { effective?.let { onSave(name, type, it) }; onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (pick) AccountPickerDialog("الحساب الرئيسي", groups, onPick = { parent = it }, onDismiss = { pick = false })
}

private fun defaultParentFor(type: Int): Long = com.golden.accountant.domain.AccountRepository.defaultParentId(type)
