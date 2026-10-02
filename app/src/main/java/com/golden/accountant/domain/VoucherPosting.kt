package com.golden.accountant.domain

import com.golden.accountant.data.*

/** ترحيل السندات والقيود اليدوية (منطق نقي قابل للاختبار). */
object VoucherPosting {

    /**
     * قبض: مدين الصندوق (المبلغ) + خصم مسموح، دائن الحساب (المبلغ + الخصم).
     * صرف: مدين الحساب (المبلغ + الخصم)، دائن الصندوق (المبلغ) + خصم مكتسب.
     */
    fun postVoucher(v: Voucher, id: Long = v.id): JournalDraft {
        if (v.type != TrType.RECEIPT && v.type != TrType.PAYMENT) throw PostingException("نوع السند غير صحيح")
        if (v.amount <= 0) throw PostingException("المبلغ يجب أن يكون أكبر من صفر")
        if (v.discount < 0) throw PostingException("الخصم لا يكون سالباً")
        if (v.accountId == 0L) throw PostingException("اختر الحساب")
        if (v.cashAccountId == 0L) throw PostingException("اختر الصندوق أو البنك")
        if (v.accountId == v.cashAccountId) throw PostingException("لا يمكن أن يكون الحساب هو الصندوق نفسه")

        val amt = Money.r(v.amount); val d = Money.r(v.discount); val full = Money.r(amt + d)
        val lines = mutableListOf<JournalLine>()
        fun dr(a: Long, x: Double) { if (x != 0.0) lines += JournalLine(accountId = a, debit = x) }
        fun cr(a: Long, x: Double) { if (x != 0.0) lines += JournalLine(accountId = a, credit = x) }
        if (v.type == TrType.RECEIPT) { dr(v.cashAccountId, amt); dr(Sys.DISCOUNT_ALLOWED, d); cr(v.accountId, full) }
        else { dr(v.accountId, full); cr(v.cashAccountId, amt); cr(Sys.DISCOUNT_EARNED, d) }

        val label = if (v.type == TrType.RECEIPT) "سند قبض" else "سند صرف"
        val draft = JournalDraft(
            JournalHeader(
                date = v.date, time = v.time, kind = v.type, refType = RefType.VOUCHER, refId = id,
                currencyId = v.currencyId, rate = v.rate, note = "$label #${v.voucherNo}" + if (v.note.isBlank()) "" else " - ${v.note}", userId = v.userId,
            ), lines,
        )
        PostingService.assertBalanced(draft)
        return draft
    }

    /** قيد يدوي: كل سطر إما مدين أو دائن (وليس الاثنين)، والمجموعان متساويان. */
    fun validateManual(lines: List<JournalLine>) {
        if (lines.size < 2) throw PostingException("القيد يحتاج سطرين على الأقل")
        lines.forEach {
            if (it.accountId == 0L) throw PostingException("اختر الحساب في كل سطر")
            if (it.debit < 0 || it.credit < 0) throw PostingException("لا يُسمح بمبالغ سالبة")
            if ((it.debit > 0) == (it.credit > 0)) throw PostingException("كل سطر إما مدين أو دائن")
        }
        val dr = Money.r(lines.sumOf { it.debit }); val cr = Money.r(lines.sumOf { it.credit })
        if (dr != cr) throw PostingException("القيد غير متوازن: مدين $dr ≠ دائن $cr")
    }
}
