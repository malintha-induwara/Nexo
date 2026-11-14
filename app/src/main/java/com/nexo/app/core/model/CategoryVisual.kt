package com.nexo.app.core.model

import java.util.Locale

/** What a category looks like: an emoji (or, failing that, a monogram) on a tile of one of the ten hues. */
data class CategoryVisual(val emoji: String?, val monogram: String, val colorIndex: Int)

/**
 * Resolves a category's visual without writing anything back, so old data, legacy imports and
 * restored backups all render without a backfill.
 *
 * Emoji: the category's own, else its parent's, else a suggestion from its name, else from its
 * parent's name, else none (the monogram shows). Colour: its own, else its parent's, else a
 * stable value from the main category's id, so a main category and its subcategories share a hue.
 */
object CategoryVisuals {
    const val HUES = 10

    fun resolve(category: Category, parent: Category?): CategoryVisual {
        val emoji = category.emoji?.takeIf { it.isNotBlank() }
            ?: parent?.emoji?.takeIf { it.isNotBlank() }
            ?: suggestEmoji(category.name)
            ?: parent?.let { suggestEmoji(it.name) }
        val color = category.colorIndex
            ?: parent?.colorIndex
            ?: stableColor(parent?.id ?: category.id)
        return CategoryVisual(emoji, monogram(category.name), Math.floorMod(color, HUES))
    }

    fun stableColor(id: String): Int = Math.floorMod(id.hashCode(), HUES)

    /** First letter (as a whole grapheme where possible), upper-cased. */
    fun monogram(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "?"
        val end = trimmed.offsetByCodePoints(0, 1)
        return trimmed.substring(0, end).uppercase(Locale.getDefault())
    }

    /**
     * A best guess from the name; specific words are listed before general ones. Keywords match
     * the start of a word ("book" → "Books"); keywords under four letters must be the whole word or
     * its plural, so "car" does not match "Card"; keywords with spaces or punctuation match as phrases.
     */
    fun suggestEmoji(name: String): String? {
        val normalized = name.lowercase(Locale.ROOT)
        val words = normalized.split(WORD_BREAK).filter { it.isNotEmpty() }
        fun matches(keyword: String): Boolean = when {
            keyword.any { !it.isLetterOrDigit() } -> normalized.contains(keyword)
            keyword.length < 4 -> words.any { it == keyword || it == keyword + "s" || it == keyword + "es" }
            else -> words.any { it.startsWith(keyword) }
        }
        return KEYWORDS.firstOrNull { (keywords, _) -> keywords.any(::matches) }?.second
    }

    private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")

    private val KEYWORDS: List<Pair<List<String>, String>> = listOf(
        listOf("lunch") to "🍱",
        listOf("dinner", "supper") to "🍽️",
        listOf("eating out", "restaurant", "takeaway", "take-away", "dining") to "🍴",
        listOf("coffee", "cafe", "café") to "☕",
        listOf("beverage", "drink") to "🥤",
        listOf("grocer", "supermarket") to "🛒",
        listOf("snack") to "🍪",
        listOf("food", "meal") to "🍜",
        listOf("friend") to "🧑‍🤝‍🧑",
        listOf("fellowship", "alumni", "club") to "🤝",
        listOf("dues", "membership", "subscription") to "🔁",
        listOf("social", "party") to "🥂",
        listOf("pet", "dog", "cat") to "🐾",
        listOf("bus") to "🚌",
        listOf("subway", "metro", "train", "rail") to "🚇",
        listOf("taxi", "uber", "ride") to "🚕",
        listOf("fuel", "petrol", "gas station") to "⛽",
        listOf("parking") to "🅿️",
        listOf("car", "vehicle") to "🚗",
        listOf("flight", "travel", "trip", "holiday", "vacation") to "✈️",
        listOf("transport", "commute") to "🚌",
        listOf("book", "textbook") to "📚",
        listOf("movie", "cinema", "film") to "🎬",
        listOf("music", "concert") to "🎵",
        listOf("app", "software") to "📱",
        listOf("game") to "🎮",
        listOf("culture", "art", "museum", "entertainment") to "🎭",
        listOf("sport", "gym", "fitness") to "🏋️",
        listOf("yoga") to "🧘",
        listOf("appliance", "electronic") to "🔌",
        listOf("furniture") to "🛋️",
        listOf("kitchen", "cookware") to "🍳",
        listOf("toiletr", "hygiene") to "🧴",
        listOf("chandlery", "sundries", "supplies") to "🧽",
        listOf("rent", "mortgage") to "🏠",
        listOf("electric", "power") to "⚡",
        listOf("water") to "💧",
        listOf("utilit", "bill") to "💡",
        listOf("internet", "wifi", "broadband") to "🌐",
        listOf("phone", "mobile") to "📱",
        listOf("household", "home", "house") to "🏠",
        listOf("laundry", "dry clean") to "🧺",
        listOf("shoe", "sneaker") to "👟",
        listOf("fashion") to "👗",
        listOf("clothing", "clothes", "apparel") to "👕",
        listOf("cosmetic", "makeup", "make-up", "beauty", "salon", "hair") to "💄",
        listOf("accessor", "jewel") to "💍",
        listOf("hospital", "clinic", "doctor", "dental") to "🏥",
        listOf("medicine", "pharmacy", "drug") to "💊",
        listOf("health", "medical") to "🩺",
        listOf("school supplies", "stationery") to "✏️",
        listOf("schooling", "school", "tuition") to "🏫",
        listOf("academy", "course", "class", "education", "university", "college") to "🎓",
        listOf("insurance") to "🛡️",
        listOf("tax") to "🧾",
        listOf("loan", "interest") to "🏦",
        listOf("invest", "stock", "dividend") to "📈",
        listOf("saving") to "🐷",
        listOf("charity", "donation") to "🤲",
        listOf("baby", "child", "kid") to "🧸",
        listOf("gift", "present") to "🎁",
        listOf("shopping") to "🛍️",
        listOf("salary", "wage", "payroll") to "💼",
        listOf("bonus") to "🎉",
        listOf("allowance", "pocket money") to "💵",
        listOf("refund", "cashback") to "↩️",
        listOf("petty cash", "cash") to "🪙",
        listOf("other", "misc") to "📦",
    )
}
