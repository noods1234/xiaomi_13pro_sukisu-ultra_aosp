package androidx.test.runner.lifecycle;

/** Shim — see tools/robolectric_shim/README.md. */
public final class ActivityLifecycleMonitorRegistry {

    private static volatile ActivityLifecycleMonitor instance;

    private ActivityLifecycleMonitorRegistry() {}

    public static ActivityLifecycleMonitor getInstance() {
        ActivityLifecycleMonitor current = instance;
        if (current == null) {
            throw new IllegalStateException("No lifecycle monitor registered!");
        }
        return current;
    }

    public static void registerInstance(ActivityLifecycleMonitor monitor) { instance = monitor; }
}
