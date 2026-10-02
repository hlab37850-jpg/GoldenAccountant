package com.golden.accountant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.golden.accountant.data.*
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.Session
import com.golden.accountant.domain.Sys
import com.golden.accountant.domain.UserRepository
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.AccountPickerDialog
import com.golden.accountant.ui.common.GoldTopBar
import com.golden.accountant.ui.nav.Menu
import com.golden.accountant.ui.nav.Routes
import kotlinx.coroutines.launch

@Composable
fun UsersScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val users by db.users().observeAll().collectAsState(emptyList())
    val branches by db.core().observeBranches().collectAsState(emptyList())
    val leaves by db.accounts().observeLeaves().collectAsState(emptyList())
    var editing by remember { mutableStateOf<AppUser?>(null) }
    var creating by remember { mutableStateOf(false) }
    var permsFor by remember { mutableStateOf<AppUser?>(null) }
    val canNew = Session.can(Routes.USERS, Action.NEW); val canEdit = Session.can(Routes.USERS, Action.EDIT)

    Scaffold(
        topBar = { GoldTopBar("المستخدمون والصلاحيات", onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (canNew) FloatingActionButton(onClick = { creating = true }, containerColor = Gold.Primary) { Icon(Icons.Default.Add, "مستخدم جديد", tint = Color.White) } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(users, key = { it.id }) { u ->
                Row(Modifier.fillMaxWidth().clickable(enabled = canEdit) { editing = u }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name + if (u.isAdmin) "  (مدير)" else "", fontWeight = FontWeight.Bold)
                        Text("${u.userName}  •  ${branches.firstOrNull { it.id == u.branchId }?.name ?: ""}" + if (!u.isActive) "  •  معطّل" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!u.isAdmin && canEdit) TextButton(onClick = { permsFor = u }) { Text("الصلاحيات") }
                }
                HorizontalDivider()
            }
        }
    }

    if (creating) UserDialog(null, branches, leaves, onDismiss = { creating = false }) { name, userName, pw, cash, branch, admin, _ ->
        scope.launch { runCatching { UserRepository(db).create(userName, name, pw, cash, branch, admin) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحفظ") } }
    }
    editing?.let { u ->
        UserDialog(u, branches, leaves, onDismiss = { editing = null }) { name, _, pw, cash, branch, admin, active ->
            scope.launch { runCatching { UserRepository(db).update(u.copy(name = name.trim(), cashAccountId = cash, branchId = branch, isAdmin = admin, isActive = active), pw) }.onFailure { snack.showSnackbar(it.message ?: "تعذر الحفظ") } }
        }
    }
    permsFor?.let { u -> PermissionsDialog(db, u, onDismiss = { permsFor = null }) }
}

@Composable
private fun UserDialog(
    u: AppUser?, branches: List<Branch>, leaves: List<Account>, onDismiss: () -> Unit,
    onSave: (name: String, userName: String, password: String, cash: Long, branch: Long, admin: Boolean, active: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(u?.name ?: "") }
    var userName by remember { mutableStateOf(u?.userName ?: "") }
    var pw by remember { mutableStateOf("") }
    var cash by remember { mutableLongStateOf(u?.cashAccountId ?: Sys.MAIN_CASH) }
    var branch by remember { mutableLongStateOf(u?.branchId ?: 1L) }
    var admin by remember { mutableStateOf(u?.isAdmin ?: false) }
    var active by remember { mutableStateOf(u?.isActive ?: true) }
    var pick by remember { mutableStateOf(false) }
    val cashAccounts = remember(leaves) { leaves.filter { it.parentId == Sys.CASH_BOXES || it.parentId == Sys.BANKS } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (u == null) "مستخدم جديد" else "تعديل المستخدم") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(userName, { userName = it }, label = { Text("اسم المستخدم") }, singleLine = true, enabled = u == null)
                OutlinedTextField(pw, { pw = it }, label = { Text(if (u == null) "كلمة المرور" else "كلمة مرور جديدة (اتركها فارغة للإبقاء)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedButton(onClick = { pick = true }) { Text("الصندوق: ${cashAccounts.firstOrNull { it.id == cash }?.name ?: "اختر"}") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.forEach { b -> FilterChip(branch == b.id, { branch = b.id }, { Text(b.name) }) } }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(admin, { admin = it }); Spacer(Modifier.width(8.dp)); Text("مدير (كل الصلاحيات)") }
                if (u != null) Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, { active = it }); Spacer(Modifier.width(8.dp)); Text("فعّال") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, userName, pw, cash, branch, admin, active); onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (pick) AccountPickerDialog("الصندوق الافتراضي", cashAccounts, onPick = { cash = it.id }, onDismiss = { pick = false })
}

/** مصفوفة الصلاحيات: شاشة × (عرض/جديد/تعديل/حذف). أي إجراء يتطلب العرض. */
@Composable
private fun PermissionsDialog(db: AppDatabase, user: AppUser, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val screens = remember { Menu.allItems }
    var map by remember { mutableStateOf<Map<String, UserPriv>>(emptyMap()) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(user.id) {
        val idByRoute = db.screens().all().associate { it.name to it.id }
        val stored = db.users().privs(user.id).associateBy { it.screenId }
        map = screens.associate { s -> s.route to (idByRoute[s.route]?.let { stored[it] } ?: UserPriv(user.id, idByRoute[s.route] ?: 0L)) }
        ready = true
    }
    fun set(route: String, f: (UserPriv) -> UserPriv) { map = map + (route to f(map[route]!!)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(8.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp)) {
                Text("صلاحيات: ${user.name}", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("الشاشة", Modifier.weight(2.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    listOf("عرض", "جديد", "تعديل", "حذف").forEach { Text(it, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
                HorizontalDivider()
                if (!ready) CircularProgressIndicator() else LazyColumn(Modifier.weight(1f)) {
                    items(screens, key = { it.route }) { s ->
                        val p = map[s.route]!!
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.title, Modifier.weight(2.2f), fontSize = 13.sp)
                            Checkbox(p.canView, { v -> set(s.route) { it.copy(canView = v, canNew = it.canNew && v, canEdit = it.canEdit && v, canDelete = it.canDelete && v) } }, Modifier.weight(1f))
                            Checkbox(p.canNew, { v -> set(s.route) { it.copy(canNew = v, canView = it.canView || v) } }, Modifier.weight(1f))
                            Checkbox(p.canEdit, { v -> set(s.route) { it.copy(canEdit = v, canView = it.canView || v) } }, Modifier.weight(1f))
                            Checkbox(p.canDelete, { v -> set(s.route) { it.copy(canDelete = v, canView = it.canView || v) } }, Modifier.weight(1f))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            // الصلاحيات المحفوظة تُربط بمعرّف الشاشة؛ الشاشات غير المسجلة تُتجاهل
                            UserRepository(db).savePrivs(user.id, map.values.filter { it.screenId != 0L })
                            onDismiss()
                        }
                    }) { Text("حفظ") }
                    OutlinedButton(onClick = { map = map.mapValues { it.value.copy(canView = true, canNew = true, canEdit = true, canDelete = true) } }) { Text("تحديد الكل") }
                    OutlinedButton(onClick = { map = map.mapValues { it.value.copy(canView = false, canNew = false, canEdit = false, canDelete = false) } }) { Text("إلغاء الكل") }
                    TextButton(onClick = onDismiss) { Text("إغلاق") }
                }
            }
        }
    }
}
