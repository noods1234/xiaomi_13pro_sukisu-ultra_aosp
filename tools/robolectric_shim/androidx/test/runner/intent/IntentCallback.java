package androidx.test.runner.intent;

import android.content.Intent;

/** Shim — see tools/robolectric_shim/README.md. */
public interface IntentCallback {
    void onIntentSent(Intent intent);
}
