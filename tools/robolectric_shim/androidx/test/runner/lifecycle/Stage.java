package androidx.test.runner.lifecycle;

/** Shim — see tools/robolectric_shim/README.md. Order matters: Robolectric calls ordinal(). */
public enum Stage {
    PRE_ON_CREATE,
    CREATED,
    STARTED,
    RESUMED,
    PAUSED,
    STOPPED,
    RESTARTED,
    DESTROYED,
}
