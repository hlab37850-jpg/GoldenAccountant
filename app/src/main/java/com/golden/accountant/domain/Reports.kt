package com.golden.accountant.domain

import com.golden.accountant.data.*

data class StockPos(val itemId: Long, val qty: Double, val avgCost: Double) {
    /** قيمة المخزون: الأرصدة السالبة لا تُقيَّم. */
    val value: Double get() = Money.r(qty.coerceAtLeast(0.0) * avgCost)
}

/** تقييم المخزون بالمتوسط المرجح: (الافتتاحي + المشتريات) ÷ كمياتها؛ الكمية من كل الحركات. */
object Valuation {
    fun positions(items: List<Item>, rows: List<MoveRow>): List<StockPos> {
        val by = rows.groupBy { it.itemId }
        return items.map { item ->
            var q = item.openingQty; var buyQty = item.openingQty; var buyVal = item.openingQty * item.openingCost
            for (r in by[item.id].orEmpty()) when {
                (r.trType == TrType.PURCHASE && !r.isBack) || r.trType == TrType.SUPPLY -> { q += r.qty; buyQty += r.qty; buyVal += r.value }
                r.trType == TrType.SALE && r.isBack -> q += r.qty
                (r.trType == TrType.SALE && !r.isBack) || r.trType == TrType.ISSUE -> q -= r.qty
                r.trType == TrType.PURCHASE && r.isBack -> q -= r.qty
                r.trType == TrType.ADJUST -> q += r.qty
            }
            StockPos(item.id, Money.r(q), if (buyQty > 0) buyVal / buyQty else item.openingCost)
        }
    }
    fun totalValue(p: List<StockPos>): Double = Money.r(p.sumOf { it.value })
}

object ProfitLoss {
    data class Line(val label: String, val amount: Double)
    data class Result(
        val sales: Double, val salesReturns: Double, val discountAllowed: Double, val netSales: Double,
        val openingInventory: Double, val purchases: Double, val purchaseReturns: Double, val discountEarned: Double, val netPurchases: Double,
        val closingInventory: Double, val cogs: Double, val otherCosts: Double, val grossProfit: Double,
        val otherIncome: Double, val expenses: List<Line>, val totalExpenses: Double, val netProfit: Double,
    )

    /** الحسابات الورقية تحت مجموعة معينة (على أي عمق). */
    fun leavesUnder(accounts: List<Account>, groupId: Long): List<Account> {
        val kids = accounts.groupBy { it.parentId }
        val out = mutableListOf<Account>()
        fun walk(id: Long) { for (a in kids[id].orEmpty()) if (a.isGroup) walk(a.id) else out += a }
        walk(groupId)
        return out
    }

    /** totals: لكل حساب (مدين، دائن) خلال الفترة. */
    fun compute(totals: Map<Long, Pair<Double, Double>>, accounts: List<Account>, openingInv: Double, closingInv: Double): Result {
        fun dr(id: Long) = totals[id]?.let { it.first - it.second } ?: 0.0   // صافي مدين
        fun cr(id: Long) = -dr(id)                                           // صافي دائن
        val sales = cr(Sys.SALES); val sret = dr(Sys.SALES_RETURNS); val dAllowed = dr(Sys.DISCOUNT_ALLOWED)
        val netSales = Money.r(sales - sret - dAllowed)
        val purchases = dr(Sys.PURCHASES); val pret = cr(Sys.PURCHASE_RETURNS); val dEarned = cr(Sys.DISCOUNT_EARNED)
        val netPurchases = Money.r(purchases - pret - dEarned)
        val cogs = Money.r(openingInv + netPurchases - closingInv)
        val otherCosts = Money.r(dr(Sys.STOCK_ADJUST) + dr(Sys.DAMAGED_ITEMS))
        val gross = Money.r(netSales - cogs - otherCosts)
        val otherIncome = Money.r(leavesUnder(accounts, Sys.OTHER_REVENUE).sumOf { cr(it.id) })
        val expenses = leavesUnder(accounts, Sys.OPEX).map { Line(it.name, Money.r(dr(it.id))) }.filter { it.amount != 0.0 }
        val totalExp = Money.r(expenses.sumOf { it.amount })
        return Result(
            Money.r(sales), Money.r(sret), Money.r(dAllowed), netSales, Money.r(openingInv), Money.r(purchases), Money.r(pret), Money.r(dEarned), netPurchases,
            Money.r(closingInv), cogs, otherCosts, gross, otherIncome, expenses, totalExp, Money.r(gross + otherIncome - totalExp),
        )
    }
}
