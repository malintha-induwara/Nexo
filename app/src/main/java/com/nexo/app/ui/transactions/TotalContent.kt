package com.nexo.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.RefData
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.ui.budgets.BudgetBlock
import com.nexo.app.ui.designsystem.component.Formats

@Composable
internal fun TotalContent(
    data: HomeData,
    main: Currency,
    budgetBlock: BudgetBlockData?,
    ref: RefData,
    bottom: Dp,
    actions: TransactionsActions,
) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            data.period?.let {
                BudgetBlock(
                    it,
                    budgetBlock,
                    ref,
                    onOpenSettings = actions.openBudgetSettings,
                    onEditBudget = actions.editBudget,
                    onAddTotal = actions.addTotalBudget,
                    onOpenDetail = actions.openBudgetDetail,
                )
            }
        }
        item {
            val s = data.summary ?: return@item
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.tab_accounts),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        data.period?.let {
                            Text(
                                Formats.range(it.start, it.end),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SummaryLine(stringResource(R.string.compared_expenses), "${s.comparedPercent}%")
                    SummaryLine(
                        stringResource(R.string.expenses_of, s.cashLabel),
                        AmountFormatter.format(s.cash, main),
                    )
                    if (s.debit != 0.0) {
                        SummaryLine(
                            stringResource(R.string.expenses_debit_card),
                            AmountFormatter.format(s.debit, main),
                        )
                    }
                    if (s.cardIn != 0.0) {
                        val cardOut = AmountFormatter.format(s.cardOut, main)
                        val cardIn = AmountFormatter.format(s.cardIn, main)
                        SummaryLine(stringResource(R.string.expenses_card_pay), "$cardOut($cardIn)")
                    } else {
                        SummaryLine(
                            stringResource(R.string.expenses_card),
                            AmountFormatter.format(s.cardOut, main),
                        )
                    }
                    SummaryLine(
                        stringResource(R.string.transfer_of, s.cashLabel),
                        AmountFormatter.format(s.transfer, main),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        data.period?.let { actions.exportToExcel(it.start, it.end) }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.export_to_excel))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
