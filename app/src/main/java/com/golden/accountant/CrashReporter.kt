package com.golden.accountant

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/** يحفظ نص آخر انهيار لعرضه في التشغيل التالي (لإرساله للمطوّر دون كمبيوتر). */
object CrashReporter {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                file(app).writeText("الجهاز: ${Build.MANUFACTURER} ${Build.MODEL} / أندرويد ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nالخيط: ${t.name}\n\n" + Log.getStackTraceString(e))
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()?.take(6000)
    fun clear(ctx: Context) { file(ctx).delete() }
}
