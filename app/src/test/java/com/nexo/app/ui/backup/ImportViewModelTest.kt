package com.nexo.app.ui.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.ImportErrorType
import com.nexo.app.data.file.AppDirs
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.testing.fixedClock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var book: TestBook
    private lateinit var dirs: AppDirs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        book = TestBook(context, scope, clock = fixedClock)
        dirs = AppDirs(context)
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    @Test
    fun aRowThatCannotBeReadStaysInTheFileAfterSaving() = runTest {
        File(dirs.imports, "lunch.tsv").writeText(
            "Date\tAccount\tCategory\tSubcategory\tNote\tAmount\tType\n" +
                "someday\tCash\tFood\t\tLunch\t100\tExpense\n",
        )
        val vm = ImportViewModel(book.importer, book.refs)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.refreshFiles()
        assertEquals(
            listOf("lunch.tsv"),
            vm.uiState.first { it.imports.isNotEmpty() }.imports.map { it.name },
        )

        vm.loadPreview("lunch.tsv", null).join()
        val row = vm.uiState.first { it.preview != null }.preview!!.rows.single()
        assertEquals(ImportErrorType.DATE, row.error?.type)

        vm.saveImport("lunch.tsv", null).join()
        assertEquals(BackupResult.ImportSaved(0, 1), vm.uiState.first { it.result != null }.result)

        vm.deleteImports(listOf("lunch.tsv")).join()
        vm.uiState.first { it.imports.isEmpty() && !it.busy }
    }
}
