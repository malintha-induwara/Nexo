package com.nexo.app.core.work

/** Jobs that run outside the app's screens, requested without knowing what runs them. */
interface BackgroundWork {
    /** Checks the inbox every few hours for as long as capture is on. */
    fun scheduleSmsCatchUp()

    fun cancelSmsCatchUp()

    /** Fetches bank rates once the phone is online. */
    fun requestFxSync()

    /** Writes today's automatic backup. */
    fun requestAutoBackup()
}
