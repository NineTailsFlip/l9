package life.furrer.l9

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.NineActivity
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.dp

/** Renaming an app, as a screen of its own in the suite's look rather than an AlertDialog with a
 *  field: the name on top, the field, the hint bar. Shared by home's per-slot "Rename" and Browse
 *  all's — both rename the same global, package-keyed [AppOverride] ([AppRepository.setCustomLabel]).
 *
 *  OK saves; a name left empty goes back to the app's own. Back deletes a character, or with
 *  nothing typed leaves without saving. Both keys are taken ahead of the T9 IME ([PromptField]),
 *  which would otherwise turn OK into a space. */
class RenameActivity : NineActivity() {

    private lateinit var field: PromptField
    private lateinit var hintBar: HintBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val activityName = intent.getStringExtra(EXTRA_ACTIVITY).orEmpty()
        val currentLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty()

        val title = TextView(this).apply {
            text = "Rename"
            textSize = 22f
            setTextColor(Palette.WHITE)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(16), dp(Dimens.SIDE_PADDING_DP), dp(4))
        }
        val prompt = TextView(this).apply {
            text = "A name for $currentLabel on this phone. Empty goes back to the app's own."
            textSize = 15f
            setTextColor(Palette.GRAY_DIM)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(8), dp(Dimens.SIDE_PADDING_DP), dp(8))
        }
        field = PromptField(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 20f
            setTextColor(Palette.WHITE)
            setBackgroundColor(Palette.BLACK)
            setSingleLine()
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(6), dp(Dimens.SIDE_PADDING_DP), dp(6))
            setText(currentLabel)
            setSelection(text.length)
            onSubmit = {
                val name = text.toString().trim().ifEmpty { null }
                AppRepository.setCustomLabel(this@RenameActivity, packageName, activityName, name)
                setResult(Activity.RESULT_OK)
                finish()
            }
            onBackWhenEmpty = { finish() }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = updateHint()
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        val rule = View(this).apply { setBackgroundColor(Palette.DIVIDER) }
        hintBar = HintBar(this)

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(prompt)
                addView(field)
                addView(rule, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
                addView(View(this@RenameActivity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(hintBar)
            },
        )
        field.requestFocus()
        updateHint()
    }

    private fun updateHint() {
        hintBar.setText(if (field.text.isEmpty()) "ok app's own name · ‹ cancel" else "ok save · ‹ delete")
    }

    /** With the field focused the IME sees keys first; this is for the ones it lets through. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        field.handleOwnKey(event) || super.dispatchKeyEvent(event)

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_ACTIVITY = "activity"
        private const val EXTRA_LABEL = "label"

        fun intentFor(context: Context, entry: AppEntry): Intent =
            Intent(context, RenameActivity::class.java)
                .putExtra(EXTRA_PACKAGE, entry.packageName)
                .putExtra(EXTRA_ACTIVITY, entry.activityName)
                .putExtra(EXTRA_LABEL, entry.displayLabel)
    }
}

/** An EditText that sees OK and Back *before* the input method does — [onKeyPreIme] is the one
 *  hook Android calls ahead of it — so OK saves instead of typing a space (as TG9's sign-in found
 *  on a keypad phone), and Back on an empty field leaves. Both act on the key's UP, so the DOWN
 *  that started them never lands on the screen behind. */
class PromptField(context: Context) : EditText(context) {
    var onSubmit: () -> Unit = {}
    var onBackWhenEmpty: () -> Unit = {}

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean =
        handleOwnKey(event) || super.onKeyPreIme(keyCode, event)

    fun handleOwnKey(event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onSubmit()
            true
        }
        KeyEvent.KEYCODE_BACK -> if (text.isEmpty()) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackWhenEmpty()
            true
        } else {
            // With text, Back is a backspace, done here ahead of the IME so one press deletes
            // exactly one character whatever the IME would have done (holding it repeats).
            if (event.action == KeyEvent.ACTION_DOWN) text.delete(text.length - 1, text.length)
            true
        }
        else -> false
    }
}
