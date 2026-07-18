package qdvc.markdownnotebook.android.app.model

enum class ThemeMode(val label: String) {
    AUTOMATIC("Automatic"),
    LIGHT("Light Mode"),
    DARK("Dark Mode");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: AUTOMATIC
    }
}

enum class DarkStyle(val label: String) {
    REGULAR("Regular Dark Mode"),
    PURE_BLACK("Pure Black Mode");

    companion object {
        fun fromName(name: String?): DarkStyle =
            entries.firstOrNull { it.name == name } ?: REGULAR
    }
}

/**
 * System-guaranteed font families the user can pick for the View and Edit
 * tabs. These map to Android's built-in generic families, so no bundled font
 * files are needed and they work on any device.
 */
enum class AppFont(val label: String) {
    MONOSPACE("Monospace"),
    SANS_SERIF("Sans Serif"),
    SERIF("Serif"),
    CURSIVE("Cursive");

    companion object {
        fun fromName(name: String?): AppFont =
            entries.firstOrNull { it.name == name } ?: MONOSPACE
    }
}
