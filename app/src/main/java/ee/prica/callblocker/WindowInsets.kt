package ee.prica.callblocker

import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * From targetSdk 35 onwards Android draws apps edge to edge and ignores any request not
 * to, so content has to be inset by hand. Applied to a scrolling container with
 * `clipToPadding="false"`, content starts below the status bar but still scrolls
 * underneath it.
 *
 * Shared by every screen so they cannot drift apart.
 */
internal fun View.padForSystemBars() {
    setOnApplyWindowInsetsListener { view, windowInsets ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val systemBarInsets = windowInsets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            view.setPadding(
                systemBarInsets.left,
                systemBarInsets.top,
                systemBarInsets.right,
                systemBarInsets.bottom,
            )
        } else {
            @Suppress("DEPRECATION")
            view.setPadding(
                windowInsets.systemWindowInsetLeft,
                windowInsets.systemWindowInsetTop,
                windowInsets.systemWindowInsetRight,
                windowInsets.systemWindowInsetBottom,
            )
        }
        windowInsets
    }
}
