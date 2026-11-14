package com.nexo.app.data.repository

import com.nexo.app.core.model.Account
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType

/** Accounts and account groups. */
interface AccountsRepository {
    suspend fun saveGroup(id: String?, name: String, type: GroupType)

    suspend fun setGroupStatus(id: String, status: GroupStatus)

    suspend fun reorderGroups(ids: List<String>)

    suspend fun nextAccountOrder(): Int

    /** Saves an account; a currency change re-derives account amounts of its rows and linked debit cards. */
    suspend fun saveAccount(account: Account)

    suspend fun setStatus(id: String, status: AccountStatus)

    /** Order = position in the whole list, group may change. */
    suspend fun reorderAccounts(order: List<Pair<String, String>>)

    suspend fun setTransferExpense(id: String, flag: Boolean)
}
