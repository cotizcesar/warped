package com.warped.ui.chat.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.warped.R

/**
 * Phase 58 (OG-02, T-58-08): single guarded ACTION_VIEW gate shared by the
 * OG card open-icon, the Fuentes rows, and the preview-sheet browser
 * button. Extracted from MessageBubble so the http/https allowlist and the
 * dual catch cannot drift across three copies.
 *
 * The intent carries the fetcher-resolved URL only — never raw pasted text,
 * never extracted text (T-53-09/T-53-10). WR-01: stored resolved URLs are
 * untrusted text, hence the scheme allowlist mirroring the fetcher
 * redirect gate. T-53-11: bare emulators without a browser must not crash
 * chat (ActivityNotFoundException); OEM exported-activity enforcement can
 * throw SecurityException from the same tap handler.
 *
 * @return true when the browser intent was launched, false otherwise (the
 * caller decides whether to dismiss a sheet on success).
 */
fun openUrlInBrowser(context: Context, url: String): Boolean {
    val uri = url.toUri()
    if (!uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)) {
        Toast.makeText(
            context,
            context.getString(R.string.toast_invalid_link),
            Toast.LENGTH_SHORT,
        ).show()
        return false
    }
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.toast_no_browser),
            Toast.LENGTH_SHORT,
        ).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(
            context,
            context.getString(R.string.toast_no_browser),
            Toast.LENGTH_SHORT,
        ).show()
        false
    }
}

/**
 * Phase 66 (RATE-02, T-66-03): always-reachable Play Store listing entry.
 * `market://` first (opens the Play app when present), `https://` fallback
 * to the web listing. The URI is derived from [context.packageName] only —
 * never user input — so no allowlist applies (unlike [openUrlInBrowser],
 * whose http/https gate would reject `market:`).
 *
 * WR-04: every launch goes through one guarded gate — `SecurityException`
 * (OEM exported-activity enforcement) falls through to the https
 * fallback exactly like `ActivityNotFoundException`, and non-Activity
 * callers get `FLAG_ACTIVITY_NEW_TASK` instead of an
 * `AndroidRuntimeException`. Only when BOTH destinations fail does the
 * user see the no-browser toast.
 *
 * @return true when a Store intent was launched, false otherwise.
 */
fun openPlayStoreListing(context: Context): Boolean {
    val packageName = context.packageName
    fun launch(uri: Uri): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
    if (launch("market://details?id=$packageName".toUri())) return true
    if (launch("https://play.google.com/store/apps/details?id=$packageName".toUri())) return true
    Toast.makeText(
        context,
        context.getString(R.string.toast_no_browser),
        Toast.LENGTH_SHORT,
    ).show()
    return false
}
