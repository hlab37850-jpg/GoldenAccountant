package com.golden.accountant.ui.report

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** مستند PDF بسيط: عنوان + أسطر معلومات + جدول (العمود الأول أقصى اليمين) + أسطر ختامية. */
data class PdfDoc(
    val title: String,
    val info: List<String> = emptyList(),
    val headers: List<String> = emptyList(),
    val weights: List<Float> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val footer: List<String> = emptyList(),
)

object PdfExport {
    private const val W = 595; private const val H = 842; private const val M = 32f
    private val GOLD = Color.rgb(184, 134, 11)

    fun write(ctx: Context, doc: PdfDoc, fileName: String): File {
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName.replace(Regex("[^\\w\\u0600-\\u06FF-]"), "_") + ".pdf")
        val pdf = PdfDocument()

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 18f; color = GOLD }
        val head = Paint(bold).apply { color = Color.WHITE }
        val fill = Paint().apply { color = GOLD }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        val usable = W - 2 * M
        val ws = if (doc.weights.size == doc.headers.size && doc.weights.isNotEmpty()) doc.weights else List(doc.headers.size) { 1f }
        val sum = ws.sum().takeIf { it > 0 } ?: 1f
        // حواف الأعمدة من اليمين لليسار
        val rights = ArrayList<Float>(); val widths = ArrayList<Float>()
        var x = W - M
        ws.forEach { w -> val cw = usable * w / sum; rights += x; widths += cw; x -= cw }

        var pageNo = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c: Canvas = page.canvas
        var y = M + 8

        fun fit(s: String, p: Paint, max: Float): String {
            if (p.measureText(s) <= max) return s
            var t = s
            while (t.length > 1 && p.measureText("$t…") > max) t = t.dropLast(1)
            return "$t…"
        }
        fun headerRow() {
            if (doc.headers.isEmpty()) return
            c.drawRect(M, y - 13f, W - M, y + 5f, fill)
            doc.headers.forEachIndexed { i, h -> c.drawText(fit(h, head, widths[i] - 6), rights[i] - 3, y, head) }
            y += 20f
        }
        fun newPage() {
            pdf.finishPage(page); pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()); c = page.canvas
            y = M + 8; headerRow()
        }

        c.drawText(doc.title, W - M, y + 6, title); y += 30f
        doc.info.forEach { c.drawText(it, W - M, y, text); y += 16f }
        if (doc.info.isNotEmpty()) y += 6f
        headerRow()
        doc.rows.forEach { r ->
            if (y > H - M - 30) newPage()
            r.forEachIndexed { i, cell -> if (i < rights.size) c.drawText(fit(cell, text, widths[i] - 6), rights[i] - 3, y, text) }
            y += 6f; c.drawLine(M, y, W - M, y, line); y += 14f
        }
        if (doc.footer.isNotEmpty()) {
            if (y > H - M - 20 * doc.footer.size) newPage()
            y += 8f
            doc.footer.forEach { c.drawText(it, W - M, y, bold); y += 17f }
        }
        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun export(ctx: Context, doc: PdfDoc, fileName: String) = share(ctx, write(ctx, doc, fileName))
}
