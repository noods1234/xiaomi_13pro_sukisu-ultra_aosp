package androidx.test.runner.intent;

import android.app.Instrumentation;
import android.content.Intent;

/** Shim — see tools/robolectric_shim/README.md. */
public interface IntentStubber {
    Instrumentation.ActivityResult getActivityResultForIntent(Intent intent);
}
