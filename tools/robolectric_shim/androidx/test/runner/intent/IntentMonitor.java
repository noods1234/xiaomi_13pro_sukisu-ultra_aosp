package androidx.test.runner.intent;

/** Shim — see tools/robolectric_shim/README.md. */
public interface IntentMonitor {
    void addIntentCallback(IntentCallback callback);
    void removeIntentCallback(IntentCallback callback);
}
