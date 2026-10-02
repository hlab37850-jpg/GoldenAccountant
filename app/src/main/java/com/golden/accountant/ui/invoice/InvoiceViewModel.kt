package com.golden.accountant.ui.invoice

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golden.accountant.data.*
import com.golden.accountant.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UnitOpt(val unitId: Long, val name: String, val factor: Double, val price: Double)

/** سطر في شاشة الفاتورة: النصوص تُحفظ كما كُتبت ويُحوَّل منها الرقم عند الحساب. */
data class LineUi(
    val key: Long, val item: Item, val units: List<UnitOpt>, val unitId: Long, val factor: Double,
    val qtyText: String = "1", val priceText: String = "0", val discountText: String = "",
) {
    fun toBillLine() = BillLine(
        itemId = item.id, unitId = unitId, unitFactor = factor,
        qty = InvoiceMath.parse(qtyText), price = InvoiceMath.parse(priceText), discount = InvoiceMath.parse(discountText),
    )
}

class InvoiceViewModel(private val db: AppDatabase, val kind: InvoiceKind, private val editId: Long) : ViewModel() {

    var date by mutableStateOf(todayIso())
    var billType by mutableIntStateOf(BillType.CASH)
    var currencyId by mutableLongStateOf(0L)
    private var loadedBranch = 1L
    var party by mutableStateOf<Party?>(null)
    val lines = mutableStateListOf<LineUi>()
    var discountText by mutableStateOf("")
    var taxPercent by mutableDoubleStateOf(0.0)
    var extraText by mutableStateOf("")
    var paidText by mutableStateOf("")
    var remarks by mutableStateOf("")
    var billNo by mutableIntStateOf(0)
    var existingId by mutableLongStateOf(editId)
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    var leave by mutableStateOf(false)
    private var keySeq = 0L

    private val partyKinds = if (kind.isSales) listOf(Party.KIND_CUSTOMER, Party.KIND_BOTH) else listOf(Party.KIND_SUPPLIER, Party.KIND_BOTH)
    val items = db.items().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val parties = db.parties().observeByKinds(partyKinds).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val units = db.core().observeUnits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currencies = db.core().observeCurrencies().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val taxes = db.core().observeTaxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val repo = BillRepository(db)

    val totals: InvoiceMath.Totals
        get() = InvoiceMath.totals(
            lines.map { it.toBillLine() }, InvoiceMath.parse(discountText), taxPercent,
            InvoiceMath.parse(extraText), if (billType == BillType.CREDIT && kind.posts) InvoiceMath.parse(paidText) else 0.0,
        )

    init {
        viewModelScope.launch {
            taxPercent = db.core().observeTaxes().first().firstOrNull { it.isDefault }?.percent ?: 0.0
            if (editId > 0) load(editId) else billNo = db.bills().nextNo(kind.trType, kind.isBack, Session.branchId)
        }
    }

    private suspend fun load(id: Long) {
        val b = db.bills().byId(id) ?: run { message = "الفاتورة غير موجودة"; leave = true; return }
        date = b.date; billType = b.billType; billNo = b.billNo; currencyId = b.currencyId; loadedBranch = b.branchId
        taxPercent = b.taxPercent; remarks = b.remarks
        discountText = fmt(b.discount); extraText = fmt(b.extraCost); paidText = fmt(b.paid)
        party = if (b.partyAccountId != 0L) db.parties().byAccount(b.partyAccountId) else null
        lines.clear()
        db.bills().lines(id).forEach { l ->
            val item = db.items().byId(l.itemId) ?: return@forEach
            lines += LineUi(++keySeq, item, unitOptions(item), l.unitId, l.unitFactor, fmt(l.qty), fmt(l.price), fmt(l.discount))
        }
    }

    private suspend fun unitOptions(item: Item): List<UnitOpt> {
        val names = units.value.associate { it.id to it.name }
        val opts = db.items().units(item.id).map { UnitOpt(it.unitId, names[it.unitId] ?: "وحدة", it.factor, it.salePrice) }
        return opts.ifEmpty { listOf(UnitOpt(item.baseUnitId, names[item.baseUnitId] ?: "وحدة", 1.0, item.salePrice)) }
    }

    fun addItem(item: Item) = viewModelScope.launch {
        val opts = unitOptions(item)
        val base = opts.firstOrNull { it.unitId == item.baseUnitId } ?: opts.first()
        val idx = lines.indexOfFirst { it.item.id == item.id && it.unitId == base.unitId }
        if (idx >= 0) {
            lines[idx] = lines[idx].let { it.copy(qtyText = fmt(InvoiceMath.parse(it.qtyText) + 1)) }
        } else {
            val price = if (kind.isSales) (if (base.price > 0) base.price else item.salePrice) else item.openingCost
            lines += LineUi(++keySeq, item, opts, base.unitId, base.factor, "1", fmt(price))
        }
    }

    fun updateLine(index: Int, f: (LineUi) -> LineUi) { lines[index] = f(lines[index]) }
    fun removeLine(index: Int) { lines.removeAt(index) }

    fun changeUnit(index: Int, opt: UnitOpt) = updateLine(index) {
        val price = if (kind.isSales && opt.price > 0) opt.price else InvoiceMath.parse(it.priceText) * opt.factor / it.factor
        it.copy(unitId = opt.unitId, factor = opt.factor, priceText = fmt(price))
    }

    fun createItem(name: String, price: Double, unitId: Long) = viewModelScope.launch {
        if (name.isBlank()) { message = "اكتب اسم الصنف"; return@launch }
        runCatching {
            val id = db.items().insert(Item(name = name.trim(), baseUnitId = unitId, salePrice = price, openingDate = todayIso()))
            db.items().upsertUnit(ItemUnit(id, unitId, 1.0, price))
            db.items().byId(id)!!
        }.onSuccess { addItem(it) }.onFailure { message = "اسم الصنف مستخدم مسبقاً" }
    }

    fun createParty(name: String, phone: String) = viewModelScope.launch {
        if (name.isBlank()) { message = "اكتب الاسم"; return@launch }
        runCatching {
            val kindCode = if (kind.isSales) Party.KIND_CUSTOMER else Party.KIND_SUPPLIER
            db.parties().byId(PartyRepository(db).create(name, kindCode, phone))
        }.onSuccess { party = it }.onFailure { message = "الاسم مستخدم مسبقاً" }
    }

    fun save() {
        if (saving) return
        val error = validate()
        if (error != null) { message = error; return }
        saving = true
        viewModelScope.launch {
            val t = totals
            val bill = Bill(
                id = existingId, trType = kind.trType, isBack = kind.isBack, billType = billType, billNo = if (existingId > 0) billNo else 0,
                date = date, time = SimpleDateFormat("HH:mm", Locale.US).format(Date()),
                partyAccountId = party?.accountId ?: 0L, cashAccountId = Session.cashAccountId, currencyId = currencyId, branchId = if (existingId > 0) loadedBranch else Session.branchId,
                subtotal = t.subtotal, discount = t.discount, taxPercent = taxPercent, taxAmount = t.tax,
                extraCost = t.extra, paid = t.paid, remarks = remarks.trim(), userId = Session.userId,
            )
            runCatching { repo.save(bill, lines.map { it.toBillLine() }) }
                .onSuccess { id ->
                    val warn = negativeStockWarning()
                    if (existingId > 0) { message = "تم تحديث الفاتورة" + warn; leave = true }
                    else { message = "تم حفظ الفاتورة رقم ${db.bills().byId(id)?.billNo}" + warn; reset() }
                }
                .onFailure { message = it.message ?: "تعذر الحفظ" }
            saving = false
        }
    }

    /** تنبيه غير مانع: أي صنف أصبح رصيده سالباً بعد فاتورة بيع. */
    private suspend fun negativeStockWarning(): String {
        if (!kind.isSales || kind.isBack || !kind.posts) return ""
        val stock = StockService(db)
        val bad = lines.map { it.item }.distinctBy { it.id }.filter { stock.onHand(it.id) < 0 }
        return if (bad.isEmpty()) "" else "\nتنبيه: رصيد ${bad.joinToString("، ") { it.name }} أصبح سالباً"
    }

    fun delete() = viewModelScope.launch {
        runCatching { repo.delete(existingId) }
            .onSuccess { message = "تم حذف الفاتورة"; leave = true }
            .onFailure { message = it.message ?: "تعذر الحذف" }
    }

    private fun validate(): String? {
        if (lines.isEmpty()) return "أضف صنفاً واحداً على الأقل"
        lines.forEach {
            val l = it.toBillLine()
            if (l.qty <= 0) return "الكمية يجب أن تكون أكبر من صفر: ${it.item.name}"
            if (l.price < 0) return "السعر غير صحيح: ${it.item.name}"
            if (l.discount > l.qty * l.price) return "خصم السطر أكبر من قيمته: ${it.item.name}"
        }
        if (kind.posts && billType == BillType.CREDIT && party == null) return "اختر ${if (kind.isSales) "العميل" else "المورد"} للفاتورة الآجلة"
        if (totals.total <= 0 && kind.posts) return "إجمالي الفاتورة صفر"
        return null
    }

    private fun reset() {
        lines.clear(); discountText = ""; extraText = ""; paidText = ""; remarks = ""; party = null
        viewModelScope.launch { billNo = db.bills().nextNo(kind.trType, kind.isBack, Session.branchId) }
    }

    companion object {
        fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        fun fmt(v: Double): String = if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    }
}
