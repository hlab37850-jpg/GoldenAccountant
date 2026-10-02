package com.golden.accountant.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.UnitDef
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.Session
import com.golden.accountant.domain.UnitRepository
import com.golden.accountant.ui.nav.Routes
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.GoldTopBar
import kotlinx.coroutines.launch

@Composable
fun UnitsScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val units by db.core().observeUnits().collectAsState(emptyList())
    var editing by remember { mutableStateOf<UnitDef?>(null) }
    var creating by remember { mutableStateOf(false) }
    val repo = remember { UnitRepository(db) }
    val canNew = Session.can(Routes.UNITS, Action.NEW); val canEdit = Session.can(Routes.UNITS, Action.EDIT); val canDelete = Session.can(Routes.UNITS, Action.DELETE)

    Scaffold(
        topBar = { GoldTopBar("الوحدات", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { creating = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "وحدة جديدة", tint = Color.White) } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(units.filter { it.id != 0L }, key = { it.id }) { u ->
                Text(u.name, Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = u }.padding(16.dp))
                HorizontalDivider()
            }
        }
    }

    if (creating) NameDialog("وحدة جديدة", "", onDismiss = { creating = false }, onSave = { n -> scope.launch { runCatching { repo.create(n) }.onFailure { snack.showSnackbar(it.message ?: "الاسم مستخدم") } } }, onDelete = null)
    editing?.let { u ->
        NameDialog("تعديل الوحدة", u.name, onDismiss = { editing = null },
            onSave = { n -> scope.launch { runCatching { repo.save(u.copy(name = n.trim(), code = n.trim())) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحفظ") } } },
            onDelete = if (!canDelete) null else { { scope.launch { runCatching { repo.delete(u) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحذف") } } } })
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name); onDismiss() }) { Text("حفظ") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        },
    )
}
