package com.nexo.app.data.database

import android.content.Context
import androidx.room.withTransaction
import com.nexo.app.R
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.GroupType
import com.nexo.app.data.database.entity.AccountEntity
import com.nexo.app.data.database.entity.AccountGroupEntity
import com.nexo.app.data.database.entity.CategoryEntity
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.data.database.entity.SettingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Seeds a fresh book. */
@Singleton
class BookSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NexoDatabase,
) {
    private fun s(id: Int) = context.getString(id)

    fun mainCurrencyRow(c: CatalogCurrency, now: Long) = CurrencyEntity(
        id = c.code, isoCode = c.code, name = c.displayName(), symbol = c.mainSymbol, symbolPosition = c.position,
        decimals = c.decimals, rate = 1.0, isMain = true, isVisible = true, orderSeq = 0, updatedAt = now,
    )

    suspend fun seedFresh(main: CatalogCurrency = CurrencyCatalog.forLocale()) = db.withTransaction {
        val now = System.currentTimeMillis()
        if (db.currencyDao().getMain() == null) db.currencyDao().upsert(listOf(mainCurrencyRow(main, now)))
        val mainId = db.currencyDao().getMain()!!.id
        if (db.categoryDao().getAll().isEmpty()) db.categoryDao().upsert(categories(now))
        if (db.accountGroupDao().getAll().none { it.status == com.nexo.app.core.model.GroupStatus.ACTIVE }) {
            db.accountGroupDao().upsert(groups(now))
        }
        if (db.accountDao().getAll().isEmpty()) db.accountDao().upsert(accounts(mainId, now))
        if (db.settingDao().getAll().isEmpty()) {
            db.settingDao().putAll(BookSettings.SEED.toMap().map { SettingEntity(it.key, it.value) })
        }
    }

    fun groups(now: Long): List<AccountGroupEntity> {
        // id, name, type, in display order.
        val rows = listOf(
            Triple(11, R.string.group_cash, GroupType.NORMAL),
            Triple(1, R.string.group_accounts, GroupType.NORMAL),
            Triple(2, R.string.group_card, GroupType.CREDIT_CARD),
            Triple(3, R.string.group_debit_card, GroupType.DEBIT_CARD),
            Triple(4, R.string.group_savings, GroupType.NORMAL),
            Triple(6, R.string.group_prepaid, GroupType.NORMAL),
            Triple(8, R.string.group_investments, GroupType.NORMAL),
            Triple(9, R.string.group_overdrafts, GroupType.NORMAL),
            Triple(5, R.string.group_loan, GroupType.NORMAL),
            Triple(10, R.string.group_insurance, GroupType.NORMAL),
            Triple(7, R.string.group_others, GroupType.NORMAL),
        )
        return rows.mapIndexed { i, (id, name, type) ->
            AccountGroupEntity(id.toString(), s(name), type, id, i + 1, updatedAt = now)
        }
    }

    private fun accounts(mainId: String, now: Long) = listOf(
        AccountEntity("11", s(R.string.group_cash), "11", mainId, 1, createdAt = now, updatedAt = now),
        AccountEntity("1", s(R.string.group_accounts), "1", mainId, 2, createdAt = now + 1, updatedAt = now),
        AccountEntity("2", s(R.string.group_card), "2", mainId, 3, linkedAccountId = "1", createdAt = now + 2, updatedAt = now),
    )

    private fun categories(now: Long): List<CategoryEntity> {
        val out = ArrayList<CategoryEntity>()
        var id = 0
        fun add(name: Int, kind: CategoryKind, parent: String? = null, emoji: String? = null, color: Int? = null): String {
            id++
            out += CategoryEntity(id.toString(), s(name), kind, parent, id, createdAt = now + id, updatedAt = now, emoji = emoji, colorIndex = color)
            return id.toString()
        }
        listOf(R.string.cat_allowance, R.string.cat_salary, R.string.cat_petty_cash, R.string.cat_bonus, R.string.cat_other)
            .forEach { res -> MAIN_LOOK[res].let { add(res, CategoryKind.INCOME, emoji = it?.first, color = it?.second) } }
        val mains = listOf(
            R.string.cat_food, R.string.cat_social_life, R.string.cat_pets, R.string.cat_transport, R.string.cat_culture,
            R.string.cat_household, R.string.cat_apparel, R.string.cat_beauty, R.string.cat_health, R.string.cat_education,
            R.string.cat_gift, R.string.cat_other,
        ).associateWith { res -> MAIN_LOOK[res].let { add(res, CategoryKind.EXPENSE, emoji = it?.first, color = it?.second) } }
        val subs = linkedMapOf(
            R.string.cat_food to listOf(R.string.cat_lunch, R.string.cat_dinner, R.string.cat_eating_out, R.string.cat_beverages),
            R.string.cat_social_life to listOf(R.string.cat_friend, R.string.cat_fellowship, R.string.cat_alumni, R.string.cat_dues),
            R.string.cat_transport to listOf(R.string.cat_bus, R.string.cat_subway, R.string.cat_taxi, R.string.cat_car),
            R.string.cat_culture to listOf(R.string.cat_books, R.string.cat_movie, R.string.cat_music, R.string.cat_apps),
            R.string.cat_household to listOf(R.string.cat_appliances, R.string.cat_furniture, R.string.cat_kitchen, R.string.cat_toiletries, R.string.cat_chandlery),
            R.string.cat_apparel to listOf(R.string.cat_clothing, R.string.cat_fashion, R.string.cat_shoes, R.string.cat_laundry),
            R.string.cat_beauty to listOf(R.string.cat_cosmetics, R.string.cat_makeup, R.string.cat_accessories, R.string.cat_beauty),
            R.string.cat_health to listOf(R.string.cat_health, R.string.cat_yoga, R.string.cat_hospital, R.string.cat_medicine),
            R.string.cat_education to listOf(R.string.cat_schooling, R.string.cat_textbooks, R.string.cat_school_supplies, R.string.cat_academy),
        )
        // Subcategories take their main category's hue (colorIndex stays null) and carry their own emoji.
        subs.forEach { (parent, children) -> children.forEach { add(it, CategoryKind.EXPENSE, mains.getValue(parent), emoji = SUB_EMOJI[it]) } }
        return out
    }

    private companion object {
        /** Seeded main categories: emoji and hue index (0 tomato … 9 stone, see CategoryHues). */
        val MAIN_LOOK: Map<Int, Pair<String, Int>> = mapOf(
            R.string.cat_allowance to ("💵" to 3),
            R.string.cat_salary to ("💼" to 5),
            R.string.cat_petty_cash to ("🪙" to 2),
            R.string.cat_bonus to ("🎉" to 7),
            R.string.cat_other to ("📦" to 9),
            R.string.cat_food to ("🍜" to 0),
            R.string.cat_social_life to ("🥂" to 2),
            R.string.cat_pets to ("🐾" to 1),
            R.string.cat_transport to ("🚌" to 5),
            R.string.cat_culture to ("🎭" to 7),
            R.string.cat_household to ("🏠" to 3),
            R.string.cat_apparel to ("👕" to 6),
            R.string.cat_beauty to ("💄" to 8),
            R.string.cat_health to ("🩺" to 4),
            R.string.cat_education to ("🎓" to 5),
            R.string.cat_gift to ("🎁" to 0),
        )

        val SUB_EMOJI: Map<Int, String> = mapOf(
            R.string.cat_lunch to "🍱", R.string.cat_dinner to "🍽️", R.string.cat_eating_out to "🍴", R.string.cat_beverages to "🥤",
            R.string.cat_friend to "🧑‍🤝‍🧑", R.string.cat_fellowship to "🤝", R.string.cat_alumni to "🎓", R.string.cat_dues to "🔁",
            R.string.cat_bus to "🚌", R.string.cat_subway to "🚇", R.string.cat_taxi to "🚕", R.string.cat_car to "🚗",
            R.string.cat_books to "📚", R.string.cat_movie to "🎬", R.string.cat_music to "🎵", R.string.cat_apps to "📱",
            R.string.cat_appliances to "🔌", R.string.cat_furniture to "🛋️", R.string.cat_kitchen to "🍳", R.string.cat_toiletries to "🧴", R.string.cat_chandlery to "🧽",
            R.string.cat_clothing to "👕", R.string.cat_fashion to "👗", R.string.cat_shoes to "👟", R.string.cat_laundry to "🧺",
            R.string.cat_cosmetics to "🧴", R.string.cat_makeup to "💋", R.string.cat_accessories to "💍", R.string.cat_beauty to "💅",
            R.string.cat_health to "🩺", R.string.cat_yoga to "🧘", R.string.cat_hospital to "🏥", R.string.cat_medicine to "💊",
            R.string.cat_schooling to "🏫", R.string.cat_textbooks to "📚", R.string.cat_school_supplies to "✏️", R.string.cat_academy to "🎓",
        )
    }
}
