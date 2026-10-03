package com.golden.accountant.domain

import com.golden.accountant.data.*

/** مسودة قيد: رأس + أسطر، قبل الحفظ. */
data class JournalDraft(val header: JournalHeader, val lines: List<JournalLine>)

class PostingException(message: String) : IllegalArgumentException(message)

/**
 * ترحيل الفواتير مطابق لـ triggers القاعدة الأصلية (inv.db):
 *  - المبلغ total = (المجموع − الخصم) + الضريبة (إن لم تكن متضمنة) + الرسوم. الخصم صافٍ ولا يُرحَّل بحساب مستقل.
 *  - بيع آجل:  مدين العميل total | دائن «مبيعات آجل» (total−ضريبة−رسوم) + «الضريبة» + حساب الرسوم
 *  - بيع نقدي: مدين الصندوق total | دائن «مبيعات نقدي» (...) + الضريبة + الرسوم
 *  - مرتجع بيع: العكس بحساب «مردودات مبيعات آجل/نقدي»
 *  - شراء آجل: مدين «مشتريات آجل» (total−ضريبة) + الضريبة | دائن المورد (total−رسوم) + دائن الصندوق بالرسوم
 *  - شراء نقدي: مدين «مشتريات نقدي» (total−ضريبة) + الضريبة | دائن الصندوق total
 *  - مرتجع شراء: العكس بحساب «مردودات مشتريات آجل/نقدي»
 *  - توريد/صرف مخزني: مقابل حساب «تسوية المخزون-صرف وتوريد» (يُحدَّد الحساب المقابل في الفاتورة)
 *  - المدفوع على الآجل: قيد منفصل بين الصندوق والطرف
 */
object PostingService {

    fun postBill(bill: Bill, billId: Long = bill.id): List<JournalDraft> {
        if (bill.trType in TrType.NON_POSTING) return emptyList()
        if (bill.trType !in setOf(TrType.SALE, TrType.PURCHASE, TrType.ISSUE, TrType.SUPPLY))
            throw PostingException("نوع الحركة ${bill.trType} غير مدعوم")
        validate(bill)

        val cash = bill.billType == BillType.CASH
        val counter = if (cash) bill.cashAccountId else bill.partyAccountId
        val total = bill.total; val tax = bill.taxAmount; val fees = bill.extraCost
        val b = LineBuilder()

        when {
            bill.trType == TrType.SALE && !bill.isBack -> {
                b.dr(counter, total)
                b.cr(if (cash) Sys.SALES_CASH else Sys.SALES_CREDIT, total - tax - fees); b.cr(Sys.VAT, tax); b.cr(bill.costAccountId, fees)
            }
            bill.trType == TrType.SALE && bill.isBack -> {
                b.cr(counter, total)
                b.dr(if (cash) Sys.SALES_RET_CASH else Sys.SALES_RET_CREDIT, total - tax - fees); b.dr(Sys.VAT, tax); b.dr(bill.costAccountId, fees)
            }
            bill.trType == TrType.PURCHASE && !bill.isBack -> {
                b.dr(if (cash) Sys.PURCHASE_CASH else Sys.PURCHASE_CREDIT, total - tax); b.dr(Sys.VAT, tax)
                if (cash) b.cr(counter, total) else { b.cr(counter, total - fees); b.cr(bill.cashAccountId, fees) }
            }
            bill.trType == TrType.PURCHASE && bill.isBack -> {
                b.cr(if (cash) Sys.PURCHASE_RET_CASH else Sys.PURCHASE_RET_CREDIT, total - tax); b.cr(Sys.VAT, tax)
                if (cash) b.dr(counter, total) else { b.dr(counter, total - fees); b.dr(bill.cashAccountId, fees) }
            }
            bill.trType == TrType.ISSUE -> {   // صرف مخزني: مدين الحساب المقابل، دائن تسوية المخزون
                b.dr(bill.partyAccountId, total); b.cr(Sys.STOCK_SETTLE, total - tax - fees); b.cr(Sys.VAT, tax); b.cr(bill.costAccountId, fees)
            }
            else -> {                          // توريد مخزني: عكس الصرف
                b.dr(Sys.STOCK_SETTLE, total - tax - fees); b.dr(Sys.VAT, tax); b.dr(bill.costAccountId, fees); b.cr(bill.partyAccountId, total)
            }
        }

        val label = when (bill.trType) {
            TrType.SALE -> "مبيعات"; TrType.PURCHASE -> "مشتريات"; TrType.ISSUE -> "صرف مخزني"; else -> "توريد مخزني"
        } + (if (bill.isBack) " (مرتجع)" else "")
        val out = mutableListOf(JournalDraft(header(bill, billId, "فاتورة $label #${bill.billNo}"), b.build()))

        // المدفوع على فاتورة آجلة (بيع/شراء فقط)
        if (!cash && bill.paid > 0.0 && bill.trType in setOf(TrType.SALE, TrType.PURCHASE)) {
            val p = LineBuilder()
            val cashIn = (bill.trType == TrType.SALE) != bill.isBack
            if (cashIn) { p.dr(bill.cashAccountId, bill.paid); p.cr(bill.partyAccountId, bill.paid) }
            else { p.dr(bill.partyAccountId, bill.paid); p.cr(bill.cashAccountId, bill.paid) }
            out += JournalDraft(header(bill, billId, "من قيمة فاتورة $label #${bill.billNo}"), p.build())
        }
        out.forEach { assertBalanced(it) }
        return out
    }

    private fun header(b: Bill, id: Long, note: String) = JournalHeader(
        date = b.date, time = b.time, kind = b.trType, refType = RefType.BILL, refId = id,
        currencyId = b.currencyId, rate = b.rate, note = note + if (b.remarks.isBlank()) "" else " - ${b.remarks}", userId = b.userId,
    )

    fun validate(b: Bill) {
        if (b.subtotal < 0 || b.discount < 0 || b.taxAmount < 0 || b.extraCost < 0 || b.paid < 0) throw PostingException("لا يُسمح بمبالغ سالبة")
        if (b.discount > b.subtotal) throw PostingException("الخصم أكبر من قيمة الفاتورة")
        val cash = b.billType == BillType.CASH
        if (cash && b.cashAccountId == 0L) throw PostingException("حدد الصندوق للفاتورة النقدية")
        if (!cash && b.partyAccountId == 0L) throw PostingException("حدد الحساب للفاتورة الآجلة")
        if (!cash && (b.paid > 0 || b.extraCost > 0 && b.trType == TrType.PURCHASE) && b.cashAccountId == 0L) throw PostingException("حدد الصندوق")
        if ((b.trType == TrType.ISSUE || b.trType == TrType.SUPPLY) && b.partyAccountId == 0L) throw PostingException("حدد الحساب المقابل")
        if (b.paid > b.total) throw PostingException("المدفوع أكبر من إجمالي الفاتورة")
    }

    fun assertBalanced(j: JournalDraft) {
        val dr = Money.r(j.lines.sumOf { it.debit }); val cr = Money.r(j.lines.sumOf { it.credit })
        if (dr != cr) throw PostingException("القيد غير متوازن: مدين $dr ≠ دائن $cr")
        if (j.lines.size < 2) throw PostingException("القيد يحتاج طرفين على الأقل")
    }

    private class LineBuilder {
        private val lines = mutableListOf<JournalLine>()
        fun dr(acc: Long, amt: Double) { if (Money.r(amt) != 0.0) lines += JournalLine(accountId = acc, debit = Money.r(amt)) }
        fun cr(acc: Long, amt: Double) { if (Money.r(amt) != 0.0) lines += JournalLine(accountId = acc, credit = Money.r(amt)) }
        fun build(): List<JournalLine> = lines.toList()
    }
}
