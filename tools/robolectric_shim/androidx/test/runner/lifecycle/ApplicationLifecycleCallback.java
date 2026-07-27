package androidx.test.runner.lifecycle;

import android.app.Application;

/** Shim — see tools/robolectric_shim/README.md. */
public interface ApplicationLifecycleCallback {
    void onApplicationLifecycleChanged(Application application, ApplicationStage stage);
}
