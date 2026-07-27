package androidx.test.runner.lifecycle;

/** Shim — see tools/robolectric_shim/README.md. */
public final class ApplicationLifecycleMonitorRegistry {

    private static volatile ApplicationLifecycleMonitor instance;

    private ApplicationLifecycleMonitorRegistry() {}

    public static ApplicationLifecycleMonitor getInstance() {
        ApplicationLifecycleMonitor current = instance;
        if (current == null) {
            throw new IllegalStateException("No application lifecycle monitor registered!");
        }
        return current;
    }

    public static void registerInstance(ApplicationLifecycleMonitor monitor) { instance = monitor; }
}
