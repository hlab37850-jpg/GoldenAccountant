package com.golden.accountant.domain

/** أنواع الحركات (نفس ترقيم القاعدة الأصلية tran_type). */
object TrType {
    const val OPENING = -1; const val JOURNAL = -2
    const val SALE = 1; const val PURCHASE = 2; const val TRANSFER = 3; const val ADJUST = 4
    const val RECEIPT = 5; const val PAYMENT = 6; const val STOCKTAKE = 7
    const val QUOTE = 8; const val PURCHASE_ORDER = 9
    const val FX_SELL = 10; const val ISSUE = 11; const val FX_BUY = 13; const val SUPPLY = 21

    /** مستندات لا تُنتج قيوداً محاسبية بنفسها. */
    val NON_POSTING = setOf(QUOTE, PURCHASE_ORDER, STOCKTAKE, TRANSFER, ADJUST)
}

/** نوع الحساب (cus_type الأصلي). */
object AccType { const val CUSTOMER = 0; const val SUPPLIER = 1; const val TRADE = 2; const val CASH = 4; const val EXPENSE = 5; const val REVENUE = 6; const val OTHER = 7 }

/** نوع التسوية المخزنية (adj_type الأصلي). */
object AdjType { const val SHORTAGE = 1; const val SURPLUS = 2; const val DAMAGED = 3; const val OPENING = 4 }

/**
 * أرقام الحسابات مطابقة للقاعدة الأصلية: المجموعات موجبة، والحسابات النظامية الفرعية سالبة.
 */
object Sys {
    // مجموعات رئيسية
    const val ASSETS = 1L; const val LIABILITIES_EQUITY = 2L; const val EXPENSES = 3L; const val INCOME = 4L
    const val FIXED_ASSETS = 11L; const val CURRENT_ASSETS = 12L
    const val EQUITY = 21L; const val CURRENT_LIAB = 22L; const val FIXED_LIAB = 23L
    const val COST_OF_ACTIVITY = 31L; const val OPEX = 32L
    const val REVENUE_ACTIVITY = 41L; const val OTHER_REVENUE = 42L
    const val CASH_BOXES = 121L; const val BANKS = 122L; const val CUSTOMERS = 123L; const val OTHER_ASSETS = 124L; const val INVENTORY_GROUP = 125L
    const val CAPITAL_GROUP = 211L; const val PROFIT_LOSS_GROUP = 212L; const val DRAWINGS_GROUP = 213L; const val PARTNERS_GROUP = 214L
    const val SUPPLIERS = 221L
    const val PURCHASES_GROUP = 311L; const val SALES_RETURNS_GROUP = 312L; const val DISCOUNT_ALLOWED_GROUP = 313L; const val STOCK_ADJ_GROUP = 314L; const val DAMAGED_GROUP = 315L
    const val ACTIVITY_EXPENSES = 321L; const val ADMIN_EXPENSES = 322L; const val OTHER_EXPENSES = 323L
    const val SALES_GROUP = 411L; const val PURCHASE_RETURNS_GROUP = 412L; const val DISCOUNT_EARNED_GROUP = 413L

    // حسابات فرعية نظامية (customers السالبة في القاعدة الأصلية)
    const val SALES_CREDIT = -1L; const val PURCHASE_CREDIT = -2L; const val CASH = -3L
    const val SALES_CASH = -5L; const val PURCHASE_CASH = -6L
    const val DISCOUNT_EARNED = -7L; const val DISCOUNT_ALLOWED = -8L
    const val SALES_RET_CREDIT = -9L; const val PURCHASE_RET_CREDIT = -10L
    const val SALES_RET_CASH = -11L; const val PURCHASE_RET_CASH = -12L
    const val CAPITAL = -13L; const val OPENING_STOCK = -14L
    const val STOCK_SHORTAGE = -15L; const val STOCK_DAMAGED = -16L
    const val PROFIT_LOSS = -17L; const val VAT = -18L
    const val OTHER_FEES = -20L; const val TRANSPORT = -24L
    const val FX_DIFF = -27L; const val STOCK_SETTLE = -30L

    /** اسم قديم للصندوق الافتراضي. */
    const val MAIN_CASH = CASH
}
