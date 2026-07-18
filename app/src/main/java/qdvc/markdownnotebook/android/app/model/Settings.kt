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
