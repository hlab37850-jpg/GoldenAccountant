package com.golden.accountant.domain

import com.golden.accountant.data.BillLine
import com.golden.accountant.data.Money

object InvoiceMath {
    data class Totals(
        val subtotal: Double, val discount: Double, val tax: Double,
        val extra: Double, val total: Double, val paid: Double, val remaining: Double,
    )

    /** يحوّل نصاً مدخلاً (يقبل الأرقام العربية والفاصلة العربية) إلى رقم؛ الفارغ = 0. */
    fun parse(text: String): Double {
        val sb = StringBuilder()
        for (c in text.trim()) when (c) {
            in '٠'..'٩' -> sb.append('0' + (c - '٠'))
            in '۰'..'۹' -> sb.append('0' + (c - '۰'))
            '٫', '،' -> sb.append('.')
            ',', '٬', ' ' -> {}
            else -> sb.append(c)
        }
        return sb.toString().toDoubleOrNull() ?: 0.0
    }

    fun totals(lines: List<BillLine>, discount: Double, taxPercent: Double, extra: Double, paid: Double): Totals {
        val sub = Money.r(lines.sumOf { it.lineTotal })
        val d = Money.r(discount.coerceIn(0.0, sub))
        val tax = PostingService.taxOf(sub, d, taxPercent)
        val ex = Money.r(extra.coerceAtLeast(0.0))
        val total = Money.r(sub - d + tax + ex)
        val p = Money.r(paid.coerceIn(0.0, total))
        return Totals(sub, d, tax, ex, total, p, Money.r(total - p))
    }
}
