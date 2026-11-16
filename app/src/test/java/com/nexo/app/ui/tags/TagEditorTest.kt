package com.nexo.app.ui.tags

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.Tag
import com.nexo.app.data.repository.TagResult
import com.nexo.app.testing.TestBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TagEditorTest {
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

    private suspend fun live(): List<Tag> = book.tags.getTagsStream().first().filter {
        !it.isDeleted
    }

    private suspend fun names(expected: List<String>): List<String> = book.tags.getTagsStream()
        .first { tags -> tags.filter { !it.isDeleted }.map { it.name } == expected }
        .filter { !it.isDeleted }.map { it.name }

    private fun editor(shown: List<Tag> = emptyList()) = TagEditor(book.tags, scope) { shown }

    @Test
    fun aNameAlreadyTakenKeepsTheDialogOpenAndSaysSo() = runBlocking {
        book.tags.add("food")
        val editor = editor()

        editor.actions.startAdd()
        editor.actions.add("FOOD")

        assertEquals(
            TagDialog.Add(nameTaken = true),
            editor.dialog.first { it == TagDialog.Add(nameTaken = true) },
        )
        assertEquals(listOf("food"), live().map { it.name })
    }

    @Test
    fun aNewNameIsAddedAndTheDialogCloses() = runBlocking {
        val editor = editor()

        editor.actions.startAdd()
        editor.actions.add("travel")

        assertEquals(listOf("travel"), names(listOf("travel")))
        editor.dialog.first { it == null }
        Unit
    }

    @Test
    fun aTagIsRenamedThroughItsDialog() = runBlocking {
        assertTrue(book.tags.add("food") is TagResult.Ok)
        val editor = editor()

        editor.actions.startRename(live().single())
        editor.actions.rename("meals")

        assertEquals(listOf("meals"), names(listOf("meals")))
    }

    @Test
    fun anUnusedTagIsDeletedWithoutAsking() = runBlocking {
        book.tags.add("food")
        val editor = editor()

        editor.actions.requestDelete(live().single())

        assertEquals(emptyList<String>(), names(emptyList()))
        assertEquals(null, editor.dialog.value)
    }

    @Test
    fun movingATagReordersTheListAsShown() = runBlocking {
        book.tags.add("first")
        book.tags.add("second")
        val shown = live()
        val editor = editor(shown)

        editor.actions.move(0, 1)

        assertEquals(shown.map { it.name }.reversed(), names(shown.map { it.name }.reversed()))
    }
}
