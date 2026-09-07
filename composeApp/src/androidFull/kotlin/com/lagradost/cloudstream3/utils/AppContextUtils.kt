package com.lagradost.cloudstream3.utils

import androidx.appcompat.app.AlertDialog

/**
 * Providers use this to pre-select a dialog button. Kept on the Android source
 * set because it is tied to the appcompat dialog.
 */
object AppContextUtils {
    fun setDefaultFocus(dialog: AlertDialog?, whichButton: Int = AlertDialog.BUTTON_POSITIVE) {
        runCatching { dialog?.getButton(whichButton)?.requestFocus() }
    }

    fun restoreFocusFromKeyboard(dialog: AlertDialog?) {
        runCatching { dialog?.window?.decorView?.requestFocus() }
    }
}
