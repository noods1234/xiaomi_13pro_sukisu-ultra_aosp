package androidx.test.espresso;

import android.os.Looper;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Shim — see tools/robolectric_shim/README.md.
 *
 * Robolectric's LocalUiController reads getResources()/getLoopers() when looping the main thread to
 * idle. Nothing in this project registers an idling resource, so both collections stay empty and
 * LocalUiController takes its "no idling resources" path — the same path a real empty registry gives.
 */
public final class IdlingRegistry {

    private static final IdlingRegistry INSTANCE = new IdlingRegistry();

    private final Set<IdlingResource> resources = new CopyOnWriteArraySet<>();
    private final Set<Looper> loopers = new CopyOnWriteArraySet<>();

    private IdlingRegistry() {}

    public static IdlingRegistry getInstance() { return INSTANCE; }

    public Collection<IdlingResource> getResources() {
        return Collections.unmodifiableSet(resources);
    }

    public Collection<Looper> getLoopers() {
        return Collections.unmodifiableSet(loopers);
    }

    public boolean register(IdlingResource... toRegister) {
        return resources.addAll(java.util.Arrays.asList(toRegister));
    }

    public boolean unregister(IdlingResource... toUnregister) {
        return resources.removeAll(java.util.Arrays.asList(toUnregister));
    }

    public boolean registerLooperAsIdlingResource(Looper looper) { return loopers.add(looper); }

    public boolean unregisterLooperAsIdlingResource(Looper looper) { return loopers.remove(looper); }
}
