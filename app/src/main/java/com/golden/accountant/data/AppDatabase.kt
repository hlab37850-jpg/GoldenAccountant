package com.golden.accountant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Account::class, Party::class, Currency::class, CurrencyRate::class, UnitDef::class, ItemType::class,
        Item::class, ItemUnit::class, Tax::class, Branch::class, AppUser::class, Screen::class, UserPriv::class,
        ClosingYear::class, Bill::class, BillLine::class, JournalHeader::class, JournalLine::class, Voucher::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun parties(): PartyDao
    abstract fun journal(): JournalDao
    abstract fun bills(): BillDao
    abstract fun items(): ItemDao
    abstract fun core(): CoreDao
    abstract fun vouchers(): VoucherDao
    abstract fun users(): UserDao
    abstract fun screens(): ScreenDao

    companion object {
        const val VERSION = 3
        const val FILE = "golden.db"

        /** v1 → v2 (الجزء 4): جدول السندات. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vouchers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `voucherNo` INTEGER NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `cashAccountId` INTEGER NOT NULL, `amount` REAL NOT NULL, `discount` REAL NOT NULL, `currencyId` INTEGER NOT NULL, `rate` REAL NOT NULL, `note` TEXT NOT NULL, `userId` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vouchers_type_voucherNo` ON `vouchers` (`type`, `voucherNo`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vouchers_accountId` ON `vouchers` (`accountId`)")
            }
        }

        /** v2 → v3 (الجزء 6): الفرع المستلم في التحويل المخزني. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `bills` ADD COLUMN `toBranchId` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, FILE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { inst = it }
        }

        /** يغلق القاعدة ويُسقط المثيل (قبل استبدال الملف عند الاستعادة). */
        fun close() = synchronized(this) { inst?.close(); inst = null }
    }
}
