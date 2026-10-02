package com.golden.accountant.ui.invoice

import com.golden.accountant.domain.TrType
import com.golden.accountant.ui.nav.Routes

data class InvoiceKind(val route: String, val trType: Int, val isBack: Boolean, val title: String, val isSales: Boolean) {
    val posts: Boolean get() = trType !in TrType.NON_POSTING
}

object InvoiceKinds {
    val SALES = InvoiceKind(Routes.SALES, TrType.SALE, false, "فاتورة مبيعات", true)
    val SALES_BACK = InvoiceKind(Routes.SALES_BACK, TrType.SALE, true, "مرتجع مبيعات", true)
    val QUOTE = InvoiceKind(Routes.QUOTE, TrType.QUOTE, false, "عرض سعر", true)
    val PURCHASE = InvoiceKind(Routes.PURCHASE, TrType.PURCHASE, false, "فاتورة مشتريات", false)
    val PURCHASE_BACK = InvoiceKind(Routes.PURCHASE_BACK, TrType.PURCHASE, true, "مرتجع مشتريات", false)
    val PURCHASE_ORDER = InvoiceKind(Routes.PURCHASE_ORDER, TrType.PURCHASE_ORDER, false, "طلب شراء", false)

    val salesFamily = listOf(SALES, SALES_BACK, QUOTE)
    val purchaseFamily = listOf(PURCHASE, PURCHASE_BACK, PURCHASE_ORDER)
    val all = salesFamily + purchaseFamily
    fun byRoute(route: String?) = all.firstOrNull { it.route == route }
}
