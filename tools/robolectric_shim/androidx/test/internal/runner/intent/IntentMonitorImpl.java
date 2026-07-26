package androidx.test.internal.runner.intent;

import android.content.Intent;
import androidx.test.runner.intent.IntentCallback;
import androidx.test.runner.intent.IntentMonitor;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shim — see tools/robolectric_shim/README.md. */
public final class IntentMonitorImpl implements IntentMonitor {

    private final List<WeakReference<IntentCallback>> callbacks = new CopyOnWriteArrayList<>();

    @Override
    public void addIntentCallback(IntentCallback callback) {
        if (callback == null) return;
        for (WeakReference<IntentCallback> ref : callbacks) {
            if (ref.get() == callback) return;
        }
        callbacks.add(new WeakReference<>(callback));
    }

    @Override
    public void removeIntentCallback(IntentCallback callback) {
        for (WeakReference<IntentCallback> ref : callbacks) {
            IntentCallback existing = ref.get();
            if (existing == null || existing == callback) {
                callbacks.remove(ref);
            }
        }
    }

    public void signalIntent(Intent intent) {
        for (WeakReference<IntentCallback> ref : callbacks) {
            IntentCallback callback = ref.get();
            if (callback == null) {
                callbacks.remove(ref);
            } else {
                callback.onIntentSent(intent);
            }
        }
    }
}
