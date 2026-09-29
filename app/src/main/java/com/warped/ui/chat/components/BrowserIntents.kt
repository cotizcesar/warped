package com.warped.ui.chat.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

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
    val uri = Uri.parse(url)
    if (!uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)) {
        Toast.makeText(
            context,
            "Invalid link.",
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
            "No browser found to open the link.",
            Toast.LENGTH_SHORT,
        ).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(
            context,
            "No browser found to open the link.",
            Toast.LENGTH_SHORT,
        ).show()
        false
    }
}
