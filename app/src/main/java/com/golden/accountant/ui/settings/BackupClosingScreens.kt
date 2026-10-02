package com.golden.accountant.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.ClosingYear
import com.golden.accountant.domain.Action
import com.golden.accountant.domain.BackupManager
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.*
import com.golden.accountant.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ------------------------------------------------------------------ النسخ الاحتياطي والاستعادة
@Composable
fun BackupScreen(db: AppDatabase, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val isAdmin = Session.isAdmin
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pending = uri }

    Scaffold(topBar = { GoldTopBar("النسخ الاحتياطي والاستعادة", onBack) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                Text("النسخة الاحتياطية ملف واحد يحوي كل البيانات. احفظه خارج الهاتف (واتساب/بريد/سحابة). الملف غير مشفّر، فلا تشاركه مع من لا تثق به.", Modifier.padding(14.dp))
            }
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { BackupManager.createShareable(ctx, db) } }
                        .onSuccess { BackupManager.share(ctx, it) }
                        .onFailure { snack.showSnackbar("تعذر إنشاء النسخة: ${it.message}") }
                    busy = false
                }
            }) { Text("إنشاء نسخة احتياطية ومشاركتها") }

            HorizontalDivider()
            Text("الاستعادة", fontWeight = FontWeight.Bold)
            Text("تستبدل كل البيانات الحالية بمحتوى الملف. تُحفظ نسخة أمان تلقائية قبل الاستبدال، ثم يعاد تشغيل التطبيق.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy && isAdmin, modifier = Modifier.fillMaxWidth(), onClick = { picker.launch("*/*") }) { Text("استعادة من ملف") }
            if (!isAdmin) Text("الاستعادة للمدير فقط.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    pending?.let { uri ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("استبدال كل البيانات؟") },
            text = { Text("سيُستبدل كل شيء بمحتوى الملف المختار ولا يمكن التراجع إلا من النسخة التلقائية الداخلية.") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null; busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val tmp = File(ctx.cacheDir, "restore_tmp.db")
                                ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                                BackupManager.restore(ctx, db, tmp)
                                tmp.delete()
                            }
                        }.onSuccess { Toast.makeText(ctx, "تمت الاستعادة، سيُعاد تشغيل التطبيق", Toast.LENGTH_LONG).show(); BackupManager.restart(ctx) }
                            .onFailure { snack.showSnackbar(it.message ?: "تعذرت الاستعادة") }
                        busy = false
                    }
                }) { Text("استبدال", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء") } },
        )
    }
}

// ------------------------------------------------------------------ إقفال السنة المالية
/**
 * الإقفال قفل: لا يُسمح بإنشاء أو تعديل أو حذف أي حركة بتاريخ ≤ تاريخ الإقفال.
 * الأرصدة لا تُصفَّر (تُحسب تراكمياً من القيود)، والأرباح تُستخرج لكل فترة من تقرير الأرباح والخسائر.
 */
@Composable
fun ClosingScreen(db: AppDatabase, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val closings by db.core().observeClosings().collectAsState(emptyList())
    var date by remember { mutableStateOf(todayIso().take(4) + "-12-31") }
    var confirm by remember { mutableStateOf(false) }
    var reopen by remember { mutableStateOf(false) }
    val canEdit = Session.isAdmin && Session.can(Routes.CLOSING, Action.EDIT)
    val last = closings.firstOrNull()?.date

    Scaffold(topBar = { GoldTopBar("إقفال السنة المالية", onBack) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Gold.Light)) {
                Text(if (last == null) "لا يوجد إقفال: كل التواريخ مفتوحة للتعديل." else "مقفلة حتى $last. أي حركة بهذا التاريخ أو قبله مرفوضة.", Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
            }
            if (!canEdit) Text("الإقفال وإعادة الفتح للمدير فقط.", color = MaterialTheme.colorScheme.error)
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("إقفال حتى"); DateButton(date) { date = it }
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        when {
                            date >= todayIso() -> snack.showSnackbar("لا يمكن الإقفال حتى تاريخ اليوم أو بعده")
                            last != null && date <= last -> snack.showSnackbar("التاريخ يجب أن يكون بعد آخر إقفال ($last)")
                            else -> confirm = true
                        }
                    }
                }) { Text("إقفال السنة") }
                if (last != null) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { reopen = true }) { Text("إعادة فتح آخر إقفال") }
            }
            HorizontalDivider()
            Text("سجل الإقفالات", fontWeight = FontWeight.Bold)
            LazyColumn { items(closings, key = { it.id }) { Text(it.date, Modifier.padding(vertical = 8.dp)); HorizontalDivider() } }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("إقفال حتى $date؟") },
        text = { Text("ستُحفظ نسخة أمان تلقائية، ثم تُقفل كل الحركات حتى هذا التاريخ.") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { BackupManager.createInternal(ctx, db, "pre_closing") }
                        db.core().insertClosing(ClosingYear(date = date))
                    }.onSuccess { snack.showSnackbar("تم الإقفال حتى $date") }.onFailure { snack.showSnackbar("تعذر الإقفال: ${it.message}") }
                }
            }) { Text("إقفال") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } },
    )
    if (reopen) AlertDialog(
        onDismissRequest = { reopen = false }, title = { Text("إعادة فتح الفترة؟") },
        text = { Text("ستُفتح الفترة التي أُقفلت حتى $last للتعديل مجدداً.") },
        confirmButton = { TextButton(onClick = { reopen = false; closings.firstOrNull()?.let { c -> scope.launch { db.core().deleteClosing(c.id) } } }) { Text("إعادة فتح", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { reopen = false }) { Text("إلغاء") } },
    )
}
