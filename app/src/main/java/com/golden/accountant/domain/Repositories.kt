package com.golden.accountant.domain

import androidx.room.withTransaction
import com.golden.accountant.data.*

class ClosedYearException(date: String) : IllegalStateException("السنة المالية مقفلة حتى $date، لا يمكن التعديل")
class CreditLimitException(name: String, limit: Double) : IllegalStateException("تجاوزت سقف حساب $name (${Money.r(limit)})")

/** العملة الأجنبية تتطلب سعر صرف معرّفاً؛ لا نفترض 1 أبداً لأن ذلك يفسد الأرصدة والأرباح. */
internal suspend fun AppDatabase.requireRate(currencyId: Long, date: String): Double {
    if (currencyId == 0L) return 1.0
    return core().rateOn(currencyId, date) ?: throw PostingException("حدد سعر صرف هذه العملة قبل التاريخ $date من شاشة سعر العملات")
}

internal suspend fun AppDatabase.assertOpenDate(date: String) {
    val last = core().lastClosing()
    if (last != null && date <= last) throw ClosedYearException(last)
}

/**
 * سقف الحساب (cus_limit الأصلي): بعد أي ترحيل يجب أن يبقى رصيد الحساب بين −db و +cr بتلك العملة.
 * 0 في أحد الاتجاهين = بلا سقف. يُرفض فقط الترحيل الذي يزيد الرصيد خروجاً عن السقف (التسديد مسموح دائماً).
 */
internal suspend fun AppDatabase.enforceLimits(lines: List<JournalLine>, currencyId: Long) {
    val byAccount = lines.groupBy { it.accountId }
    for ((acc, ls) in byAccount) {
        val lim = limits().get(acc, currencyId) ?: continue
        val delta = Money.r(ls.sumOf { it.debit - it.credit })
        val after = accounts().balance(acc, currencyId)           // بعد الترحيل (داخل المعاملة)
        if (lim.cr > 0 && after > lim.cr && delta > 0) throw CreditLimitException(accounts().byId(acc)?.name ?: "", lim.cr)
        if (lim.db > 0 && after < -lim.db && delta < 0) throw CreditLimitException(accounts().byId(acc)?.name ?: "", lim.db)
    }
}

/** كل عمليات الحفظ والحذف تتم داخل معاملة واحدة: إما تنجح كلها أو لا شيء. */
class BillRepository(private val db: AppDatabase) {

    suspend fun save(bill: Bill, lines: List<BillLine>): Long = db.withTransaction {
        db.assertOpenDate(bill.date)
        val old = if (bill.id != 0L) db.bills().byId(bill.id) else null
        if (old != null) db.assertOpenDate(old.date)

        val no = if (bill.billNo > 0) bill.billNo else db.bills().nextNo(bill.trType, bill.isBack, bill.branchId)
        val rate = when {
            bill.currencyId == 0L -> 1.0
            bill.rate > 0 && bill.rate != 1.0 -> bill.rate                       // سعر أدخله المستخدم يدوياً
            bill.trType in TrType.NON_POSTING -> db.core().rateOn(bill.currencyId, bill.date) ?: 1.0
            else -> db.requireRate(bill.currencyId, bill.date)
        }
        val prepared = bill.copy(billNo = no, rate = rate)

        val id = if (old == null) db.bills().insert(prepared) else { db.bills().update(prepared); prepared.id }
        db.bills().deleteLines(id)
        db.bills().insertLines(lines.map { it.copy(id = 0, billId = id) })

        db.journal().deleteLinesByRef(RefType.BILL, id)
        db.journal().deleteHeadersByRef(RefType.BILL, id)
        PostingService.postBill(prepared, id).forEach { d ->
            val hid = db.journal().insertHeader(d.header)
            val ls = d.lines.map { it.copy(id = 0, journalId = hid) }
            db.journal().insertLines(ls)
            db.enforceLimits(ls, prepared.currencyId)
        }
        id
    }

    suspend fun delete(billId: Long) = db.withTransaction {
        val b = db.bills().byId(billId) ?: return@withTransaction
        db.assertOpenDate(b.date)
        db.journal().deleteLinesByRef(RefType.BILL, billId)
        db.journal().deleteHeadersByRef(RefType.BILL, billId)
        db.bills().deleteLines(billId)
        db.bills().delete(billId)
    }
}

class PartyRepository(private val db: AppDatabase) {
    /** ينشئ حساباً فرعياً تحت العملاء/الموردين ثم بطاقة الطرف. */
    suspend fun create(name: String, kind: Int, phone: String = "", address: String = "", creditLimit: Double = 0.0): Long =
        db.withTransaction {
            val parent = if (kind == Party.KIND_SUPPLIER) Sys.SUPPLIERS else Sys.CUSTOMERS
            val nature = if (kind == Party.KIND_SUPPLIER) Nature.LIABILITY else Nature.ASSET
            val type = if (kind == Party.KIND_SUPPLIER) AccType.SUPPLIER else AccType.CUSTOMER
            val accId = db.accounts().insert(Account(name = name.trim(), parentId = parent, nature = nature, type = type))
            db.parties().insert(Party(accountId = accId, name = name.trim(), kind = kind, phone = phone, address = address, creditLimit = creditLimit))
        }
}

class StockService(private val db: AppDatabase) {
    /** الرصيد الحالي بالوحدة الأساسية = الافتتاحي + حركة الفواتير. */
    suspend fun onHand(itemId: Long): Double {
        val item = db.items().byId(itemId) ?: return 0.0
        return Money.r(item.openingQty + db.items().movementQty(itemId))
    }
}

class VoucherRepository(private val db: AppDatabase) {
    private suspend fun requireLeaf(id: Long, what: String) {
        val a = db.accounts().byId(id) ?: throw PostingException("$what غير موجود")
        if (a.isGroup) throw PostingException("$what لا يمكن أن يكون حساباً رئيسياً")
    }

    suspend fun save(v: Voucher): Long = db.withTransaction {
        db.assertOpenDate(v.date)
        val old = if (v.id != 0L) db.vouchers().byId(v.id) else null
        if (old != null) db.assertOpenDate(old.date)
        requireLeaf(v.accountId, "الحساب"); requireLeaf(v.cashAccountId, "الصندوق")

        val no = if (v.voucherNo > 0) v.voucherNo else db.vouchers().nextNo(v.type)
        val rate = if (v.currencyId != 0L && v.rate > 0 && v.rate != 1.0) v.rate else db.requireRate(v.currencyId, v.date)
        val prepared = v.copy(voucherNo = no, rate = rate)
        val draft = VoucherPosting.postVoucher(prepared, prepared.id) // يتحقق قبل أي كتابة

        val id = if (old == null) db.vouchers().insert(prepared) else { db.vouchers().update(prepared); prepared.id }
        db.journal().deleteLinesByRef(RefType.VOUCHER, id)
        db.journal().deleteHeadersByRef(RefType.VOUCHER, id)
        val hid = db.journal().insertHeader(draft.header.copy(refId = id))
        val ls = draft.lines.map { it.copy(id = 0, journalId = hid) }
        db.journal().insertLines(ls)
        db.enforceLimits(ls, prepared.currencyId)
        id
    }

    suspend fun delete(id: Long) = db.withTransaction {
        val v = db.vouchers().byId(id) ?: return@withTransaction
        db.assertOpenDate(v.date)
        db.journal().deleteLinesByRef(RefType.VOUCHER, id)
        db.journal().deleteHeadersByRef(RefType.VOUCHER, id)
        db.vouchers().delete(id)
    }
}

class JournalRepository(private val db: AppDatabase) {
    /** opening=true: قيد افتتاحي (نوع −1) وإلا قيد يومي (−2). */
    suspend fun saveManual(date: String, note: String, currencyId: Long, lines: List<JournalLine>, opening: Boolean = false): Long = db.withTransaction {
        db.assertOpenDate(date)
        VoucherPosting.validateManual(lines)
        lines.map { it.accountId }.distinct().forEach { id ->
            val a = db.accounts().byId(id) ?: throw PostingException("حساب غير موجود")
            if (a.isGroup) throw PostingException("لا يمكن الترحيل إلى حساب رئيسي: ${a.name}")
        }
        val rate = db.requireRate(currencyId, date)
        val hid = db.journal().insertHeader(
            JournalHeader(date = date, kind = if (opening) TrType.OPENING else TrType.JOURNAL, refType = RefType.MANUAL, currencyId = currencyId, rate = rate,
                note = note.ifBlank { if (opening) "قيد افتتاحي" else "قيد يومية" }, userId = Session.userId)
        )
        val ls = lines.map { it.copy(id = 0, journalId = hid, debit = Money.r(it.debit), credit = Money.r(it.credit)) }
        db.journal().insertLines(ls)
        db.enforceLimits(ls, currencyId)
        hid
    }

    suspend fun delete(journalId: Long) = db.withTransaction {
        val h = db.journal().header(journalId) ?: return@withTransaction
        if (h.refType != RefType.MANUAL) throw PostingException("هذا قيد آلي من فاتورة أو سند، احذف مصدره")
        db.assertOpenDate(h.date)
        db.journal().deleteLines(journalId); db.journal().deleteHeader(journalId)
    }
}

/** إضافة/تعديل/حذف الحسابات والأطراف مع الحماية من كسر السجلات. */
class AccountRepository(private val db: AppDatabase) {
    suspend fun updateParty(p: Party) = db.withTransaction {
        db.parties().update(p); db.accounts().rename(p.accountId, p.name)
    }

    suspend fun deleteParty(p: Party) = db.withTransaction {
        if (db.journal().countLines(p.accountId) > 0) throw PostingException("لا يمكن حذف ${p.name}: له حركات مسجلة")
        db.parties().delete(p.id); db.accounts().delete(p.accountId)
    }

    companion object {
        /** الحساب الأب الافتراضي لكل نوع حساب (كما في القاعدة الأصلية). */
        fun defaultParentId(type: Int): Long = when (type) {
            AccType.CUSTOMER -> Sys.CUSTOMERS; AccType.SUPPLIER -> Sys.SUPPLIERS; AccType.TRADE -> Sys.SALES_GROUP
            AccType.CASH -> Sys.CASH_BOXES; AccType.EXPENSE -> Sys.OTHER_EXPENSES; AccType.REVENUE -> Sys.OTHER_REVENUE; else -> Sys.OTHER_ASSETS
        }
    }

    /** يضيف حساباً بنوع وأب؛ العملاء والموردون تُنشأ لهم بطاقة طرف أيضاً. */
    suspend fun addAccount(name: String, type: Int, parent: Account): Long = db.withTransaction {
        if (name.isBlank()) throw PostingException("اكتب اسم الحساب")
        if (!parent.isGroup) throw PostingException("الحساب الأب يجب أن يكون رئيسياً")
        val id = db.accounts().insert(Account(name = name.trim(), parentId = parent.id, nature = parent.nature, type = type))
        if (type == AccType.CUSTOMER || type == AccType.SUPPLIER)
            db.parties().insert(Party(accountId = id, name = name.trim(), kind = if (type == AccType.SUPPLIER) Party.KIND_SUPPLIER else Party.KIND_CUSTOMER))
        id
    }

    suspend fun addAccount(name: String, parent: Account): Long = addAccount(name, AccType.OTHER, parent)

    suspend fun deleteAccount(a: Account) = db.withTransaction {
        if (a.isSystem) throw PostingException("حساب نظامي لا يُحذف")
        if (db.accounts().childCount(a.id) > 0) throw PostingException("للحساب حسابات فرعية")
        if (db.journal().countLines(a.id) > 0) throw PostingException("للحساب حركات مسجلة")
        db.parties().byAccount(a.id)?.let { db.parties().delete(it.id) }
        db.accounts().delete(a.id)
    }
}

class ItemRepository(private val db: AppDatabase) {
    /** يحفظ الصنف ووحداته وقيده الافتتاحي معاً: الوحدة الأساسية دائماً بمعامل 1 وسعر البيع الأساسي. */
    suspend fun save(item: Item, extraUnits: List<ItemUnit>): Long = db.withTransaction {
        if (item.name.isBlank()) throw PostingException("اكتب اسم الصنف")
        if (item.baseUnitId == 0L) throw PostingException("اختر الوحدة الأساسية")
        extraUnits.forEach { if (it.factor <= 0) throw PostingException("معامل الوحدة يجب أن يكون أكبر من صفر") }
        val id = if (item.id == 0L) db.items().insert(item) else { db.items().update(item); item.id }
        db.items().deleteUnits(id)
        db.items().upsertUnit(ItemUnit(id, item.baseUnitId, 1.0, item.salePrice))
        extraUnits.filter { it.unitId != item.baseUnitId }.distinctBy { it.unitId }.forEach { db.items().upsertUnit(it.copy(itemId = id)) }
        postOpening(item.copy(id = id))
        id
    }

    /** قيد بضاعة أول المدة (مدين −14 / دائن رأس المال −13) بقيمة الكمية × التكلفة، كما items_OP_insert الأصلي. */
    private suspend fun postOpening(item: Item) {
        db.journal().deleteLinesByRef(RefType.OPENING, item.id)
        db.journal().deleteHeadersByRef(RefType.OPENING, item.id)
        val value = Money.r(item.openingQty * item.openingCost)
        if (value <= 0.0) return
        db.assertOpenDate(item.openingDate.ifEmpty { "0000-00-00" })
        val hid = db.journal().insertHeader(JournalHeader(date = item.openingDate.ifEmpty { "2000-01-01" }, kind = TrType.OPENING, refType = RefType.OPENING, refId = item.id,
            currencyId = 0, rate = 1.0, note = "افتتاحي#${item.name}", userId = Session.userId))
        db.journal().insertLines(listOf(JournalLine(journalId = hid, accountId = Sys.OPENING_STOCK, debit = value), JournalLine(journalId = hid, accountId = Sys.CAPITAL, credit = value)))
    }

    suspend fun delete(item: Item) = db.withTransaction {
        if (db.items().usageCount(item.id) > 0) throw PostingException("لا يمكن حذف ${item.name}: عليه حركات. عطّله بدلاً من حذفه")
        db.journal().deleteLinesByRef(RefType.OPENING, item.id); db.journal().deleteHeadersByRef(RefType.OPENING, item.id)
        db.items().deleteUnits(item.id); db.items().delete(item.id)
    }
}

class UnitRepository(private val db: AppDatabase) {
    suspend fun save(u: UnitDef) {
        if (u.name.isBlank()) throw PostingException("اكتب اسم الوحدة")
        if (u.id == 0L) throw PostingException("الوحدة الافتراضية لا تُعدَّل")
        db.core().upsertUnit(u)
    }
    suspend fun create(name: String) = save(UnitDef(db.core().nextUnitId(), name.trim(), name.trim()))
    suspend fun delete(u: UnitDef) {
        if (u.id == 0L) throw PostingException("الوحدة الافتراضية لا تُحذف")
        if (db.core().unitUsage(u.id) > 0) throw PostingException("الوحدة مستخدمة في أصناف أو فواتير")
        db.core().deleteUnit(u.id)
    }
}
