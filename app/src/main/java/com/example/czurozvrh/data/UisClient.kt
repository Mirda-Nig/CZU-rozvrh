package com.example.czurozvrh.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface LoginResult {
    data object Success : LoginResult
    data class Need2FA(val authId: String, val type: String) : LoginResult
    data class Failed(val message: String) : LoginResult
}

class NotLoggedInException : IOException("Přihlášení vypršelo")

/** Komunikace s UIS ČZU (is.czu.cz). */
class UisClient(private val cookieJar: PersistentCookieJar) {
    private val base = "https://is.czu.cz"

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android) CZURozvrh/1.0")
                    .header("Accept-Language", "cs")
                    .build()
            )
        }
        .build()

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use {
            val body = it.body?.string().orEmpty()
            // UIS vrací nepřihlášenému 401/403 spolu s přihlašovacím formulářem – to není chyba,
            // ale signál "nejsi přihlášen" (volající to pozná přes TimetableParser.isLoginPage).
            if (!it.isSuccessful && !TimetableParser.isLoginPage(body)) throw IOException("UIS vrátil chybu ${it.code}")
            body
        }

    private fun post(url: String, form: Map<String, String>, ajax: Boolean = false): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(url).post(body).apply {
            if (ajax) header("X-Requested-With", "XMLHttpRequest")
        }.build()
        return http.newCall(req).execute().use {
            val body = it.body?.string().orEmpty()
            // UIS vrací nepřihlášenému 401/403 spolu s přihlašovacím formulářem – to není chyba,
            // ale signál "nejsi přihlášen" (volající to pozná přes TimetableParser.isLoginPage).
            if (!it.isSuccessful && !TimetableParser.isLoginPage(body)) throw IOException("UIS vrátil chybu ${it.code}")
            body
        }
    }

    /**
     * Přihlášení stejně jako webový formulář: nejdřív AJAX ověření jména/hesla (zjistí, jestli je
     * potřeba dvoufázové ověření), pak odeslání formuláře na /system/login.pl.
     */
    suspend fun login(
        creds: Credentials,
        twoFaCode: String? = null,
        authId: String = "0",
        twoFaType: String = "no",
    ): LoginResult = withContext(Dispatchers.IO) {
        try {
            runCatching { get("$base/auth/") } // získá úvodní cookies (nepřihlášenému vrací 403 + formulář)

            if (twoFaCode == null) {
                val ajax = runCatching {
                    JSONObject(post("$base/system/ajax_handler.pl",
                        mapOf("login" to creds.login, "password" to creds.password), ajax = true).trim())
                }.getOrNull()
                if (ajax != null) {
                    if (ajax.has("error")) {
                        val err = ajax.optString("error")
                        return@withContext LoginResult.Failed(
                            if (err.contains("missing")) "Vyplň jméno i heslo." else "Špatné jméno nebo heslo."
                        )
                    }
                    if (ajax.optString("need2FA") == "true") {
                        return@withContext LoginResult.Need2FA(
                            ajax.optString("authID", "0"), ajax.optString("type", "no")
                        )
                    }
                }
            }

            val form = buildMap {
                put("login_hidden", "1")
                put("destination", "/auth/")
                put("auth_id_hidden", authId)
                put("auth_2fa_type", twoFaType)
                put("credential_0", creds.login)
                put("credential_1", creds.password)
                if (twoFaCode != null) put("credential_k", twoFaCode)
                put("credential_cookie", "1")
                put("login", "Přihlásit se")
            }
            val html = post("$base/system/login.pl", form)
            if (TimetableParser.isLoginPage(html)) LoginResult.Failed("Přihlášení se nepovedlo – zkontroluj údaje.")
            else LoginResult.Success
        } catch (e: IOException) {
            LoginResult.Failed("Nepodařilo se spojit s UIS: ${e.message}")
        }
    }

    /** Zkusí najít číslo osobního rozvrhu (rozvrh_student=…) na stránkách studia. */
    suspend fun detectStudentId(): String? = withContext(Dispatchers.IO) {
        val re = Regex("""rozvrh_student=(\d+)""")
        for (path in listOf("/auth/student/moje_studium.pl?lang=cz", "/auth/?lang=cz", "/auth/student/?lang=cz")) {
            val html = runCatching { get(base + path) }.getOrNull() ?: continue
            re.find(html)?.let { return@withContext it.groupValues[1] }
        }
        null
    }

    fun timetableUrl(studentId: String, format: String = "list") =
        "$base/auth/katalog/rozvrhy_view.pl?rozvrh_student_obec=1;zobraz=1;format=$format;rozvrh_student=$studentId;lang=cz"

    suspend fun fetchTimetable(studentId: String): Timetable = withContext(Dispatchers.IO) {
        val html = get(timetableUrl(studentId))
        if (TimetableParser.isLoginPage(html)) throw NotLoggedInException()
        val t = TimetableParser.parse(html)
        if (t.lessons.isEmpty() && html.contains("Chyba při použití")) {
            throw IOException("UIS hlásí chybu při zobrazení rozvrhu – zkontroluj číslo studenta.")
        }
        t
    }

    fun logout() = cookieJar.clear()
}
