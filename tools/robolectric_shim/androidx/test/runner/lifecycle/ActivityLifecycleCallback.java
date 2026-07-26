package androidx.test.runner.lifecycle;

import android.app.Activity;

/** Shim — see tools/robolectric_shim/README.md. */
public interface ActivityLifecycleCallback {
    void onActivityLifecycleChanged(Activity activity, Stage stage);
}
