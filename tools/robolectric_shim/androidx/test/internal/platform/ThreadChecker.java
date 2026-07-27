package androidx.test.internal.platform;

/** Shim — see tools/robolectric_shim/README.md. Implemented by RobolectricThreadChecker. */
public interface ThreadChecker {
    void checkMainThread();
    void checkNotMainThread();
}
