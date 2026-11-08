package com.novatrade.demo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import kotlin.random.Random

/** The trader's profile, sign-in state and security settings. */
class ProfilePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    private val _name = MutableStateFlow(prefs.getString("name", null) ?: "Trader")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _notify = MutableStateFlow(prefs.getBoolean("notify", true))
    /** Pop-ups for automatic events (SL/TP hits, pending fills, stop outs, price alerts). */
    val notify: StateFlow<Boolean> = _notify.asStateFlow()

    private val _loggedIn = MutableStateFlow(prefs.getBoolean("loggedIn", true))
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _hasPin = MutableStateFlow(prefs.getString("pin", null) != null)
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    // On by default once a PIN exists; the user can switch it off in Settings.
    private val _biometrics = MutableStateFlow(prefs.getBoolean("biometrics", prefs.getString("pin", null) != null))
    val biometrics: StateFlow<Boolean> = _biometrics.asStateFlow()

    private val _pinInterval = MutableStateFlow(prefs.getInt("pinInterval", 10))
    /** Minutes the app may stay in the background before the PIN is asked again (0 = immediately). */
    val pinInterval: StateFlow<Int> = _pinInterval.asStateFlow()

    private val _secureScreen = MutableStateFlow(prefs.getBoolean("secureScreen", false))
    /** Hides the app in recent tasks and blocks screenshots. */
    val secureScreen: StateFlow<Boolean> = _secureScreen.asStateFlow()

    private val _flipToHide = MutableStateFlow(prefs.getBoolean("flipToHide", false))
    val flipToHide: StateFlow<Boolean> = _flipToHide.asStateFlow()

    private val _balancesHidden = MutableStateFlow(false)
    /** Toggled by turning the phone face down when [flipToHide] is on. */
    val balancesHidden: StateFlow<Boolean> = _balancesHidden.asStateFlow()

    private val _locked = MutableStateFlow(_hasPin.value)
    /** True while the PIN screen must be shown. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    val id: Long = prefs.getLong("id", 0L).takeIf { it != 0L } ?: Random.nextLong(100_000_000, 999_999_999).also {
        prefs.edit().putLong("id", it).apply()
    }

    fun setName(value: String) = save("name", value) { _name.value = value }

    fun setNotify(on: Boolean) = save("notify", on) { _notify.value = on }

    fun logIn(name: String?) {
        if (!name.isNullOrBlank()) setName(name.trim())
        save("loggedIn", true) { _loggedIn.value = true }
    }

    fun logOut() = save("loggedIn", false) { _loggedIn.value = false }

    fun setPin(pin: String?) {
        if (pin == null) {
            prefs.edit().remove("pin").putBoolean("biometrics", false).apply()
            _hasPin.value = false
            _biometrics.value = false
        } else {
            prefs.edit().putString("pin", hash(pin)).putInt("pinLength", pin.length).apply()
            _hasPin.value = true
        }
    }

    /** Digits in the saved PIN; PINs from before 6-digit PINs were 4 long. */
    val pinLength: Int get() = prefs.getInt("pinLength", 4)

    /** Forgot PIN: clears the PIN and signs out; signing in again needs no PIN. */
    fun forgotPin() {
        setPin(null)
        _locked.value = false
        logOut()
    }

    fun checkPin(pin: String): Boolean = prefs.getString("pin", null) == hash(pin)

    fun setBiometrics(on: Boolean) = save("biometrics", on) { _biometrics.value = on }

    fun setPinInterval(minutes: Int) = save("pinInterval", minutes) { _pinInterval.value = minutes }

    fun setSecureScreen(on: Boolean) = save("secureScreen", on) { _secureScreen.value = on }

    fun setFlipToHide(on: Boolean) = save("flipToHide", on) {
        _flipToHide.value = on
        if (!on) _balancesHidden.value = false
    }

    fun toggleBalances() {
        _balancesHidden.value = !_balancesHidden.value
    }

    fun unlock() {
        _locked.value = false
    }

    /** Called when the app returns from the background after [awayMillis]. */
    fun onReturn(awayMillis: Long) {
        if (_hasPin.value && awayMillis >= _pinInterval.value * 60_000L) _locked.value = true
    }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("novatrade:$id:$pin").toByteArray()).joinToString("") { "%02x".format(it) }

    private fun save(key: String, value: Any, apply: () -> Unit) {
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }.apply()
        apply()
    }
}
