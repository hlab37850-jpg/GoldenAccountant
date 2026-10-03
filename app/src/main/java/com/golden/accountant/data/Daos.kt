package com.golden.accountant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class AccountBalance(val accountId: Long, val balance: Double)

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(a: Account): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(list: List<Account>)
    @Query("SELECT COUNT(*) FROM accounts") suspend fun count(): Int
    @Query("SELECT * FROM accounts ORDER BY id") fun observeAll(): Flow<List<Account>>
    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun byId(id: Long): Account?
    @Query("SELECT * FROM accounts WHERE parentId=:parent ORDER BY id") suspend fun children(parent: Long): List<Account>
    @Query("SELECT * FROM accounts WHERE isGroup=0 ORDER BY name") fun observeLeaves()
    @Query("UPDATE accounts SET type=:type WHERE id=:id") suspend fun setType(id: Long, type: Int)
    @Query("SELECT * FROM accounts WHERE isGroup=0 AND type IN (:types) ORDER BY name") fun observeByTypes(types: List<Int>): Flow<List<Account>>: Flow<List<Account>>
    @Query("UPDATE accounts SET name=:name WHERE id=:id") suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM accounts WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM accounts WHERE parentId=:id") suspend fun childCount(id: Long): Int

    /** أرصدة كل الحسابات بعملة واحدة (مدين - دائن)، تتجدد تلقائياً مع أي قيد. */
    @Query("""SELECT l.accountId AS accountId, SUM(l.debit - l.credit) AS balance FROM journal_lines l
              JOIN journal_headers h ON h.id=l.journalId WHERE h.currencyId=:currencyId GROUP BY l.accountId""")
    fun balances(currencyId: Long): Flow<List<AccountBalance>>

    /** رصيد حساب بعملة معينة: مدين - دائن (موجب = مدين). */
    @Query("""SELECT IFNULL(SUM(l.debit - l.credit),0) FROM journal_lines l
              JOIN journal_headers h ON h.id=l.journalId
              WHERE l.accountId=:accountId AND h.currencyId=:currencyId""")
    suspend fun balance(accountId: Long, currencyId: Long): Double
}

@Dao
interface PartyDao {
    @Insert suspend fun insert(p: Party): Long
    @Update suspend fun update(p: Party)
    @Query("SELECT * FROM parties ORDER BY name") fun observeAll(): Flow<List<Party>>
    @Query("SELECT * FROM parties WHERE kind IN (:kinds) ORDER BY name") fun observeByKinds(kinds: List<Int>): Flow<List<Party>>
    @Query("SELECT * FROM parties WHERE accountId=:accountId") suspend fun byAccount(accountId: Long): Party?
    @Query("SELECT * FROM parties WHERE id=:id") suspend fun byId(id: Long): Party?
    @Query("DELETE FROM parties WHERE id=:id") suspend fun delete(id: Long)
}

@Dao
interface JournalDao {
    @Insert suspend fun insertHeader(h: JournalHeader): Long
    @Insert suspend fun insertLines(lines: List<JournalLine>)
    @Query("DELETE FROM journal_lines WHERE journalId IN (SELECT id FROM journal_headers WHERE refType=:refType AND refId=:refId)")
    suspend fun deleteLinesByRef(refType: Int, refId: Long)
    @Query("DELETE FROM journal_headers WHERE refType=:refType AND refId=:refId")
    suspend fun deleteHeadersByRef(refType: Int, refId: Long)

    /** كشف حساب: الحركات بترتيب التاريخ. */
    @Query("""SELECT h.date AS date, h.note AS note, h.kind AS kind, h.refType AS refType, h.refId AS refId,
              l.debit AS debit, l.credit AS credit
              FROM journal_lines l JOIN journal_headers h ON h.id=l.journalId
              WHERE l.accountId=:accountId AND h.currencyId=:currencyId
              AND h.date BETWEEN :from AND :to ORDER BY h.date, h.id""")
    suspend fun statement(accountId: Long, currencyId: Long, from: String, to: String): List<StatementRow>

    @Query("""SELECT IFNULL(SUM(l.debit - l.credit),0) FROM journal_lines l JOIN journal_headers h ON h.id=l.journalId
              WHERE l.accountId=:accountId AND h.currencyId=:currencyId AND h.date<:date""")
    suspend fun balanceBefore(accountId: Long, currencyId: Long, date: String): Double

    @Query("""SELECT l.accountId AS accountId, IFNULL(SUM(l.debit),0) AS debit, IFNULL(SUM(l.credit),0) AS credit
              FROM journal_lines l JOIN journal_headers h ON h.id=l.journalId
              WHERE h.currencyId=:currencyId AND h.date BETWEEN :from AND :to GROUP BY l.accountId""")
    suspend fun periodTotals(currencyId: Long, from: String, to: String): List<PeriodTotal>

    @Query("SELECT COUNT(*) FROM journal_lines WHERE accountId=:accountId") suspend fun countLines(accountId: Long): Int

    @Query("SELECT * FROM journal_headers WHERE id=:id") suspend fun header(id: Long): JournalHeader?
    @Query("SELECT * FROM journal_lines WHERE journalId=:journalId ORDER BY id") suspend fun linesOf(journalId: Long): List<JournalLine>
    @Query("DELETE FROM journal_lines WHERE journalId=:id") suspend fun deleteLines(id: Long)
    @Query("DELETE FROM journal_headers WHERE id=:id") suspend fun deleteHeader(id: Long)
    @Query("""SELECT h.id AS id, h.date AS date, h.note AS note, h.currencyId AS currencyId, h.kind AS kind,
              IFNULL((SELECT SUM(debit) FROM journal_lines WHERE journalId=h.id),0) AS total
              FROM journal_headers h WHERE h.refType=3 ORDER BY h.date DESC, h.id DESC""")
    fun observeManual(): Flow<List<JournalSummary>>

    @Query("SELECT IFNULL(SUM(debit),0) - IFNULL(SUM(credit),0) FROM journal_lines") suspend fun trialDiff(): Double
}

data class StatementRow(val date: String, val note: String, val kind: Int, val refType: Int, val refId: Long, val debit: Double, val credit: Double)
data class PeriodTotal(val accountId: Long, val debit: Double, val credit: Double)
data class JournalSummary(val id: Long, val date: String, val note: String, val currencyId: Long, val kind: Int, val total: Double)

data class BillSummary(val count: Int, val subtotal: Double, val discount: Double, val tax: Double, val extra: Double) {
    val total: Double get() = Money.r(subtotal - discount + tax + extra)
}

@Dao
interface BillDao {
    @Insert suspend fun insert(b: Bill): Long
    @Update suspend fun update(b: Bill)
    @Insert suspend fun insertLines(lines: List<BillLine>)
    @Query("DELETE FROM bill_lines WHERE billId=:billId") suspend fun deleteLines(billId: Long)
    @Query("DELETE FROM bills WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT * FROM bills WHERE id=:id") suspend fun byId(id: Long): Bill?
    @Query("SELECT * FROM bill_lines WHERE billId=:billId ORDER BY id") suspend fun lines(billId: Long): List<BillLine>
    @Query("SELECT * FROM bills WHERE trType=:trType AND isBack=:isBack ORDER BY date DESC, id DESC") fun observe(trType: Int, isBack: Boolean): Flow<List<Bill>>
    @Query("""SELECT COUNT(*) AS count, IFNULL(SUM(subtotal),0) AS subtotal, IFNULL(SUM(discount),0) AS discount,
              IFNULL(SUM(taxAmount),0) AS tax, IFNULL(SUM(extraCost),0) AS extra FROM bills
              WHERE trType=:trType AND isBack=:isBack AND currencyId=:currencyId AND date BETWEEN :from AND :to""")
    suspend fun summary(trType: Int, isBack: Boolean, currencyId: Long, from: String, to: String): BillSummary
    @Query("SELECT IFNULL(MAX(billNo),0)+1 FROM bills WHERE trType=:trType AND isBack=:isBack AND branchId=:branchId")
    suspend fun nextNo(trType: Int, isBack: Boolean, branchId: Long): Int
}

data class ItemQty(val itemId: Long, val qty: Double)
data class MoveRow(val itemId: Long, val trType: Int, val isBack: Boolean, val qty: Double, val value: Double)
data class ItemMove(val date: String, val trType: Int, val isBack: Boolean, val billNo: Int, val branchId: Long, val toBranchId: Long, val qty: Double, val price: Double, val remarks: String)
data class TopItem(val name: String, val qty: Double, val amount: Double)

@Dao
interface ItemDao {
    @Insert suspend fun insert(i: Item): Long
    @Update suspend fun update(i: Item)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertUnit(u: ItemUnit)
    @Query("SELECT * FROM items ORDER BY name") fun observeAll(): Flow<List<Item>>
    @Query("SELECT * FROM items WHERE id=:id") suspend fun byId(id: Long): Item?
    @Query("SELECT * FROM items WHERE barcode=:code LIMIT 1") suspend fun byBarcode(code: String): Item?
    @Query("SELECT * FROM item_units WHERE itemId=:itemId") suspend fun units(itemId: Long): List<ItemUnit>

    /** الكمية بالوحدة الأساسية من حركات الفواتير والتسويات فقط (بدون الافتتاحي). */
    @Query("""SELECT IFNULL(SUM(CASE
                WHEN b.trType IN (2,21) AND b.isBack=0 THEN l.qty*l.unitFactor
                WHEN b.trType=1 AND b.isBack=1 THEN l.qty*l.unitFactor
                WHEN b.trType IN (1,11) AND b.isBack=0 THEN -l.qty*l.unitFactor
                WHEN b.trType=2 AND b.isBack=1 THEN -l.qty*l.unitFactor
                WHEN b.trType=4 THEN l.qty*l.unitFactor
                ELSE 0 END),0)
              FROM bill_lines l JOIN bills b ON b.id=l.billId WHERE l.itemId=:itemId""")
    suspend fun movementQty(itemId: Long): Double

    @Query("""SELECT l.itemId AS itemId, IFNULL(SUM(CASE
                WHEN b.trType IN (2,21) AND b.isBack=0 THEN l.qty*l.unitFactor
                WHEN b.trType=1 AND b.isBack=1 THEN l.qty*l.unitFactor
                WHEN b.trType IN (1,11) AND b.isBack=0 THEN -l.qty*l.unitFactor
                WHEN b.trType=2 AND b.isBack=1 THEN -l.qty*l.unitFactor
                WHEN b.trType=4 THEN l.qty*l.unitFactor
                ELSE 0 END),0) AS qty
              FROM bill_lines l JOIN bills b ON b.id=l.billId GROUP BY l.itemId""")
    fun movementByItem(): Flow<List<ItemQty>>

    @Query("SELECT * FROM items ORDER BY name") suspend fun all(): List<Item>

    /** رصيد كل صنف في فرع واحد من الحركات (الافتتاحي يُحسب للفرع 1). التحويل: يخصم من المرسل ويضيف للمستلم. */
    @Query("""SELECT l.itemId AS itemId, IFNULL(SUM(CASE
                WHEN b.trType=3 AND b.toBranchId=:branch THEN l.qty*l.unitFactor
                WHEN b.trType=3 AND b.branchId=:branch THEN -l.qty*l.unitFactor
                WHEN b.branchId<>:branch THEN 0
                WHEN b.trType IN (2,21) AND b.isBack=0 THEN l.qty*l.unitFactor
                WHEN b.trType=1 AND b.isBack=1 THEN l.qty*l.unitFactor
                WHEN b.trType IN (1,11) AND b.isBack=0 THEN -l.qty*l.unitFactor
                WHEN b.trType=2 AND b.isBack=1 THEN -l.qty*l.unitFactor
                WHEN b.trType=4 THEN l.qty*l.unitFactor
                ELSE 0 END),0) AS qty
              FROM bill_lines l JOIN bills b ON b.id=l.billId GROUP BY l.itemId""")
    suspend fun stockByBranch(branch: Long): List<ItemQty>

    /** حركات صنف واحد في فترة (للتقرير): الكمية بالأساسية، موجبة = دخول، سالبة = خروج (حسب نوع الحركة). */
    @Query("""SELECT b.date AS date, b.trType AS trType, b.isBack AS isBack, b.billNo AS billNo, b.branchId AS branchId, b.toBranchId AS toBranchId,
              l.qty*l.unitFactor AS qty, l.price AS price, b.remarks AS remarks
              FROM bill_lines l JOIN bills b ON b.id=l.billId
              WHERE l.itemId=:itemId AND b.date BETWEEN :from AND :to ORDER BY b.date, b.id""")
    suspend fun itemMoves(itemId: Long, from: String, to: String): List<ItemMove>

    @Query("""SELECT IFNULL(SUM(CASE
                WHEN b.trType IN (2,21) AND b.isBack=0 THEN l.qty*l.unitFactor
                WHEN b.trType=1 AND b.isBack=1 THEN l.qty*l.unitFactor
                WHEN b.trType IN (1,11) AND b.isBack=0 THEN -l.qty*l.unitFactor
                WHEN b.trType=2 AND b.isBack=1 THEN -l.qty*l.unitFactor
                WHEN b.trType=4 THEN l.qty*l.unitFactor
                ELSE 0 END),0)
              FROM bill_lines l JOIN bills b ON b.id=l.billId WHERE l.itemId=:itemId AND b.date<:before""")
    suspend fun qtyBefore(itemId: Long, before: String): Double
    @Query("DELETE FROM items WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM item_units WHERE itemId=:itemId") suspend fun deleteUnits(itemId: Long)
    @Query("SELECT COUNT(*) FROM bill_lines WHERE itemId=:itemId") suspend fun usageCount(itemId: Long): Int

    /** تجميع الحركات لكل صنف ونوع (للتقييم): حتى تاريخ شامل / قبل تاريخ. */
    @Query("""SELECT l.itemId AS itemId, b.trType AS trType, b.isBack AS isBack,
              SUM(l.qty*l.unitFactor) AS qty, SUM((l.qty*l.price - l.discount) * b.rate) AS value
              FROM bill_lines l JOIN bills b ON b.id=l.billId WHERE b.date<=:upTo
              GROUP BY l.itemId, b.trType, b.isBack""")
    suspend fun movementRowsUpTo(upTo: String): List<MoveRow>

    @Query("""SELECT l.itemId AS itemId, b.trType AS trType, b.isBack AS isBack,
              SUM(l.qty*l.unitFactor) AS qty, SUM((l.qty*l.price - l.discount) * b.rate) AS value
              FROM bill_lines l JOIN bills b ON b.id=l.billId WHERE b.date<:before
              GROUP BY l.itemId, b.trType, b.isBack""")
    suspend fun movementRowsBefore(before: String): List<MoveRow>

    @Query("""SELECT i.name AS name, SUM(l.qty*l.unitFactor) AS qty, SUM(l.qty*l.price - l.discount) AS amount
              FROM bill_lines l JOIN bills b ON b.id=l.billId JOIN items i ON i.id=l.itemId
              WHERE b.trType=1 AND b.isBack=0 AND b.currencyId=:currencyId AND b.date BETWEEN :from AND :to
              GROUP BY l.itemId ORDER BY amount DESC LIMIT 20""")
    suspend fun topSold(currencyId: Long, from: String, to: String): List<TopItem>
}

@Dao
interface CoreDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCurrencies(l: List<Currency>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertUnits(l: List<UnitDef>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTaxes(l: List<Tax>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBranches(l: List<Branch>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertUsers(l: List<AppUser>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRate(r: CurrencyRate)
    @Insert suspend fun insertClosing(c: ClosingYear)

    @Query("SELECT * FROM currencies") fun observeCurrencies(): Flow<List<Currency>>
    @Query("SELECT * FROM units") fun observeUnits(): Flow<List<UnitDef>>
    @Query("SELECT * FROM taxes WHERE isActive=1") fun observeTaxes(): Flow<List<Tax>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertUnit(u: UnitDef)
    @Query("SELECT IFNULL(MAX(id),0)+1 FROM units") suspend fun nextUnitId(): Long
    @Query("DELETE FROM units WHERE id=:id") suspend fun deleteUnit(id: Long)
    @Query("""SELECT (SELECT COUNT(*) FROM items WHERE baseUnitId=:id) + (SELECT COUNT(*) FROM item_units WHERE unitId=:id)
              + (SELECT COUNT(*) FROM bill_lines WHERE unitId=:id)""") suspend fun unitUsage(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCurrency(c: Currency)
    @Query("SELECT IFNULL(MAX(id),0)+1 FROM currencies") suspend fun nextCurrencyId(): Long
    @Query("SELECT * FROM currency_rates WHERE currencyId=:id ORDER BY fromDate DESC") fun observeRates(id: Long): Flow<List<CurrencyRate>>
    @Query("DELETE FROM currency_rates WHERE id=:id") suspend fun deleteRate(id: Long)
    @Query("SELECT * FROM taxes ORDER BY id") fun observeAllTaxes(): Flow<List<Tax>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTax(t: Tax)
    @Query("UPDATE taxes SET isDefault=0") suspend fun clearDefaultTax()
    @Query("SELECT * FROM branches ORDER BY id") fun observeBranches(): Flow<List<Branch>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBranch(b: Branch)
    @Query("SELECT * FROM closing_years ORDER BY date DESC") fun observeClosings(): Flow<List<ClosingYear>>
    @Query("DELETE FROM closing_years WHERE id=:id") suspend fun deleteClosing(id: Long)
    @Query("SELECT MAX(date) FROM closing_years") suspend fun lastClosing(): String?
    @Query("SELECT price FROM currency_rates WHERE currencyId=:id AND fromDate<=:date ORDER BY fromDate DESC LIMIT 1")
    suspend fun rateOn(id: Long, date: String): Double?
}

@Dao
interface VoucherDao {
    @Insert suspend fun insert(v: Voucher): Long
    @Update suspend fun update(v: Voucher)
    @Query("DELETE FROM vouchers WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT * FROM vouchers WHERE id=:id") suspend fun byId(id: Long): Voucher?
    @Query("SELECT * FROM vouchers WHERE type=:type ORDER BY date DESC, id DESC") fun observe(type: Int): Flow<List<Voucher>>
    @Query("SELECT IFNULL(MAX(voucherNo),0)+1 FROM vouchers WHERE type=:type") suspend fun nextNo(type: Int): Int
}

@Dao
interface UserDao {
    @Insert suspend fun insert(u: AppUser): Long
    @Update suspend fun update(u: AppUser)
    @Query("SELECT * FROM users ORDER BY id") fun observeAll(): Flow<List<AppUser>>
    @Query("SELECT * FROM users WHERE userName=:name COLLATE NOCASE LIMIT 1") suspend fun byUserName(name: String): AppUser?
    @Query("SELECT * FROM users WHERE id=:id") suspend fun byId(id: Long): AppUser?
    @Query("SELECT COUNT(*) FROM users WHERE isAdmin=1 AND isActive=1") suspend fun activeAdmins(): Int
    @Query("SELECT * FROM users WHERE pwdHash='' AND isAdmin=1 LIMIT 1") suspend fun adminWithoutPassword(): AppUser?
    @Query("SELECT * FROM user_priv WHERE userId=:id") suspend fun privs(id: Long): List<UserPriv>
    @Query("DELETE FROM user_priv WHERE userId=:id") suspend fun deletePrivs(id: Long)
    @Insert suspend fun insertPrivs(list: List<UserPriv>)
}

@Dao
interface ScreenDao {
    @Query("SELECT * FROM screens ORDER BY id") suspend fun all(): List<Screen>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(list: List<Screen>)
}

@Dao
interface CusLimitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(l: CusLimit)
    @Query("DELETE FROM cus_limit WHERE accountId=:a AND currencyId=:c") suspend fun delete(a: Long, c: Long)
    @Query("SELECT * FROM cus_limit") fun observeAll(): Flow<List<CusLimit>>
    @Query("SELECT * FROM cus_limit WHERE accountId=:a AND currencyId=:c") suspend fun get(a: Long, c: Long): CusLimit?
}

@Dao
interface SysConfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun set(c: SysConf)
    @Query("SELECT value FROM sys_conf WHERE `key`=:k") suspend fun get(k: String): String?
    @Query("SELECT * FROM sys_conf") suspend fun all(): List<SysConf>
}

@Dao
interface ItemPriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: ItemPrice)
    @Query("DELETE FROM item_prices WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT * FROM item_prices WHERE itemId=:itemId ORDER BY date DESC, id DESC") fun observeFor(itemId: Long): Flow<List<ItemPrice>>
    @Query("SELECT price FROM item_prices WHERE itemId=:itemId AND currencyId=:currencyId AND unitId=:unitId AND date<=:date ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun priceOn(itemId: Long, currencyId: Long, unitId: Long, date: String): Double?
}
