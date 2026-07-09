package com.oiw.camera.capture

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File

/**
 * Loads capture profiles from bundled assets (configs/capture_profiles/*.json, copied into
 * app/src/main/assets/capture_profiles/ at build time) and from the user plugin directory
 * (/sdcard/OIW_MEDIA/plugins/profiles/, docs/API_PLUGIN_FRAMEWORK.md #1).
 *
 * Never throws on a single malformed profile — malformed files are skipped and reported via [loadErrors]
 * so one bad hand-edited JSON file doesn't take down the whole profile list (docs/UI_UX.md #Error UX rule).
 */
class ProfileRepository(private val context: Context) {

    private val gson = Gson()
    val loadErrors = mutableListOf<String>()

    fun loadAll(): List<CaptureProfile> {
        loadErrors.clear()
        val bundled = loadBundledProfiles()
        val user = loadUserProfiles()
        // User profiles with a matching id override the bundled default, rather than duplicating it.
        val byId = LinkedHashMap<String, CaptureProfile>()
        (bundled + user).forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    private fun loadBundledProfiles(): List<CaptureProfile> {
        val assetDir = "capture_profiles"
        val names = runCatching { context.assets.list(assetDir)?.toList() ?: emptyList() }.getOrDefault(emptyList())
        return names.filter { it.endsWith(".json") }.mapNotNull { name ->
            parseOrNull("bundled:$name") {
                context.assets.open("$assetDir/$name").bufferedReader().use { it.readText() }
            }
        }
    }

    private fun loadUserProfiles(): List<CaptureProfile> {
        val dir = com.oiw.camera.util.OiwPaths.userProfilesDir()
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            parseOrNull("user:${file.name}") { file.readText() }
        }
    }

    private inline fun parseOrNull(label: String, readText: () -> String): CaptureProfile? {
        return try {
            gson.fromJson(readText(), CaptureProfile::class.java)
        } catch (e: JsonSyntaxException) {
            loadErrors += "$label: malformed JSON (${e.message})"
            null
        } catch (e: Exception) {
            loadErrors += "$label: could not read (${e.message})"
            null
        }
    }
}
