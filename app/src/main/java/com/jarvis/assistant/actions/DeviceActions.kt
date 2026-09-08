package com.jarvis.assistant.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Very simple intent-based command matching. This runs BEFORE sending text
 * to the Brain, so obvious device commands are fast and don't cost an API call.
 * Returns null if no local action matched (falls through to the LLM brain).
 *
 * Order matters: more specific patterns (play, text) are checked before the
 * generic "search" catch-all so they don't get swallowed by it.
 */
object DeviceActions {

    fun tryHandle(context: Context, text: String): String? {
        val lower = text.lowercase().trim()

        // "play believer by imagine dragons" / "play some jazz"
        Regex("play (?:the song )?(.+)").find(lower)?.let { match ->
            val query = match.groupValues[1].trim()
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManagerQuery, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                "Playing $query"
            } catch (e: Exception) {
                "I couldn't find a music app that supports voice search. Try installing Spotify or YouTube Music."
            }
        }

        // "text 5551234567 saying I'm running late" / "text mom saying on my way"
        Regex("text (\\S+) (?:saying|that says|message) (.+)").find(lower)?.let { match ->
            val target = match.groupValues[1].trim()
            val message = match.groupValues[2].trim()

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return "I need SMS permission to send texts. Please grant it in the app's permissions."
            }

            val phoneNumber = if (target.all { it.isDigit() || it == '+' }) {
                target
            } else {
                lookupContactNumber(context, target)
            }

            if (phoneNumber == null) {
                return "I couldn't find a contact number for $target."
            }

            return try {
                @Suppress("DEPRECATION")
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                "Texted $target: $message"
            } catch (e: Exception) {
                "Sending the text failed: ${e.message}"
            }
        }

        // "open spotify" / "launch spotify"
        Regex("(?:open|launch|start) (.+)").find(lower)?.let { match ->
            val appName = match.groupValues[1].trim()
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            val target = apps.firstOrNull {
                pm.getApplicationLabel(it).toString().lowercase().contains(appName)
            }
            if (target != null) {
                val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                    return "Opening $appName"
                }
            }
        }

        // "set an alarm for 7 am" / "set alarm for 7:30"
        Regex("set (?:an? )?alarm (?:for|at) (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val meridiem = match.groupValues[3]
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return "Alarm set for $hour:${minute.toString().padStart(2, '0')}"
        }

        // "search for best pizza near me"
        Regex("search (?:for|the web for)? ?(.+)").find(lower)?.let { match ->
            val query = match.groupValues[1].trim()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return "Searching for $query"
        }

        return null // no local match — let the LLM brain handle it
    }

    private const val SearchManagerQuery = "query"

    /** Requires READ_CONTACTS permission; returns null if not granted or not found. */
    private fun lookupContactNumber(context: Context, name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")

        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return cursor.getString(idx)
            }
        }
        return null
    }
}
