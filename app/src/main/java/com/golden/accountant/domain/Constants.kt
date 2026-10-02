package com.golden.accountant.domain

/** أنواع الحركات (نفس ترقيم قاعدة البيانات المرجعية لتسهيل الاستيراد لاحقاً). */
object TrType {
    const val OPENING = -1; const val JOURNAL = -2
    const val SALE = 1; const val PURCHASE = 2; const val TRANSFER = 3; const val ADJUST = 4
    const val RECEIPT = 5; const val PAYMENT = 6; const val STOCKTAKE = 7
    const val QUOTE = 8; const val PURCHASE_ORDER = 9
    const val FX_SELL = 10; const val ISSUE = 11; const val FX_BUY = 13; const val SUPPLY = 21

    /** أنواع لا تُنتج قيوداً محاسبية (مستندات فقط). */
    val NON_POSTING = setOf(QUOTE, PURCHASE_ORDER, STOCKTAKE, TRANSFER, ADJUST)
}

/** معرّفات الحسابات النظامية الثابتة (تُزرع عند أول تشغيل). */
object Sys {
    // مجموعات
    const val ASSETS = 1L; const val LIABILITIES_EQUITY = 2L; const val EXPENSES = 3L; const val INCOME = 4L
    const val FIXED_ASSETS = 11L; const val CURRENT_ASSETS = 12L
    const val EQUITY = 21L; const val CURRENT_LIAB = 22L; const val FIXED_LIAB = 23L
    const val COST_OF_ACTIVITY = 31L; const val OPEX = 32L
    const val REVENUE_ACTIVITY = 41L; const val OTHER_REVENUE = 42L
    const val CASH_BOXES = 121L; const val BANKS = 122L; const val CUSTOMERS = 123L; const val OTHER_ASSETS = 124L
    const val INVENTORY_GROUP = 125L
    const val CAPITAL_GROUP = 211L; const val PROFIT_LOSS_GROUP = 212L; const val DRAWINGS_GROUP = 213L; const val PARTNERS_GROUP = 214L
    const val SUPPLIERS = 221L
    // حسابات تشغيلية (أوراق)
    const val MAIN_CASH = 1211L
    const val VAT = 1241L
    const val INVENTORY = 1251L
    const val CAPITAL = 2111L
    const val PURCHASES = 3111L
    const val SALES_RETURNS = 3121L
    const val DISCOUNT_ALLOWED = 3131L
    const val STOCK_ADJUST = 3141L
    const val DAMAGED_ITEMS = 3151L
    const val ACTIVITY_EXPENSES = 3211L
    const val ADMIN_EXPENSES = 3221L
    const val OTHER_EXPENSES = 3231L
    const val FX_DIFF = 3232L
    const val SALES = 4111L
    const val PURCHASE_RETURNS = 4121L
    const val DISCOUNT_EARNED = 4131L
    const val FEES_INCOME = 4201L
}
