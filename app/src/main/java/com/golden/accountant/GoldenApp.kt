package com.golden.accountant

import android.app.Application
import com.golden.accountant.data.AppDatabase

class GoldenApp : Application() {
    /** يُعاد إنشاؤه بعد الاستعادة (تُغلق القاعدة ويُعاد تشغيل العملية). */
    val db: AppDatabase get() = AppDatabase.get(this)
}
