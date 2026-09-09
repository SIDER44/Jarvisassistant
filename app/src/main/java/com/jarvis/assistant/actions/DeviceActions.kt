package com.jarvis.assistant.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.SmsManager
import android.view.KeyEvent
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

        // Identity — answered directly here, never left to the AI model, so it's
        // always correct regardless of which backend (Gemini/OpenAI/etc.) is active.
        if (Regex("who (?:is|was) your (?:developer|creator|maker)|who (?:made|created|built|developed) you")
                .containsMatchIn(lower)
        ) {
            return "I was developed by my creator, Sydney, also known as Almeer."
        }

        // Media transport controls — checked BEFORE "play <song>" so
        // "stop the music" / "pause" / "next song" don't get treated as a song title.
        if (Regex("(?:pause|stop)(?: the)? (?:music|song|playback)?").matches(lower) ||
            lower in listOf("pause", "stop", "pause music", "stop music")
        ) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
            return "Paused"
        }
        if (Regex("(?:resume|continue|unpause)(?: the)? ?(?:music|song|playback)?").matches(lower) ||
            lower in listOf("resume", "continue", "play music", "resume music", "unpause")
        ) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
            return "Resuming"
        }
        if (lower in listOf("next song", "skip", "skip song", "next track")) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            return "Skipping to next track"
        }
        if (lower in listOf("previous song", "go back", "last song", "previous track")) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return "Going to previous track"
        }

        // "play believer by imagine dragons" / "play some jazz"
        Regex("^play (?:the song )?(.+)").find(lower)?.let { match ->
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

        // "call mom" / "call 5551234567" — places an actual phone call (not WhatsApp;
        // WhatsApp doesn't expose an official intent to start a call automatically).
        Regex("^call (.+)").find(lower)?.let { match ->
            val target = match.groupValues[1].trim()
            val phoneNumber = if (target.all { it.isDigit() || it == '+' }) target
                else lookupContactNumber(context, target)

            if (phoneNumber == null) {
                return "I couldn't find a number for $target to call."
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return "I need phone call permission to do that hands-free. Please grant it in the app's permissions."
            }

            return try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Calling $target"
            } catch (e: Exception) {
                "Couldn't place the call: ${e.message}"
            }
        }

        // "whatsapp 5551234567 saying I'm on my way" — opens WhatsApp with the
        // message prefilled. WhatsApp deliberately blocks fully-automated sending
        // from other apps (anti-spam policy) — you still tap Send once yourself.
        Regex("^whatsapp (\\S+) (?:saying|that says|message) (.+)").find(lower)?.let { match ->
            val target = match.groupValues[1].trim()
            val message = match.groupValues[2].trim()
            val phoneNumber = if (target.all { it.isDigit() || it == '+' }) target
                else lookupContactNumber(context, target)

            if (phoneNumber == null) {
                return "I couldn't find a number for $target to message on WhatsApp."
            }

            return try {
                val encoded = java.net.URLEncoder.encode(message, "UTF-8")
                val uri = Uri.parse("https://wa.me/$phoneNumber?text=$encoded")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Opened WhatsApp with your message to $target ready — tap send to confirm."
            } catch (e: Exception) {
                "Couldn't open WhatsApp: ${e.message}"
            }
        }

        // "text 5551234567 saying I'm running late" / "text mom saying on my way"
        Regex("^text (\\S+) (?:saying|that says|message) (.+)").find(lower)?.let { match ->
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

        // "open spotify" / "launch spotify" / "open whatsapp status" (extra words tolerated)
        Regex("^(?:open|launch|start) (.+)").find(lower)?.let { match ->
            val appName = match.groupValues[1].trim()
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)

            // Try an exact-ish match first, then fall back to matching on the
            // first significant word so trailing extra words ("...and call
            // this number", "...status") don't prevent the app from opening.
            val words = appName.split(" ").filter { it.length > 2 }
            val target = apps.firstOrNull {
                val label = pm.getApplicationLabel(it).toString().lowercase()
                label.contains(appName) || words.any { w -> label.contains(w) }
            }
            if (target != null) {
                val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                    return "Opening ${pm.getApplicationLabel(target)}"
                }
            }
            return "I couldn't find an app matching \"$appName\" installed on this phone."
        }

        // "set an alarm for 7 am" / "set alarm for 7:30"
        Regex("^set (?:an? )?alarm (?:for|at) (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)?.let { match ->
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
        Regex("^search (?:for|the web for)? ?(.+)").find(lower)?.let { match ->
            val query = match.groupValues[1].trim()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return "Searching for $query"
        }

        return null // no local match — let the LLM brain handle it
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            // Some devices/OEMs restrict this; the reply text still confirms
            // intent to the user even if the underlying dispatch silently no-ops.
        }
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
