package com.nexo.app.core.work

interface BackgroundWork {
    // Checks the inbox every few hours while capture is on.
    fun scheduleSmsCatchUp()

    fun cancelSmsCatchUp()

    // Runs once the phone is online.
    fun requestFxSync()

    fun requestAutoBackup()
}
