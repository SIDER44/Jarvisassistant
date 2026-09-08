package com.jarvis.assistant.ui

/**
 * A theme is just an accent color used for the idle reactor state, gauge
 * rings, terminal text, and UI chrome. The background always stays near-black
 * for HUD contrast; only the accent swaps per theme.
 */
data class JarvisTheme(val id: String, val label: String, val accent: Int)

object ThemeManager {
    val themes = listOf(
        JarvisTheme("iron_man", "Iron Man", 0xFF29B6F6.toInt()),   // arc-reactor blue
        JarvisTheme("matrix", "Matrix", 0xFF39FF88.toInt()),        // hacker green (default)
        JarvisTheme("ultron", "Ultron", 0xFFFF3B5C.toInt()),        // red
        JarvisTheme("vibranium", "Vibranium", 0xFF9C6BFF.toInt()),  // purple
        JarvisTheme("gold_titanium", "Gold Titanium", 0xFFFFC24B.toInt())
    )

    fun byId(id: String?): JarvisTheme = themes.firstOrNull { it.id == id } ?: themes[1]
}
