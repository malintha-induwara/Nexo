package com.nexo.app.ui.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.AccountGroup
import com.nexo.app.core.model.Category
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.core.model.Tag
import com.nexo.app.core.model.ThemeMode
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.theme.NexoTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object PreviewData {
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-14T06:30:00Z"), ZoneId.of("Asia/Colombo"))
    val today: LocalDate = LocalDate.now(clock)

    private val rupee = Currency(
        id = "LKR",
        isoCode = "LKR",
        name = "Sri Lankan Rupee",
        symbol = "Rs.",
        symbolPosition = SymbolPosition.PREFIX,
        decimals = 2,
        rate = 1.0,
        isMain = true,
        isVisible = true,
        orderSeq = 0,
        updatedAt = 0,
    )
    private val dollar = rupee.copy(
        id = "USD",
        isoCode = "USD",
        name = "US Dollar",
        symbol = "$",
        rate = 300.0,
        isMain = false,
        orderSeq = 1,
    )

    val groups = listOf(
        AccountGroup("11", "Cash", GroupType.NORMAL, 11, 0, updatedAt = 0),
        AccountGroup("1", "Accounts", GroupType.NORMAL, 1, 1, updatedAt = 0),
        AccountGroup("2", "Card", GroupType.CREDIT_CARD, 2, 2, updatedAt = 0),
    )

    val accounts = listOf(
        Account("cash", "Wallet", "11", "LKR", 0, createdAt = 0, updatedAt = 0),
        Account("bank", "Savings", "1", "LKR", 1, createdAt = 0, updatedAt = 0),
        Account(
            "card",
            "Visa",
            "2",
            "LKR",
            2,
            linkedAccountId = "bank",
            createdAt = 0,
            updatedAt = 0,
        ),
    )

    val categories = listOf(
        Category("food", "Food", CategoryKind.EXPENSE, orderSeq = 0, createdAt = 0, updatedAt = 0),
        Category(
            "groceries",
            "Groceries",
            CategoryKind.EXPENSE,
            parentId = "food",
            orderSeq = 0,
            createdAt = 0,
            updatedAt = 0,
        ),
        Category(
            "transport",
            "Transport",
            CategoryKind.EXPENSE,
            orderSeq = 1,
            createdAt = 0,
            updatedAt = 0,
        ),
        Category(
            "salary",
            "Salary",
            CategoryKind.INCOME,
            orderSeq = 0,
            createdAt = 0,
            updatedAt = 0,
        ),
    )

    val tags = listOf(
        Tag("work", "work", 0, updatedAt = 0),
        Tag("family", "family", 1, updatedAt = 0),
    )

    val ref = RefData(groups, accounts, categories, listOf(rupee, dollar), tags)

    val device = DeviceSettings(setupDone = true)
}

// App theme and a fixed clock, on the app background.
@Composable
fun NexoPreview(dark: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalClock provides PreviewData.clock) {
        NexoTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}
