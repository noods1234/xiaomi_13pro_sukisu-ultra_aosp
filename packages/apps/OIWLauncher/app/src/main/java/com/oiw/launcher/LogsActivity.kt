package com.oiw.launcher

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Minimal logs viewer (docs/UI_UX.md #1 "logs shortcut"). Shows the most recent OIWCamera debug
 * log file, if present — a full log-collector UI is out of scope for the launcher (that's
 * tools/log_collector.sh for host-side collection); this is a quick field-glance view only.
 */
class LogsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            setPadding(24, 24, 24, 24)
            text = readLatestLog()
            textSize = 12f
        }
        setContentView(ScrollView(this).apply { addView(textView) })
    }

    private fun readLatestLog(): String {
        val logsDir = File(
            getExternalFilesDir(null)?.parentFile?.parentFile,
            "OIW_MEDIA/logs",
        )
        val latest = logsDir.listFiles()?.maxByOrNull { it.lastModified() }
        return latest?.readText() ?: "No log files found under ${logsDir.absolutePath}."
    }
}
