package com.oiw.camera.metadata

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.oiw.camera.util.OiwPaths

/**
 * Permission-free cross-app status handshake (stub-audit path-bug fix): OIWLauncher calls
 * ContentResolver.call() on this provider instead of reading a file it may not have access to.
 * Read-only, non-sensitive data (storage/thermal/battery/profile summary), hence exported without
 * a permission gate; no user data or media ever crosses this surface.
 */
class StatusProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_GET_STATUS) return null
        val file = OiwPaths.statusFile()
        val json = runCatching { if (file.exists()) file.readText() else null }.getOrNull()
        return Bundle().apply { putString(KEY_STATUS_JSON, json) }
    }

    // Pure call()-based provider: the table-style API is intentionally unsupported.
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.oiw.camera.status"
        const val METHOD_GET_STATUS = "get_status"
        const val KEY_STATUS_JSON = "status_json"
    }
}
