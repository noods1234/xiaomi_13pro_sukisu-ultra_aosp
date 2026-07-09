package com.oiw.camera.metadata

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Declarative CSV exporter (docs/API_PLUGIN_FRAMEWORK.md #1): applies an exporter template
 * (configs/schema/exporter_template.schema.json) to clip sidecar JSON. Pure function — file I/O
 * stays with the caller so this is unit-testable without Android.
 */
class CsvExporter {

    data class Template(val id: String, val format: String, val fields: List<Field>) {
        data class Field(val sourceField: String, val columnName: String, val default: String? = null)
    }

    private val gson = Gson()

    fun parseTemplate(json: String): Template = gson.fromJson(json, Template::class.java).also {
        require(it.format == "csv") { "Template '${it.id}' has format '${it.format}', expected csv" }
        require(it.fields.isNotEmpty()) { "Template '${it.id}' has no fields" }
    }

    /** @param sidecars each element is one clip's sidecar JSON text. */
    fun export(template: Template, sidecars: List<String>): String {
        val sb = StringBuilder()
        sb.append(template.fields.joinToString(",") { escape(it.columnName) }).append("\r\n")
        for (raw in sidecars) {
            val obj = gson.fromJson(raw, JsonObject::class.java)
            sb.append(template.fields.joinToString(",") { f ->
                escape(resolve(obj, f.sourceField) ?: f.default ?: "")
            }).append("\r\n")
        }
        return sb.toString()
    }

    /** Dot-path resolution into the sidecar object, e.g. "lensName" or "integrity.sha256". */
    private fun resolve(obj: JsonObject, path: String): String? {
        var current: com.google.gson.JsonElement = obj
        for (part in path.split('.')) {
            if (!current.isJsonObject) return null
            current = current.asJsonObject.get(part) ?: return null
        }
        return when {
            current.isJsonNull -> null
            current.isJsonPrimitive -> current.asJsonPrimitive.asString
            else -> current.toString()
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
