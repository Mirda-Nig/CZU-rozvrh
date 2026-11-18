package com.example.czurozvrh.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Credentials(val login: String, val password: String)

/** Přihlašovací údaje šifrované klíčem z Android Keystore (klíč nikdy neopustí zařízení). */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure", Context.MODE_PRIVATE)
    private val alias = "czu_rozvrh_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String? = runCatching {
        val raw = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12))
    }.getOrNull()

    var credentials: Credentials?
        get() {
            val l = prefs.getString("login", null) ?: return null
            val p = prefs.getString("pwd", null)?.let(::decrypt) ?: return null
            return Credentials(l, p)
        }
        set(v) {
            prefs.edit().apply {
                if (v == null) { remove("login"); remove("pwd") }
                else { putString("login", v.login); putString("pwd", encrypt(v.password)) }
            }.apply()
        }

    var studentId: String?
        get() = prefs.getString("studentId", null)
        set(v) = prefs.edit().putString("studentId", v).apply()
}

/** Offline kopie posledního staženého rozvrhu. */
class TimetableCache(context: Context) {
    private val file = File(context.filesDir, "timetable.json")
    fun load(): Timetable? = runCatching { Timetable.fromJson(file.readText()) }.getOrNull()
    fun save(t: Timetable) = file.writeText(t.toJson())
    fun clear() { file.delete() }
}

/** Cookies přežijí restart aplikace, takže se nemusíme přihlašovat při každém spuštění. */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        prefs.all.values.filterIsInstance<String>().mapNotNull(::decode)
            .filter { it.expiresAt > System.currentTimeMillis() }
            .forEach { cookies[keyOf(it)] = it }
    }

    private fun keyOf(c: Cookie) = "${c.domain}|${c.path}|${c.name}"

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val edit = prefs.edit()
        for (c in cookies) {
            val k = keyOf(c)
            if (c.expiresAt < System.currentTimeMillis()) {
                this.cookies.remove(k); edit.remove(k)
            } else {
                this.cookies[k] = c
                // Session cookies (bez expirace) ukládáme také – UIS session drží i po restartu appky.
                edit.putString(k, encode(c))
            }
        }
        edit.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.values.removeAll { it.expiresAt < now }
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() { cookies.clear(); prefs.edit().clear().apply() }

    private fun encode(c: Cookie) = listOf(
        c.name, c.value, c.domain, c.path, c.expiresAt.toString(),
        c.secure.toString(), c.httpOnly.toString(), c.hostOnly.toString(),
    ).joinToString("\t")

    private fun decode(s: String): Cookie? = runCatching {
        val p = s.split("\t")
        Cookie.Builder().name(p[0]).value(p[1]).path(p[3]).expiresAt(p[4].toLong()).apply {
            if (p[7].toBoolean()) hostOnlyDomain(p[2]) else domain(p[2])
            if (p[5].toBoolean()) secure()
            if (p[6].toBoolean()) httpOnly()
        }.build()
    }.getOrNull()
}
