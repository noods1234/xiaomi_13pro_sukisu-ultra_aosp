package androidx.test.internal.runner.lifecycle;

import android.app.Activity;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitor;
import androidx.test.runner.lifecycle.Stage;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shim — see tools/robolectric_shim/README.md.
 *
 * Robolectric's RoboMonitoringInstrumentation calls signalLifecycleChange on every callActivityOn*.
 * Activities are held weakly so a test that leaks nothing here cannot leak an Activity through the
 * monitor either (the real implementation does the same).
 */
public final class ActivityLifecycleMonitorImpl implements ActivityLifecycleMonitor {

    private final List<WeakReference<ActivityLifecycleCallback>> callbacks =
        new CopyOnWriteArrayList<>();
    private final Map<Activity, Stage> stages = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void addLifecycleCallback(ActivityLifecycleCallback callback) {
        if (callback == null) return;
        for (WeakReference<ActivityLifecycleCallback> ref : callbacks) {
            if (ref.get() == callback) return;
        }
        callbacks.add(new WeakReference<>(callback));
    }

    @Override
    public void removeLifecycleCallback(ActivityLifecycleCallback callback) {
        for (WeakReference<ActivityLifecycleCallback> ref : callbacks) {
            ActivityLifecycleCallback existing = ref.get();
            if (existing == null || existing == callback) {
                callbacks.remove(ref);
            }
        }
    }

    @Override
    public Stage getLifecycleStageOf(Activity activity) {
        Stage stage = stages.get(activity);
        if (stage == null) {
            throw new IllegalArgumentException("Unknown activity: " + activity);
        }
        return stage;
    }

    @Override
    public Collection<Activity> getActivitiesInStage(Stage stage) {
        List<Activity> matching = new ArrayList<>();
        synchronized (stages) {
            for (Map.Entry<Activity, Stage> entry : stages.entrySet()) {
                if (entry.getValue() == stage) matching.add(entry.getKey());
            }
        }
        return matching;
    }

    public void signalLifecycleChange(Stage stage, Activity activity) {
        stages.put(activity, stage);
        for (WeakReference<ActivityLifecycleCallback> ref : callbacks) {
            ActivityLifecycleCallback callback = ref.get();
            if (callback == null) {
                callbacks.remove(ref);
            } else {
                callback.onActivityLifecycleChanged(activity, stage);
            }
        }
    }
}
