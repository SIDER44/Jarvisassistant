package com.jarvis.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock

/**
 * Very simple intent-based command matching. This runs BEFORE sending text
 * to the Brain, so obvious device commands are fast and don't cost an API call.
 * Returns null if no local action matched (falls through to the LLM brain).
 */
object DeviceActions {

    fun tryHandle(context: Context, text: String): String? {
        val lower = text.lowercase().trim()

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
}
