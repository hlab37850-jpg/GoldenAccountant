package com.golden.accountant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.domain.AuthRepository
import com.golden.accountant.domain.Session
import com.golden.accountant.domain.Settings
import com.golden.accountant.ui.common.GoldTopBar
import com.golden.accountant.ui.common.IconBadge
import com.golden.accountant.ui.nav.Menu
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(db: AppDatabase, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var changePw by remember { mutableStateOf(false) }

    Scaffold(topBar = { GoldTopBar("الإعدادات", onBack) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                Text("المستخدم: ${Session.user?.name ?: ""}", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                HorizontalDivider()
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("تفعيل ضريبة القيمة المضافة", Modifier.weight(1f))
                    Switch(Settings.vatEnabled, { on -> if (Session.isAdmin) scope.launch { Settings.setVat(db, on) } })
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إظهار تاريخ الانتهاء للأصناف", Modifier.weight(1f))
                    Switch(Settings.showEndDate, { on -> if (Session.isAdmin) scope.launch { Settings.setShowEndDate(db, on) } })
                }
                HorizontalDivider()
            }
            items(Menu.settings.filter { Session.can(it.route) }) { s ->
                Row(Modifier.fillMaxWidth().clickable { onNavigate(s.route) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(s.icon); Spacer(Modifier.width(14.dp)); Text(s.title, Modifier.weight(1f))
                }
                HorizontalDivider()
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { changePw = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Lock); Spacer(Modifier.width(14.dp)); Text("تغيير كلمة المرور", Modifier.weight(1f))
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().clickable { Session.end() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Person); Spacer(Modifier.width(14.dp)); Text("تسجيل الخروج", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                Text("المحاسب الذهبي — الإصدار 1.1.0", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (changePw) {
        var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var again by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changePw = false }, title = { Text("تغيير كلمة المرور") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(old, { old = it }, label = { Text("الحالية") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                    OutlinedTextField(new, { new = it }, label = { Text("الجديدة") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                    OutlinedTextField(again, { again = it }, label = { Text("تأكيد الجديدة") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    changePw = false
                    scope.launch {
                        runCatching {
                            if (new != again) throw IllegalStateException("كلمتا المرور غير متطابقتين")
                            AuthRepository(db).changePassword(Session.userId, old, new)
                        }.onSuccess { snack.showSnackbar("تم تغيير كلمة المرور") }.onFailure { snack.showSnackbar(it.message ?: "تعذر التغيير") }
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { changePw = false }) { Text("إلغاء") } },
        )
    }
}
