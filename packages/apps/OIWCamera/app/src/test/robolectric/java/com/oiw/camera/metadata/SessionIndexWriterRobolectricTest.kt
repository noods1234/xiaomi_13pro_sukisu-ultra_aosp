package com.oiw.camera.metadata

import com.oiw.camera.util.OiwPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tier 1.5 (Robolectric): [SessionIndexWriter] walks a real session tree and writes the shot list a
 * DIT actually opens. [CsvExporter] was already unit-tested as a pure function; what had never run
 * is the part that touches disk — the recursive scan, the ordering, and the tmp+rename.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionIndexWriterRobolectricTest {

    private lateinit var sessionDir: File
    private val writer = SessionIndexWriter()

    @Before
    fun setUp() {
        OiwPaths.mediaRoot().deleteRecursively()
        sessionDir = File(OiwPaths.mediaRoot(), "proj/2026-07-26").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @Test
    fun writesOneHeaderAndOneRowPerSidecarFoundAnywhereInTheSession() {
        sidecar("A/CLIP_0001", scene = "12", shot = "A", take = 1)
        sidecar("A/CLIP_0002", scene = "12", shot = "B", take = 2)
        sidecar("B/CLIP_0003", scene = "13", shot = "A", take = 1)

        val csv = writer.write(sessionDir)
        assertNotNull("a session with sidecars must produce an index", csv)
        assertEquals("session_index.csv", csv!!.name)

        val lines = csv.readText().split("\r\n").filter { it.isNotEmpty() }
        assertEquals("header + 3 clips", 4, lines.size)
        // Asserted as a set, not a prefix string: a new column used to break this the moment one
        // was added, which trains people to update the expectation rather than read it.
        val header = lines[0].split(",")
        listOf("DateTime", "Project", "Scene", "Shot", "Take", "Resolution", "FPS", "Codec")
            .forEach { assertTrue("shot list must carry a '$it' column, got $header", it in header) }
    }

    @Test
    fun rowsAreOrderedByClipNameSoTheShotListReadsChronologically() {
        sidecar("B/CLIP_0003", scene = "13", shot = "A", take = 1)
        sidecar("A/CLIP_0001", scene = "12", shot = "A", take = 1)
        sidecar("A/CLIP_0002", scene = "12", shot = "B", take = 2)

        val lines = writer.write(sessionDir)!!.readText().split("\r\n").filter { it.isNotEmpty() }
        val scenesAndShots = lines.drop(1).map { it.split(",").let { c -> c[2] + c[3] + c[4] } }
        assertEquals(listOf("12A1", "12B2", "13A1"), scenesAndShots)
    }

    @Test
    fun missingOptionalFieldsFallBackToTheTemplateDefaults() {
        // No scene/shot/take/lens — a run-and-gun take with nothing slated.
        File(sessionDir, "A").mkdirs()
        File(sessionDir, "A/CLIP_0009.json").writeText(
            """{"dateTimeIso8601":"2026-07-26T10:00:00Z","project":"untitled","resolution":"3840x2160",
               "frameRateFps":24,"codec":"hevc10","bitrateBps":180000000,"colorProfile":"flat_approximation"}"""
        )

        // Looked up by header rather than by index: a new column (Circled) used to silently shift
        // every assertion after it, which makes an index-based test a maintenance trap.
        val lines = writer.write(sessionDir)!!.readText().split("\r\n")
        assertEquals("Scene defaults to empty", "", column(lines, "Scene"))
        assertEquals("Drops defaults to 0", "0", column(lines, "Drops"))
        assertEquals("Recovered defaults to false", "false", column(lines, "Recovered"))
        assertEquals("Circled defaults to false", "false", column(lines, "Circled"))
    }

    private fun column(lines: List<String>, header: String): String {
        val index = lines[0].split(",").indexOf(header)
        assertTrue("no '$header' column in ${lines[0]}", index >= 0)
        return lines[1].split(",")[index]
    }

    @Test
    fun commasAndQuotesInSlateTextAreRfc4180Escaped() {
        File(sessionDir, "A").mkdirs()
        File(sessionDir, "A/CLIP_0001.json").writeText(
            """{"dateTimeIso8601":"2026-07-26T10:00:00Z","project":"Rain, Later","resolution":"3840x2160",
               "frameRateFps":24,"codec":"hevc10","bitrateBps":1,"colorProfile":"standard",
               "lensName":"Helios 44-2 \"swirly\""}"""
        )

        val row = writer.write(sessionDir)!!.readText().split("\r\n")[1]
        assertTrue("a comma inside a field must be quoted", row.contains("\"Rain, Later\""))
        assertTrue("an embedded quote must be doubled", row.contains("\"\"swirly\"\""))
    }

    @Test
    fun leavesNoTemporaryFileBehind() {
        sidecar("A/CLIP_0001", scene = "1", shot = "A", take = 1)
        writer.write(sessionDir)
        assertFalse(File(sessionDir, "session_index.csv.tmp").exists())
    }

    @Test
    fun rewritingASessionReplacesTheIndexRatherThanAppending() {
        sidecar("A/CLIP_0001", scene = "1", shot = "A", take = 1)
        writer.write(sessionDir)
        sidecar("A/CLIP_0002", scene = "1", shot = "B", take = 1)

        val lines = writer.write(sessionDir)!!.readText().split("\r\n").filter { it.isNotEmpty() }
        assertEquals("header + 2 clips, not 1 + 2 + a stale copy", 3, lines.size)
    }

    @Test
    fun anEmptySessionProducesNoIndexAtAll() {
        assertNull("nothing recorded yet means no shot list", writer.write(sessionDir))
        assertFalse(File(sessionDir, "session_index.csv").exists())
    }

    @Test
    fun aMissingSessionDirectoryIsReportedNotThrown() {
        assertNull(writer.write(File(sessionDir, "never-created")))
    }

    /** An index is a convenience; one unreadable sidecar must never abort a recording session. */
    @Test
    fun aCorruptSidecarFailsTheIndexSoftlyInsteadOfThrowing() {
        sidecar("A/CLIP_0001", scene = "1", shot = "A", take = 1)
        File(sessionDir, "A/CLIP_0002.json").writeText("{ truncated")

        val result = writer.write(sessionDir) // must not throw
        if (result != null) {
            assertTrue(result.readText().isNotEmpty())
        }
    }

    private fun sidecar(relativePath: String, scene: String, shot: String, take: Int) {
        val file = File(sessionDir, "$relativePath.json")
        file.parentFile!!.mkdirs()
        file.writeText(
            """{"dateTimeIso8601":"2026-07-26T10:00:00Z","project":"proj","scene":"$scene",
               "shot":"$shot","take":$take,"resolution":"3840x2160","frameRateFps":24,
               "codec":"hevc10","bitrateBps":180000000,"colorProfile":"flat_approximation",
               "isoSensitivity":100,"shutter":"angle","whiteBalanceKelvin":5600,"droppedFrames":0,
               "recovered":false}"""
        )
    }
}
