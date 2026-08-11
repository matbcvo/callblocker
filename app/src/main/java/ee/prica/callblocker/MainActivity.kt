package ee.prica.callblocker

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Shows whether blocking is actually live, lets the user fix whatever is missing, and
 * exposes the options. The list of blocked calls lives in [BlockedCallsActivity] so this
 * stays a settings screen.
 */
class MainActivity : Activity() {

    private lateinit var preferences: Preferences

    private lateinit var statusTitleText: TextView
    private lateinit var statusDetailText: TextView
    private lateinit var screeningRoleButton: Button
    private lateinit var contactsPermissionButton: Button
    private lateinit var blockingEnabledSwitch: Switch
    private lateinit var blockEveryCallSwitch: Switch
    private lateinit var silenceInsteadOfRejectSwitch: Switch
    private lateinit var keepInCallLogSwitch: Switch
    private lateinit var notifyWhenBlockedSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).padForSystemBars()
        preferences = Preferences(this)

        statusTitleText = findViewById(R.id.status_title)
        statusDetailText = findViewById(R.id.status_detail)
        screeningRoleButton = findViewById(R.id.button_screening_role)
        contactsPermissionButton = findViewById(R.id.button_contacts_permission)
        blockingEnabledSwitch = findViewById(R.id.switch_blocking_enabled)
        blockEveryCallSwitch = findViewById(R.id.switch_block_every_call)
        silenceInsteadOfRejectSwitch = findViewById(R.id.switch_silence_instead_of_reject)
        keepInCallLogSwitch = findViewById(R.id.switch_keep_in_call_log)
        notifyWhenBlockedSwitch = findViewById(R.id.switch_notify_when_blocked)

        screeningRoleButton.setOnClickListener { requestCallScreeningRole() }
        contactsPermissionButton.setOnClickListener { requestContactsPermission() }
        findViewById<Button>(R.id.button_view_blocked_calls).setOnClickListener {
            startActivity(Intent(this, BlockedCallsActivity::class.java))
        }

        findViewById<TextView>(R.id.version_text).text =
            getString(R.string.about_version, applicationVersionName())
        findViewById<Button>(R.id.button_view_source).setOnClickListener {
            openLink(getString(R.string.url_source))
        }
        findViewById<Button>(R.id.button_privacy_policy).setOnClickListener {
            openLink(getString(R.string.url_privacy))
        }

        blockingEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.blockingEnabled = isChecked
            renderStatus()
        }
        blockEveryCallSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.blockEveryIncomingCall = isChecked
            renderStatus()
        }
        silenceInsteadOfRejectSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.silenceInsteadOfReject = isChecked
            renderOptions()
        }
        keepInCallLogSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.keepBlockedCallsInCallLog = isChecked
        }
        notifyWhenBlockedSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.notifyWhenCallBlocked = isChecked
            if (isChecked) requestNotificationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        blockingEnabledSwitch.isChecked = preferences.blockingEnabled
        blockEveryCallSwitch.isChecked = preferences.blockEveryIncomingCall
        silenceInsteadOfRejectSwitch.isChecked = preferences.silenceInsteadOfReject
        keepInCallLogSwitch.isChecked = preferences.keepBlockedCallsInCallLog
        notifyWhenBlockedSwitch.isChecked = preferences.notifyWhenCallBlocked
        renderStatus()
        renderOptions()
    }


    // --- setup -------------------------------------------------------------------

    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, R.string.role_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(
            roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
            REQUEST_CODE_CALL_SCREENING_ROLE,
        )
    }

    private fun requestContactsPermission() =
        requestPermissions(
            arrayOf(Manifest.permission.READ_CONTACTS),
            REQUEST_CODE_READ_CONTACTS,
        )

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS,
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CALL_SCREENING_ROLE && resultCode != RESULT_OK) {
            Toast.makeText(this, R.string.role_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_CONTACTS &&
            grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.contacts_denied, Toast.LENGTH_LONG).show()
        }
        renderStatus()
    }

    private fun isPermissionGranted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // --- about -------------------------------------------------------------------

    private fun applicationVersionName(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return packageInfo.versionName.orEmpty()
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: ActivityNotFoundException) {
            // A device with no browser is unusual but not impossible; better a toast
            // than a crash from an unhandled intent.
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
        }
    }

    // --- rendering ---------------------------------------------------------------

    private fun renderStatus() {
        val hasCallScreeningRole = CallBlockerService.isCallScreeningRoleHeld(this)
        val hasContactsPermission = isPermissionGranted(Manifest.permission.READ_CONTACTS)

        // Contacts are only consulted when somebody is meant to get through, so when
        // everything is blocked the permission is neither needed nor worth nagging for.
        val needsContacts = !preferences.blockEveryIncomingCall

        screeningRoleButton.visibility = if (hasCallScreeningRole) View.GONE else View.VISIBLE
        contactsPermissionButton.visibility =
            if (hasContactsPermission || !needsContacts) View.GONE else View.VISIBLE

        val (statusTitleRes, statusDetailRes) = when {
            !hasCallScreeningRole ->
                R.string.status_inactive to R.string.status_inactive_detail
            !preferences.blockingEnabled ->
                R.string.status_paused to R.string.status_paused_detail
            preferences.blockEveryIncomingCall ->
                R.string.status_blocking_everything to
                    R.string.status_blocking_everything_detail
            !hasContactsPermission ->
                R.string.status_no_contacts to R.string.status_no_contacts_detail
            else ->
                R.string.status_active to R.string.status_active_detail
        }
        statusTitleText.setText(statusTitleRes)
        statusDetailText.setText(statusDetailRes)
    }

    private fun renderOptions() {
        // Until Android routes calls to this app, none of these options do anything.
        // Leaving them usable would suggest the app is configured when it is not.
        val hasCallScreeningRole = CallBlockerService.isCallScreeningRoleHeld(this)

        // The call-log and notification options only apply to rejected calls; a silenced
        // call is handled by the system dialer and always logged.
        val isRejectingCalls = !preferences.silenceInsteadOfReject

        blockingEnabledSwitch.isEnabled = hasCallScreeningRole
        blockEveryCallSwitch.isEnabled = hasCallScreeningRole
        silenceInsteadOfRejectSwitch.isEnabled = hasCallScreeningRole
        keepInCallLogSwitch.isEnabled = hasCallScreeningRole && isRejectingCalls
        notifyWhenBlockedSwitch.isEnabled = hasCallScreeningRole && isRejectingCalls
    }


    private companion object {
        const val REQUEST_CODE_CALL_SCREENING_ROLE = 1
        const val REQUEST_CODE_READ_CONTACTS = 2
        const val REQUEST_CODE_POST_NOTIFICATIONS = 3
    }
}
