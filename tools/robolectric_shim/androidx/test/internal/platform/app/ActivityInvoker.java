package androidx.test.internal.platform.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;

/** Shim — see tools/robolectric_shim/README.md. Implemented by Robolectric's LocalActivityInvoker. */
public interface ActivityInvoker {
    Intent getIntentForActivity(Class<? extends Activity> activityClass);
    void startActivity(Intent intent, Bundle activityOptions);
    void startActivity(Intent intent);
    void startActivityForResult(Intent intent, Bundle activityOptions);
    void startActivityForResult(Intent intent);
    Instrumentation.ActivityResult getActivityResult();
    void resumeActivity(Activity activity);
    void pauseActivity(Activity activity);
    void stopActivity(Activity activity);
    void recreateActivity(Activity activity);
    void finishActivity(Activity activity);
}
