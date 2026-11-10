package com.nexo.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.launch
import com.nexo.app.MainActivity
import com.nexo.app.R
import com.nexo.app.core.model.EntryType
import com.nexo.app.ui.IntentRouter

object NexoWidgets {
    suspend fun updateAll(context: Context) {
        TransactionsWidget().updateAll(context)
        TransactionsWideWidget().updateAll(context)
        CardWidget().updateAll(context)
        QuickAddWidget().updateAll(context)
        QuickTxWidget().updateAll(context)
    }
}

private fun launch(context: Context, action: String = IntentRouter.ACTION_MAIN, extra: (Intent.() -> Unit)? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        extra?.invoke(this)
    }

/**
 * Glance keeps a widget session running between updates and only recomposes it, so anything read
 * before provideContent goes stale. This reloads [load] inside the composition whenever
 * [WidgetData.revision] moves (every data or settings change).
 */
@Composable
private fun <T> live(initial: T, load: suspend () -> T): T {
    val rev by WidgetData.revision.collectAsState()
    val start = remember { rev }
    var value by remember { mutableStateOf(initial) }
    LaunchedEffect(rev) { if (rev != start) value = load() }
    return value
}

private suspend fun colorsAndMonth(context: Context) = WidgetData.colors(context) to WidgetData.month(context)

private fun color(v: Long, alpha: Float = 1f) = ColorProvider(Color(v).copy(alpha = alpha))

@Composable
private fun Shell(colors: WidgetColors, modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(color(colors.background, colors.opacity)).cornerRadius(16.dp).padding(10.dp).then(modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun NoData(context: Context, colors: WidgetColors) {
    Text(context.getString(R.string.no_data), style = TextStyle(color = color(colors.muted), fontSize = 13.sp),
        modifier = GlanceModifier.clickable(actionStartActivity(launch(context))))
}

/** Switches a transactions widget between Income (0), Expenses (1) and Total (2). */
class ModeCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mode = parameters[MODE_KEY] ?: 2
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetData.entry(context).prefs().setWidgetMode(id, mode)
        // A running session picks the new mode up from the preference flow; this starts one if none is running.
        TransactionsWidget().updateAll(context)
        TransactionsWideWidget().updateAll(context)
    }

    companion object {
        val MODE_KEY = ActionParameters.Key<Int>("mode")
    }
}

@Composable
private fun ModeTabs(context: Context, mode: Int, colors: WidgetColors) {
    Row(GlanceModifier.fillMaxWidth()) {
        listOf(R.string.income, R.string.expenses, R.string.total).forEachIndexed { i, label ->
            Text(
                context.getString(label),
                style = TextStyle(color = color(if (i == mode) colors.text else colors.muted), fontSize = 12.sp, fontWeight = if (i == mode) FontWeight.Bold else FontWeight.Normal),
                modifier = GlanceModifier.padding(end = 12.dp).clickable(actionRunCallback<ModeCallback>(actionParametersOf(ModeCallback.MODE_KEY to i))),
            )
        }
    }
}

@Composable
private fun TransactionsContent(context: Context, f: MonthFigures, mode: Int, colors: WidgetColors, large: Boolean) {
    val open = GlanceModifier.clickable(actionStartActivity(launch(context)))
    Column(GlanceModifier.fillMaxSize()) {
        ModeTabs(context, mode, colors)
        Spacer(GlanceModifier.height(if (large) 10.dp else 4.dp))
        Column(open.fillMaxWidth()) {
            when (mode) {
                0, 1 -> {
                    val income = mode == 0
                    Text(context.getString(R.string.this_month), style = TextStyle(color = color(colors.muted), fontSize = 11.sp))
                    Text(
                        if (income) f.incomeFormatted else f.expenseFormatted,
                        style = TextStyle(color = color(if (income) f.incomeColor else f.expenseColor), fontSize = if (large) 26.sp else 18.sp, fontWeight = FontWeight.Bold),
                    )
                    val d = if (income) f.incomeDiff else f.expenseDiff
                    val good = if (income) d >= 0 else d <= 0
                    Text(
                        context.getString(R.string.vs_mom) + " " + if (income) f.incomeDiffText else f.expenseDiffText,
                        style = TextStyle(color = color(if (d == 0.0) colors.muted else if (good) 0xFF2E9E5B else 0xFFE5533D), fontSize = 11.sp),
                    )
                }
                else -> {
                    if (large) Text(f.total, style = TextStyle(color = color(colors.text), fontSize = 26.sp, fontWeight = FontWeight.Bold))
                    Row(GlanceModifier.fillMaxWidth()) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(
                                context.getString(R.string.income),
                                style = TextStyle(color = color(colors.muted), fontSize = 11.sp),
                            )
                            Text(f.income, style = TextStyle(color = color(f.incomeColor), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        }
                        Column(GlanceModifier.defaultWeight()) {
                            Text(context.getString(R.string.expenses), style = TextStyle(color = color(colors.muted), fontSize = 11.sp))
                            Text(f.expense, style = TextStyle(color = color(f.expenseColor), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        }
                        if (!large) Column(GlanceModifier.defaultWeight()) {
                            Text(
                                if (f.carryOver != 0.0) context.getString(R.string.carry_over) + " " + f.carryFormatted else context.getString(R.string.total),
                                style = TextStyle(color = color(colors.muted), fontSize = 11.sp), maxLines = 1,
                            )
                            Text(f.total, style = TextStyle(color = color(colors.text), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    if (large && f.carryOver != 0.0) {
                        Text(context.getString(R.string.carry_over) + " " + f.carryFormatted, style = TextStyle(color = color(colors.muted), fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

/** Transactions widget 4×1 (UI-028). */
class TransactionsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val prefs = WidgetData.entry(context).prefs()
        val initial = colorsAndMonth(context)
        val initialMode = prefs.widgetModeNow(appWidgetId)
        provideContent {
            val (colors, figures) = live(initial) { colorsAndMonth(context) }
            val storedMode by remember { prefs.widgetMode(appWidgetId) }.collectAsState(initialMode)
            val mode = if (storedMode in 0..2) storedMode else 2
            Shell(colors) { if (!figures.ready) NoData(context, colors) else TransactionsContent(context, figures, mode, colors, large = false) }
        }
    }
}

/** Transactions widget 4×2 (UI-029). */
class TransactionsWideWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val prefs = WidgetData.entry(context).prefs()
        val initial = colorsAndMonth(context)
        val initialMode = prefs.widgetModeNow(appWidgetId)
        provideContent {
            val (colors, figures) = live(initial) { colorsAndMonth(context) }
            val storedMode by remember { prefs.widgetMode(appWidgetId) }.collectAsState(initialMode)
            val mode = if (storedMode in 0..2) storedMode else 2
            Shell(colors) { if (!figures.ready) NoData(context, colors) else TransactionsContent(context, figures, mode, colors, large = true) }
        }
    }
}

/** Card widget 4×2 (UI-030). */
class CardWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = Triple(WidgetData.colors(context), WidgetData.cards(context), WidgetData.month(context))
        provideContent {
            val (colors, cards, figures) = live(initial) { Triple(WidgetData.colors(context), WidgetData.cards(context), WidgetData.month(context)) }
            Shell(colors) {
                when {
                    cards == null -> NoData(context, colors)
                    cards.isEmpty() -> Text(context.getString(R.string.no_credit_cards), style = TextStyle(color = color(colors.muted), fontSize = 13.sp),
                        modifier = GlanceModifier.clickable(actionStartActivity(launch(context))))
                    else -> Column(GlanceModifier.fillMaxSize()) {
                        Row(GlanceModifier.fillMaxWidth()) {
                            Text(context.getString(R.string.group_card), style = TextStyle(color = color(colors.muted), fontSize = 11.sp), modifier = GlanceModifier.defaultWeight())
                            Text(context.getString(R.string.balance_payable), style = TextStyle(color = color(colors.muted), fontSize = 11.sp), modifier = GlanceModifier.width(90.dp))
                            Text(context.getString(R.string.outstanding_balance), style = TextStyle(color = color(colors.muted), fontSize = 11.sp), modifier = GlanceModifier.width(90.dp))
                        }
                        cards.take(5).forEach { c ->
                            Row(
                                GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(launch(context, IntentRouter.ACTION_ACCOUNT) { putExtra(IntentRouter.EXTRA_ACCOUNT, c.id) })),
                            ) {
                                Text(c.name, style = TextStyle(color = color(colors.text), fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                                Text(c.payable, style = TextStyle(color = color(if (c.payableNegative) figures.expenseColor else figures.incomeColor), fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.width(90.dp))
                                Text(c.outstanding, style = TextStyle(color = color(if (c.outstandingNegative) figures.expenseColor else figures.incomeColor), fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.width(90.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickButton(context: Context, label: String, colors: WidgetColors, intent: Intent, accent: Long? = null, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier.fillMaxHeight().padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            GlanceModifier.fillMaxSize().background(color(accent ?: colors.text, 0.12f)).cornerRadius(12.dp).clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(color = color(accent ?: colors.text), fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

private fun addIntent(context: Context, type: EntryType) = launch(context, IntentRouter.ACTION_ADD) { putExtra(IntentRouter.EXTRA_TYPE, type.name) }

/** Quick Add widget 4×1 (UI-031). */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = colorsAndMonth(context)
        provideContent {
            val (colors, f) = live(initial) { colorsAndMonth(context) }
            Shell(colors) {
                Row(GlanceModifier.fillMaxSize()) {
                    QuickButton(context, context.getString(R.string.app_name), colors, launch(context), modifier = GlanceModifier.defaultWeight())
                    QuickButton(context, context.getString(R.string.search), colors, launch(context, IntentRouter.ACTION_SEARCH), modifier = GlanceModifier.defaultWeight())
                    QuickButton(context, context.getString(R.string.income), colors, addIntent(context, EntryType.INCOME), f.incomeColor, GlanceModifier.defaultWeight())
                    QuickButton(context, context.getString(R.string.expense), colors, addIntent(context, EntryType.EXPENSE), f.expenseColor, GlanceModifier.defaultWeight())
                    QuickButton(context, context.getString(R.string.bookmarks), colors, launch(context, IntentRouter.ACTION_BOOKMARKS), modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/** Quick Transaction Add 2×1 / 2×2 (UI-032): two buttons, plus Bookmarks when tall enough. */
class QuickTxWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 50.dp), DpSize(110.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = colorsAndMonth(context)
        provideContent {
            val (colors, f) = live(initial) { colorsAndMonth(context) }
            val tall = LocalSize.current.height >= 100.dp
            Shell(colors) {
                Column(GlanceModifier.fillMaxSize()) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        QuickButton(context, context.getString(R.string.income), colors, addIntent(context, EntryType.INCOME), f.incomeColor, GlanceModifier.defaultWeight())
                        QuickButton(context, context.getString(R.string.expense), colors, addIntent(context, EntryType.EXPENSE), f.expenseColor, GlanceModifier.defaultWeight())
                    }
                    if (tall) Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        QuickButton(context, context.getString(R.string.bookmarks), colors, launch(context, IntentRouter.ACTION_BOOKMARKS), modifier = GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

class TransactionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransactionsWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearModes(context, appWidgetIds)
    }
}

class TransactionsWideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransactionsWideWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearModes(context, appWidgetIds)
    }
}

class CardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardWidget()
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

class QuickTxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickTxWidget()
}

/** Removing a widget deletes its display mode (UI-027). */
private fun clearModes(context: Context, ids: IntArray) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        runCatching { ids.forEach { WidgetData.entry(context).prefs().clearWidgetMode(it) } }
    }
}
