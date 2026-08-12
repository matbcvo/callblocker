package ee.prica.callblocker

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The list of recently blocked calls, on its own screen so the main screen stays a
 * settings screen. Each row offers to call the number back or send it a message.
 */
class BlockedCallsActivity : Activity() {

    private lateinit var preferences: Preferences
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyText: TextView

    /** Drives whether the clear action is offered; there is nothing to clear when empty. */
    private var hasBlockedCalls = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_calls)
        findViewById<View>(R.id.root).padForSystemBars()
        actionBar?.setDisplayHomeAsUpEnabled(true)

        preferences = Preferences(this)
        listContainer = findViewById(R.id.history_container)
        emptyText = findViewById(R.id.history_empty_message)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.blocked_calls, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_clear_history)?.isVisible = hasBlockedCalls
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_clear_history -> {
                confirmClearHistory()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /** Clearing cannot be undone, so it asks first. */
    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setMessage(R.string.clear_history_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_clear_history) { _, _ ->
                preferences.clearBlockedCallHistory()
                render()
            }
            .show()
    }

    private fun render() {
        val blockedCalls = preferences.blockedCallHistory()
        listContainer.removeAllViews()

        hasBlockedCalls = blockedCalls.isNotEmpty()
        emptyText.visibility = if (hasBlockedCalls) View.GONE else View.VISIBLE
        invalidateOptionsMenu()

        for (blockedCall in blockedCalls) {
            listContainer.addView(createRow(blockedCall))
        }
    }

    private fun createRow(blockedCall: BlockedCall): View {
        val rowView = layoutInflater.inflate(R.layout.item_blocked_call, listContainer, false)

        // A withheld number is stored empty, so the label is localised here rather than
        // baked into the history when the call arrived.
        val hasNumber = blockedCall.number.isNotBlank()
        rowView.findViewById<TextView>(R.id.blocked_number).text =
            if (hasNumber) blockedCall.number else getString(R.string.unknown_number)
        rowView.findViewById<TextView>(R.id.blocked_time).text =
            DateUtils.getRelativeTimeSpanString(
                blockedCall.timeMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )

        val callButton = rowView.findViewById<ImageButton>(R.id.button_call_back)
        val messageButton = rowView.findViewById<ImageButton>(R.id.button_send_message)

        // Nothing to dial or text when the caller withheld their number.
        callButton.visibility = if (hasNumber) View.VISIBLE else View.GONE
        messageButton.visibility = if (hasNumber) View.VISIBLE else View.GONE

        if (hasNumber) {
            // ACTION_DIAL opens the dialer with the number filled in rather than placing
            // the call, which keeps this free of the CALL_PHONE permission.
            callButton.setOnClickListener {
                startExternally(Intent(Intent.ACTION_DIAL, phoneUri("tel", blockedCall.number)))
            }
            messageButton.setOnClickListener {
                startExternally(
                    Intent(Intent.ACTION_SENDTO, phoneUri("smsto", blockedCall.number))
                )
            }
        }
        return rowView
    }

    private fun phoneUri(scheme: String, number: String): Uri =
        Uri.fromParts(scheme, number, null)

    private fun startExternally(intent: Intent) {
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            // A device with no dialer or messaging app is unusual, but an unhandled
            // intent would take the activity down.
            Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_LONG).show()
        }
    }
}
