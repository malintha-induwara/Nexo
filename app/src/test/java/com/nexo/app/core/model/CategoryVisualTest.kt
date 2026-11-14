package com.nexo.app.core.model

import com.nexo.app.data.database.entity.CategoryEntity
import com.nexo.app.core.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryVisualTest {
    private fun cat(id: String, name: String, parent: String? = null, emoji: String? = null, color: Int? = null) =
        CategoryEntity(id, name, CategoryKind.EXPENSE, parent, 0, createdAt = 0, updatedAt = 0, emoji = emoji, colorIndex = color)

    @Test
    fun ownEmojiAndColourWin() {
        val main = cat("m", "Food", emoji = "🍜", color = 0)
        val sub = cat("s", "Taxi", parent = "m", emoji = "🍣", color = 7)
        val v = CategoryVisuals.resolve(sub, main)
        assertEquals("🍣", v.emoji)
        assertEquals(7, v.colorIndex)
    }

    @Test
    fun subcategoryInheritsParentEmojiBeforeSuggestions() {
        val main = cat("m", "Transport", emoji = "🛴", color = 5)
        val sub = cat("s", "Taxi", parent = "m")
        val v = CategoryVisuals.resolve(sub, main)
        assertEquals("🛴", v.emoji)
        assertEquals(5, v.colorIndex)
    }

    @Test
    fun unstyledDataFallsBackToSuggestionsAndSharedHue() {
        val main = cat("m", "Transport")
        val sub = cat("s", "Taxi", parent = "m")
        val v = CategoryVisuals.resolve(sub, main)
        assertEquals("🚕", v.emoji)
        // A main category and its subcategories share the hue derived from the main id.
        assertEquals(CategoryVisuals.resolve(main, null).colorIndex, v.colorIndex)
        assertEquals(CategoryVisuals.stableColor("m"), v.colorIndex)
    }

    @Test
    fun subcategoryWithUnknownNameUsesParentSuggestion() {
        val v = CategoryVisuals.resolve(cat("s", "Zakat", parent = "m"), cat("m", "Gifts"))
        assertEquals("🎁", v.emoji)
    }

    @Test
    fun unknownNameShowsMonogram() {
        val v = CategoryVisuals.resolve(cat("x", "zakat"), null)
        assertNull(v.emoji)
        assertEquals("Z", v.monogram)
    }

    @Test
    fun shortKeywordsMatchWholeWordsOnly() {
        assertEquals("🚗", CategoryVisuals.suggestEmoji("Car"))
        assertEquals("🚗", CategoryVisuals.suggestEmoji("Cars"))
        assertNull(CategoryVisuals.suggestEmoji("Card fees"))
        assertEquals("⛽", CategoryVisuals.suggestEmoji("Petrol"))
        assertEquals("🐾", CategoryVisuals.suggestEmoji("Pets"))
        assertEquals("📚", CategoryVisuals.suggestEmoji("Books"))
        assertEquals("↩️", CategoryVisuals.suggestEmoji("Cashback"))
        assertEquals("🍴", CategoryVisuals.suggestEmoji("Eating out"))
    }

    @Test
    fun colourIndexIsAlwaysInRange() {
        val v = CategoryVisuals.resolve(cat("x", "Rent", color = 23), null)
        assertEquals(3, v.colorIndex)
        repeat(50) { i -> assert(CategoryVisuals.stableColor("id-$i") in 0 until CategoryVisuals.HUES) }
    }

    @Test
    fun monogramKeepsWholeCodePoint() {
        assertEquals("𝔸", CategoryVisuals.monogram("𝔸lpha"))
        assertEquals("?", CategoryVisuals.monogram("  "))
    }
}
