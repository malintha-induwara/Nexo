package com.nexo.app.ui.accounts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.R
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.navigation.AccountFormKey
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var book: TestBook

    @Before
    fun setUp() {
        book = TestBook(ApplicationProvider.getApplicationContext<Context>(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    private suspend fun TestScope.openForm(
        key: AccountFormKey = AccountFormKey(),
    ): AccountFormViewModel {
        val vm = AccountFormViewModel(key, book.refs, book.accounts, book.transactions)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.initialize()
        vm.uiState.first { it.loaded && it.ref.groups.isNotEmpty() }
        return vm
    }

    @Test
    fun aBlankNameIsRefused() = runTest {
        val vm = openForm()
        vm.save()
        val state = vm.uiState.value
        assertEquals(UiText.of(R.string.no_title), state.userMessage?.text)
        assertNull(state.close)

        vm.onUserMessageShown(state.userMessage!!.id)
        assertNull(vm.uiState.value.userMessage)
    }

    @Test
    fun anOpeningAmountIsRecordedThenTheFormCloses() = runTest {
        val vm = openForm()
        vm.setName("Wallet")
        vm.setAmount(500.0)
        vm.save()
        assertEquals(500.0, vm.uiState.first { it.difference != null }.difference!!, 0.0)

        val id = vm.uiState.value.id
        vm.recordDifference(asIncomeExpense = false)
        assertEquals(AccountFormClose.BACK, vm.uiState.first { it.close != null }.close)
        assertEquals("Wallet", book.refs.getRefData().accountById[id]?.name)
        assertEquals(500.0, book.transactions.getAllForAccounts(listOf(id)).single().amount, 0.0)
    }

    @Test
    fun anExistingAccountShowsItsBalance() = runTest {
        val vm = openForm(AccountFormKey(accountId = "11"))
        val state = vm.uiState.value
        assertEquals("11", state.id)
        assertEquals(false, state.isNew)
        assertEquals(0.0, state.balance, 0.0)
    }

    @Test
    fun deletingClosesToTheMainScreen() = runTest {
        val vm = openForm(AccountFormKey(accountId = "11"))
        vm.delete().join()
        assertEquals(AccountFormClose.TO_MAIN, vm.uiState.first { it.close != null }.close)
        vm.onCloseHandled()
        assertNull(vm.uiState.value.close)
    }
}
