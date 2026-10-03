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
    val qtyText: String = "1", val priceText: String = "", val discountText: String = "", val expiryText: String = "", val remark: String = "",
) {
    fun toBillLine() = BillLine(
        itemId = item.id, unitId = unitId, unitFactor = factor,
        qty = InvoiceMath.parse(qtyText), price = InvoiceMath.parse(priceText), discount = InvoiceMath.parse(discountText),
        remarks = remark, expiry = expiryText,
    )
}

/**
 * حقول الفاتورة مطابقة لجدول bills الأصلي: نوع (نقد/آجل)، عملة وسعرها، مخزن، صندوق، طرف،
 * خصم (نسبة/مبلغ)، ضريبة (متضمنة/غير متضمنة)، رسوم بحساب، مدفوع، رقم الفاتورة الأصلية للمرتجع.
 */
class InvoiceViewModel(private val db: AppDatabase, val kind: InvoiceKind, private val editId: Long) : ViewModel() {

    var date by mutableStateOf(todayIso())
    var billType by mutableIntStateOf(BillType.CASH)
    var currencyId by mutableLongStateOf(0L)
    var rateText by mutableStateOf("")
    var branchId by mutableLongStateOf(Session.branchId)
    var cashAccount by mutableStateOf<Account?>(null)
    var party by mutableStateOf<Party?>(null)
    var counter by mutableStateOf<Account?>(null)          // توريد/صرف مخزني
    val lines = mutableStateListOf<LineUi>()
    var discountType by mutableIntStateOf(0)               // 0 نسبة، 1 مبلغ
    var discountText by mutableStateOf("")
    var taxId by mutableLongStateOf(0L)
    var taxPercent by mutableDoubleStateOf(0.0)
    var taxIncluded by mutableStateOf(false)
    var extraText by mutableStateOf("")
    var costAccount by mutableStateOf<Account?>(null)
    var paidText by mutableStateOf("")
    var remarks by mutableStateOf("")
    var refNo by mutableStateOf("")
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
    val warehouses = db.core().observeBranches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cashAccounts = db.accounts().observeByTypes(listOf(AccType.CASH)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAccounts = db.accounts().observeLeaves().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val repo = BillRepository(db)
    private var loadedBranch = 1L

    val showCashRow: Boolean get() = kind.posts && !kind.isStock

    val totals: InvoiceMath.Totals
        get() = InvoiceMath.totals(
            lines.map { it.toBillLine() }, discountType, InvoiceMath.parse(discountText),
            if (Settings.vatEnabled) taxPercent else 0.0, taxIncluded && Settings.vatEnabled,
            InvoiceMath.parse(extraText), if (billType == BillType.CREDIT && kind.posts && !kind.isStock) InvoiceMath.parse(paidText) else 0.0,
        )

    init {
        viewModelScope.launch {
            cashAccount = db.accounts().byId(Session.cashAccountId)
            costAccount = db.accounts().byId(Sys.TRANSPORT)
            db.core().observeTaxes().first().firstOrNull { it.isDefault }?.let { applyTax(it) }
            if (editId > 0) load(editId) else {
                billNo = db.bills().nextNo(kind.trType, kind.isBack, branchId)
                if (kind.isStock) billType = BillType.CREDIT
            }
        }
    }

    fun applyTax(t: Tax) { taxId = t.id; taxPercent = t.percent; taxIncluded = t.included }

    fun changeCurrency(id: Long) = viewModelScope.launch {
        currencyId = id
        rateText = if (id == 0L) "" else fmt(db.core().rateOn(id, date) ?: 0.0)
        // أسعار الأسطر الموجودة تُعاد من جدول الأسعار بالعملة الجديدة
        lines.indices.forEach { i -> val l = lines[i]; val opt = l.units.firstOrNull { it.unitId == l.unitId }; if (opt != null) lines[i] = l.copy(priceText = fmt(priceFor(l.item, opt))) }
    }

    private suspend fun load(id: Long) {
        val b = db.bills().byId(id) ?: run { message = "الفاتورة غير موجودة"; leave = true; return }
        date = b.date; billType = b.billType; billNo = b.billNo; currencyId = b.currencyId; loadedBranch = b.branchId; branchId = b.branchId
        rateText = if (b.currencyId == 0L) "" else fmt(b.rate)
        taxId = b.taxId; taxPercent = b.taxPercent; taxIncluded = b.taxIncluded; remarks = b.remarks; refNo = b.refNo
        discountType = b.discountType; discountText = fmt(b.discountVal); extraText = fmt(b.extraCost); paidText = fmt(b.paid)
        cashAccount = db.accounts().byId(b.cashAccountId); costAccount = db.accounts().byId(b.costAccountId)
        if (kind.isStock) counter = db.accounts().byId(b.partyAccountId)
        else party = if (b.partyAccountId != 0L) db.parties().byAccount(b.partyAccountId) else null
        lines.clear()
        db.bills().lines(id).forEach { l ->
            val item = db.items().byId(l.itemId) ?: return@forEach
            lines += LineUi(++keySeq, item, unitOptions(item), l.unitId, l.unitFactor, fmt(l.qty), fmt(l.price), fmt(l.discount), l.expiry, l.remarks)
        }
    }

    private suspend fun unitOptions(item: Item): List<UnitOpt> {
        val names = units.value.associate { it.id to it.name }
        val opts = db.items().units(item.id).map { UnitOpt(it.unitId, names[it.unitId] ?: "وحدة", it.factor, it.salePrice) }
        return opts.ifEmpty { listOf(UnitOpt(item.baseUnitId, names[item.baseUnitId] ?: "وحدة", 1.0, item.salePrice)) }
    }

    /** سعر الوحدة: من جدول الأسعار (عملة+وحدة+تاريخ)، وإلا سعر الوحدة المحلي محوّلاً بسعر الصرف؛ وللشراء والتوريد التكلفة. */
    private suspend fun priceFor(item: Item, opt: UnitOpt): Double {
        if (!kind.usesSalePrice) return Money.r(item.openingCost * opt.factor)
        db.itemPrices().priceOn(item.id, currencyId, opt.unitId, date)?.let { return it }
        val local = if (opt.price > 0) opt.price else item.salePrice * opt.factor
        val rate = InvoiceMath.parse(rateText)
        return if (currencyId == 0L || rate <= 0) local else Money.r(local / rate)
    }

    fun addItem(item: Item) = viewModelScope.launch {
        val opts = unitOptions(item)
        val base = opts.firstOrNull { it.unitId == item.baseUnitId } ?: opts.first()
        val idx = lines.indexOfFirst { it.item.id == item.id && it.unitId == base.unitId }
        if (idx >= 0) lines[idx] = lines[idx].let { it.copy(qtyText = fmt(InvoiceMath.parse(it.qtyText) + 1)) }
        else lines += LineUi(++keySeq, item, opts, base.unitId, base.factor, "1", fmt(priceFor(item, base)))
    }

    fun updateLine(index: Int, f: (LineUi) -> LineUi) { lines[index] = f(lines[index]) }
    fun removeLine(index: Int) { lines.removeAt(index) }

    fun changeUnit(index: Int, opt: UnitOpt) = viewModelScope.launch {
        val l = lines[index]
        lines[index] = l.copy(unitId = opt.unitId, factor = opt.factor, priceText = fmt(priceFor(l.item, opt)))
    }

    fun createItem(name: String, price: Double, unitId: Long) = viewModelScope.launch {
        if (name.isBlank()) { message = "اكتب اسم الصنف"; return@launch }
        runCatching {
            val id = ItemRepository(db).save(Item(name = name.trim(), baseUnitId = unitId, salePrice = price, openingDate = todayIso(), openingBranchId = branchId), emptyList())
            db.items().byId(id)!!
        }.onSuccess { addItem(it) }.onFailure { message = it.message ?: "اسم الصنف مستخدم مسبقاً" }
    }

    fun createParty(name: String, phone: String) = viewModelScope.launch {
        if (name.isBlank()) { message = "اكتب الاسم"; return@launch }
        runCatching {
            val k = if (kind.isSales) Party.KIND_CUSTOMER else Party.KIND_SUPPLIER
            db.parties().byId(PartyRepository(db).create(name, k, phone))
        }.onSuccess { party = it }.onFailure { message = "الاسم مستخدم مسبقاً" }
    }

    fun save() {
        if (saving) return
        val error = validate()
        if (error != null) { message = error; return }
        saving = true
        viewModelScope.launch {
            val t = totals
            val partyAcc = if (kind.isStock) counter?.id ?: 0L else party?.accountId ?: 0L
            val bill = Bill(
                id = existingId, trType = kind.trType, isBack = kind.isBack, billType = billType, billNo = if (existingId > 0) billNo else 0,
                date = date, time = SimpleDateFormat("HH:mm", Locale.US).format(Date()),
                branchId = if (existingId > 0) loadedBranch.takeIf { it != 0L } ?: branchId else branchId,
                partyAccountId = partyAcc, cashAccountId = cashAccount?.id ?: Session.cashAccountId,
                currencyId = currencyId, rate = if (currencyId == 0L) 1.0 else InvoiceMath.parse(rateText).takeIf { it > 0 } ?: 1.0,
                subtotal = t.subtotal, discount = t.discount, discountType = discountType, discountVal = InvoiceMath.parse(discountText),
                taxId = taxId, taxPercent = if (Settings.vatEnabled) taxPercent else 0.0, taxAmount = t.tax, taxIncluded = taxIncluded && Settings.vatEnabled,
                extraCost = t.extra, costAccountId = costAccount?.id ?: Sys.TRANSPORT, paid = t.paid,
                remarks = remarks.trim(), refNo = refNo.trim(), userId = Session.userId,
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

    /** تنبيه غير مانع: أي صنف أصبح رصيده سالباً بعد خروج مخزني. */
    private suspend fun negativeStockWarning(): String {
        val out = (kind.trType == TrType.SALE && !kind.isBack) || kind.trType == TrType.ISSUE
        if (!out) return ""
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
        if (kind.isStock && counter == null) return "اختر الحساب المقابل"
        if (kind.posts && !kind.isStock && billType == BillType.CREDIT && party == null) return "اختر ${if (kind.isSales) "العميل" else "المورد"} للفاتورة الآجلة"
        if (currencyId != 0L && InvoiceMath.parse(rateText) <= 0 && kind.posts) return "حدد سعر صرف العملة"
        if (kind.posts && totals.total <= 0) return "إجمالي الفاتورة صفر"
        return null
    }

    private fun reset() {
        lines.clear(); discountText = ""; extraText = ""; paidText = ""; remarks = ""; refNo = ""; party = null; counter = null
        viewModelScope.launch { billNo = db.bills().nextNo(kind.trType, kind.isBack, branchId) }
    }

    companion object {
        fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        fun fmt(v: Double): String = if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    }
}
