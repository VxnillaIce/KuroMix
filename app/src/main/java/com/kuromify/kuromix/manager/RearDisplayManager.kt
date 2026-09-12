package com.kuromify.kuromix.manager

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Finds the secondary/rear panel (display id != DEFAULT_DISPLAY). On devices
 * with a real second screen (flip phones, HyperOS rear-display models) this
 * is usually display id 1, but we don't hardcode it — we ask DisplayManager.
 */
class RearDisplayManager(context: Context) {

    private val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun listNonDefaultDisplays(): List<Display> =
        dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }

    /** Best-effort guess at "the" rear display id, or null if none present/off. */
    fun primaryRearDisplayId(): Int? = listNonDefaultDisplays().firstOrNull()?.displayId

    fun isRearDisplayPresent(): Boolean = listNonDefaultDisplays().isNotEmpty()
}
