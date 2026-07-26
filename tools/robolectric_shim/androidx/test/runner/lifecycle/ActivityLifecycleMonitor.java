package androidx.test.runner.lifecycle;

import android.app.Activity;
import java.util.Collection;

/** Shim — see tools/robolectric_shim/README.md. */
public interface ActivityLifecycleMonitor {
    void addLifecycleCallback(ActivityLifecycleCallback callback);
    void removeLifecycleCallback(ActivityLifecycleCallback callback);
    Stage getLifecycleStageOf(Activity activity);
    Collection<Activity> getActivitiesInStage(Stage stage);
}
