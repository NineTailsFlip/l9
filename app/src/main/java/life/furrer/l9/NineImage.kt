package life.furrer.l9

/** What the thor2 image (android_vendor_thor2) puts around L9. Its ro.config.* properties are
 *  readable by every app; anywhere else they're empty and L9 is on its own. */
object NineImage {
    /** Settings' status bar, always shown across the top. It has the time, the battery and new
     *  notifications already, so home doesn't repeat them under it. */
    fun hasStatusBar(): Boolean = property("ro.config.thor2_statusbar").isNotEmpty()

    private fun property(key: String): String = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as String
    }.getOrDefault("")
}
