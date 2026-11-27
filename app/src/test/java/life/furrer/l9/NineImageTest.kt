package life.furrer.l9

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemProperties

@RunWith(RobolectricTestRunner::class)
class NineImageTest {
    @Test
    fun `the status bar is there only on the thor2 image`() {
        assertFalse(NineImage.hasStatusBar())
        ShadowSystemProperties.override("ro.config.thor2_statusbar", "io.github.ninetailsflip.settings/.StatusBarService")
        assertTrue(NineImage.hasStatusBar())
    }
}
