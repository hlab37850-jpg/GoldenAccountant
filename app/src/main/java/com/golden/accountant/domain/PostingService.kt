package com.golden.accountant.domain

import com.golden.accountant.data.*

/** مسودة قيد: رأس + أسطر، قبل الحفظ. */
data class JournalDraft(val header: JournalHeader, val lines: List<JournalLine>)

class PostingException(message: String) : IllegalArgumentException(message)

/**
 * يحوّل الفاتورة إلى قيود يومية متوازنة (مدين = دائن). منطق نقي بلا اعتماد على أندرويد.
 *
 * المعادلة: total = subtotal - discount + tax + extra
 *  - بيع:        مدين الطرف/الصندوق total + خصم مسموح، دائن مبيعات subtotal + ضريبة + رسوم
 *  - مرتجع بيع:  عكس ذلك مع حساب مردودات المبيعات
 *  - شراء:       مدين مشتريات (subtotal+extra) + ضريبة، دائن الطرف/الصندوق total + خصم مكتسب
 *  - مرتجع شراء: عكس ذلك مع حساب مردودات المشتريات
 *  - فاتورة آجلة مع مدفوع: قيد ثانٍ بين الصندوق والطرف بقيمة المدفوع
 */
object PostingService {

    fun postBill(bill: Bill, billId: Long = bill.id): List<JournalDraft> {
        if (bill.trType in TrType.NON_POSTING) return emptyList()
        if (bill.trType != TrType.SALE && bill.trType != TrType.PURCHASE)
            throw PostingException("نوع الحركة ${bill.trType} غير مدعوم في هذا الإصدار")
        validate(bill)

        val counter = if (bill.billType == BillType.CASH) bill.cashAccountId else bill.partyAccountId
        val b = LineBuilder()
        val total = bill.total
        val d = bill.discount; val t = bill.taxAmount; val x = bill.extraCost; val s = bill.subtotal

        when {
            bill.trType == TrType.SALE && !bill.isBack -> {
                b.dr(counter, total); b.dr(Sys.DISCOUNT_ALLOWED, d)
                b.cr(Sys.SALES, s); b.cr(Sys.VAT, t); b.cr(Sys.FEES_INCOME, x)
            }
            bill.trType == TrType.SALE && bill.isBack -> {
                b.cr(counter, total); b.cr(Sys.DISCOUNT_ALLOWED, d)
                b.dr(Sys.SALES_RETURNS, s); b.dr(Sys.VAT, t); b.dr(Sys.FEES_INCOME, x)
            }
            bill.trType == TrType.PURCHASE && !bill.isBack -> {
                b.dr(Sys.PURCHASES, s + x); b.dr(Sys.VAT, t)
                b.cr(counter, total); b.cr(Sys.DISCOUNT_EARNED, d)
            }
            else -> { // مرتجع شراء
                b.cr(Sys.PURCHASE_RETURNS, s + x); b.cr(Sys.VAT, t)
                b.dr(counter, total); b.dr(Sys.DISCOUNT_EARNED, d)
            }
        }

        val label = (if (bill.trType == TrType.SALE) "مبيعات" else "مشتريات") + (if (bill.isBack) " (مرتجع)" else "")
        val out = mutableListOf(
            JournalDraft(header(bill, billId, "فاتورة $label #${bill.billNo}"), b.build())
        )

        // الدفعة المقدّمة على فاتورة آجلة
        if (bill.billType == BillType.CREDIT && bill.paid > 0.0) {
            val p = LineBuilder()
            val cashIn = (bill.trType == TrType.SALE) != bill.isBack // بيع عادي أو مرتجع شراء = قبض
            if (cashIn) { p.dr(bill.cashAccountId, bill.paid); p.cr(bill.partyAccountId, bill.paid) }
            else { p.dr(bill.partyAccountId, bill.paid); p.cr(bill.cashAccountId, bill.paid) }
            out += JournalDraft(header(bill, billId, "دفعة من فاتورة $label #${bill.billNo}"), p.build())
        }
        out.forEach { assertBalanced(it) }
        return out
    }

    private fun header(b: Bill, id: Long, note: String) = JournalHeader(
        date = b.date, time = b.time, kind = b.trType, refType = RefType.BILL, refId = id,
        currencyId = b.currencyId, rate = b.rate, note = note + if (b.remarks.isBlank()) "" else " - ${b.remarks}",
        userId = b.userId,
    )

    fun validate(b: Bill) {
        if (b.subtotal < 0 || b.discount < 0 || b.taxAmount < 0 || b.extraCost < 0 || b.paid < 0)
            throw PostingException("لا يُسمح بمبالغ سالبة")
        if (b.discount > b.subtotal + b.extraCost + b.taxAmount) throw PostingException("الخصم أكبر من قيمة الفاتورة")
        if (b.billType == BillType.CASH && b.cashAccountId == 0L) throw PostingException("حدد الصندوق للفاتورة النقدية")
        if (b.billType == BillType.CREDIT && b.partyAccountId == 0L) throw PostingException("حدد العميل/المورد للفاتورة الآجلة")
        if (b.billType == BillType.CREDIT && b.paid > 0 && b.cashAccountId == 0L) throw PostingException("حدد الصندوق للمدفوع")
        if (b.paid > b.total) throw PostingException("المدفوع أكبر من إجمالي الفاتورة")
    }

    fun assertBalanced(j: JournalDraft) {
        val dr = Money.r(j.lines.sumOf { it.debit }); val cr = Money.r(j.lines.sumOf { it.credit })
        if (dr != cr) throw PostingException("القيد غير متوازن: مدين $dr ≠ دائن $cr")
        if (j.lines.size < 2) throw PostingException("القيد يحتاج طرفين على الأقل")
    }

    /** نسبة الضريبة -> مبلغ، على (المجموع - الخصم). */
    fun taxOf(subtotal: Double, discount: Double, percent: Double): Double = Money.r((subtotal - discount) * percent / 100.0)

    private class LineBuilder {
        private val lines = mutableListOf<JournalLine>()
        fun dr(acc: Long, amt: Double) { if (Money.r(amt) != 0.0) lines += JournalLine(accountId = acc, debit = Money.r(amt)) }
        fun cr(acc: Long, amt: Double) { if (Money.r(amt) != 0.0) lines += JournalLine(accountId = acc, credit = Money.r(amt)) }
        fun build(): List<JournalLine> = lines.toList()
    }
}
