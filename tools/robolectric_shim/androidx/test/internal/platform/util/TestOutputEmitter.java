package androidx.test.internal.platform.util;

/**
 * Shim — see tools/robolectric_shim/README.md.
 *
 * The real implementation writes artifacts to the instrumentation's output dir. Robolectric calls
 * dumpThreadStates only from diagnostic paths (e.g. an idling-resource timeout), so a no-op loses
 * a debugging aid, never a test result.
 */
public final class TestOutputEmitter {
    private TestOutputEmitter() {}
    public static void dumpThreadStates(String outputFilename) { /* no-op: diagnostics only */ }
}
