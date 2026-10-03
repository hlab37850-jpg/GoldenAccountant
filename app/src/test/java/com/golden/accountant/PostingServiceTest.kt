package com.golden.accountant

import com.golden.accountant.data.*
import com.golden.accountant.domain.*
import org.junit.Assert.*
import org.junit.Test

private fun sum(j: JournalDraft, acc: Long, debit: Boolean) = j.lines.filter { it.accountId == acc }.sumOf { if (debit) it.debit else it.credit }

class PostingServiceTest {
    private fun bill(type: Int = TrType.SALE, billType: Int = BillType.CASH, back: Boolean = false, paid: Double = 0.0, tax: Double = 0.0, fees: Double = 0.0, included: Boolean = false) = Bill(
        trType = type, isBack = back, billType = billType, billNo = 1, date = "2026-10-02",
        partyAccountId = 9001, cashAccountId = Sys.CASH, subtotal = 1000.0, discount = 50.0,
        taxAmount = tax, extraCost = fees, paid = paid, taxIncluded = included,
    )

    @Test fun cashSaleSimple() {                       // مطابق bills_sales_insert_cash
        val j = PostingService.postBill(bill(), 1).single()
        assertEquals(950.0, sum(j, Sys.CASH, true), 0.0001)
        assertEquals(950.0, sum(j, Sys.SALES_CASH, false), 0.0001)
        assertEquals(0.0, sum(j, Sys.DISCOUNT_ALLOWED, true), 0.0001)   // الخصم صافٍ ولا يُرحَّل بحساب مستقل
    }

    @Test fun creditSaleWithTaxAndFees() {             // مطابق bills_sales_insert_t
        val b = bill(billType = BillType.CREDIT, tax = 47.5, fees = 20.0)   // 950 + 47.5 + 20 = 1017.5
        assertEquals(1017.5, b.total, 0.0001)
        val j = PostingService.postBill(b, 1).single()
        assertEquals(1017.5, sum(j, 9001, true), 0.0001)
        assertEquals(950.0, sum(j, Sys.SALES_CREDIT, false), 0.0001)
        assertEquals(47.5, sum(j, Sys.VAT, false), 0.0001)
        assertEquals(20.0, sum(j, Sys.TRANSPORT, false), 0.0001)         // حساب الرسوم الافتراضي: أجور نقل
    }

    @Test fun includedTaxDoesNotIncreaseTotal() {
        val tax = InvoiceMath.taxOf(950.0, 5.0, true)
        assertEquals(45.2381, tax, 0.0001)
        val b = bill(tax = tax, included = true)
        assertEquals(950.0, b.total, 0.0001)
        val j = PostingService.postBill(b, 1).single()
        assertEquals(950.0 - tax, sum(j, Sys.SALES_CASH, false), 0.0001)
    }

    @Test fun creditSaleWithPaidMakesSecondJournal() {
        val j = PostingService.postBill(bill(billType = BillType.CREDIT, paid = 500.0), 7)
        assertEquals(2, j.size)
        assertEquals(500.0, sum(j[1], Sys.CASH, true), 0.0001); assertEquals(500.0, sum(j[1], 9001, false), 0.0001)
    }

    @Test fun saleReturnUsesReturnAccounts() {
        val j = PostingService.postBill(bill(billType = BillType.CREDIT, back = true), 3).single()
        assertEquals(950.0, sum(j, 9001, false), 0.0001)
        assertEquals(950.0, sum(j, Sys.SALES_RET_CREDIT, true), 0.0001)
        val c = PostingService.postBill(bill(back = true), 3).single()
        assertEquals(950.0, sum(c, Sys.SALES_RET_CASH, true), 0.0001)
    }

    @Test fun creditPurchaseWithFeesPaidFromCash() {   // مطابق bills_purchase_insert_t
        val b = bill(TrType.PURCHASE, BillType.CREDIT, tax = 47.5, fees = 20.0)
        val j = PostingService.postBill(b, 4).single()
        assertEquals(1017.5 - 47.5, sum(j, Sys.PURCHASE_CREDIT, true), 0.0001)
        assertEquals(47.5, sum(j, Sys.VAT, true), 0.0001)
        assertEquals(1017.5 - 20.0, sum(j, 9001, false), 0.0001)
        assertEquals(20.0, sum(j, Sys.CASH, false), 0.0001)     // الرسوم تُدفع من الصندوق
    }

    @Test fun cashPurchaseAndReturn() {
        val j = PostingService.postBill(bill(TrType.PURCHASE), 4).single()
        assertEquals(950.0, sum(j, Sys.PURCHASE_CASH, true), 0.0001); assertEquals(950.0, sum(j, Sys.CASH, false), 0.0001)
        val r = PostingService.postBill(bill(TrType.PURCHASE, back = true), 4).single()
        assertEquals(950.0, sum(r, Sys.PURCHASE_RET_CASH, false), 0.0001)
    }

    @Test fun stockIssueAndSupplyUseSettlementAccount() {
        val out = PostingService.postBill(bill(TrType.ISSUE, BillType.CREDIT), 5).single()
        assertEquals(950.0, sum(out, 9001, true), 0.0001); assertEquals(950.0, sum(out, Sys.STOCK_SETTLE, false), 0.0001)
        val inn = PostingService.postBill(bill(TrType.SUPPLY, BillType.CREDIT), 5).single()
        assertEquals(950.0, sum(inn, Sys.STOCK_SETTLE, true), 0.0001); assertEquals(950.0, sum(inn, 9001, false), 0.0001)
    }

    @Test(expected = PostingException::class) fun paidMoreThanTotalRejected() { PostingService.postBill(bill(billType = BillType.CREDIT, paid = 99999.0)) }
    @Test fun nonPostingDocsReturnNothing() {
        listOf(TrType.QUOTE, TrType.PURCHASE_ORDER, TrType.TRANSFER, TrType.ADJUST).forEach { assertTrue(PostingService.postBill(bill(it)).isEmpty()) }
    }
}

class InvoiceMathTest {
    @Test fun parsesArabicDigits() {
        assertEquals(1250.5, InvoiceMath.parse("١٢٥٠٫٥"), 0.0001); assertEquals(0.0, InvoiceMath.parse(""), 0.0); assertEquals(1234.0, InvoiceMath.parse("1,234"), 0.0001)
    }
    private val lines = listOf(BillLine(itemId = 1, unitId = 1, qty = 2.0, price = 100.0), BillLine(itemId = 2, unitId = 1, qty = 1.0, price = 50.0, discount = 10.0))

    @Test fun percentDiscountAndExcludedTax() {
        val t = InvoiceMath.totals(lines, discountType = 0, discountValue = 10.0, taxPercent = 5.0, taxIncluded = false, extra = 5.0, paid = 9999.0)
        assertEquals(240.0, t.subtotal, 0.0001); assertEquals(24.0, t.discount, 0.0001)
        assertEquals(10.8, t.tax, 0.0001)                  // (240-24)*5%
        assertEquals(231.8, t.total, 0.0001); assertEquals(231.8, t.paid, 0.0001); assertEquals(0.0, t.remaining, 0.0001)
    }
    @Test fun amountDiscountCappedAndIncludedTax() {
        val t = InvoiceMath.totals(lines, 1, 9999.0, 5.0, true, 0.0, 0.0)
        assertEquals(240.0, t.discount, 0.0001); assertEquals(0.0, t.total, 0.0001)
        val t2 = InvoiceMath.totals(lines, 1, 40.0, 5.0, true, 0.0, 0.0)
        assertEquals(200.0, t2.total, 0.0001)              // المتضمنة لا تزيد الإجمالي
        assertEquals(9.5238, t2.tax, 0.0001)
    }
}

class VoucherPostingTest {
    private fun v(type: Int, d: Double = 0.0) = Voucher(type = type, voucherNo = 1, date = "2026-10-02", accountId = 9001, cashAccountId = Sys.CASH, amount = 950.0, discount = d)
    @Test fun receiptWithDiscountClearsFullDebt() {
        val j = VoucherPosting.postVoucher(v(TrType.RECEIPT, 50.0), 1)
        assertEquals(950.0, sum(j, Sys.CASH, true), 0.0001); assertEquals(50.0, sum(j, Sys.DISCOUNT_ALLOWED, true), 0.0001); assertEquals(1000.0, sum(j, 9001, false), 0.0001)
    }
    @Test fun paymentDirection() {
        val j = VoucherPosting.postVoucher(v(TrType.PAYMENT), 1)
        assertEquals(950.0, sum(j, 9001, true), 0.0001); assertEquals(950.0, sum(j, Sys.CASH, false), 0.0001)
    }
    @Test(expected = PostingException::class) fun zeroAmountRejected() { VoucherPosting.postVoucher(v(TrType.RECEIPT).copy(amount = 0.0)) }
    @Test(expected = PostingException::class) fun sameAccountRejected() { VoucherPosting.postVoucher(v(TrType.RECEIPT).copy(accountId = Sys.CASH)) }
    @Test fun manualJournalMustBalance() {
        VoucherPosting.validateManual(listOf(JournalLine(accountId = 1, debit = 10.0), JournalLine(accountId = 2, credit = 10.0)))
        try { VoucherPosting.validateManual(listOf(JournalLine(accountId = 1, debit = 10.0), JournalLine(accountId = 2, credit = 9.0))); fail() } catch (_: PostingException) {}
    }
}

class ReportsTest {
    private fun item(id: Long, q: Double, c: Double) = Item(id = id, name = "i$id", openingQty = q, openingCost = c)
    @Test fun weightedAverageAndQuantity() {
        val rows = listOf(MoveRow(1, TrType.PURCHASE, false, 10.0, 70.0), MoveRow(1, TrType.SALE, false, 4.0, 40.0), MoveRow(1, TrType.SALE, true, 1.0, 10.0), MoveRow(1, TrType.ADJUST, false, -2.0, 0.0))
        val p = Valuation.positions(listOf(item(1, 10.0, 5.0)), rows).single()
        assertEquals(15.0, p.qty, 0.0001); assertEquals(6.0, p.avgCost, 0.0001); assertEquals(90.0, p.value, 0.0001)
    }
    @Test fun negativeStockNotValued() {
        val p = Valuation.positions(listOf(item(1, 0.0, 5.0)), listOf(MoveRow(1, TrType.SALE, false, 3.0, 30.0))).single()
        assertEquals(-3.0, p.qty, 0.0001); assertEquals(0.0, p.value, 0.0001)
    }
    @Test fun profitLossFormula() {
        val accounts = listOf(
            Account(Sys.OPEX, "مصاريف", 0, Nature.EXPENSE, isGroup = true), Account(Sys.ADMIN_EXPENSES, "إدارية", Sys.OPEX, Nature.EXPENSE, isGroup = true),
            Account(-50, "كهرباء", Sys.ADMIN_EXPENSES, Nature.EXPENSE), Account(Sys.OTHER_REVENUE, "أخرى", 0, Nature.INCOME, isGroup = true), Account(-51, "رسوم", Sys.OTHER_REVENUE, Nature.INCOME),
        )
        val totals = mapOf(
            Sys.SALES_CASH to (0.0 to 600.0), Sys.SALES_CREDIT to (0.0 to 400.0), Sys.SALES_RET_CREDIT to (100.0 to 0.0), Sys.DISCOUNT_ALLOWED to (50.0 to 0.0),
            Sys.PURCHASE_CREDIT to (600.0 to 0.0), Sys.PURCHASE_RET_CASH to (0.0 to 50.0), -50L to (80.0 to 0.0), -51L to (0.0 to 20.0),
        )
        val r = ProfitLoss.compute(totals, accounts, openingInv = 200.0, closingInv = 250.0)
        assertEquals(850.0, r.netSales, 0.0001); assertEquals(550.0, r.netPurchases, 0.0001); assertEquals(500.0, r.cogs, 0.0001)
        assertEquals(350.0, r.grossProfit, 0.0001); assertEquals(290.0, r.netProfit, 0.0001)
    }
}

class PrivilegesTest {
    private fun p(v: Boolean = false, n: Boolean = false, e: Boolean = false, d: Boolean = false) = UserPriv(1, 1, v, n, e, d)
    @Test fun adminCanEverything() { Action.values().forEach { assertTrue(Privileges(true, emptyMap()).can("any", it)) } }
    @Test fun unknownRouteDenied() { assertFalse(Privileges(false, emptyMap()).can("sales_invoice", Action.VIEW)) }
    @Test fun actionRequiresView() {
        val pr = Privileges(false, mapOf("a" to p(v = false, n = true, e = true, d = true), "b" to p(v = true, n = true)))
        assertFalse(pr.can("a", Action.NEW)); assertFalse(pr.can("a", Action.DELETE)); assertTrue(pr.can("b", Action.NEW)); assertFalse(pr.can("b", Action.EDIT))
    }
}
