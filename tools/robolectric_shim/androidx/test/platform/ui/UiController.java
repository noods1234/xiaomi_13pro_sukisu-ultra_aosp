package androidx.test.platform.ui;

import android.view.KeyEvent;
import android.view.MotionEvent;

/** Shim — see tools/robolectric_shim/README.md. Implemented by Robolectric's LocalUiController. */
public interface UiController {
    boolean injectMotionEvent(MotionEvent event) throws InjectEventSecurityException;
    boolean injectKeyEvent(KeyEvent event) throws InjectEventSecurityException;
    boolean injectString(String str) throws InjectEventSecurityException;
    void loopMainThreadUntilIdle();
    void loopMainThreadForAtLeast(long millisDelay);
}
