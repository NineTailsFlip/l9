package life.furrer.l9

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import life.furrer.nine.core.ui.NineActivity
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.util.KeyRouter
import life.furrer.nine.core.util.NineAction

private const val ROW_SIDE_PADDING = 28
private const val REQUEST_CALL_PERMISSION = 200
private const val REQUEST_PICK_CONTACT = 201

class SpeedDialSettingsActivity : NineActivity() {

    private val digits = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    private var contacts: Map<Int, SpeedDialContact> = emptyMap()
    private var selectedIndex = 0
    private var okLongPressHandled = false
    private var pendingOkKeyCode: Int? = null
    private lateinit var rowContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private val rows = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply {
            addView(rowContainer)
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        setContentView(scrollView)

        digits.forEach { _ ->
            val row = TextView(this).apply {
                textSize = 26f
                setPadding(ROW_SIDE_PADDING, 26, ROW_SIDE_PADDING, 26)
            }
            rows.add(row)
            rowContainer.addView(row)
        }

        if (!SpeedDialRepository.hasCallPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PERMISSION)
        }
    }

    override fun onResume() {
        super.onResume()
        contacts = SpeedDialRepository.loadContacts(this)
        render()
    }

    override fun onPause() {
        super.onPause()
        // The OK press that opened this screen (from SettingsActivity) can have its matching
        // key-up delivered here once focus returns, since SettingsActivity's key-down handler
        // starts this activity synchronously before the physical key is released. Drop any
        // in-flight tracking so a stray key-up on resume can't be mistaken for a real press.
        pendingOkKeyCode = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PERMISSION && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Call permission denied — speed dial calls won't work", Toast.LENGTH_LONG).show()
        }
    }

    private fun render() {
        digits.forEachIndexed { index, digit ->
            val row = rows[index]
            val contact = contacts[digit]
            row.text = "$digit: ${contact?.name ?: "not set"}"
            val selected = index == selectedIndex
            row.setTextColor(if (selected) Color.WHITE else Palette.GRAY_DIM)
            row.paintFlags = if (selected) {
                row.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                row.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
        }
        rows.getOrNull(selectedIndex)?.let { row ->
            scrollView.post { scrollView.smoothScrollTo(0, row.top) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking()
            pendingOkKeyCode = keyCode
            return true
        }
        when (KeyRouter.map(keyCode, event.number)) {
            NineAction.Up -> {
                selectedIndex = (selectedIndex - 1 + rows.size) % rows.size
                render()
                return true
            }
            NineAction.Down -> {
                selectedIndex = (selectedIndex + 1) % rows.size
                render()
                return true
            }
            else -> {}
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // Only act if this activity actually witnessed the matching key-down — see onPause.
            if (pendingOkKeyCode == keyCode && !okLongPressHandled) {
                pickContact()
            }
            okLongPressHandled = false
            pendingOkKeyCode = null
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            okLongPressHandled = true
            clearContact()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private fun clearContact() {
        val digit = digits[selectedIndex]
        SpeedDialRepository.setContact(this, digit, null)
        contacts = SpeedDialRepository.loadContacts(this)
        render()
        Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
    }

    private fun pickContact() {
        startActivityForResult(
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
            REQUEST_PICK_CONTACT,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_PICK_CONTACT || resultCode != RESULT_OK || uri == null) return

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val number = numberIndex.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: return@use
            val name = nameIndex.takeIf { it >= 0 }?.let { cursor.getString(it) } ?: number

            val digit = digits[selectedIndex]
            SpeedDialRepository.setContact(this, digit, SpeedDialContact(name, number))
            contacts = SpeedDialRepository.loadContacts(this)
            render()
        }
    }
}
