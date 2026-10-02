package com.golden.accountant.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golden.accountant.ui.Gold
import com.golden.accountant.ui.nav.MenuItem
import com.golden.accountant.ui.nav.MenuSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) = CenterAlignedTopAppBar(
    title = { Text(title, fontWeight = FontWeight.Bold) },
    navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
    actions = actions,
    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = Gold.Primary, titleContentColor = androidx.compose.ui.graphics.Color.White,
        navigationIconContentColor = androidx.compose.ui.graphics.Color.White,
        actionIconContentColor = androidx.compose.ui.graphics.Color.White,
    ),
)

/** شاشة مؤقتة لما لم يُبنَ بعد؛ تُستبدل في الأجزاء اللاحقة. */
@Composable
fun PlaceholderScreen(title: String, part: Int, onBack: () -> Unit) = Scaffold(topBar = { GoldTopBar(title, onBack) }) { pad ->
    Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("هذه الشاشة تُبنى في الجزء $part")
    }
}

@Composable
fun IconBadge(icon: ImageVector, size: Int = 40) = Box(
    Modifier.size(size.dp).clip(CircleShape).background(Gold.Light), contentAlignment = Alignment.Center,
) { Icon(icon, null, tint = Gold.Primary) }

/** قسم قابل للطي في القائمة (كما في الشاشة الرئيسية للتطبيق المرجعي). */
@Composable
fun CollapsibleSection(section: MenuSection, onNavigate: (String) -> Unit, startExpanded: Boolean = false) {
    var open by rememberSaveable(section.title) { mutableStateOf(startExpanded) }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, Gold.Light),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(section.icon)
                Spacer(Modifier.width(12.dp))
                Text(section.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Gold.Primary)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    section.items.forEach { MenuRow(it, onNavigate) }
                }
            }
        }
    }
}

@Composable
fun MenuRow(item: MenuItem, onNavigate: (String) -> Unit) = Row(
    Modifier.fillMaxWidth().clickable { onNavigate(item.route) }.padding(horizontal = 20.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(item.icon, null, tint = Gold.Primary, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
    Text(item.title)
}
