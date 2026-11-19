package com.example.czurozvrh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.czurozvrh.BuildConfig
import com.example.czurozvrh.data.Credentials
import com.example.czurozvrh.data.DemoData
import com.example.czurozvrh.data.LoginResult
import com.example.czurozvrh.data.NeedsManualLogin
import com.example.czurozvrh.data.Timetable
import com.example.czurozvrh.data.TimetableRepository
import com.example.czurozvrh.notify.LessonNotifier
import com.example.czurozvrh.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class UiState(
    val loggedIn: Boolean = false,
    val timetable: Timetable? = null,
    val loading: Boolean = false,
    val error: String? = null,
    // přihlašovací obrazovka
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    val pending2FA: LoginResult.Need2FA? = null,
    val savedLogin: String = "",
    val studentId: String = "",
    val hideEmptyDays: Boolean = false,
    val lessonCountdown: Boolean = true,
)

class RozvrhViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TimetableRepository.get(app)
    private val store = repo.store
    private val cache = repo.cache
    private val client = repo.client
    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        if (BuildConfig.DEMO) {
            _state.value = UiState(loggedIn = true, timetable = DemoData.timetable(), hideEmptyDays = false,
                lessonCountdown = LessonNotifier.isEnabled(app))
        } else initReal()
        LessonNotifier.update(app)
    }

    private fun initReal() {
        val creds = store.credentials
        val cached = cache.load()
        _state.value = UiState(
            loggedIn = creds != null,
            timetable = cached,
            savedLogin = creds?.login.orEmpty(),
            studentId = store.studentId.orEmpty(),
            hideEmptyDays = prefs.getBoolean("hideEmptyDays", false),
            lessonCountdown = LessonNotifier.isEnabled(getApplication()),
        )
        if (creds != null) refresh()
        WidgetUpdater.schedulePeriodic(getApplication())
    }

    fun timetableWebUrl(): String? = store.studentId?.let { client.timetableUrl(it, "html") }

    fun login(login: String, password: String, studentId: String, code: String?) {
        val creds = Credentials(login.trim(), password)
        val pending = _state.value.pending2FA
        _state.update { it.copy(loginBusy = true, loginError = null) }
        viewModelScope.launch {
            val result = if (pending != null && code != null)
                client.login(creds, code.trim(), pending.authId, pending.type)
            else client.login(creds)

            when (result) {
                is LoginResult.Need2FA -> _state.update {
                    it.copy(loginBusy = false, pending2FA = result, loginError = null)
                }
                is LoginResult.Failed -> _state.update {
                    it.copy(loginBusy = false, loginError = result.message)
                }
                LoginResult.Success -> {
                    store.credentials = creds
                    val id = studentId.trim().ifBlank { null } ?: client.detectStudentId()
                    if (id == null) {
                        _state.update {
                            it.copy(loginBusy = false, loginError =
                                "Přihlášeno, ale nenašel jsem číslo rozvrhu. Vyplň ho ručně (je v adrese rozvrhu jako rozvrh_student=…).")
                        }
                        return@launch
                    }
                    store.studentId = id
                    _state.update {
                        it.copy(loginBusy = false, loggedIn = true, pending2FA = null,
                            savedLogin = creds.login, studentId = id)
                    }
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        if (BuildConfig.DEMO) return
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val t = repo.fetch()
                _state.update { it.copy(loading = false, timetable = t) }
                WidgetUpdater.updateAll(getApplication())
                LessonNotifier.update(getApplication())
            } catch (e: NeedsManualLogin) {
                _state.update {
                    it.copy(loading = false, loggedIn = false, loginError = e.message, pending2FA = e.pending2FA ?: it.pending2FA)
                }
            } catch (e: IOException) {
                _state.update {
                    it.copy(loading = false, error =
                        if (it.timetable != null) "Offline – zobrazuji uložený rozvrh." else "Nepodařilo se načíst rozvrh: ${e.message}")
                }
            }
        }
    }

    fun setHideEmptyDays(hide: Boolean) {
        prefs.edit().putBoolean("hideEmptyDays", hide).apply()
        _state.update { it.copy(hideEmptyDays = hide) }
    }

    fun setLessonCountdown(enabled: Boolean) {
        LessonNotifier.setEnabled(getApplication(), enabled)
        _state.update { it.copy(lessonCountdown = enabled) }
    }

    /** Po udělení oprávnění k notifikacím je hned ukaž (pokud právě něco probíhá). */
    fun onNotificationPermissionResult() = LessonNotifier.update(getApplication())

    fun clearError() = _state.update { it.copy(error = null) }

    fun cancel2FA() = _state.update { it.copy(pending2FA = null, loginError = null) }

    fun logout() {
        repo.logout()
        _state.value = UiState(hideEmptyDays = _state.value.hideEmptyDays, lessonCountdown = _state.value.lessonCountdown)
        LessonNotifier.update(getApplication())
        viewModelScope.launch { WidgetUpdater.updateAll(getApplication()) }
    }
}
