package com.gkeepdroid

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * The lock of the whole app: fingerprint (or face), or the PIN, pattern or password of the phone where
 * it has no sensor. Once opened, the app stays open while it is used and for a short time in the background.
 */
object AppLock {
    private const val GRACE_MS = 30_000L

    private var openedOnce = false
    private var lastActive = 0L

    private fun valid() = openedOnce && SystemClock.elapsedRealtime() - lastActive < GRACE_MS

    private fun touch() {
        lastActive = SystemClock.elapsedRealtime()
    }

    fun enabled(activity: ComponentActivity) =
        AppSettingsStore.load(activity).lockApp && activity.getSystemService(KeyguardManager::class.java).isDeviceSecure

    /** Asks for the lock; [onResult] gets true when it was passed. */
    @Suppress("DEPRECATION")
    fun prompt(activity: ComponentActivity, launchCredential: (android.content.Intent) -> Unit, onResult: (Boolean) -> Unit) {
        val title = activity.getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false)
            })
        } else {
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            val intent = keyguard.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) onResult(true) else launchCredential(intent)
        }
    }

    /** Shows [content] once the lock is passed; with the lock off (or no lock on the phone) it shows it at once. */
    @Composable
    fun Gate(activity: ComponentActivity, content: @Composable () -> Unit) {
        if (!enabled(activity)) {
            content()
            return
        }
        var unlocked by remember { mutableStateOf(valid()) }
        var asking by remember { mutableStateOf(false) }
        val credential = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            asking = false
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                openedOnce = true
                touch()
                unlocked = true
            }
        }
        fun ask() {
            if (asking) return
            asking = true
            prompt(activity, { credential.launch(it) }) { ok ->
                asking = false
                if (ok) {
                    openedOnce = true
                    touch()
                    unlocked = true
                }
            }
        }
        DisposableEffect(activity) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> if (unlocked && !asking) touch()
                    Lifecycle.Event.ON_RESUME -> if (!valid()) {
                        unlocked = false
                        ask()
                    } else {
                        unlocked = true
                    }
                    else -> {}
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
        if (unlocked) {
            content()
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { ask() }) { Text(stringResource(R.string.app_locked_unlock)) }
            }
        }
    }
}
