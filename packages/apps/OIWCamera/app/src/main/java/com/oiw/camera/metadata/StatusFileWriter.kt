package com.oiw.camera.metadata

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

/**
 * Writes /sdcard/OIW_MEDIA/.status.json — the small status file OIWLauncher's status strip reads
 * (see OIWLauncher LauncherStatusReader). Same atomic tmp+rename discipline as clip sidecars.
 */
class StatusFileWriter(private val context: Context) {

    data class Status(
        val storageFreeGb: Double?,
        val thermalMode: String?,
        val batteryPercent: Int?,
        val lastProfileId: String?,
    )

    private val gson = Gson()

    fun write(status: Status) {
        val root = File(context.getExternalFilesDir(null)?.parentFile?.parentFile, "OIW_MEDIA")
        if (!root.isDirectory && !root.mkdirs()) return
        val target = File(root, ".status.json")
        val tmp = File(root, ".status.json.tmp")
        runCatching {
            FileOutputStream(tmp).use { fos ->
                fos.write(gson.toJson(status).toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true); tmp.delete()
            }
        }
    }
}
