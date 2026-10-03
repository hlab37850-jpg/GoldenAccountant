package com.golden.accountant.ui.invoice

import com.golden.accountant.domain.TrType
import com.golden.accountant.ui.nav.Routes

/**
 * نوع مستند فاتورة. permRoute = الشاشة التي تُفحص صلاحياتها (المرتجع يتبع المبيعات/المشتريات كما في الأصل).
 * isStock = توريد/صرف مخزني: الطرف المقابل حساب حر (لا عميل/مورد).
 */
data class InvoiceKind(
    val route: String, val trType: Int, val isBack: Boolean, val title: String, val isSales: Boolean,
    val permRoute: String = route, val isStock: Boolean = false,
) {
    val posts: Boolean get() = trType !in TrType.NON_POSTING
    /** تُحسب الأسعار من أسعار البيع (مبيعات وعرض سعر) وإلا من التكلفة. */
    val usesSalePrice: Boolean get() = isSales && !isStock
}

object InvoiceKinds {
    val SALES = InvoiceKind("k_sales", TrType.SALE, false, "فاتورة مبيعات", true, Routes.SALES)
    val SALES_BACK = InvoiceKind("k_sales_back", TrType.SALE, true, "مرتجع مبيعات", true, Routes.SALES)
    val QUOTE = InvoiceKind("k_quote", TrType.QUOTE, false, "عرض سعر", true, Routes.QUOTE)
    val PURCHASE = InvoiceKind("k_purchase", TrType.PURCHASE, false, "فاتورة مشتريات", false, Routes.PURCHASE)
    val PURCHASE_BACK = InvoiceKind("k_purchase_back", TrType.PURCHASE, true, "مرتجع مشتريات", false, Routes.PURCHASE)
    val PURCHASE_ORDER = InvoiceKind("k_purchase_order", TrType.PURCHASE_ORDER, false, "طلب شراء", false, Routes.PURCHASE_ORDER)
    val SUPPLY = InvoiceKind("k_supply", TrType.SUPPLY, false, "توريد مخزني", false, Routes.SUPPLY, isStock = true)
    val ISSUE = InvoiceKind("k_issue", TrType.ISSUE, false, "صرف مخزني", true, Routes.ISSUE, isStock = true)

    val all = listOf(SALES, SALES_BACK, QUOTE, PURCHASE, PURCHASE_BACK, PURCHASE_ORDER, SUPPLY, ISSUE)
    fun byRoute(route: String?) = all.firstOrNull { it.route == route }
}
