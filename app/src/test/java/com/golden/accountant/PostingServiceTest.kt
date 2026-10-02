package com.golden.accountant

import com.golden.accountant.data.*
import com.golden.accountant.domain.*
import org.junit.Assert.*
import org.junit.Test

class PostingServiceTest {
    private fun sale(type: Int, back: Boolean = false, paid: Double = 0.0) = Bill(
        trType = TrType.SALE, isBack = back, billType = type, billNo = 1, date = "2026-10-02",
        partyAccountId = 9001, cashAccountId = Sys.MAIN_CASH,
        subtotal = 1000.0, discount = 50.0, taxPercent = 15.0,
        taxAmount = PostingService.taxOf(1000.0, 50.0, 15.0), extraCost = 20.0, paid = paid,
    )

    @Test fun cashSaleBalances() {
        val j = PostingService.postBill(sale(BillType.CASH, paid = 0.0), 1)
        assertEquals(1, j.size)
        val dr = j[0].lines.sumOf { it.debit }; val cr = j[0].lines.sumOf { it.credit }
        assertEquals(dr, cr, 0.0001)
        // الإجمالي = 1000 - 50 + 142.5 + 20 = 1112.5
        assertEquals(1112.5, sale(BillType.CASH).total, 0.0001)
        assertEquals(1112.5, j[0].lines.first { it.accountId == Sys.MAIN_CASH }.debit, 0.0001)
    }

    @Test fun creditSaleWithPaidMakesSecondJournal() {
        val j = PostingService.postBill(sale(BillType.CREDIT, paid = 500.0), 7)
        assertEquals(2, j.size)
        assertEquals(500.0, j[1].lines.first { it.accountId == Sys.MAIN_CASH }.debit, 0.0001)
        assertEquals(500.0, j[1].lines.first { it.accountId == 9001L }.credit, 0.0001)
    }

    @Test fun saleReturnReversesDirection() {
        val j = PostingService.postBill(sale(BillType.CREDIT, back = true), 3)
        assertEquals(1112.5, j[0].lines.first { it.accountId == 9001L }.credit, 0.0001)
        assertTrue(j[0].lines.any { it.accountId == Sys.SALES_RETURNS && it.debit == 1000.0 })
    }

    @Test fun purchaseBalances() {
        val b = sale(BillType.CREDIT).copy(trType = TrType.PURCHASE)
        val j = PostingService.postBill(b, 4)
        assertEquals(j[0].lines.sumOf { it.debit }, j[0].lines.sumOf { it.credit }, 0.0001)
        assertEquals(1020.0, j[0].lines.first { it.accountId == Sys.PURCHASES }.debit, 0.0001)
    }

    @Test(expected = PostingException::class) fun paidMoreThanTotalRejected() {
        PostingService.postBill(sale(BillType.CREDIT, paid = 99999.0))
    }

    @Test fun quotesDoNotPost() {
        assertTrue(PostingService.postBill(sale(BillType.CASH).copy(trType = TrType.QUOTE)).isEmpty())
    }
}

class InvoiceMathTest {
    @Test fun parsesArabicDigits() {
        assertEquals(1250.5, InvoiceMath.parse("١٢٥٠٫٥"), 0.0001)
        assertEquals(0.0, InvoiceMath.parse(""), 0.0)
        assertEquals(1234.0, InvoiceMath.parse("1,234"), 0.0001)
    }
    @Test fun totalsCapDiscountAndPaid() {
        val lines = listOf(BillLine(itemId = 1, unitId = 1, qty = 2.0, price = 100.0), BillLine(itemId = 2, unitId = 1, qty = 1.0, price = 50.0, discount = 10.0))
        val t = InvoiceMath.totals(lines, discount = 40.0, taxPercent = 10.0, extra = 5.0, paid = 9999.0)
        assertEquals(240.0, t.subtotal, 0.0001)
        assertEquals(20.0, t.tax, 0.0001)      // (240-40)*10%
        assertEquals(225.0, t.total, 0.0001)   // 240-40+20+5
        assertEquals(225.0, t.paid, 0.0001)    // مقيّد بالإجمالي
        assertEquals(0.0, t.remaining, 0.0001)
    }
}

class VoucherPostingTest {
    private fun v(type: Int, d: Double = 0.0) = Voucher(type = type, voucherNo = 1, date = "2026-10-02", accountId = 9001, cashAccountId = Sys.MAIN_CASH, amount = 950.0, discount = d)

    @Test fun receiptWithDiscountClearsFullDebt() {
        val j = VoucherPosting.postVoucher(v(TrType.RECEIPT, 50.0), 1)
        assertEquals(950.0, j.lines.first { it.accountId == Sys.MAIN_CASH }.debit, 0.0001)
        assertEquals(50.0, j.lines.first { it.accountId == Sys.DISCOUNT_ALLOWED }.debit, 0.0001)
        assertEquals(1000.0, j.lines.first { it.accountId == 9001L }.credit, 0.0001)
    }
    @Test fun paymentDirection() {
        val j = VoucherPosting.postVoucher(v(TrType.PAYMENT), 1)
        assertEquals(950.0, j.lines.first { it.accountId == 9001L }.debit, 0.0001)
        assertEquals(950.0, j.lines.first { it.accountId == Sys.MAIN_CASH }.credit, 0.0001)
    }
    @Test(expected = PostingException::class) fun zeroAmountRejected() { VoucherPosting.postVoucher(v(TrType.RECEIPT).copy(amount = 0.0)) }
    @Test(expected = PostingException::class) fun sameAccountRejected() { VoucherPosting.postVoucher(v(TrType.RECEIPT).copy(accountId = Sys.MAIN_CASH)) }
    @Test fun manualJournalMustBalance() {
        VoucherPosting.validateManual(listOf(JournalLine(accountId = 1, debit = 10.0), JournalLine(accountId = 2, credit = 10.0)))
        try { VoucherPosting.validateManual(listOf(JournalLine(accountId = 1, debit = 10.0), JournalLine(accountId = 2, credit = 9.0))); fail() } catch (_: PostingException) {}
        try { VoucherPosting.validateManual(listOf(JournalLine(accountId = 1, debit = 10.0, credit = 10.0), JournalLine(accountId = 2, credit = 0.0))); fail() } catch (_: PostingException) {}
    }
}

class ReportsTest {
    private fun item(id: Long, q: Double, c: Double) = Item(id = id, name = "i$id", openingQty = q, openingCost = c)

    @Test fun weightedAverageAndQuantity() {
        // افتتاحي 10 بسعر 5، اشتريت 10 بسعر 7 (قيمة 70)، بعت 4، مرتجع بيع 1، تسوية -2
        val rows = listOf(
            MoveRow(1, TrType.PURCHASE, false, 10.0, 70.0), MoveRow(1, TrType.SALE, false, 4.0, 40.0),
            MoveRow(1, TrType.SALE, true, 1.0, 10.0), MoveRow(1, TrType.ADJUST, false, -2.0, 0.0),
        )
        val p = Valuation.positions(listOf(item(1, 10.0, 5.0)), rows).single()
        assertEquals(15.0, p.qty, 0.0001)             // 10+10-4+1-2
        assertEquals(6.0, p.avgCost, 0.0001)          // (50+70)/20
        assertEquals(90.0, p.value, 0.0001)
    }
    @Test fun negativeStockNotValued() {
        val p = Valuation.positions(listOf(item(1, 0.0, 5.0)), listOf(MoveRow(1, TrType.SALE, false, 3.0, 30.0))).single()
        assertEquals(-3.0, p.qty, 0.0001); assertEquals(0.0, p.value, 0.0001)
    }
    @Test fun profitLossFormula() {
        val accounts = listOf(
            Account(Sys.OPEX, "مصاريف", 0, Nature.EXPENSE, isGroup = true), Account(Sys.ADMIN_EXPENSES, "إدارية", Sys.OPEX, Nature.EXPENSE),
            Account(Sys.OTHER_REVENUE, "أخرى", 0, Nature.INCOME, isGroup = true), Account(Sys.FEES_INCOME, "رسوم", Sys.OTHER_REVENUE, Nature.INCOME),
        )
        val totals = mapOf(
            Sys.SALES to (0.0 to 1000.0), Sys.SALES_RETURNS to (100.0 to 0.0), Sys.DISCOUNT_ALLOWED to (50.0 to 0.0),
            Sys.PURCHASES to (600.0 to 0.0), Sys.PURCHASE_RETURNS to (0.0 to 50.0),
            Sys.ADMIN_EXPENSES to (80.0 to 0.0), Sys.FEES_INCOME to (0.0 to 20.0),
        )
        val r = ProfitLoss.compute(totals, accounts, openingInv = 200.0, closingInv = 250.0)
        assertEquals(850.0, r.netSales, 0.0001)          // 1000-100-50
        assertEquals(550.0, r.netPurchases, 0.0001)      // 600-50
        assertEquals(500.0, r.cogs, 0.0001)              // 200+550-250
        assertEquals(350.0, r.grossProfit, 0.0001)
        assertEquals(290.0, r.netProfit, 0.0001)         // 350+20-80
    }
}

class PrivilegesTest {
    private fun p(v: Boolean = false, n: Boolean = false, e: Boolean = false, d: Boolean = false) = UserPriv(1, 1, v, n, e, d)

    @Test fun adminCanEverything() {
        val pr = Privileges(true, emptyMap())
        Action.values().forEach { assertTrue(pr.can("any", it)) }
    }
    @Test fun unknownRouteDenied() { assertFalse(Privileges(false, emptyMap()).can("sales_invoice", Action.VIEW)) }
    @Test fun actionRequiresView() {
        val pr = Privileges(false, mapOf("a" to p(v = false, n = true, e = true, d = true), "b" to p(v = true, n = true)))
        assertFalse(pr.can("a", Action.NEW)); assertFalse(pr.can("a", Action.DELETE))
        assertTrue(pr.can("b", Action.NEW)); assertFalse(pr.can("b", Action.EDIT))
    }
}
