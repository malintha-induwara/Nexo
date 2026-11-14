package com.nexo.app.ui.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.ui.designsystem.theme.NexoType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every digit slot of a [RollingAmount] must stay on the row's baseline once it has rolled. If a
 * slot reports a baseline shifted by its rolling layer, the row aligns it a line lower and grows
 * to twice its height, which is how the bug showed on the home header.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RollingAmountTest {
    @get:Rule val rule = createComposeRule()

    private fun parts(value: Double) = AmountParts.of(value, "Rs.", SymbolPosition.PREFIX, 2)

    private fun height(value: Double): Dp =
        rule.onNodeWithContentDescription(parts(value).text).getUnclippedBoundsInRoot().let {
            it.bottom -
                it.top
        }

    @Test
    fun rowStaysOneLineTallAfterEveryRoll() {
        var value by mutableStateOf(0.0)
        var direction by mutableStateOf(0)
        rule.setContent {
            RollingAmount(parts(value), direction, style = NexoType.Hero, color = Color.White)
        }
        val oneLine = height(0.0)

        for (next in listOf(16_429.0, 16_664.0, 3_675.0, 16_429.0, 159.0, 8_282.5)) {
            direction = if (next > value) 1 else -1
            value = next
            rule.waitForIdle()
            assertEquals("row height after rolling to $next", oneLine, height(next))
        }
    }
}
