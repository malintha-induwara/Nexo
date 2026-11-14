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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.nexo.app.R
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SectionHeader
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.preview.NexoPreview
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

@Composable
fun HelpRoute() {
    val nav = LocalNavigator.current
    HelpScreen(onBack = nav::back, onOpenPage = { nav.navigate(HelpPageKey(it)) })
}

@Composable
internal fun HelpScreen(onBack: () -> Unit, onOpenPage: (page: String) -> Unit) {
    Scaffold(topBar = { NexoTopBar(stringResource(R.string.help), onBack = onBack) }) { p ->
        Column(Modifier.padding(p)) {
            HELP_TOPICS.forEach { t ->
                SettingRow(stringResource(t.title), onClick = { onOpenPage(t.page) })
            }
            SettingRow(stringResource(R.string.faq), onClick = { onOpenPage("faq") })
        }
    }
}

@Composable
fun FeedbackRoute() {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    FeedbackScreen(
        onBack = nav::back,
        onOpenPage = { nav.navigate(HelpPageKey(it)) },
        onSendFeedback = { runCatching { context.startActivity(feedbackIntent(context)) } },
        onReportMistranslation = {
            runCatching {
                context.startActivity(feedbackIntent(context, R.string.mistranslation_subject))
            }
        },
    )
}

@Composable
internal fun FeedbackScreen(
    onBack: () -> Unit,
    onOpenPage: (page: String) -> Unit,
    onSendFeedback: () -> Unit,
    onReportMistranslation: () -> Unit,
) {
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.feedback), onBack = onBack)
    }) { p ->
        Column(Modifier.padding(p)) {
            SectionHeader(stringResource(R.string.faq))
            SettingRow(stringResource(R.string.faq), onClick = { onOpenPage("faq") })
            SectionHeader(stringResource(R.string.other))
            SettingRow(
                stringResource(R.string.feedback),
                stringResource(R.string.support_email),
                onClick = onSendFeedback,
            )
            SettingRow(stringResource(R.string.mistranslation), onClick = onReportMistranslation)
            SettingRow(stringResource(R.string.privacy_policy), onClick = {
                onOpenPage("privacy")
            })
            SettingRow(stringResource(R.string.terms), onClick = {
                onOpenPage("terms")
            })
            SettingRow(stringResource(R.string.open_source), onClick = {
                onOpenPage("licenses")
            })
            SettingRow(stringResource(R.string.whats_new), onClick = {
                onOpenPage("whats_new")
            })
        }
    }
}

@Composable
fun HelpPageRoute(key: HelpPageKey) {
    val nav = LocalNavigator.current
    HelpPageScreen(page = key.page, onBack = nav::back)
}

/** Offline page viewer: shows only pages shipped in the app and blocks every network load. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun HelpPageScreen(page: String, onBack: () -> Unit) {
    val safePage = page.filter { it.isLetterOrDigit() || it == '_' }
    Scaffold(topBar = {
        NexoTopBar(stringResource(PAGE_TITLES[safePage] ?: R.string.help), onBack = onBack)
    }) { p ->
        // Previews can't host a WebView.
        if (LocalInspectionMode.current) return@Scaffold
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(p),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = true
                    }
                    loadUrl("file:///android_asset/help/$safePage.html")
                }
            },
        )
    }
}

@Preview
@Composable
private fun HelpScreenPreview() = NexoPreview { HelpScreen(onBack = {}, onOpenPage = {}) }

@Preview
@Composable
private fun FeedbackScreenPreview() = NexoPreview {
    FeedbackScreen(onBack = {}, onOpenPage = {}, onSendFeedback = {}, onReportMistranslation = {})
}

@Preview
@Composable
private fun HelpPageScreenPreview() = NexoPreview { HelpPageScreen(page = "faq", onBack = {}) }
