package ee.prica.callblocker

import android.app.Activity
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The list of recently blocked calls, on its own screen so the main screen stays a
 * settings screen. Read-only apart from clearing the list.
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
            val rowView = layoutInflater.inflate(R.layout.item_blocked_call, listContainer, false)
            rowView.findViewById<TextView>(R.id.blocked_number).text = blockedCall.number
            rowView.findViewById<TextView>(R.id.blocked_time).text =
                DateUtils.getRelativeTimeSpanString(
                    blockedCall.timeMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                )
            listContainer.addView(rowView)
        }
    }
}
