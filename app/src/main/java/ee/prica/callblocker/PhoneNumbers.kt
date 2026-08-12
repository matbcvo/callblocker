package ee.prica.callblocker

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Formats a raw number for display — `+37251234567` becomes `+372 5123 4567`.
 *
 * Grouping is country-specific, so the number is formatted against the network's country
 * when there is one, falling back to the device locale on a phone with no SIM. Numbers
 * already in international form carry their own country code and format correctly
 * regardless.
 *
 * `formatNumber` returns null for anything it cannot parse — short codes, service
 * numbers, malformed input — in which case the raw string is shown unchanged.
 *
 * Display only. Dialling, messaging and copying all use the stored number, which is the
 * canonical value and always dialable.
 */
internal fun Context.formatPhoneNumberForDisplay(number: String): String {
    if (number.isBlank()) return number
    val countryIso = getSystemService(TelephonyManager::class.java)
        ?.networkCountryIso
        ?.takeIf { it.isNotBlank() }
        ?.uppercase(Locale.US)
        ?: Locale.getDefault().country
    return PhoneNumberUtils.formatNumber(number, countryIso) ?: number
}
