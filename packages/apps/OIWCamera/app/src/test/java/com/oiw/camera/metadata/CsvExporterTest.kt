package com.oiw.camera.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvExporterTest {
    private val exporter = CsvExporter()

    private val template = """
      {"id":"editorial","format":"csv","fields":[
        {"sourceField":"project","columnName":"Project"},
        {"sourceField":"take","columnName":"Take","default":"-"},
        {"sourceField":"lensName","columnName":"Lens","default":"unknown"}
      ]}
    """.trimIndent()

    @Test
    fun `exports rows with defaults and dot paths`() {
        val t = exporter.parseTemplate(template)
        val csv = exporter.export(
            t,
            listOf(
                """{"project":"OIW Demo","take":3,"lensName":"Helios 44-2"}""",
                """{"project":"B,Roll"}""",
            ),
        )
        val lines = csv.trim().split("\r\n")
        assertEquals("Project,Take,Lens", lines[0])
        assertEquals("OIW Demo,3,Helios 44-2", lines[1])
        assertEquals("\"B,Roll\",-,unknown", lines[2])
    }

    @Test
    fun `rejects non-csv template`() {
        assertThrows(IllegalArgumentException::class.java) {
            exporter.parseTemplate("""{"id":"x","format":"xml","fields":[{"sourceField":"a","columnName":"A"}]}""")
        }
    }
}
