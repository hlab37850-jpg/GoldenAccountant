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

    /** مبلغ الخصم من المدخل: نسبة (0) أو مبلغ (1)، لا يتجاوز المجموع. */
    fun discountAmount(subtotal: Double, discountType: Int, value: Double): Double =
        Money.r((if (discountType == 0) subtotal * value / 100.0 else value).coerceIn(0.0, subtotal))

    /** الضريبة: متضمنة (تُستخرج من السعر) أو غير متضمنة (تُضاف). */
    fun taxOf(base: Double, percent: Double, included: Boolean): Double =
        Money.r(if (percent <= 0) 0.0 else if (included) base * percent / (100.0 + percent) else base * percent / 100.0)

    fun totals(
        lines: List<BillLine>, discountType: Int, discountValue: Double,
        taxPercent: Double, taxIncluded: Boolean, extra: Double, paid: Double,
    ): Totals {
        val sub = Money.r(lines.sumOf { it.lineTotal })
        val d = discountAmount(sub, discountType, discountValue)
        val base = Money.r(sub - d)
        val tax = taxOf(base, taxPercent, taxIncluded)
        val ex = Money.r(extra.coerceAtLeast(0.0))
        val total = Money.r(if (taxIncluded) base + ex else base + tax + ex)
        val p = Money.r(paid.coerceIn(0.0, total))
        return Totals(sub, d, tax, ex, total, p, Money.r(total - p))
    }
}
