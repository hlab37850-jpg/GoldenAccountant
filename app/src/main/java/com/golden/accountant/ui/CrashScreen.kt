package com.golden.accountant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** يظهر بعد انهيار سابق: انسخ النص أو شاركه وأرسله للمطوّر. */
@Composable
fun CrashScreen(text: String, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("توقف التطبيق في المرة السابقة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text("انسخ النص التالي أو شاركه وأرسله لإصلاح المشكلة.", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SelectionContainer { Text(text, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth()) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", text))
            }) { Text("نسخ") }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                ctx.startActivity(Intent.createChooser(send, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("مشاركة") }
            TextButton(onClick = onContinue) { Text("متابعة") }
        }
    }
}
