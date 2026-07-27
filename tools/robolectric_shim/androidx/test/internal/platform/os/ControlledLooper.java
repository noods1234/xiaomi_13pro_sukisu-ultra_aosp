package androidx.test.internal.platform.os;

import android.view.View;

/** Shim — see tools/robolectric_shim/README.md. Implemented by LocalControlledLooper. */
public interface ControlledLooper {
    void drainMainThreadUntilIdle();
    void simulateWindowFocus(View decorView);
}
