package com.golden.accountant.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** طبيعة الحساب في شجرة الحسابات. */
object Nature { const val ASSET = 1; const val LIABILITY = 2; const val EXPENSE = 3; const val INCOME = 4; const val EQUITY = 5 }

@Entity(tableName = "accounts", indices = [Index("parentId"), Index(value = ["name"], unique = true)])
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long = 0,
    val nature: Int,
    val isGroup: Boolean = false,
    val isSystem: Boolean = false,
)

/** عميل أو مورد؛ لكل طرف حساب فرعي في الشجرة. */
@Entity(tableName = "parties", indices = [Index("accountId", unique = true)])
data class Party(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val name: String,
    val kind: Int = KIND_CUSTOMER,
    val phone: String = "",
    val address: String = "",
    val creditLimit: Double = 0.0,
    val remarks: String = "",
    val createdAt: String = "",
) { companion object { const val KIND_CUSTOMER = 0; const val KIND_SUPPLIER = 1; const val KIND_BOTH = 2 } }

@Entity(tableName = "currencies")
data class Currency(
    @PrimaryKey val id: Long,
    val name: String,
    val code: String,
    val subName: String = "",
    val isLocal: Boolean = false,
)

/** سعر العملة مقابل المحلية ابتداءً من تاريخ معين (ISO yyyy-MM-dd). */
@Entity(tableName = "currency_rates", indices = [Index(value = ["currencyId", "fromDate"], unique = true)])
data class CurrencyRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val currencyId: Long,
    val fromDate: String,
    val price: Double,
)

@Entity(tableName = "units")
data class UnitDef(@PrimaryKey val id: Long, val name: String, val code: String = "")

@Entity(tableName = "item_types")
data class ItemType(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "items", indices = [Index(value = ["name"], unique = true), Index(value = ["barcode"])])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val typeId: Long = 0,
    val baseUnitId: Long = 0,
    val barcode: String? = null,
    val openingQty: Double = 0.0,
    val openingCost: Double = 0.0,
    val openingDate: String = "",
    val salePrice: Double = 0.0,
    val currencyId: Long = 0,
    val isActive: Boolean = true,
    val remarks: String = "",
)

/** وحدات الصنف: factor = كم وحدة أساسية في هذه الوحدة (الأساسية = 1). */
@Entity(tableName = "item_units", primaryKeys = ["itemId", "unitId"])
data class ItemUnit(val itemId: Long, val unitId: Long, val factor: Double = 1.0, val salePrice: Double = 0.0)

@Entity(tableName = "taxes")
data class Tax(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val percent: Double,
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
)

@Entity(tableName = "branches")
data class Branch(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val address: String = "", val phone: String = "")

@Entity(tableName = "users", indices = [Index(value = ["userName"], unique = true)])
data class AppUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userName: String,
    val name: String,
    val pwdHash: String,
    val isActive: Boolean = true,
    val cashAccountId: Long = 0,
    val branchId: Long = 1,
    val isAdmin: Boolean = false,
)

@Entity(tableName = "screens")
data class Screen(@PrimaryKey val id: Long, val name: String, val parentId: Long = 0)

@Entity(tableName = "user_priv", primaryKeys = ["userId", "screenId"])
data class UserPriv(
    val userId: Long, val screenId: Long,
    val canView: Boolean = false, val canNew: Boolean = false,
    val canEdit: Boolean = false, val canDelete: Boolean = false,
)

/** تاريخ إقفال السنة: لا يُسمح بأي حركة بتاريخ <= آخر إقفال. */
@Entity(tableName = "closing_years")
data class ClosingYear(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: String)

/** فاتورة: بيع/شراء/مرتجع/عرض سعر... بحسب trType. */
@Entity(tableName = "bills", indices = [Index(value = ["trType", "isBack", "branchId", "billNo"], unique = true), Index("partyAccountId"), Index("date")])
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trType: Int,
    val isBack: Boolean = false,
    val billType: Int = BillType.CASH,
    val billNo: Int = 0,
    val date: String,
    val time: String = "",
    val branchId: Long = 1,
    val partyAccountId: Long = 0,
    val cashAccountId: Long = 0,
    val currencyId: Long = 0,
    val rate: Double = 1.0,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val extraCost: Double = 0.0,
    val paid: Double = 0.0,
    val remarks: String = "",
    val userId: Long = 0,
    /** للتحويل المخزني فقط: الفرع المستلم (branchId = الفرع المرسل). */
    val toBranchId: Long = 0,
) {
    /** الإجمالي المستحق = المجموع - الخصم + الضريبة + الرسوم. */
    val total: Double get() = Money.r(subtotal - discount + taxAmount + extraCost)
}

object BillType { const val CASH = 1; const val CREDIT = 2 }

@Entity(tableName = "bill_lines", indices = [Index("billId"), Index("itemId")])
data class BillLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long = 0,
    val itemId: Long,
    val unitId: Long,
    val unitFactor: Double = 1.0,
    val qty: Double,
    val price: Double,
    val cost: Double = 0.0,
    val discount: Double = 0.0,
    val remarks: String = "",
) { val lineTotal: Double get() = Money.r(qty * price - discount) }

/** رأس قيد يومية؛ المبالغ بعملة القيد (currencyId). */
@Entity(tableName = "journal_headers", indices = [Index("date"), Index(value = ["refType", "refId"])])
data class JournalHeader(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String = "",
    val kind: Int,
    val refType: Int = 0,
    val refId: Long = 0,
    val currencyId: Long = 0,
    val rate: Double = 1.0,
    val note: String = "",
    val userId: Long = 0,
)

@Entity(tableName = "journal_lines", indices = [Index("journalId"), Index("accountId")])
data class JournalLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journalId: Long = 0,
    val accountId: Long,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val note: String = "",
)

/** سند قبض (5) أو صرف (6) بين حساب وصندوق/بنك. */
@Entity(tableName = "vouchers", indices = [Index(value = ["type", "voucherNo"], unique = true), Index("accountId")])
data class Voucher(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val voucherNo: Int = 0,
    val date: String,
    val time: String = "",
    val accountId: Long,
    val cashAccountId: Long,
    val amount: Double,
    val discount: Double = 0.0,
    val currencyId: Long = 0,
    val rate: Double = 1.0,
    val note: String = "",
    val userId: Long = 0,
)

object RefType { const val NONE = 0; const val BILL = 1; const val VOUCHER = 2; const val MANUAL = 3; const val OPENING = 4 }

object Money {
    /** تقريب إلى 4 منازل لتفادي أخطاء الفاصلة العائمة عند الجمع. */
    fun r(v: Double): Double = Math.round(v * 10000.0) / 10000.0
}
