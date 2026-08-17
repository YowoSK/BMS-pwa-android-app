package com.example.kmbe_bms

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay


private enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    OFFLINE
}


class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "kmbe_bms_settings"
        private const val KEY_SERVER_URL = "server_url"

        private const val DEFAULT_PORT = 1880
        private const val NODE_RED_PATH = "/ui/"

        // 15 seconds
        private const val CONNECTION_INFO_TIMEOUT_MS = 15_000L
    }

    private var webView: WebView? = null

    private var savedUrl by mutableStateOf<String?>(null)

    private var connectionState by mutableStateOf(
        ConnectionState.CONNECTING
    )

    private var showServerDialog by mutableStateOf(false)

    private var serverInput by mutableStateOf("")

    private val preferences by lazy {
        getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.Q
        ) {
            window.isNavigationBarContrastEnforced = false
        }

        enterImmersiveFullscreen()


        savedUrl = preferences.getString(
            KEY_SERVER_URL,
            null
        )

        if (savedUrl == null) {
            serverInput = ""
            showServerDialog = true
        } else {
            serverInput = serverToInput(savedUrl!!)
            connectionState = ConnectionState.CONNECTING
        }


        setContent {

            MaterialTheme {

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),

                        factory = { context ->
                            createWebView(context)
                        }
                    )


                    ConnectionHeader(
                        state = connectionState,
                        serverUrl = savedUrl,

                        onChangeServer = {

                            serverInput =
                                savedUrl?.let {
                                    serverToInput(it)
                                } ?: ""

                            showServerDialog = true
                        }
                    )


                    if (showServerDialog) {

                        ServerDialog(
                            value = serverInput,

                            onValueChange = {
                                serverInput = it
                            },

                            onCancel = {

                                if (savedUrl != null) {
                                    showServerDialog = false
                                }
                            },

                            onConfirm = {
                                saveAndConnect(serverInput)
                            },

                            canCancel = savedUrl != null
                        )
                    }
                }
            }
        }


        setupBackNavigation()
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(
        context: Context
    ): WebView {

        return WebView(context).apply {

            webView = this

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setBackgroundColor(Color.TRANSPARENT)


            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true

                cacheMode = WebSettings.LOAD_DEFAULT

                useWideViewPort = true
                loadWithOverviewMode = false

                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)

                loadsImagesAutomatically = true

                allowFileAccess = false
                allowContentAccess = false

                mixedContentMode =
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }


            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    return !isAllowedServerUrl(
                        request.url.toString()
                    )
                }


                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
                ): Boolean {

                    return !isAllowedServerUrl(url)
                }


                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?
                ) {

                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    connectionState =
                        ConnectionState.CONNECTING
                }


                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    if (isAllowedServerUrl(url)) {

                        connectionState =
                            ConnectionState.CONNECTED
                    }
                }


                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {

                    super.onReceivedError(
                        view,
                        request,
                        error
                    )

                    if (request.isForMainFrame) {

                        connectionState =
                            ConnectionState.OFFLINE
                    }
                }


                @Suppress("DEPRECATION")
                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {

                    super.onReceivedError(
                        view,
                        errorCode,
                        description,
                        failingUrl
                    )

                    connectionState =
                        ConnectionState.OFFLINE
                }
            }


            savedUrl?.let {
                loadUrl(it)
            }
        }
    }


    private fun saveAndConnect(
        input: String
    ) {

        val normalizedUrl =
            normalizeServerInput(input)
                ?: return


        preferences
            .edit()
            .putString(
                KEY_SERVER_URL,
                normalizedUrl
            )
            .apply()


        savedUrl = normalizedUrl

        serverInput =
            serverToInput(normalizedUrl)

        connectionState =
            ConnectionState.CONNECTING

        showServerDialog = false


        webView?.apply {
            stopLoading()
            clearHistory()
            loadUrl(normalizedUrl)
        }
    }


    private fun normalizeServerInput(
        input: String
    ): String? {

        var value = input.trim()

        if (value.isEmpty()) {
            return null
        }

        value = value.trimEnd('/')


        if (
            !value.startsWith("http://") &&
            !value.startsWith("https://")
        ) {
            value = "http://$value"
        }


        val uri =
            try {
                Uri.parse(value)
            } catch (_: Exception) {
                return null
            }


        val scheme =
            uri.scheme?.lowercase()
                ?: return null

        if (
            scheme != "http" &&
            scheme != "https"
        ) {
            return null
        }


        val host =
            uri.host ?: return null


        if (uri.userInfo != null) {
            return null
        }

        if (uri.fragment != null) {
            return null
        }


        val port =
            if (uri.port == -1) {
                DEFAULT_PORT
            } else {
                uri.port
            }


        if (port !in 1..65535) {
            return null
        }


        return "$scheme://$host:$port$NODE_RED_PATH"
    }


    private fun serverToInput(
        url: String
    ): String {

        return try {

            val uri = Uri.parse(url)

            val port =
                if (uri.port == -1) {
                    ""
                } else {
                    ":${uri.port}"
                }

            "${uri.host}$port"

        } catch (_: Exception) {

            ""
        }
    }


    private fun isAllowedServerUrl(
        url: String
    ): Boolean {

        val configured =
            savedUrl ?: return false

        val configuredUri =
            try {
                Uri.parse(configured)
            } catch (_: Exception) {
                return false
            }

        val targetUri =
            try {
                Uri.parse(url)
            } catch (_: Exception) {
                return false
            }


        return configuredUri.scheme ==
                targetUri.scheme &&

                configuredUri.host ==
                targetUri.host &&

                configuredUri.port ==
                targetUri.port
    }


    private fun setupBackNavigation() {

        onBackPressedDispatcher.addCallback(
            this,

            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    val currentWebView = webView

                    if (
                        currentWebView?.canGoBack() == true
                    ) {

                        currentWebView.goBack()

                    } else {

                        isEnabled = false

                        onBackPressedDispatcher
                            .onBackPressed()
                    }
                }
            }
        )
    }


    private fun enterImmersiveFullscreen() {

        val controller =
            WindowInsetsControllerCompat(
                window,
                window.decorView
            )


        controller.hide(
            WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.navigationBars()
        )


        controller.systemBarsBehavior =
            WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }


    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {

        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            enterImmersiveFullscreen()
        }
    }


    override fun onDestroy() {

        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }

        webView = null

        super.onDestroy()
    }
}


/*
 * CONNECTION HEADER
 *
 * - Vpravo hore
 * - Po otvorení zostane zobrazený 15 sekúnd
 * - Potom sa automaticky skryje
 * - Hamburger zostane dostupný
 * - Kliknutím na hamburger sa informácie znova zobrazia
 * - Po otvorení sa 15-sekundový časovač spustí odznova
 * - Pri zmene stavu pripojenia sa panel tiež zobrazí
 *
 */

@Composable
private fun ConnectionHeader(
    state: ConnectionState,
    serverUrl: String?,
    onChangeServer: () -> Unit
) {

    var expanded by remember {
        mutableStateOf(true)
    }


    val statusColor =
        when (state) {

            ConnectionState.CONNECTED ->
                ComposeColor(0xFF32D74B)

            ConnectionState.CONNECTING ->
                ComposeColor(0xFFFFCC00)

            ConnectionState.OFFLINE ->
                ComposeColor(0xFFFF453A)
        }


    val statusText =
        when (state) {

            ConnectionState.CONNECTED ->
                "PRIPOJENÉ"

            ConnectionState.CONNECTING ->
                "PRIPÁJANIE..."

            ConnectionState.OFFLINE ->
                "ODPOJENÉ"
        }


    val topInset =
        WindowInsets.statusBars
            .asPaddingValues()
            .calculateTopPadding()


    /*
     * Tento efekt sa spustí pri každej zmene expanded
     *
     * Keď sa panel otvorí:
     *     expanded = true
     *
     * začne odpočítavanie 15 sekúnd
     *
     * Po 15 sekundách:
     *     expanded = false
     *
     * Panel sa skryje a zostane iba burgir
     */

    LaunchedEffect(expanded) {

        if (expanded) {

            delay(
                15_000L
            )

            expanded = false
        }
    }


    /*
     * Pri zmene stavu pripojenia panel opäť otvorí
     *
     * LaunchedEffect(expanded) následne automaticky
     * spustí nový 15-sekundový časovač
     */

    LaunchedEffect(state) {

        expanded = true
    }


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = topInset,
                start = 12.dp,
                end = 12.dp
            )
    ) {

        if (expanded) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        ComposeColor(0xE6161616)
                    )
                    .border(
                        width = 1.dp,
                        color =
                            ComposeColor(0x33FFFFFF)
                    )
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 14.dp,
                            vertical = 9.dp
                        ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .background(
                                statusColor
                            )
                    )


                    Spacer(
                        modifier =
                            Modifier.width(9.dp)
                    )


                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            text =
                                statusText,

                            color =
                                ComposeColor.White,

                            fontSize = 13.sp,

                            fontWeight =
                                FontWeight.Bold
                        )


                        if (
                            !serverUrl.isNullOrBlank()
                        ) {

                            Text(
                                text =
                                    serverUrl,

                                color =
                                    ComposeColor(
                                        0xFFBDBDBD
                                    ),

                                fontSize = 10.sp,

                                maxLines = 1
                            )
                        }
                    }


                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )


                    Box(
                        modifier = Modifier
                            .clickable {
                                onChangeServer()
                            }
                            .padding(
                                horizontal = 10.dp,
                                vertical = 8.dp
                            ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                "ZMENIŤ IP",

                            color =
                                ComposeColor.White,

                            fontSize = 11.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }


                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(40.dp)
                            .clickable {
                                expanded = false
                            },

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text = "×",

                            color =
                                ComposeColor.White,

                            fontSize = 22.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

        } else {

            /*
             * burgermenu v pravom hornom rohu
             */

            Box(
                modifier =
                    Modifier.fillMaxWidth(),

                contentAlignment =
                    Alignment.TopEnd
            ) {

                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(46.dp)
                        .background(
                            ComposeColor(0xCC161616)
                        )
                        .border(
                            width = 1.dp,
                            color =
                                ComposeColor(0x33FFFFFF)
                        )
                        .clickable {

                            expanded = true
                        },

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "☰",

                        color =
                            ComposeColor.White,

                        fontSize = 22.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }
    }
}



//SERVER DIALOG

@Composable
private fun ServerDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    canCancel: Boolean
) {

    Dialog(
        onDismissRequest = {

            if (canCancel) {
                onCancel()
            }
        },

        properties =
            DialogProperties(
                dismissOnBackPress =
                    canCancel,

                dismissOnClickOutside =
                    canCancel,

                usePlatformDefaultWidth =
                    false
            )
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 24.dp
                )
                .background(
                    ComposeColor(0xFF202020)
                )
                .border(
                    width = 1.dp,
                    color =
                        ComposeColor(0x55FFFFFF)
                )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {

                Text(
                    text =
                        "KMBE BMS SERVER",

                    color =
                        ComposeColor.White,

                    fontSize = 18.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )


                Text(
                    text =
                        "Zadajte IP adresu alebo VPN adresu vášho BMS servera.",

                    color =
                        ComposeColor(0xFFD0D0D0),

                    fontSize = 14.sp
                )


                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )


                Text(
                    text =
                        "IP ADRESA SERVERA / VPN IP",

                    color =
                        ComposeColor(0xFFBDBDBD),

                    fontSize = 11.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )


                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            ComposeColor(0xFF151515)
                        )
                        .border(
                            width = 1.dp,
                            color =
                                ComposeColor(0xFF707070)
                        )
                        .padding(
                            horizontal = 12.dp,
                            vertical = 12.dp
                        )
                ) {

                    BasicTextField(
                        value = value,

                        onValueChange =
                            onValueChange,

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true,

                        textStyle =
                            TextStyle(
                                color =
                                    ComposeColor.White,

                                fontSize = 15.sp
                            ),

                        decorationBox = {
                                innerTextField ->

                            if (
                                value.isEmpty()
                            ) {

                                Text(
                                    text =
                                        "12.34.567.890:1880",

                                    color =
                                        ComposeColor(
                                            0xFF777777
                                        ),

                                    fontSize = 15.sp
                                )
                            }

                            innerTextField()
                        }
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                Text(
                    text =
                        "Príklad: 12.34.567.890 alebo 11.34.567.890:1880",

                    color =
                        ComposeColor(0xFF888888),

                    fontSize = 11.sp
                )


                Spacer(
                    modifier =
                        Modifier.height(22.dp)
                )


                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.End,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    if (canCancel) {

                        Box(
                            modifier = Modifier
                                .background(
                                    ComposeColor(
                                        0xFF303030
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color =
                                        ComposeColor(
                                            0xFF666666
                                        )
                                )
                                .clickable {
                                    onCancel()
                                }
                                .padding(
                                    horizontal = 18.dp,
                                    vertical = 12.dp
                                )
                        ) {

                            Text(
                                text =
                                    "ZRUŠIŤ",

                                color =
                                    ComposeColor.White,

                                fontSize = 12.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }


                        Spacer(
                            modifier =
                                Modifier.width(10.dp)
                        )
                    }


                    Box(
                        modifier = Modifier
                            .background(
                                ComposeColor(
                                    0xFF1976D2
                                )
                            )
                            .clickable {

                                if (
                                    value
                                        .trim()
                                        .isNotEmpty()
                                ) {
                                    onConfirm()
                                }
                            }
                            .padding(
                                horizontal = 18.dp,
                                vertical = 12.dp
                            )
                    ) {

                        Text(
                            text =
                                "PRIPOJIŤ",

                            color =
                                ComposeColor.White,

                            fontSize = 12.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}