package com.golden.accountant.ui.accounts

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.Account
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.domain.AccountRepository
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.nav.Routes
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import kotlinx.coroutines.launch

private data class Node(val account: Account, val depth: Int, val balance: Double)

@Composable
fun AccountsTreeScreen(db: AppDatabase, onStatement: (Long) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val accounts by db.accounts().observeAll().collectAsState(emptyList())
    val currencies by db.core().observeCurrencies().collectAsState(emptyList())
    var currency by rememberSaveable { mutableLongStateOf(0L) }
    val balances by remember(currency) { db.accounts().balances(currency) }.collectAsState(emptyList())
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var adding by remember { mutableStateOf(false) }
    var pickParent by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val canNew = Session.can(Routes.ACCOUNTS, Action.NEW); val canDelete = Session.can(Routes.ACCOUNTS, Action.DELETE)
    var parent by remember { mutableStateOf<Account?>(null) }

    val nodes = remember(accounts, balances, expanded) {
        val bal = balances.associate { it.accountId to it.balance }
        val kids = accounts.groupBy { it.parentId }
        // رصيد المجموعة = مجموع أرصدة أوراقها
        fun total(a: Account): Double = if (!a.isGroup) bal[a.id] ?: 0.0 else (kids[a.id] ?: emptyList()).sumOf { total(it) }
        val out = mutableListOf<Node>()
        fun walk(parentId: Long, depth: Int) {
            for (a in kids[parentId].orEmpty()) {
                out += Node(a, depth, total(a))
                if (a.isGroup && a.id in expanded) walk(a.id, depth + 1)
            }
        }
        walk(0L, 0)
        out
    }

    Scaffold(
        topBar = { GoldTopBar("شجرة الحسابات", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { adding = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "حساب جديد", tint = Color.White) } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.padding(12.dp)) { CurrencyChips(currencies, currency) { currency = it } }
            LazyColumn {
                items(nodes, key = { it.account.id }) { n ->
                    val a = n.account
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { if (a.isGroup) expanded = if (a.id in expanded) expanded - a.id else expanded + a.id else onStatement(a.id) }
                            .padding(start = (16 + n.depth * 20).dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (a.isGroup) Icon(if (a.id in expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Gold.Primary)
                        else Spacer(Modifier.width(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(a.name, Modifier.weight(1f), fontWeight = if (a.isGroup) FontWeight.Bold else null)
                        Text(balanceText(n.balance), style = MaterialTheme.typography.bodySmall)
                        if (!a.isSystem && !a.isGroup && canDelete) IconButton(onClick = {
                            scope.launch { runCatching { AccountRepository(db).deleteAccount(a) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } }
                        }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) AlertDialog(
        onDismissRequest = { adding = false },
        title = { Text("حساب جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newName, { newName = it }, label = { Text("اسم الحساب") }, singleLine = true)
                OutlinedButton(onClick = { pickParent = true }) { Text(parent?.name ?: "اختر الحساب الرئيسي") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = parent
                scope.launch {
                    if (p == null) snack.showSnackbar("اختر الحساب الرئيسي")
                    else runCatching { AccountRepository(db).addAccount(newName, p) }
                        .onSuccess { newName = ""; parent = null; expanded = expanded + p.id }
                        .onFailure { snack.showSnackbar(it.message ?: "الاسم مستخدم") }
                }
                adding = false
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { adding = false }) { Text("إلغاء") } },
    )
    if (pickParent) AccountPickerDialog("الحساب الرئيسي", accounts.filter { it.isGroup }, onPick = { parent = it }, onDismiss = { pickParent = false })
}
