package androidx.test.internal.platform.content;

/** Shim — see tools/robolectric_shim/README.md. Implemented by LocalPermissionGranter. */
public interface PermissionGranter {
    void addPermissions(String... permissions);
    void requestPermissions();
}
