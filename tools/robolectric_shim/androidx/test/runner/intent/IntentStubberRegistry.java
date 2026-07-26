package androidx.test.runner.intent;

/**
 * Shim — see tools/robolectric_shim/README.md.
 *
 * Robolectric asks isLoaded() before consulting a stubber; nothing in this project installs one, so
 * it stays false and startActivity takes its normal (unstubbed) path.
 */
public final class IntentStubberRegistry {

    private static volatile IntentStubber instance;

    private IntentStubberRegistry() {}

    public static IntentStubber getInstance() {
        IntentStubber current = instance;
        if (current == null) {
            throw new IllegalStateException("No intent stubber registered!");
        }
        return current;
    }

    public static boolean isLoaded() { return instance != null; }

    public static void load(IntentStubber stubber) {
        if (stubber == null) throw new NullPointerException("stubber cannot be null");
        instance = stubber;
    }

    public static void reset() { instance = null; }
}
