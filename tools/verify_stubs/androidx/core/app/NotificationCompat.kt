package androidx.core.app
object NotificationCompat {
    const val CATEGORY_SERVICE = "service"
    class Builder(context: android.content.Context, channelId: String) {
        fun setContentTitle(t: CharSequence?): Builder = this
        fun setContentText(t: CharSequence?): Builder = this
        fun setSmallIcon(icon: Int): Builder = this
        fun setOngoing(o: Boolean): Builder = this
        fun setAutoCancel(a: Boolean): Builder = this
        fun setCategory(c: String?): Builder = this
        fun setContentIntent(i: android.app.PendingIntent?): Builder = this
        fun build(): android.app.Notification = android.app.Notification()
    }
}
