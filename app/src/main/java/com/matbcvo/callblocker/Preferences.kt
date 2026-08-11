package com.matbcvo.callblocker

import android.content.Context
import android.content.SharedPreferences

/**
 * All persisted state. Backed by a single SharedPreferences file so the app needs no
 * database and no background initialisation.
 */
class Preferences(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("call_blocker", Context.MODE_PRIVATE)

    /** Master switch. When off, every call is allowed through untouched. */
    var blockingEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_BLOCKING_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_BLOCKING_ENABLED, value).apply()

    /**
     * When true a blocked call is silenced and left to ring out (caller hears normal
     * ringing, then voicemail). When false it is rejected immediately, which most
     * networks present to the caller as busy.
     */
    var silenceInsteadOfReject: Boolean
        get() = sharedPreferences.getBoolean(KEY_SILENCE_INSTEAD_OF_REJECT, false)
        set(value) =
            sharedPreferences.edit().putBoolean(KEY_SILENCE_INSTEAD_OF_REJECT, value).apply()

    /** Keep blocked calls visible in the system call log. */
    var keepBlockedCallsInCallLog: Boolean
        get() = sharedPreferences.getBoolean(KEY_KEEP_IN_CALL_LOG, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_KEEP_IN_CALL_LOG, value).apply()

    /** Post a notification each time a call is blocked. */
    var notifyWhenCallBlocked: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTIFY_WHEN_BLOCKED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_NOTIFY_WHEN_BLOCKED, value).apply()

    /**
     * Blocked-call history, newest first, capped at [MAXIMUM_HISTORY_ENTRIES] entries.
     * Stored as newline-separated "epochMillis|number" records — cheaper than JSON
     * for a list this small.
     */
    fun blockedCallHistory(): List<BlockedCall> =
        sharedPreferences.getString(KEY_BLOCKED_CALL_HISTORY, "")
            .orEmpty()
            .lineSequence()
            .filter { line -> line.isNotBlank() }
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('|')
                if (separatorIndex <= 0) return@mapNotNull null
                val timeMillis = line.substring(0, separatorIndex).toLongOrNull()
                    ?: return@mapNotNull null
                BlockedCall(timeMillis, line.substring(separatorIndex + 1))
            }
            .toList()

    fun addBlockedCallToHistory(blockedCall: BlockedCall) {
        val trimmedHistory =
            (listOf(blockedCall) + blockedCallHistory()).take(MAXIMUM_HISTORY_ENTRIES)
        sharedPreferences.edit()
            .putString(
                KEY_BLOCKED_CALL_HISTORY,
                trimmedHistory.joinToString("\n") { entry -> "${entry.timeMillis}|${entry.number}" },
            )
            .apply()
    }

    fun clearBlockedCallHistory() =
        sharedPreferences.edit().remove(KEY_BLOCKED_CALL_HISTORY).apply()

    private companion object {
        const val KEY_BLOCKING_ENABLED = "enabled"
        const val KEY_SILENCE_INSTEAD_OF_REJECT = "silence_instead_of_reject"
        const val KEY_KEEP_IN_CALL_LOG = "keep_in_call_log"
        const val KEY_NOTIFY_WHEN_BLOCKED = "notify_on_block"
        const val KEY_BLOCKED_CALL_HISTORY = "history"
        const val MAXIMUM_HISTORY_ENTRIES = 100
    }
}

data class BlockedCall(val timeMillis: Long, val number: String)
