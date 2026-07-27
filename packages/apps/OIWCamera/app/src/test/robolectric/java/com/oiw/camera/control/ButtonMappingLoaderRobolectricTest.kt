package com.oiw.camera.control

import android.content.Context
import com.oiw.camera.util.OiwPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tier 1.5 (Robolectric): the user-override half of [ButtonMappingLoader].
 *
 * Same scope caveat as `ProfileRepositoryRobolectricTest`: `context.assets` is empty in the offline
 * harness, so the bundled `assets/button_mappings/default.json` branch is covered by
 * `BundledProfileAssetsTest` (tier 1) and by `./gradlew testDebugUnitTest`.
 *
 * The override precedence matters on set: a user who remaps a button expects their file to win
 * over the shipped default, every time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ButtonMappingLoaderRobolectricTest {

    private lateinit var context: Context
    private lateinit var loader: ButtonMappingLoader

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        loader = ButtonMappingLoader(context)
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @After
    fun tearDown() {
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @Test
    fun aUserMappingIsLoadedFromTheDocumentedOverrideDirectory() {
        userMapping("rig", """{"id":"rig","mappings":[{"input":"hid_key_r","action":"record_toggle"}]}""")

        val mapping = loader.load("rig")
        assertNotNull("a hand-dropped mapping must load without a rebuild", mapping)
        assertEquals("rig", mapping!!.id)
        assertEquals("record_toggle", loader.actionFor(mapping, "hid_key_r"))
    }

    @Test
    fun anUnmappedInputResolvesToNullRatherThanADefaultAction() {
        userMapping("rig", """{"id":"rig","mappings":[{"input":"hid_key_r","action":"record_toggle"}]}""")
        val mapping = loader.load("rig")!!
        assertNull(
            "an unknown input must do nothing — never fall through to some other action",
            loader.actionFor(mapping, "volume_up_short"),
        )
    }

    @Test
    fun theFirstEntryWinsWhenAnInputIsMappedTwice() {
        userMapping(
            "dupe",
            """{"id":"dupe","mappings":[
                 {"input":"volume_up_short","action":"record_toggle"},
                 {"input":"volume_up_short","action":"still_capture"}]}""",
        )
        val mapping = loader.load("dupe")!!
        assertEquals("record_toggle", loader.actionFor(mapping, "volume_up_short"))
    }

    @Test
    fun anUnknownMappingIdReturnsNullInsteadOfThrowing() {
        assertNull(loader.load("no_such_mapping"))
    }

    @Test
    fun aMalformedUserMappingReturnsNullInsteadOfCrashingTheCameraUi() {
        userMapping("broken", "{ this is not json")
        assertNull("a bad remap file must not take the camera down mid-shoot", loader.load("broken"))
    }

    @Test
    fun anEmptyMappingListLoadsAndSimplyMapsNothing() {
        userMapping("empty", """{"id":"empty","mappings":[]}""")
        val mapping = loader.load("empty")
        assertNotNull(mapping)
        assertNull(loader.actionFor(mapping!!, "volume_up_short"))
    }

    private fun userMapping(id: String, json: String) {
        val dir = File(OiwPaths.mediaRoot(), "plugins/button_mappings").apply { mkdirs() }
        File(dir, "$id.json").writeText(json)
    }
}
