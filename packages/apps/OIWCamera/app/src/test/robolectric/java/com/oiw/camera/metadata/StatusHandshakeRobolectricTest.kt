package com.oiw.camera.metadata

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tier 1.5 (Robolectric): the OIWCamera -> OIWLauncher status handshake, end to end for the first
 * time. [StatusFileWriter] writes `/sdcard/OIW_MEDIA/.status.json`; [StatusProvider] serves it over
 * `ContentResolver.call` so the launcher needs no storage permission.
 *
 * Both halves type-checked fine while never having run together. This test drives the exact call
 * OIWLauncher's `LauncherStatusReader` makes — including the JSON shape, which is the actual
 * contract between the two apps and the thing a compiler cannot check across module boundaries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatusHandshakeRobolectricTest {

    private lateinit var context: Context
    private lateinit var writer: StatusFileWriter
    private val statusUri: Uri = Uri.parse("content://${StatusProvider.AUTHORITY}")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        writer = StatusFileWriter(context)
        OiwPaths.mediaRoot().deleteRecursively()
        Robolectric.setupContentProvider(StatusProvider::class.java, StatusProvider.AUTHORITY)
    }

    @After
    fun tearDown() {
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @Test
    fun writerCreatesTheMediaRootOnFirstWrite() {
        assertFalse(OiwPaths.mediaRoot().exists())
        writer.write(status())
        assertTrue("first status write must create /OIW_MEDIA itself", OiwPaths.mediaRoot().isDirectory)
        assertTrue(OiwPaths.statusFile().isFile)
    }

    @Test
    fun writerLeavesNoTemporaryFileBehind() {
        writer.write(status())
        assertFalse(File(OiwPaths.mediaRoot(), ".status.json.tmp").exists())
    }

    @Test
    fun providerServesExactlyWhatTheWriterWrote() {
        writer.write(status(freeGb = 128.5, thermal = "warm", battery = 61, profile = "cinema_4k_24_log"))

        val json = context.contentResolver
            .call(statusUri, StatusProvider.METHOD_GET_STATUS, null, null)
            ?.getString(StatusProvider.KEY_STATUS_JSON)

        assertNotNull("the launcher's call() must return a payload", json)
        assertEquals(OiwPaths.statusFile().readText(), json)
    }

    /**
     * The cross-module contract: the launcher deserialises this JSON into its own `Status` data
     * class with the same four field names. Asserting the names here is what stops a rename in one
     * app from silently blanking the other app's status strip.
     */
    @Test
    fun theServedJsonCarriesTheFourFieldsTheLauncherReads() {
        writer.write(status(freeGb = 64.25, thermal = "nominal", battery = 88, profile = "cinema_long_take_safe"))

        val json = context.contentResolver
            .call(statusUri, StatusProvider.METHOD_GET_STATUS, null, null)!!
            .getString(StatusProvider.KEY_STATUS_JSON)!!
        val obj = Gson().fromJson(json, com.google.gson.JsonObject::class.java)

        assertEquals(64.25, obj.get("storageFreeGb").asDouble, 1e-9)
        assertEquals("nominal", obj.get("thermalMode").asString)
        assertEquals(88, obj.get("batteryPercent").asInt)
        assertEquals("cinema_long_take_safe", obj.get("lastProfileId").asString)
    }

    @Test
    fun providerReturnsANullPayloadWhenNothingHasBeenWrittenYet() {
        val bundle = context.contentResolver.call(statusUri, StatusProvider.METHOD_GET_STATUS, null, null)
        assertNotNull("a missing status file is still a valid answer, not an error", bundle)
        assertNull(bundle!!.getString(StatusProvider.KEY_STATUS_JSON))
    }

    @Test
    fun providerIgnoresUnknownMethods() {
        assertNull(context.contentResolver.call(statusUri, "delete_everything", null, null))
    }

    /** Read-only by construction: the table-style API is deliberately inert. */
    @Test
    fun providerExposesNoWritableTableSurface() {
        val provider = StatusProvider()
        assertNull(provider.query(statusUri, null, null, null, null))
        assertNull(provider.getType(statusUri))
        assertNull(provider.insert(statusUri, null))
        assertEquals(0, provider.delete(statusUri, null, null))
        assertEquals(0, provider.update(statusUri, null, null, null))
    }

    @Test
    fun aLaterWriteIsVisibleToTheNextProviderCall() {
        writer.write(status(profile = "cinema_4k_24_log"))
        writer.write(status(profile = "cinema_low_light"))

        val json = context.contentResolver
            .call(statusUri, StatusProvider.METHOD_GET_STATUS, null, null)!!
            .getString(StatusProvider.KEY_STATUS_JSON)!!
        assertTrue(json.contains("cinema_low_light"))
        assertFalse("status must be replaced, not appended to", json.contains("cinema_4k_24_log"))
    }

    private fun status(
        freeGb: Double? = 100.0,
        thermal: String? = "nominal",
        battery: Int? = 90,
        profile: String? = "cinema_4k_24_log",
    ) = StatusFileWriter.Status(freeGb, thermal, battery, profile)
}
