package com.oiw.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.oiw.launcher.databinding.ActivityHomeBinding

/**
 * Camera-first HOME/DEFAULT launcher (docs/UI_UX.md #1). Deliberately minimal: no app drawer, no
 * widgets — the largest tile always launches OIWCamera. "Distraction-free mode" (Settings) hides
 * everything except the record shortcut and status strip.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: LauncherPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = LauncherPreferences(this)

        binding.recordShortcut.setOnClickListener { launchCamera() }
        binding.settingsShortcut.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
        binding.fileBrowserShortcut.setOnClickListener { launchFileBrowser() }
        binding.logsShortcut.setOnClickListener { launchLogsView() }

        applyDistractionFreeMode()
        refreshStatusStrip()
    }

    override fun onResume() {
        super.onResume()
        refreshStatusStrip()
    }

    private fun applyDistractionFreeMode() {
        val distractionFree = prefs.distractionFreeMode
        binding.secondaryShortcutsRow.visibility =
            if (distractionFree) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun refreshStatusStrip() {
        // Storage/thermal/battery status strip (docs/UI_UX.md #1) — reads OIWCamera's last-known
        // state via a shared, world-readable status file under OIW_MEDIA rather than a bound
        // service, to keep the launcher decoupled from OIWCamera's process lifecycle.
        val status = LauncherStatusReader(this).readLastKnownStatus()
        binding.statusStrip.text = status?.summaryLine() ?: getString(R.string.status_unavailable)
    }

    private fun launchCamera() {
        val intent = packageManager.getLaunchIntentForPackage(CAMERA_PACKAGE)
        if (intent != null) {
            startActivity(intent)
        } else {
            android.widget.Toast.makeText(
                this,
                "OIWCamera ($CAMERA_PACKAGE) is not installed.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun launchFileBrowser() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                android.provider.DocumentsContract.buildRootUri(
                    "com.android.externalstorage.documents", "primary",
                ),
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
            )
        }
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }

    private fun launchLogsView() {
        startActivity(Intent(this, LogsActivity::class.java))
    }

    private fun isCameraInstalled(): Boolean = try {
        packageManager.getPackageInfo(CAMERA_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val CAMERA_PACKAGE = "com.oiw.camera"
    }
}
