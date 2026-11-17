package com.nexo.app.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

// Opened from the fields and the top bar but drawn over everything, so they share one
// holder that survives rotation.
@Stable
class EntryDialogState {
    var date by mutableStateOf(false)
    var time by mutableStateOf(false)
    var calculator by mutableStateOf(false)
    var repeatList by mutableStateOf(false)
    var repeatMenu by mutableStateOf(false)
    var installments by mutableStateOf(false)
    var repeatEnd by mutableStateOf(false)
    var tags by mutableStateOf(false)
    var copy by mutableStateOf(false)
    var photoNotice by mutableStateOf(false)
    var scanMenu by mutableStateOf(false)
    var transferGuide by mutableStateOf(false)

    // Index of the photo open full screen, or -1.
    var viewerIndex by mutableIntStateOf(-1)

    // 0 closed, 1 editing the main-currency amount, 2 editing the rate.
    var rateEdit by mutableIntStateOf(0)

    companion object {
        val Saver: Saver<EntryDialogState, Any> = listSaver(
            save = {
                listOf(
                    it.date, it.time, it.calculator, it.repeatList, it.repeatMenu,
                    it.installments, it.repeatEnd, it.tags, it.copy, it.photoNotice,
                    it.scanMenu, it.transferGuide, it.viewerIndex, it.rateEdit,
                )
            },
            restore = { v ->
                EntryDialogState().apply {
                    date = v[0] as Boolean
                    time = v[1] as Boolean
                    calculator = v[2] as Boolean
                    repeatList = v[3] as Boolean
                    repeatMenu = v[4] as Boolean
                    installments = v[5] as Boolean
                    repeatEnd = v[6] as Boolean
                    tags = v[7] as Boolean
                    copy = v[8] as Boolean
                    photoNotice = v[9] as Boolean
                    scanMenu = v[10] as Boolean
                    transferGuide = v[11] as Boolean
                    viewerIndex = v[12] as Int
                    rateEdit = v[13] as Int
                }
            },
        )
    }
}

@Composable
fun rememberEntryDialogState(): EntryDialogState =
    rememberSaveable(saver = EntryDialogState.Saver) { EntryDialogState() }
