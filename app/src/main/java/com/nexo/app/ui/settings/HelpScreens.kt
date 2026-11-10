package com.nexo.app.ui.settings

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.nexo.app.R
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.SectionHeader
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.navigation.HelpPageKey
import com.nexo.app.ui.navigation.LocalNavigator

data class HelpTopic(val page: String, val title: Int)

val HELP_TOPICS = listOf(
    HelpTopic("getting_started", R.string.help_getting_started),
    HelpTopic("accounts", R.string.help_accounts),
    HelpTopic("budgets", R.string.help_budgets),
    HelpTopic("backup", R.string.help_backup),
    HelpTopic("import", R.string.help_import),
)

private val PAGE_TITLES = HELP_TOPICS.associate { it.page to it.title } + mapOf(
    "faq" to R.string.faq,
    "privacy" to R.string.privacy_policy,
    "terms" to R.string.terms,
    "licenses" to R.string.open_source,
    "whats_new" to R.string.whats_new,
)

/** Help index (CFG-056). */
@Composable
fun HelpScreen() {
    val nav = LocalNavigator.current
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.help), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            HELP_TOPICS.forEach { t -> SettingRow(stringResource(t.title), onClick = { nav.navigate(HelpPageKey(t.page)) }) }
            SettingRow(stringResource(R.string.faq), onClick = { nav.navigate(HelpPageKey("faq")) })
        }
    }
}

/** Feedback (CFG-055). */
@Composable
fun FeedbackScreen() {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.feedback), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p)) {
            SectionHeader(stringResource(R.string.faq))
            SettingRow(stringResource(R.string.faq), onClick = { nav.navigate(HelpPageKey("faq")) })
            SectionHeader(stringResource(R.string.other))
            SettingRow(stringResource(R.string.feedback), stringResource(R.string.support_email), onClick = { runCatching { context.startActivity(feedbackIntent(context)) } })
            SettingRow(stringResource(R.string.mistranslation), onClick = { runCatching { context.startActivity(feedbackIntent(context, R.string.mistranslation_subject)) } })
            SettingRow(stringResource(R.string.privacy_policy), onClick = { nav.navigate(HelpPageKey("privacy")) })
            SettingRow(stringResource(R.string.terms), onClick = { nav.navigate(HelpPageKey("terms")) })
            SettingRow(stringResource(R.string.open_source), onClick = { nav.navigate(HelpPageKey("licenses")) })
            SettingRow(stringResource(R.string.whats_new), onClick = { nav.navigate(HelpPageKey("whats_new")) })
        }
    }
}

/** Offline page viewer: shows only pages shipped in the app and blocks every network load. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HelpPageScreen(key: HelpPageKey) {
    val nav = LocalNavigator.current
    val page = key.page.filter { it.isLetterOrDigit() || it == '_' }
    Scaffold(topBar = { NexoTopBar(stringResource(PAGE_TITLES[page] ?: R.string.help), onBack = { nav.back() }) }) { p ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(p),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                    }
                    loadUrl("file:///android_asset/help/$page.html")
                }
            },
        )
    }
}
