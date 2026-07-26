package androidx.core.app
object ActivityCompat {
    @JvmStatic fun requestPermissions(activity: android.app.Activity, permissions: Array<out String>, requestCode: Int) {}
    @JvmStatic fun checkSelfPermission(context: android.content.Context, permission: String): Int = 0
}
