package com.bantai.ui

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.bantai.R

/** Kumpirmahin bago i-pause ang proteksyon — baka aksidenteng napindot lang. */
object ProtectionToggle {

    fun confirmPause(activity: Activity, onConfirm: () -> Unit, onCancel: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.pause_confirm_title)
            .setMessage(R.string.pause_confirm_message)
            .setPositiveButton(R.string.pause_confirm_yes) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.pause_confirm_no) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }
}
