package com.nexo.app.data.resource

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The app's own words, for the rows the ledger writes by itself (a fee, a balance difference). */
class TextResourceDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getString(@StringRes id: Int): String = context.getString(id)
}
