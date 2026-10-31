package com.gkeepdroid

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Signs in with Google instead of pasting a master token: Google's own setup page runs in a web
 * view, and once the user has agreed, the page sets an oauth_token cookie. The app swaps that
 * cookie for a master token and stores it; the password never reaches the app. The account's
 * e-mail address is read from the signed-in session, so it does not have to be typed twice.
 */
class LoginActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeMode = SettingsStore.loadGlobal(this).themeMode
        // Start without leftover sessions, so the page really asks who is signing in.
        CookieManager.getInstance().removeAllCookies(null)
        setContent { KeepTheme(themeMode) { LoginScreen() } }
    }

    @Composable
    private fun LoginScreen() {
        val scope = rememberCoroutineScope()
        var webView by remember { mutableStateOf<WebView?>(null) }
        // Where the address can come from: typed into the page, or shown somewhere on it.
        var typedEmail by remember { mutableStateOf("") }
        var pageEmail by remember { mutableStateOf("") }
        var webToken by remember { mutableStateOf<String?>(null) }
        var needEmail by remember { mutableStateOf(false) }
        var manualEmail by remember { mutableStateOf("") }
        var working by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        var attempt by remember { mutableIntStateOf(0) }
        val failedTemplate = stringResource(R.string.login_failed, "%s")

        /** Trades the cookie for a master token and stores the account. */
        fun finishSignIn(email: String, cookieToken: String) {
            working = true
            needEmail = false
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        PythonRuntime.get(this@LoginActivity).getModule("keep_bridge")
                            .callAttr("exchange_master_token", email.trim(), cookieToken, randomAndroidId())
                            .toString()
                    }
                    val json = JSONObject(result)
                    CredentialStore.put(this@LoginActivity, json.getString("email"), json.getString("token"))
                    SyncWorker.syncNow(this@LoginActivity)
                    setResult(RESULT_OK)
                    finish()
                } catch (e: Exception) {
                    error = failedTemplate.format(e.message ?: e.javaClass.simpleName)
                    working = false
                }
            }
        }

        // Wait until the page has set the cookie, then work out which account just signed in.
        LaunchedEffect(attempt) {
            webToken = null
            while (webToken == null) {
                delay(1000)
                // Ask the page for what was typed into its e-mail field and for any address it shows.
                webView?.evaluateJavascript(READ_PAGE_SCRIPT) { raw ->
                    try {
                        val page = JSONObject(JSONTokener(raw).nextValue() as String)
                        val typed = page.optString("typed")
                        if (typed.contains("@")) typedEmail = typed
                        EMAIL_REGEX.find(page.optString("text"))?.value?.let { pageEmail = it }
                    } catch (e: Exception) {
                        // The page was not ready yet; the next round tries again.
                    }
                }
                val cookies = CookieManager.getInstance().getCookie(SETUP_URL).orEmpty()
                webToken = cookies.split(";").map { it.trim() }
                    .firstOrNull { it.startsWith("oauth_token=") }?.removePrefix("oauth_token=")
                    ?.takeIf { it.isNotEmpty() }
                if (webToken != null) {
                    val fromSession = readAccountEmail(cookies)
                    val email = typedEmail.ifEmpty { null } ?: fromSession ?: pageEmail.ifEmpty { null }
                    // Only the way is logged, never the address or the token.
                    Log.i(TAG, "address from: " + when {
                        typedEmail.isNotEmpty() -> "typed field"
                        fromSession != null -> "session"
                        pageEmail.isNotEmpty() -> "page text"
                        else -> "nowhere, asking"
                    })
                    if (email != null) finishSignIn(email, webToken!!) else needEmail = true
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.sign_in_google),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(20.dp),
            )
            when {
                working -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.login_working))
                }
                error.isNotEmpty() -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = {
                            CookieManager.getInstance().removeAllCookies(null)
                            error = ""
                            attempt++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.login_continue)) }
                }
                needEmail -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Reading the address from the session failed: ask for it, once.
                    Text(stringResource(R.string.login_need_email))
                    OutlinedTextField(
                        value = manualEmail, onValueChange = { manualEmail = it },
                        label = { Text(stringResource(R.string.email_label)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { webToken?.let { finishSignIn(manualEmail, it) } },
                        enabled = manualEmail.contains("@"),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.login_continue)) }
                }
                else -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    view.evaluateJavascript(HOOK_SCRIPT, null)
                                }
                            }
                            loadUrl(SETUP_URL)
                            webView = this
                        }
                    },
                )
            }
        }
    }

    /**
     * Asks Google which account the signed-in session belongs to. The answer is a small page that
     * contains the account list as escaped text, so it is unescaped before the address is picked out.
     */
    private suspend fun readAccountEmail(cookies: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(ACCOUNT_LIST_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Cookie", cookies)
            connection.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(this@LoginActivity))
            val body = connection.inputStream.bufferedReader().readText()
            val plain = Regex("\\\\x([0-9a-fA-F]{2})").replace(body) { it.groupValues[1].toInt(16).toChar().toString() }
            EMAIL_REGEX.find(plain)?.value
        } catch (e: Exception) {
            null
        }
    }

    /** A random device id; Google only needs one to be present when exchanging the cookie. */
    private fun randomAndroidId() = Random.nextLong().toULong().toString(16).padStart(16, '0')

    companion object {
        private const val TAG = "KeepLogin"
        private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

        /** Remembers the last address typed into any field of the page, even after the field is gone. */
        private const val HOOK_SCRIPT = "(function(){if(window.__kwHooked)return;window.__kwHooked=true;" +
            "var remember=function(e){var v=e.target&&e.target.value;if(typeof v==='string'&&v.indexOf('@')>0)window.__kw=v;};" +
            "document.addEventListener('input',remember,true);document.addEventListener('change',remember,true);})()"

        private const val READ_PAGE_SCRIPT = "(function(){return JSON.stringify({typed:window.__kw||''," +
            "text:document.body?document.body.innerText:''});})()"

        private const val SETUP_URL = "https://accounts.google.com/EmbeddedSetup"
        private const val ACCOUNT_LIST_URL =
            "https://accounts.google.com/ListAccounts?gpsia=1&source=ChromiumBrowser&json=standard&origin=https%3A%2F%2Faccounts.google.com"
    }
}
