package com.golden.accountant.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.domain.Session
import com.golden.accountant.domain.Sys
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.common.CollapsibleSection
import com.golden.accountant.ui.nav.Menu
import com.golden.accountant.ui.nav.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Tile(val title: String, val icon: ImageVector, val route: String)

private val tiles = listOf(
    Tile("المبيعات", Icons.Default.ShoppingCart, Routes.SALES),
    Tile("المشتريات", Icons.Default.Star, Routes.PURCHASE),
    Tile("قبض / صرف", Icons.Default.Email, Routes.RECEIPT),
    Tile("الحسابات", Icons.Default.AccountBox, Routes.ACCOUNTS),
)

@Composable
fun HomeScreen(db: AppDatabase, onNavigate: (String) -> Unit, onOpenDrawer: () -> Unit) {
    val ctx = LocalContext.current
    // ضغطتان للخروج: الأولى تعرض تنبيهاً والثانية تغلق خلال ثانيتين
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) (ctx as? android.app.Activity)?.finish()
        else { lastBack = now; Toast.makeText(ctx, "اضغط مرة أخرى للخروج", Toast.LENGTH_SHORT).show() }
    }

    val cash by produceState(0.0) { value = runCatching { db.accounts().balance(Session.cashAccountId, 0) }.getOrDefault(0.0) }
    val today = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(Date()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        // الترويسة الذهبية
        Box(Modifier.fillMaxWidth().background(Gold.header).statusBarsPadding().padding(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "القائمة", tint = Color.White) }
                    Text("المحاسب الذهبي", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "الإعدادات", tint = Color.White) }
                }
                Text(today, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("رصيد صندوقك", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
                        Text(String.format(Locale.US, "%,.2f", cash), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Column(Modifier.padding(12.dp)) {
            // البلاطات الأربع
            tiles.filter { Session.can(it.route) }.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t -> TileCard(t, Modifier.weight(1f)) { onNavigate(t.route) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            // الأقسام القابلة للطي
            Menu.visibleSections().forEach { CollapsibleSection(it, onNavigate) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TileCard(t: Tile, modifier: Modifier, onClick: () -> Unit) = Card(
    modifier.height(104.dp).clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(t.icon, null, tint = Gold.Primary, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(t.title, fontWeight = FontWeight.Bold, color = Gold.Ink)
    }
}
