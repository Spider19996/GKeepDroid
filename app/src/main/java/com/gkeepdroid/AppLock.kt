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
    /** Counts every "lock now", so open screens lock at once. */
    val lockVersion = androidx.compose.runtime.mutableIntStateOf(0)

    enum class Result { OK, CANCELED, FAILED }

    private var openedOnce = false
    private var lastActive = 0L

    /** Seconds in the background (or the lock screen) that the app stays open; at least two, so passing the lock itself never asks again. */
    private fun graceMs(context: android.content.Context) = maxOf(AppSettingsStore.load(context).lockGraceSec * 1000L, 2_000L)

    private fun valid(context: android.content.Context) =
        openedOnce && SystemClock.elapsedRealtime() - lastActive < graceMs(context)

    /** Locks at once: the next look at the app asks for the lock. */
    fun lockNow() {
        openedOnce = false
        lockVersion.intValue++
    }

    private fun touch() {
        lastActive = SystemClock.elapsedRealtime()
    }

    private fun deviceSecure(context: android.content.Context) = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    fun enabled(activity: ComponentActivity) = AppSettingsStore.load(activity).lockApp && deviceSecure(activity)

    /** True when notes may be changed now: no edit lock, or the lock was passed a moment ago (or the whole app is unlocked). */
    fun editAllowed(activity: ComponentActivity): Boolean {
        val settings = AppSettingsStore.load(activity)
        if (!settings.lockEdit || !deviceSecure(activity)) return true
        return valid(activity)
    }

    /** Asks for the lock before something is changed; [onDone] gets true when it was passed. */
    fun unlockEdit(activity: ComponentActivity, onDone: (Boolean) -> Unit) {
        // Android 8 has no prompt to start from here; the edit lock needs Android 9 or newer.
        if (Build.VERSION.SDK_INT < 29) return onDone(true)
        prompt(activity, { }) { result ->
            if (result == Result.OK) {
                openedOnce = true
                touch()
            }
            onDone(result == Result.OK)
        }
    }

    /** Asks for the lock; [onResult] gets true when it was passed. */
    @Suppress("DEPRECATION")
    fun prompt(activity: ComponentActivity, launchCredential: (android.content.Intent) -> Unit, onResult: (Result) -> Unit) {
        val title = activity.getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            val builder = BiometricPrompt.Builder(activity).setTitle(title)
            if (Build.VERSION.SDK_INT >= 30) {
                builder.setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                builder.setDeviceCredentialAllowed(true)
            }
            val prompt = builder.build()
            try {
                prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(Result.OK)
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED || errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        ) Result.CANCELED else Result.FAILED
                    )
                })
            } catch (e: Exception) {
                onResult(Result.FAILED)
            }
        } else {
            // Android 8 and 9: the credential screen of the phone (no fingerprint here).
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            val intent = keyguard.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) onResult(Result.OK) else launchCredential(intent)
        }
    }

    /** Shows [content] once the lock is passed; with the lock off (or no lock on the phone) it shows it at once. */
    @Composable
    fun Gate(activity: ComponentActivity, content: @Composable () -> Unit) {
        if (!enabled(activity)) {
            content()
            return
        }
        var unlocked by remember { mutableStateOf(valid(activity)) }
        var asking by remember { mutableStateOf(false) }
        val credential = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            asking = false
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                openedOnce = true
                touch()
                unlocked = true
            } else {
                activity.finish()
            }
        }
        fun ask() {
            if (asking) return
            asking = true
            prompt(activity, { credential.launch(it) }) { result ->
                asking = false
                when (result) {
                    Result.OK -> {
                        openedOnce = true
                        touch()
                        unlocked = true
                    }
                    // Backing out of the lock leaves the app instead of leaving an empty screen.
                    Result.CANCELED, Result.FAILED -> activity.finish()
                }
            }
        }
        // "Lock now" (menu, screen off) takes effect while the app is open, too.
        val lockCount = lockVersion.intValue
        androidx.compose.runtime.LaunchedEffect(lockCount) {
            if (!valid(activity)) {
                unlocked = false
                if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) ask()
            }
        }
        DisposableEffect(activity) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        if (unlocked && !asking) touch()
                        // Closing the app with Back locks it again at once.
                        if (activity.isFinishing && activity.isTaskRoot) lockNow()
                    }
                    Lifecycle.Event.ON_RESUME -> if (!valid(activity)) {
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
            // Nothing to show while locked: the lock dialog is on top, and Back or Home leave the app.
            Box(Modifier.fillMaxSize())
        }
    }
}
