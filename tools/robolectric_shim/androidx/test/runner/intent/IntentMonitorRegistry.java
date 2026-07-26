package androidx.test.runner.intent;

/** Shim — see tools/robolectric_shim/README.md. */
public final class IntentMonitorRegistry {

    private static volatile IntentMonitor instance;

    private IntentMonitorRegistry() {}

    public static IntentMonitor getInstance() {
        IntentMonitor current = instance;
        if (current == null) {
            throw new IllegalStateException("No intent monitor registered!");
        }
        return current;
    }

    public static void registerInstance(IntentMonitor monitor) { instance = monitor; }
}
