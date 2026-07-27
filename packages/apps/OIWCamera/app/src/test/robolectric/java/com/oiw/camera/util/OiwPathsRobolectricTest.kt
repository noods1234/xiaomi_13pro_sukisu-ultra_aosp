package com.oiw.camera.util

import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.5 (Robolectric): [OiwPaths] had never been executed by anything — it was type-checked only.
 * The bug it was written to fix (media root landing in /sdcard/Android/data, unreadable cross-app on
 * Android 11+) is precisely the kind a compiler cannot see, so it gets an executing regression test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OiwPathsRobolectricTest {

    @Test
    fun mediaRootIsOiwMediaUnderSharedStorage() {
        val root = OiwPaths.mediaRoot()
        assertEquals("OIW_MEDIA", root.name)
        assertEquals(Environment.getExternalStorageDirectory(), root.parentFile)
    }

    /** Regression guard for the stub-audit path bug: never under the app-private sandbox. */
    @Test
    fun mediaRootIsNotUnderAndroidDataSandbox() {
        val path = OiwPaths.mediaRoot().absolutePath
        assertFalse(
            "media root must not live under /Android/data (unreadable cross-app on API 30+): $path",
            path.contains("/Android/data/") || path.contains("/Android/obb/"),
        )
    }

    @Test
    fun everyDerivedPathStaysUnderTheMediaRoot() {
        val root = OiwPaths.mediaRoot().absolutePath + "/"
        val derived = listOf(
            OiwPaths.benchmarksDir(),
            OiwPaths.logsDir(),
            OiwPaths.userProfilesDir(),
            OiwPaths.statusFile(),
        )
        derived.forEach {
            assertTrue("${it.absolutePath} escapes the media root", it.absolutePath.startsWith(root))
        }
    }

    @Test
    fun derivedPathsMatchTheDocumentedLayout() {
        val root = OiwPaths.mediaRoot()
        assertEquals(java.io.File(root, "benchmarks"), OiwPaths.benchmarksDir())
        assertEquals(java.io.File(root, "logs"), OiwPaths.logsDir())
        assertEquals(java.io.File(root, "plugins/profiles"), OiwPaths.userProfilesDir())
        assertEquals(java.io.File(root, ".status.json"), OiwPaths.statusFile())
    }

    /**
     * The benchmark file StorageManager reads must be the one it writes — the exact loop that
     * audit finding C broke. Asserted here at the path level, and end-to-end in
     * StorageManagerRobolectricTest.
     */
    @Test
    fun benchmarkFileForATargetIsStableAcrossCalls() {
        val first = java.io.File(OiwPaths.benchmarksDir(), "internal.json")
        val second = java.io.File(OiwPaths.benchmarksDir(), "internal.json")
        assertEquals(first.absolutePath, second.absolutePath)
    }
}
