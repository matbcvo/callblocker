package ee.prica.callblocker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.View
import android.widget.Button
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
    private lateinit var clearButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_calls)
        findViewById<View>(R.id.root).padForSystemBars()
        actionBar?.setDisplayHomeAsUpEnabled(true)

        preferences = Preferences(this)
        listContainer = findViewById(R.id.history_container)
        emptyText = findViewById(R.id.history_empty_message)
        clearButton = findViewById(R.id.button_clear_history)

        clearButton.setOnClickListener {
            preferences.clearBlockedCallHistory()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun render() {
        val blockedCalls = preferences.blockedCallHistory()
        listContainer.removeAllViews()

        val isEmpty = blockedCalls.isEmpty()
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        clearButton.visibility = if (isEmpty) View.GONE else View.VISIBLE

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
