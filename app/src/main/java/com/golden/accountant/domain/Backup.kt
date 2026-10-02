package com.golden.accountant.domain

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import com.golden.accountant.data.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupException(msg: String) : IllegalStateException(msg)

object BackupManager {
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private fun dbFile(ctx: Context) = ctx.getDatabasePath(AppDatabase.FILE)

    /** ينسخ القاعدة (بعد تفريغ سجل WAL) إلى ملف واحد متماسك. */
    fun copyTo(ctx: Context, db: AppDatabase, target: File) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        target.parentFile?.mkdirs()
        dbFile(ctx).copyTo(target, overwrite = true)
    }

    /** نسخة للمشاركة (في الكاش). */
    fun createShareable(ctx: Context, db: AppDatabase): File =
        File(ctx.cacheDir, "backups/golden_${stamp()}.db").also { copyTo(ctx, db, it) }

    /** نسخة تلقائية داخلية قبل العمليات الخطرة (إقفال/استعادة)، تحتفظ بآخر 10. */
    fun createInternal(ctx: Context, db: AppDatabase, tag: String): File {
        val dir = File(ctx.filesDir, "auto_backups")
        val f = File(dir, "${tag}_${stamp()}.db").also { copyTo(ctx, db, it) }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
        return f
    }

    fun share(ctx: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        ctx.startActivity(Intent.createChooser(send, "حفظ النسخة الاحتياطية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** يتحقق أن الملف قاعدة بيانات هذا التطبيق وبإصدار لا يتجاوز الحالي. */
    fun validate(file: File) {
        val head = ByteArray(16)
        file.inputStream().use { if (it.read(head) < 16) throw BackupException("الملف ليس نسخة صحيحة") }
        if (String(head, 0, 15, Charsets.US_ASCII) != "SQLite format 3") throw BackupException("الملف ليس قاعدة بيانات")
        val sq = try { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) } catch (e: Exception) { throw BackupException("الملف تالف") }
        try {
            val hasAccounts = sq.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('accounts','journal_lines','bills')", null).use { it.count == 3 }
            if (!hasAccounts) throw BackupException("الملف ليس نسخة من هذا التطبيق")
            if (sq.version > AppDatabase.VERSION) throw BackupException("النسخة أحدث من هذا الإصدار من التطبيق")
        } finally { sq.close() }
    }

    /** يستبدل القاعدة بالملف المختار بعد التحقق ونسخة أمان. يجب إعادة تشغيل التطبيق بعده. */
    fun restore(ctx: Context, db: AppDatabase, source: File) {
        validate(source)
        createInternal(ctx, db, "pre_restore")
        AppDatabase.close()
        val target = dbFile(ctx)
        File(target.path + "-wal").delete(); File(target.path + "-shm").delete()
        source.copyTo(target, overwrite = true)
    }

    fun restart(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
        Runtime.getRuntime().exit(0)
    }
}
