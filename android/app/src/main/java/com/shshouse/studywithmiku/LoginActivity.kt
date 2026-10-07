@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.shshouse.studywithmiku

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity: ComponentActivity() {
    private val app get() = application as MikuApplication
    private var web: WebView? = null
    private var accepting = false
    private var pageError by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(true)
    private fun intercept(url: String): Boolean {
        val token = StudyProtocol.callbackToken(url) ?: return false
        if (accepting) return true
        accepting = true
        web?.stopLoading(); web?.loadUrl("about:blank"); web?.clearHistory()
        lifecycleScope.launch {
            if (app.auth.accept(token)) finish()
            else { accepting = false; pageError = app.auth.state.value.error; loading = false }
        }
        return true
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val prefs by app.store.prefs.collectAsStateWithLifecycle()
            val account by app.auth.state.collectAsStateWithLifecycle()
            MikuTheme(prefs) {
                BackHandler { if (web?.canGoBack() == true && !accepting) web?.goBack() else finish() }
                Scaffold(topBar = {
                    TopAppBar(title = { Text("MikuMod 官方登录") },navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"返回") } },
                        actions = { TextButton(onClick = { pageError = null; accepting = false; web?.loadUrl(LOGIN_URL) },enabled = !account.validating) { Text("重试") } })
                }) { insets ->
                    Column(Modifier.fillMaxSize().padding(insets)) {
                        if (loading || account.validating) LinearProgressIndicator(Modifier.fillMaxWidth())
                        pageError?.let { Text(it,Modifier.padding(16.dp),color = MaterialTheme.colorScheme.error) }
                        AndroidView(modifier = Modifier.fillMaxSize(),factory = { context ->
                            WebView(context).apply {
                                web = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.setSupportMultipleWindows(true)
                                webViewClient = object: WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest): Boolean {
                                        if (!request.isForMainFrame) return false
                                        val url = request.url.toString()
                                        if (intercept(url)) return true
                                        if (request.url.scheme !in listOf("https","about")) {
                                            pageError = "此登录方式需要外部应用，请选择官方页面中的其他登录方式"; return true
                                        }
                                        return false
                                    }
                                    override fun onPageStarted(view: WebView,url: String,favicon: android.graphics.Bitmap?) {
                                        if (!intercept(url)) loading = true
                                    }
                                    override fun onPageFinished(view: WebView,url: String) { loading = false; intercept(url) }
                                    override fun onReceivedError(view: WebView,request: WebResourceRequest,error: WebResourceError) {
                                        if (request.isForMainFrame) { pageError = "官方登录页加载失败，请检查网络后重试"; loading = false }
                                    }
                                    override fun onReceivedSslError(view: WebView,handler: SslErrorHandler,error: android.net.http.SslError) {
                                        handler.cancel(); pageError = "官方登录页的安全连接验证失败"; loading = false
                                    }
                                }
                                webChromeClient = object: WebChromeClient() {
                                    override fun onProgressChanged(view: WebView,progress: Int) { view.url?.let(::intercept) }
                                    override fun onCreateWindow(view: WebView,isDialog: Boolean,isUserGesture: Boolean,resultMsg: android.os.Message): Boolean {
                                        if (!isUserGesture) return false
                                        val popup = WebView(context)
                                        popup.webViewClient = object: WebViewClient() {
                                            override fun shouldOverrideUrlLoading(child: WebView,request: WebResourceRequest): Boolean {
                                                if (request.isForMainFrame && request.url.scheme == "https") {
                                                    view.loadUrl(request.url.toString()); child.destroy(); return true
                                                }
                                                return false
                                            }
                                        }
                                        (resultMsg.obj as WebView.WebViewTransport).webView = popup
                                        resultMsg.sendToTarget(); return true
                                    }
                                }
                                loadUrl(LOGIN_URL)
                            }
                        })
                    }
                }
            }
        }
    }
    override fun onDestroy() { web?.apply { stopLoading(); clearHistory(); destroy() }; web = null; super.onDestroy() }
}
