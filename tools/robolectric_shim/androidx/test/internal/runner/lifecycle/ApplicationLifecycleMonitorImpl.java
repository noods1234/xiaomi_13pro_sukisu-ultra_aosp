package androidx.test.internal.runner.lifecycle;

import android.app.Application;
import androidx.test.runner.lifecycle.ApplicationLifecycleCallback;
import androidx.test.runner.lifecycle.ApplicationLifecycleMonitor;
import androidx.test.runner.lifecycle.ApplicationStage;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shim — see tools/robolectric_shim/README.md. */
public final class ApplicationLifecycleMonitorImpl implements ApplicationLifecycleMonitor {

    private final List<WeakReference<ApplicationLifecycleCallback>> callbacks =
        new CopyOnWriteArrayList<>();

    @Override
    public void addLifecycleCallback(ApplicationLifecycleCallback callback) {
        if (callback == null) return;
        for (WeakReference<ApplicationLifecycleCallback> ref : callbacks) {
            if (ref.get() == callback) return;
        }
        callbacks.add(new WeakReference<>(callback));
    }

    @Override
    public void removeLifecycleCallback(ApplicationLifecycleCallback callback) {
        for (WeakReference<ApplicationLifecycleCallback> ref : callbacks) {
            ApplicationLifecycleCallback existing = ref.get();
            if (existing == null || existing == callback) {
                callbacks.remove(ref);
            }
        }
    }

    public void signalLifecycleChange(Application application, ApplicationStage stage) {
        for (WeakReference<ApplicationLifecycleCallback> ref : callbacks) {
            ApplicationLifecycleCallback callback = ref.get();
            if (callback == null) {
                callbacks.remove(ref);
            } else {
                callback.onApplicationLifecycleChanged(application, stage);
            }
        }
    }
}
