package com.bantai.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone

/**
 * Tinitingnan kung nasa contacts ng phone ang nag-message. Sa phone lang; walang ipinapadala kahit saan.
 * Walang pahintulot o walang tugma: null, kaya karaniwang babala pa rin ang lalabas.
 */
object ContactHelper {

    data class SavedContact(val name: String, val number: String?)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun isSavedContact(context: Context, senderIdentifier: String): Boolean = find(context, senderIdentifier) != null

    /** [sender] ay ang title ng notification: numero (hindi naka-save) o pangalan (naka-save, o profile name sa chat app). */
    fun find(context: Context, sender: String): SavedContact? {
        val id = sender.trim()
        if (id.isEmpty() || !hasPermission(context)) return null
        return runCatching { if (looksLikeNumber(id)) byNumber(context, id) else byName(context, id) }.getOrNull()
    }

    fun looksLikeNumber(sender: String): Boolean =
        sender.count(Char::isDigit) >= 7 && sender.all { it.isDigit() || it in "+-() " }

    private fun byNumber(context: Context, number: String): SavedContact? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) SavedContact(c.getString(0) ?: number, number) else null
        }
    }

    // Mga contact na may numero lang, para laging may matatawagan sa "Tawagan si …".
    private fun byName(context: Context, name: String): SavedContact? {
        return context.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            "${Phone.DISPLAY_NAME} = ? COLLATE NOCASE", arrayOf(name), null,
        )?.use { c ->
            if (c.moveToFirst()) SavedContact(c.getString(0) ?: name, c.getString(1)) else null
        }
    }
}
