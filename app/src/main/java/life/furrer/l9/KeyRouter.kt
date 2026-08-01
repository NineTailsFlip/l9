package life.furrer.l9

import android.view.KeyEvent

sealed interface Action {
    data object Up : Action
    data object Down : Action
    data object Select : Action
    data object BrowseAll : Action
    data object LeftKey : Action
    data object RightKey : Action
    data object ReorderToggle : Action
    data class Digit(val n: Int) : Action
}

object KeyRouter {
    /** [number] is the key's mapped character (KeyEvent.getNumber()) — passed in rather than
     *  a KeyEvent so this stays a pure function testable without any Android dependency. */
    fun map(keyCode: Int, number: Char): Action? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> Action.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> Action.Down
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> Action.Select
        KeyEvent.KEYCODE_DPAD_LEFT -> Action.LeftKey
        KeyEvent.KEYCODE_DPAD_RIGHT -> Action.RightKey
        KeyEvent.KEYCODE_STAR -> Action.ReorderToggle
        KeyEvent.KEYCODE_0 -> Action.BrowseAll
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
            val digit = number.digitToIntOrNull()
            if (digit == null) null else Action.Digit(digit)
        }
        else -> null
    }
}
