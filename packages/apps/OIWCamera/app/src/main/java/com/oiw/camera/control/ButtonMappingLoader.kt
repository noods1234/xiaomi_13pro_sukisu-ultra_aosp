package com.oiw.camera.control

import android.content.Context
import com.google.gson.Gson
import com.oiw.camera.util.OiwPaths
import java.io.File

/**
 * Loads configs/button_mappings/<id>.json (bundled asset, user-overridable from
 * OIW_MEDIA/plugins/button_mappings/) and resolves inputs to actions
 * (docs/CINEMA_FEATURES.md #3 — was a stub-audit gap, now real; consumed by CameraActivity's
 * KeyEvent interception).
 */
class ButtonMappingLoader(private val context: Context) {

    data class Mapping(val id: String, val mappings: List<Entry>) {
        data class Entry(val input: String, val action: String)
    }

    private val gson = Gson()

    fun load(id: String): Mapping? {
        val userFile = File(OiwPaths.mediaRoot(), "plugins/button_mappings/$id.json")
        val json = runCatching {
            if (userFile.exists()) userFile.readText()
            else context.assets.open("button_mappings/$id.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return runCatching { gson.fromJson(json, Mapping::class.java) }.getOrNull()
    }

    /** @return the mapped action string (see configs/schema/button_mapping.schema.json), or null. */
    fun actionFor(mapping: Mapping, input: String): String? =
        mapping.mappings.firstOrNull { it.input == input }?.action
}
