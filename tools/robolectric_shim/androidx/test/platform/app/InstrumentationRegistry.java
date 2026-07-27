package androidx.test.platform.app;

import android.app.Instrumentation;
import android.os.Bundle;

/**
 * Shim — see tools/robolectric_shim/README.md.
 *
 * Robolectric's AndroidTestEnvironment registers its RoboMonitoringInstrumentation here during
 * setUpApplicationState and clears it in clearEnvironment. Holding the instance in a static field
 * is the whole contract Robolectric relies on.
 */
public final class InstrumentationRegistry {

    private static volatile Instrumentation instrumentation;
    private static volatile Bundle arguments = new Bundle();

    private InstrumentationRegistry() {}

    public static Instrumentation getInstrumentation() {
        Instrumentation instance = instrumentation;
        if (instance == null) {
            throw new IllegalStateException(
                "No instrumentation registered! Must run under a registering instrumentation.");
        }
        return instance;
    }

    public static Bundle getArguments() {
        Bundle current = arguments;
        return current == null ? new Bundle() : new Bundle(current);
    }

    public static void registerInstance(Instrumentation instrumentation, Bundle arguments) {
        InstrumentationRegistry.instrumentation = instrumentation;
        InstrumentationRegistry.arguments = arguments == null ? new Bundle() : new Bundle(arguments);
    }
}
