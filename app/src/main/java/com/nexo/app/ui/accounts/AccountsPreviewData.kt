package com.nexo.app.ui.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.AccountGroup
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.nexo

/** The preview book plus a savings group, a deleted account and a deleted group. */
internal val previewSettingsRef = PreviewData.ref.copy(
    groups = PreviewData.groups +
        listOf(
            AccountGroup("4", "Savings", GroupType.NORMAL, 4, 3, updatedAt = 0),
            AccountGroup(
                "trip",
                "Trip fund",
                GroupType.NORMAL,
                null,
                4,
                GroupStatus.DELETED,
                updatedAt = 0,
            ),
        ),
    accounts = PreviewData.accounts +
        listOf(
            Account(
                "fd",
                "Fixed deposit",
                "4",
                "LKR",
                3,
                transferExpense = true,
                createdAt = 0,
                updatedAt = 0,
            ),
            Account(
                "old",
                "Old wallet",
                "11",
                "LKR",
                4,
                status = AccountStatus.DELETED,
                createdAt = 0,
                updatedAt = 0,
            ),
        ),
)

internal val previewTotalStatsState: TotalStatsUiState = run {
    val periods = PreviewData.ref.settings.periods
    TotalStatsUiState(
        ref = PreviewData.ref,
        anchor = PreviewData.today,
        periods = generateSequence(periods.monthPeriodContaining(PreviewData.today)) {
            periods.previous(it)
        }.take(6).toList().reversed(),
        balances = listOf(180_000.0, 192_500.0, 188_000.0, 205_250.0, 221_000.0, 239_250.0),
        income = listOf(185_000.0, 185_000.0, 190_000.0, 185_000.0, 195_000.0, 185_000.0),
        expense = listOf(162_000.0, 172_500.0, 194_500.0, 167_750.0, 179_250.0, 166_750.0),
    )
}
