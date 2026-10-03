package com.golden.accountant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.golden.accountant.domain.Seeder
import com.golden.accountant.domain.Session
import com.golden.accountant.ui.CrashScreen
import com.golden.accountant.ui.GoldenTheme
import com.golden.accountant.ui.auth.LoginScreen
import com.golden.accountant.ui.nav.AppNav
import com.golden.accountant.ui.nav.Menu

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()   // إلزامي مع targetSdk 36؛ الشاشات تتعامل مع الحواف بنفسها
        super.onCreate(savedInstanceState)
        val db = (application as GoldenApp).db
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GoldenTheme {
                    var crash by remember { mutableStateOf(CrashReporter.read(applicationContext)) }
                    var seeded by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        // الزرع قبل شاشة الدخول: وإلا قد لا يوجد المدير فيعلق التشغيل الأول
                        Seeder.seedIfEmpty(db)
                        Seeder.syncScreens(db, Menu.allItems.map { it.route })
                        com.golden.accountant.domain.Settings.load(db)
                        seeded = true
                    }
                    val c = crash
                    if (c != null) CrashScreen(c) { CrashReporter.clear(applicationContext); crash = null }
                    else if (!seeded) CircularProgressIndicator()
                    else if (Session.isLoggedIn) AppNav(db)
                    else LoginScreen(db)
                }
            }
        }
    }
}
