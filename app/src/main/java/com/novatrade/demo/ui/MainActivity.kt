package com.novatrade.demo.ui

import com.novatrade.demo.Brand

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.novatrade.demo.TradingApp
import com.novatrade.demo.ui.theme.NovaTradeTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity(), SensorEventListener {
    private val app get() = application as TradingApp
    private var stoppedAt = 0L
    private var faceDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        lifecycleScope.launch {
            app.profile.secureScreen.collect { on ->
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        val canUseBiometrics = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        setContent {
            NovaTradeTheme {
                // Surface gives every screen (including PIN and sign-in, drawn outside the Scaffold) light text on the dark background.
                androidx.compose.material3.Surface(color = com.novatrade.demo.ui.theme.AppColors.Background, contentColor = com.novatrade.demo.ui.theme.AppColors.TextPrimary) {
                AppRoot(
                    app.market, app.engine, app.favorites, app.chartPrefs, app.drawingStore, app.profile, app.alerts, app.insights,
                    onBiometric = if (canUseBiometrics) ::authenticate else null,
                )
                }
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sign in to ${Brand.name}")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0) app.profile.onReturn(System.currentTimeMillis() - stoppedAt)
        val sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
    }

    /** Turning the phone face down toggles hidden balances when that setting is on. */
    override fun onSensorChanged(event: SensorEvent) {
        if (!app.profile.flipToHide.value) return
        val z = event.values[2]
        if (!faceDown && z < -8.5f) {
            faceDown = true
            app.profile.toggleBalances()
        } else if (faceDown && z > -2f) {
            faceDown = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
