package androidx.test.platform.ui;

/** Shim — see tools/robolectric_shim/README.md. */
public final class InjectEventSecurityException extends Exception {
    public InjectEventSecurityException(String message) { super(message); }
    public InjectEventSecurityException(Throwable cause) { super(cause); }
    public InjectEventSecurityException(String message, Throwable cause) { super(message, cause); }
}
