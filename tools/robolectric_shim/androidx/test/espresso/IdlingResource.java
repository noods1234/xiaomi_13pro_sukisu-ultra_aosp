package androidx.test.espresso;

/** Shim — see tools/robolectric_shim/README.md. */
public interface IdlingResource {
    String getName();
    boolean isIdleNow();
    void registerIdleTransitionCallback(ResourceCallback callback);

    interface ResourceCallback {
        void onTransitionToIdle();
    }
}
