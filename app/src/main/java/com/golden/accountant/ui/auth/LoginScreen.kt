package com.golden.accountant.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.AppUser
import com.golden.accountant.domain.AuthRepository
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.Gold
import kotlinx.coroutines.launch

/** أول تشغيل: المدير بلا كلمة مرور فيُطلب تعيينها. بعدها: دخول عادي. */
@Composable
fun LoginScreen(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    val auth = remember { AuthRepository(db) }
    var setupFor by remember { mutableStateOf<AppUser?>(null) }
    var ready by remember { mutableStateOf(false) }
    var userName by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { setupFor = db.users().adminWithoutPassword(); ready = true }
    if (!ready) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("المحاسب الذهبي", style = MaterialTheme.typography.headlineLarge, color = Gold.Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(if (setupFor != null) "أول تشغيل: عيّن كلمة مرور المدير" else "تسجيل الدخول")
        Spacer(Modifier.height(20.dp))

        if (setupFor == null) OutlinedTextField(userName, { userName = it }, Modifier.fillMaxWidth(), label = { Text("اسم المستخدم") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (setupFor != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth(), label = { Text("تأكيد كلمة المرور") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching {
                        val admin = setupFor
                        if (admin != null) {
                            if (password != confirm) throw IllegalStateException("كلمتا المرور غير متطابقتين")
                            Session.start(db, auth.setupAdminPassword(admin, password))
                        } else Session.start(db, auth.login(userName, password))
                    }.onFailure { error = it.message ?: "تعذر الدخول" }
                    busy = false
                }
            },
        ) { Text(if (setupFor != null) "حفظ والدخول" else "دخول") }
    }
}
