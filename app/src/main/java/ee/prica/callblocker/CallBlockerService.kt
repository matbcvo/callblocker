package ee.prica.callblocker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Rejects every incoming call whose number is not in the user's contacts.
 *
 * The framework binds this service for each incoming call once the app holds
 * [RoleManager.ROLE_CALL_SCREENING], and expects a response within a few seconds. The
 * contact lookup therefore runs synchronously on the main thread — `PhoneLookup` is an
 * indexed query and returns in single-digit milliseconds.
 *
 * Anything unexpected (missing permission, provider failure) allows the call through.
 * Failing open means a revoked permission degrades to "no blocking" rather than
 * "every call is swallowed".
 */
class CallBlockerService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val allowCall = try {
            shouldAllowCall(callDetails)
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Screening failed, allowing call", error)
            true
        }

        if (allowCall) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val preferences = Preferences(this)
        val incomingNumber = callDetails.handle?.schemeSpecificPart
        val callResponse = if (preferences.silenceInsteadOfReject) {
            // Let the call arrive, but keep the phone quiet. It rings out to voicemail
            // instead of giving the caller a busy tone.
            CallResponse.Builder()
                .setSilenceCall(true)
                .build()
        } else {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                // These two are only honoured when the call is disallowed.
                .setSkipCallLog(!preferences.keepBlockedCallsInCallLog)
                .setSkipNotification(true)
                .build()
        }
        respondToCall(callDetails, callResponse)

        preferences.addBlockedCallToHistory(
            BlockedCall(
                timeMillis = callDetails.creationTimeMillis,
                // Stored empty rather than as translated text: the history is data, and
                // a withheld number has to stay distinguishable from a real one so the
                // list knows whether calling back is even possible.
                number = incomingNumber.orEmpty(),
            )
        )
        if (preferences.notifyWhenCallBlocked) {
            notifyCallBlocked(incomingNumber)
        }
    }

    private fun shouldAllowCall(callDetails: Call.Details): Boolean {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return true

        val preferences = Preferences(this)
        if (!preferences.blockingEnabled) return true

        // Checked before the contacts lookup, so this mode keeps working even if
        // contacts permission is missing — there is nothing to look up.
        if (preferences.blockEveryIncomingCall) return false

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(LOG_TAG, "READ_CONTACTS not granted, allowing call")
            return true
        }

        // A withheld / payphone / unknown-presentation call has no number to match,
        // so it can never be a contact.
        val incomingNumber = callDetails.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
            ?: return false

        return isNumberInContacts(incomingNumber)
    }

    /**
     * `PhoneLookup` does the hard part for us: it matches on the normalised form of the
     * number, so +372 5123 4567 in contacts still matches an incoming 55123456-style
     * national number.
     */
    private fun isNumberInContacts(phoneNumber: String): Boolean {
        val phoneLookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        contentResolver.query(
            phoneLookupUri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null,
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun notifyCallBlocked(blockedNumber: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_blocked_calls),
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        val openApplicationIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_blocked)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(blockedNumber ?: getString(R.string.unknown_number))
            .setContentIntent(openApplicationIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        // A distinct id per number keeps repeat callers from stacking up endlessly
        // while still showing separate entries for separate callers.
        notificationManager.notify(blockedNumber.hashCode(), notification)
    }

    companion object {
        private const val LOG_TAG = "CallBlocker"
        private const val NOTIFICATION_CHANNEL_ID = "blocked_calls"

        /** True when this app currently holds the call-screening role. */
        fun isCallScreeningRoleHeld(context: Context): Boolean {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
            return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
    }
}
