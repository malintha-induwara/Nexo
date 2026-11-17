package com.nexo.app.ui.sms

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.R
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.SmsKind
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.ui.designsystem.UiText
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Synthetic messages only; real bank SMS never enters the repository.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsQueueViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var book: TestBook
    private val at = 1_793_700_000_000L

    @Before
    fun setUp() {
        book = TestBook(ApplicationProvider.getApplicationContext<Context>(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    private suspend fun pendingItem(categoryId: String? = "6"): String {
        val e = SmsItemEntity(
            id = UUID.randomUUID().toString(),
            certainty = Certainty.EXACT,
            kind = SmsKind.EXPENSE,
            bank = "COMBANK",
            sender = "COMBANK",
            template = "purchase",
            amount = 2450.0,
            currency = "LKR",
            merchant = "Green Leaf Cafe",
            merchantKey = "green leaf cafe",
            cardLast4 = "4321",
            occurredAt = at,
            smsAt = at,
            capturedAt = at,
            dedupeKey = UUID.randomUUID().toString(),
            text = "Purchase at GREEN LEAF CAFE",
            accountId = "1",
            categoryId = categoryId,
            updatedAt = at,
        )
        book.db.smsDao().insert(e)
        return e.id
    }

    private suspend fun TestScope.openQueue(waitFor: String): SmsQueueViewModel {
        val b = book
        val vm =
            SmsQueueViewModel(
                b.sms,
                b.refs,
                b.prefs,
                b.getFeePlan,
                b.approveSms,
                b.transactions,
                b.inbox,
                SavedStateHandle(),
            )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { s -> s.pending.any { it.id == waitFor } }
        return vm
    }

    @Test
    fun approvingShowsANoticeAndLeavesTheQueue() = runTest {
        val id = pendingItem()
        val vm = openQueue(id)
        vm.approve(id).join()

        val notice = vm.uiState.first { it.notice != null }.notice!!
        assertEquals(UiText.of(R.string.sms_approved), notice.text)
        assertNull(notice.undo)
        vm.uiState.first { it.pending.isEmpty() }
        assertEquals(1, book.db.transactionDao().count())
    }

    @Test
    fun anItemMissingDetailsStaysInTheQueue() = runTest {
        val id = pendingItem(categoryId = null)
        val vm = openQueue(id)
        vm.approve(id).join()

        assertEquals(UiText.of(R.string.sms_needs_details_first), vm.uiState.value.notice?.text)
        assertEquals(listOf(id), vm.uiState.value.pending.map { it.id })
        assertEquals(0, book.db.transactionDao().count())
    }

    @Test
    fun denyingOffersAnUndoThatRestoresTheItem() = runTest {
        val id = pendingItem()
        val vm = openQueue(id)
        vm.deny(listOf(id)).join()

        val notice = vm.uiState.first { it.notice != null }.notice!!
        assertEquals(UiText.plural(R.plurals.sms_n_denied, 1), notice.text)
        assertEquals(QueueUndo.Restore(listOf(id)), notice.undo)
        vm.uiState.first { it.pending.isEmpty() }

        vm.undo(notice)
        assertNull(vm.uiState.value.notice)
        vm.uiState.first { s -> s.pending.any { it.id == id } }
    }
}
