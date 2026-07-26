package androidx.test.runner.lifecycle;

/** Shim — see tools/robolectric_shim/README.md. */
public interface ApplicationLifecycleMonitor {
    void addLifecycleCallback(ApplicationLifecycleCallback callback);
    void removeLifecycleCallback(ApplicationLifecycleCallback callback);
}
