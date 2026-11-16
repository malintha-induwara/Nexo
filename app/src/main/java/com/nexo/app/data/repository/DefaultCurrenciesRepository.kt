package com.nexo.app.data.repository

import androidx.room.withTransaction
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.BackupFiles
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.data.database.entity.asEntity
import com.nexo.app.data.file.AppDirs
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/** Currencies with manually entered rates. */
class DefaultCurrenciesRepository @Inject constructor(
    private val db: NexoDatabase,
    private val refs: ReferenceDataRepository,
    private val reset: BookRepository,
    private val backups: BackupRepository,
    private val dirs: AppDirs,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) : CurrenciesRepository {
    private fun now() = clock.millis()

    override suspend fun setMainFormat(position: SymbolPosition, decimals: Int) {
        val main = db.currencyDao().getMain() ?: return
        db.currencyDao().upsert(
            listOf(
                main.copy(
                    symbolPosition = position,
                    decimals = decimals,
                    rate = 1.0,
                    updatedAt = now(),
                ),
            ),
        )
    }

    override suspend fun saveSub(c: Currency) {
        val order = db.currencyDao().getById(c.id)?.orderSeq ?: (db.currencyDao().maxOrder() + 1)
        db.currencyDao().upsert(
            listOf(
                c.copy(
                    isVisible = true,
                    isMain = false,
                    orderSeq = order,
                    updatedAt = now(),
                ).asEntity(),
            ),
        )
    }

    override fun builtinSub(
        c: CatalogCurrency,
        rate: Double,
        position: SymbolPosition,
        decimals: Int,
        existing: Currency?,
    ) = Currency(
        id = c.code,
        isoCode = c.code,
        name = c.displayName(),
        symbol = c.subSymbol,
        symbolPosition = position,
        decimals = decimals,
        rate = rate,
        isMain = false,
        isVisible = true,
        orderSeq = existing?.orderSeq ?: 0,
        updatedAt = now(),
    )

    override fun customSub(
        id: String?,
        name: String,
        symbol: String,
        position: SymbolPosition,
        decimals: Int,
        rate: Double,
    ) = Currency(
        id = id ?: UUID.randomUUID().toString(),
        isoCode = null,
        name = name.trim(),
        symbol = symbol.trim(),
        symbolPosition = position,
        decimals = decimals,
        rate = rate,
        isMain = false,
        isVisible = true,
        orderSeq = 0,
        updatedAt = now(),
    )

    override suspend fun hide(id: String) {
        val c = db.currencyDao().getById(id) ?: return
        db.currencyDao().upsert(listOf(c.copy(isVisible = false, updatedAt = now())))
    }

    override suspend fun reorder(ids: List<String>) = db.withTransaction {
        val all = db.currencyDao().getAll().associateBy { it.id }
        db.currencyDao().upsert(ids.mapIndexedNotNull { i, id -> all[id]?.copy(orderSeq = i + 1) })
    }

    /** "Data initialization": complete reset with the new main currency. */
    override suspend fun changeMainWithReset(target: CatalogCurrency) =
        reset.completeReset(deletePhotos = false, main = target)

    /**
     * "Data retention": every main amount and budget converts with [r] (1 old main = r new main),
     * and every rate becomes old rate × r. A safety backup is taken first and restored on failure.
     */
    override suspend fun changeMainWithRate(target: CatalogCurrency, r: Double): Boolean {
        val safety = File(dirs.backups, BackupFiles.name(LocalDateTime.now(clock)))
        if (!backups.writeBackup(safety)) return false
        prefs.setLastAutoBackup(now())
        val ref = refs.getRefData()
        return runCatching {
            db.withTransaction {
                val existing = db.currencyDao().getAll()
                val newRow = existing.firstOrNull { it.id == target.code }
                val d = newRow?.decimals ?: target.decimals
                val rb = BigDecimal.valueOf(r)
                // Transactions: main amount re-expressed in the new main currency.
                val rows = db.transactionDao().getAll()
                db.transactionDao().updateAll(
                    rows.map { t ->
                        val main = if (t.currencyId == target.code) {
                            t.amount
                        } else {
                            BigDecimal.valueOf(
                                t.amountMain,
                            ).multiply(rb).setScale(d, RoundingMode.HALF_UP).toDouble()
                        }
                        t.copy(amountMain = main)
                    },
                )
                // Budgets.
                val amounts = db.budgetDao().getAmounts()
                db.budgetDao().upsertAmounts(
                    amounts.map {
                        it.copy(amount = Money.round(it.amount * r, d), updatedAt = now())
                    },
                )
                // Currencies rebuilt relative to the new main.
                val oldMain = ref.mainCurrency
                val rebuilt = existing.filter { it.id != target.code }.map { c ->
                    val catalog = CurrencyCatalog.find(c.isoCode)
                    c.copy(
                        rate = BigDecimal.valueOf(
                            if (c.id == oldMain.id) {
                                1.0
                            } else {
                                c.rate
                            },
                        ).multiply(rb).setScale(6, RoundingMode.HALF_UP).toDouble(),
                        isMain = false,
                        symbol = catalog?.subSymbol ?: c.symbol,
                        updatedAt = now(),
                    )
                }.sortedBy { it.orderSeq }.mapIndexed { i, c -> c.copy(orderSeq = i + 1) }
                val main = (
                    newRow
                        ?: CurrencyEntity(
                            target.code,
                            target.code,
                            target.displayName(),
                            target.mainSymbol,
                            target.position,
                            target.decimals,
                            1.0,
                            true,
                            true,
                            0,
                            now(),
                        )
                    )
                    .copy(
                        rate = 1.0,
                        isMain = true,
                        isVisible = true,
                        symbol = target.mainSymbol,
                        orderSeq = 0,
                        updatedAt = now(),
                    )
                db.currencyDao().deleteAll()
                db.currencyDao().upsert(listOf(main) + rebuilt)
            }
            true
        }.getOrElse {
            backups.restoreFile(safety)
            false
        }
    }
}
