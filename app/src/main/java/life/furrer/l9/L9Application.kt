package life.furrer.l9

import android.app.Application
import life.furrer.nine.core.KeyHelp
import life.furrer.nine.core.Nine
import life.furrer.nine.core.NineApp
import life.furrer.nine.core.NineSurface

class L9Application : Application() {
    override fun onCreate() {
        super.onCreate()
        Nine.init(l9App)
    }
}

private val l9App = NineApp(
    id = "l9",
    name = "L9",
    tagline = "Launcher on 9 keys",
    hasHintBar = false,
    settingsActivity = SettingsActivity::class.java,
    surfaces = listOf(
        NineSurface(
            id = "",
            name = "L9",
            introText = """
                L9 ("Launcher on 9 keys", an analogue to T9 "Text on 9 keys") is a minimal, all-black
                home launcher built for keypad phones — a D-pad and a numeric keypad, no touchscreen
                assumed.

                The home screen shows a fixed list of app slots, however many you configure in Settings.
                Move the selection with Up/Down, press OK to launch. Each number key 1-9 jumps straight
                to that slot and launches it immediately. Press 0 to browse every installed app instead
                of just the configured slots.

                Left and Right are a second pair of one-tap shortcuts to apps of your choice, independent
                of the numbered slots. You can also assign a contact to any digit for speed dial: hold
                the key down and, after a short warning, it places the call.

                The "*" key is a command prefix for the less common actions — reordering slots, renaming
                a slot, reassigning a slot's app, and jumping into Settings. See "Key mapping" for the
                full list of what every key does.
            """.trimIndent(),
            keyMap = listOf(
                KeyHelp("Up / Down", "Move the selection."),
                KeyHelp("OK", "Launch the selected slot's app."),
                KeyHelp("1-9", "Jump to and launch that slot directly."),
                KeyHelp("0", "Browse every installed app."),
                KeyHelp("Left / Right", "Launch the configured shortcut app."),
                KeyHelp("Hold Left / Right", "Reassign that shortcut's app."),
                KeyHelp("Hold a digit\nwith speed dial set", "Call the assigned contact after a warning."),
                KeyHelp("*", "Does nothing on its own — see below."),
                KeyHelp(
                    "* then #",
                    "Toggle reorder mode. Up/Down swaps the selected slot with its neighbor; " +
                        "* or Back drops it back in place.",
                ),
                KeyHelp("* then 0", "(Re)assign the app for the currently selected slot."),
                KeyHelp("* twice", "Rename the currently selected slot."),
                KeyHelp("Hold *", "Open Settings."),
                KeyHelp("Back", "Cancel the current screen, or exit reorder mode."),
            ),
        ),
    ),
)
