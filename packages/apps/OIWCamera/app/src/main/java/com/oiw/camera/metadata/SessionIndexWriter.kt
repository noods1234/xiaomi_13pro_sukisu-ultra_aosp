package com.oiw.camera.metadata

import java.io.File

/**
 * Writes the per-session CSV index required by docs/STORAGE_MEDIA.md §7 ("CSV index optionally") —
 * one row per clip, generated from the JSON sidecars already on disk. This is what an editor or DIT
 * actually opens to see a shot list without parsing every sidecar by hand.
 *
 * Uses [CsvExporter] with a built-in default template, so the CSV column set is declarative and
 * user-overridable via configs/exporters/ (docs/API_PLUGIN_FRAMEWORK.md §1).
 */
class SessionIndexWriter(private val exporter: CsvExporter = CsvExporter()) {

    /**
     * Scans [sessionDir] recursively for `*.json` sidecars and writes `session_index.csv` beside
     * them. Returns the file, or null if there were no sidecars (nothing recorded yet).
     * Failures are returned as null rather than thrown — an index is a convenience, and losing it
     * must never take down a recording session.
     */
    fun write(sessionDir: File, templateJson: String = DEFAULT_TEMPLATE): File? = runCatching {
        if (!sessionDir.isDirectory) return null
        val sidecars = sessionDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" && it.name != "session_index.json" }
            .sortedBy { it.name }
            .map { it.readText() }
            .toList()
        if (sidecars.isEmpty()) return null
        val csv = exporter.export(exporter.parseTemplate(templateJson), sidecars)
        val out = File(sessionDir, "session_index.csv")
        val tmp = File(sessionDir, "session_index.csv.tmp")
        tmp.writeText(csv)
        if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        out
    }.getOrNull()

    companion object {
        /** Shot-list columns an edit/DIT workflow actually wants first. */
        const val DEFAULT_TEMPLATE = """
        {"id":"session_index","format":"csv","fields":[
          {"sourceField":"dateTimeIso8601","columnName":"DateTime"},
          {"sourceField":"project","columnName":"Project"},
          {"sourceField":"scene","columnName":"Scene","default":""},
          {"sourceField":"shot","columnName":"Shot","default":""},
          {"sourceField":"take","columnName":"Take","default":""},
          {"sourceField":"resolution","columnName":"Resolution"},
          {"sourceField":"frameRateFps","columnName":"FPS"},
          {"sourceField":"codec","columnName":"Codec"},
          {"sourceField":"bitrateBps","columnName":"Bitrate"},
          {"sourceField":"colorProfile","columnName":"ColorProfile"},
          {"sourceField":"isoSensitivity","columnName":"ISO","default":""},
          {"sourceField":"shutter","columnName":"Shutter","default":""},
          {"sourceField":"whiteBalanceKelvin","columnName":"WB","default":""},
          {"sourceField":"lensName","columnName":"Lens","default":""},
          {"sourceField":"anamorphicSqueeze","columnName":"Squeeze","default":""},
          {"sourceField":"lutId","columnName":"LUT","default":""},
          {"sourceField":"droppedFrames","columnName":"Drops","default":"0"},
          {"sourceField":"thermalStateAtStop","columnName":"ThermalAtStop","default":""},
          {"sourceField":"recovered","columnName":"Recovered","default":"false"}
        ]}
        """
    }
}
