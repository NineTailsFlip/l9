package life.furrer.l9

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import life.furrer.nine.core.ui.dialogThemeResId

/** Shared by MainActivity's per-slot "Rename" menu item and AppPickerActivity's Browse-all one —
 *  both rename the same global, package-keyed [AppOverride], just from a different starting
 *  screen, so the dialog itself doesn't need to differ. [onSave] receives null for a blanked-out
 *  input (reverts to the app's own label), trimmed otherwise. */
fun Activity.showRenameDialog(currentLabel: String, onSave: (String?) -> Unit) {
    val builder = AlertDialog.Builder(this, dialogThemeResId())
    // Built from the dialog's own themed context (not the activity's) so the EditText's
    // cursor/underline picks up the dialog theme's white accent instead of the activity theme's
    // default one.
    val input = EditText(builder.context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_FILTER or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine()
        setText(currentLabel)
        setSelection(text.length)
    }
    builder
        .setTitle("Rename")
        .setView(input)
        .setPositiveButton("Save") { _, _ -> onSave(input.text.toString().trim().ifEmpty { null }) }
        .setNegativeButton("Cancel", null)
        .show()
}
