package com.nexo.app.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import java.util.Locale

/** "BALANCE · NOV": what a chart shows and which period the figures above it are for. */
@Composable
fun ChartTitle(title: String, period: String) {
    Text(
        "$title · $period".uppercase(Locale.getDefault()),
        style = NexoType.Caps,
        color = LocalNexoColors.current.textTertiary,
        maxLines = 1,
    )
}

/**
 * A series' key and name over its value for the picked period; doubles as the chart's legend.
 * The key is a square for filled marks and a short stroke for a line; null leaves it out.
 */
@Composable
fun KeyFigure(
    label: String,
    value: Double,
    currency: Currency?,
    swatch: Color?,
    modifier: Modifier = Modifier,
    stroke: Boolean = false,
    valueColor: Color = LocalNexoColors.current.text,
) {
    val colors = LocalNexoColors.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) {
                if (stroke) {
                    Box(Modifier.width(12.dp).height(2.dp).background(swatch))
                } else {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(swatch))
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = NexoType.Meta, color = colors.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(Space.xxs))
        AmountText(
            value,
            currency,
            Modifier.shrinkToFit(),
            style = NexoType.Amount,
            color = valueColor,
        )
    }
}

/**
 * Balance over time, money in and out, and the same figures as a table. One period is picked
 * across all three: tap or drag a chart, or tap a table row. It starts on the latest period.
 */
@Composable
fun FlowCharts(
    labels: List<String>,
    balances: List<Double>,
    inflow: List<Double>,
    outflow: List<Double>,
    currency: Currency?,
    flowTitle: String,
    inflowLabel: String,
    outflowLabel: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(top = Space.xs, bottom = 96.dp),
) {
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    val haptics = rememberHaptics()
    if (labels.isEmpty() || balances.size != labels.size || inflow.size != labels.size ||
        outflow.size != labels.size
    ) {
        EmptyState(modifier = modifier)
        return
    }
    var picked by rememberSaveable(labels) { mutableIntStateOf(labels.lastIndex) }
    val i = picked.coerceIn(0, labels.lastIndex)
    val pick: (Int) -> Unit = {
        if (it != picked) {
            haptics.tick()
            picked = it
        }
    }
    val card = Modifier.padding(horizontal = Space.l, vertical = Space.xs)
    LazyColumn(modifier, contentPadding = contentPadding) {
        item(key = "balance") {
            GroupCard(card) {
                Column(
                    Modifier.padding(
                        start = Space.l,
                        end = Space.l,
                        top = Space.l,
                        bottom = Space.m,
                    ),
                ) {
                    ChartTitle(stringResource(R.string.balance), labels[i])
                    Spacer(Modifier.height(Space.xxs))
                    AmountText(
                        balances[i],
                        currency,
                        Modifier.shrinkToFit(),
                        style = NexoType.Balance,
                        color = if (balances[i] < 0) {
                            money.expense
                        } else {
                            colors.text
                        },
                    )
                    Spacer(Modifier.height(Space.s))
                    AreaChart(labels, balances, colors.primary, i, pick)
                }
            }
        }
        item(key = "flow") {
            GroupCard(card) {
                Column(
                    Modifier.padding(
                        start = Space.l,
                        end = Space.l,
                        top = Space.l,
                        bottom = Space.m,
                    ),
                ) {
                    ChartTitle(flowTitle, labels[i])
                    Spacer(Modifier.height(Space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        KeyFigure(
                            inflowLabel,
                            inflow[i],
                            currency,
                            money.incomeFill,
                            Modifier.weight(1f),
                        )
                        KeyFigure(
                            outflowLabel,
                            outflow[i],
                            currency,
                            money.expenseFill,
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Space.s))
                    ColumnChart(
                        labels,
                        listOf(inflow, outflow),
                        listOf(money.incomeFill, money.expenseFill),
                        i,
                        pick,
                    )
                }
            }
        }
        item(key = "table") {
            GroupCard(card) {
                Column(Modifier.padding(Space.s)) {
                    Row(
                        Modifier.padding(horizontal = Space.s, vertical = Space.s),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Spacer(Modifier.width(44.dp))
                        listOf(
                            stringResource(R.string.balance),
                            inflowLabel,
                            outflowLabel,
                        ).forEach { label ->
                            Text(
                                label.uppercase(Locale.getDefault()),
                                style = NexoType.Caps,
                                color = colors.textTertiary,
                                maxLines = 1,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    labels.forEachIndexed { row, label ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(NexoShapes.control)
                                .background(if (row == i) colors.primarySoft else Color.Transparent)
                                .clickable { pick(row) }
                                .padding(horizontal = Space.s, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                label,
                                style = NexoType.Meta,
                                color = if (row == i) {
                                    colors.primaryText
                                } else {
                                    colors.textSecondary
                                },
                                maxLines = 1,
                                modifier = Modifier.width(44.dp),
                            )
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                FlowAmount(
                                    balances[row],
                                    currency,
                                    colors.text,
                                    Modifier.shrinkToFit(),
                                    NexoType.AmountSmall,
                                )
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                FlowAmount(
                                    inflow[row],
                                    currency,
                                    money.income,
                                    Modifier.shrinkToFit(),
                                    NexoType.AmountSmall,
                                )
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                FlowAmount(
                                    outflow[row],
                                    currency,
                                    money.expense,
                                    Modifier.shrinkToFit(),
                                    NexoType.AmountSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
