package life.furrer.l9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyRouterTest {

    @Test
    fun `dpad up and down`() {
        assertEquals(Action.Up, KeyRouter.map(KeyEvent.KEYCODE_DPAD_UP, ' '))
        assertEquals(Action.Down, KeyRouter.map(KeyEvent.KEYCODE_DPAD_DOWN, ' '))
    }

    @Test
    fun `dpad center and enter both mean select`() {
        assertEquals(Action.Select, KeyRouter.map(KeyEvent.KEYCODE_DPAD_CENTER, ' '))
        assertEquals(Action.Select, KeyRouter.map(KeyEvent.KEYCODE_ENTER, ' '))
    }

    @Test
    fun `dpad left and right are the configurable side shortcuts`() {
        assertEquals(Action.LeftKey, KeyRouter.map(KeyEvent.KEYCODE_DPAD_LEFT, ' '))
        assertEquals(Action.RightKey, KeyRouter.map(KeyEvent.KEYCODE_DPAD_RIGHT, ' '))
    }

    @Test
    fun `star toggles reorder mode`() {
        assertEquals(Action.ReorderToggle, KeyRouter.map(KeyEvent.KEYCODE_STAR, ' '))
    }

    @Test
    fun `0 means browse all, not digit 0`() {
        assertEquals(Action.BrowseAll, KeyRouter.map(KeyEvent.KEYCODE_0, '0'))
    }

    @Test
    fun `digits 1 through 9 map to their own value`() {
        val keycodesToChars = mapOf(
            KeyEvent.KEYCODE_1 to '1',
            KeyEvent.KEYCODE_2 to '2',
            KeyEvent.KEYCODE_3 to '3',
            KeyEvent.KEYCODE_4 to '4',
            KeyEvent.KEYCODE_5 to '5',
            KeyEvent.KEYCODE_6 to '6',
            KeyEvent.KEYCODE_7 to '7',
            KeyEvent.KEYCODE_8 to '8',
            KeyEvent.KEYCODE_9 to '9',
        )
        keycodesToChars.forEach { (keyCode, char) ->
            assertEquals(Action.Digit(char.digitToInt()), KeyRouter.map(keyCode, char))
        }
    }

    @Test
    fun `digit keycode with a non-digit mapped character yields no action`() {
        // Defensive case: an unusual keymap could map a "digit" keycode to a non-numeric
        // character. KeyRouter should not crash or guess — it should decline to act.
        assertNull(KeyRouter.map(KeyEvent.KEYCODE_5, 'x'))
    }

    @Test
    fun `unmapped keycodes yield no action`() {
        assertNull(KeyRouter.map(KeyEvent.KEYCODE_BACK, ' '))
        assertNull(KeyRouter.map(KeyEvent.KEYCODE_POUND, ' '))
        assertNull(KeyRouter.map(KeyEvent.KEYCODE_VOLUME_UP, ' '))
    }

}
