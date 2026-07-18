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

/** The four style variants a custom font can supply for syntax highlighting. */
enum class FontVariant(val label: String) {
    REGULAR("Regular"),
    ITALIC("Italic"),
    BOLD("Bold"),
    BOLD_ITALIC("Bold Italic"),
}

/** Font-size bounds and default (in sp) for the View and Edit tabs. */
object FontSizes {
    const val DEFAULT = 14f
    const val MIN = 10f
    const val MAX = 28f
    const val STEP = 1f

    /** The line height that pairs with a given font size (roughly 1.57x). */
    fun lineHeightFor(sizeSp: Float): Float = sizeSp * 1.57f
}

/**
 * The display names of the custom-font files currently copied into a tab's four
 * slots. A null entry means that slot is empty. The actual font files live in
 * app storage at fixed paths, so only these labels need to be surfaced to the UI.
 */
data class CustomFontSet(
    val regularName: String? = null,
    val italicName: String? = null,
    val boldName: String? = null,
    val boldItalicName: String? = null,
) {
    fun nameFor(variant: FontVariant): String? = when (variant) {
        FontVariant.REGULAR -> regularName
        FontVariant.ITALIC -> italicName
        FontVariant.BOLD -> boldName
        FontVariant.BOLD_ITALIC -> boldItalicName
    }

    /** True if at least one slot has a file. */
    val hasAny: Boolean
        get() = regularName != null || italicName != null ||
            boldName != null || boldItalicName != null
}
