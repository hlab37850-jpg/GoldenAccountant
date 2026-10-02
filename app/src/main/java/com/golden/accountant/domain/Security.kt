package com.golden.accountant.domain

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.golden.accountant.data.AppDatabase
import com.golden.accountant.data.AppUser
import com.golden.accountant.data.UserPriv
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** كلمات المرور لا تُخزَّن أبداً كنص: PBKDF2 مع ملح عشوائي. الصيغة: iterations$salt$hash */
object Passwords {
    private const val ITER = 120_000
    private const val ALGO = "PBKDF2WithHmacSHA1" // متاح من API 10 (SHA256 يحتاج API 26)

    private fun derive(pw: CharArray, salt: ByteArray, iter: Int): ByteArray =
        SecretKeyFactory.getInstance(ALGO).generateSecret(PBEKeySpec(pw, salt, iter, 256)).encoded

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val h = derive(password.toCharArray(), salt, ITER)
        return "$ITER$" + Base64.encodeToString(salt, Base64.NO_WRAP) + "$" + Base64.encodeToString(h, Base64.NO_WRAP)
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 3) return false
        val iter = parts[0].toIntOrNull() ?: return false
        val salt = Base64.decode(parts[1], Base64.NO_WRAP); val expected = Base64.decode(parts[2], Base64.NO_WRAP)
        return MessageDigest.isEqual(expected, derive(password.toCharArray(), salt, iter)) // مقارنة ثابتة الزمن
    }

    fun validate(password: String): String? = when {
        password.length < 4 -> "كلمة المرور 4 أحرف على الأقل"
        else -> null
    }
}

enum class Action { VIEW, NEW, EDIT, DELETE }

/** منطق الصلاحيات النقي: المدير يملك كل شيء؛ غيره بحسب الجدول (route -> صلاحيات). */
class Privileges(private val isAdmin: Boolean, private val byRoute: Map<String, UserPriv>) {
    fun can(route: String, a: Action): Boolean {
        if (isAdmin) return true
        val p = byRoute[route] ?: return false
        return when (a) {
            Action.VIEW -> p.canView
            // أي إجراء آخر يتطلب العرض أولاً
            Action.NEW -> p.canView && p.canNew
            Action.EDIT -> p.canView && p.canEdit
            Action.DELETE -> p.canView && p.canDelete
        }
    }
}

/** الجلسة الحالية (مستخدم واحد في التطبيق). */
object Session {
    var user by mutableStateOf<AppUser?>(null)
        private set
    private var privileges = Privileges(false, emptyMap())

    val isLoggedIn: Boolean get() = user != null
    val isAdmin: Boolean get() = user?.isAdmin == true
    val userId: Long get() = user?.id ?: 0L
    val branchId: Long get() = user?.branchId?.takeIf { it > 0 } ?: 1L
    val cashAccountId: Long get() = user?.cashAccountId?.takeIf { it != 0L } ?: Sys.MAIN_CASH

    fun can(route: String, a: Action = Action.VIEW): Boolean = isLoggedIn && privileges.can(route, a)

    suspend fun start(db: AppDatabase, u: AppUser) {
        // route = اسم الشاشة المخزَّن في جدول screens
        val screens = db.screens().all().associate { it.id to it.name }
        val map = db.users().privs(u.id).mapNotNull { p -> screens[p.screenId]?.let { it to p } }.toMap()
        privileges = Privileges(u.isAdmin, map)
        user = u
    }

    fun end() { user = null; privileges = Privileges(false, emptyMap()) }
}

class AuthException(msg: String) : IllegalStateException(msg)

class AuthRepository(private val db: AppDatabase) {
    private companion object { var failures = 0; var lockedUntil = 0L }

    /** يرفض بعد 5 محاولات فاشلة لمدة 30 ثانية. */
    suspend fun login(userName: String, password: String): AppUser {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) throw AuthException("محاولات كثيرة. انتظر ${(lockedUntil - now) / 1000 + 1} ثانية")
        val u = db.users().byUserName(userName.trim())
        val ok = u != null && u.isActive && u.pwdHash.isNotEmpty() && Passwords.verify(password, u.pwdHash)
        if (!ok) {
            failures++
            if (failures >= 5) { failures = 0; lockedUntil = now + 30_000 }
            throw AuthException("اسم المستخدم أو كلمة المرور غير صحيحة")
        }
        failures = 0
        return u!!
    }

    suspend fun setupAdminPassword(admin: AppUser, password: String): AppUser {
        Passwords.validate(password)?.let { throw AuthException(it) }
        val updated = admin.copy(pwdHash = Passwords.hash(password))
        db.users().update(updated)
        return updated
    }

    suspend fun changePassword(userId: Long, old: String, new: String) {
        Passwords.validate(new)?.let { throw AuthException(it) }
        val u = db.users().byId(userId) ?: throw AuthException("المستخدم غير موجود")
        if (!Passwords.verify(old, u.pwdHash)) throw AuthException("كلمة المرور الحالية غير صحيحة")
        db.users().update(u.copy(pwdHash = Passwords.hash(new)))
    }
}

class UserRepository(private val db: AppDatabase) {
    suspend fun create(userName: String, name: String, password: String, cashAccountId: Long, branchId: Long, isAdmin: Boolean): Long {
        if (userName.isBlank() || name.isBlank()) throw AuthException("اكتب الاسم واسم المستخدم")
        Passwords.validate(password)?.let { throw AuthException(it) }
        if (db.users().byUserName(userName.trim()) != null) throw AuthException("اسم المستخدم مستخدم")
        return db.users().insert(AppUser(userName = userName.trim(), name = name.trim(), pwdHash = Passwords.hash(password), cashAccountId = cashAccountId, branchId = branchId, isAdmin = isAdmin))
    }

    suspend fun update(u: AppUser, newPassword: String?) {
        if (u.name.isBlank()) throw AuthException("اكتب الاسم")
        val old = db.users().byId(u.id) ?: throw AuthException("المستخدم غير موجود")
        // لا يُترك النظام بلا مدير فعّال
        if (old.isAdmin && old.isActive && (!u.isAdmin || !u.isActive) && db.users().activeAdmins() <= 1) throw AuthException("يجب أن يبقى مدير فعّال واحد على الأقل")
        val hash = if (newPassword.isNullOrEmpty()) old.pwdHash else { Passwords.validate(newPassword)?.let { throw AuthException(it) }; Passwords.hash(newPassword) }
        db.users().update(u.copy(userName = old.userName, pwdHash = hash))
    }

    suspend fun savePrivs(userId: Long, privs: List<UserPriv>) {
        db.users().deletePrivs(userId)
        db.users().insertPrivs(privs.filter { it.canView || it.canNew || it.canEdit || it.canDelete }.map { it.copy(userId = userId) })
    }
}
