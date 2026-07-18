package qdvc.markdownnotebook.android.app.model

/**
 * A colour theme, loaded from a JSON file in assets/themes/. Each theme maps
 * the handful of Material roles the app actually uses. [dark] selects whether
 * it appears in the Light Mode Style or Dark Mode Style list (and which
 * Material base scheme it builds on).
 *
 * The JSON shape is:
 * ```
 * {
 *   "id": "rose_pine",
 *   "name": "Rose Pine",
 *   "dark": true,
 *   "colors": {
 *     "background": "#191724",
 *     "surface": "#1F1D2E",
 *     "surfaceVariant": "#26233A",
 *     "onBackground": "#E0DEF4",
 *     "onSurfaceVariant": "#908CAA",
 *     "outline": "#403D52",
 *     "primary": "#C4A7E7",
 *     "onPrimary": "#191724",
 *     "secondary": "#EBBCBA",
 *     "onSecondary": "#191724",
 *     "error": "#EB6F92"
 *   }
 * }
 * ```
 * Every colour is an "#RRGGBB" (or "#AARRGGBB") hex string.
 */
data class ThemeSpec(
    val id: String,
    val name: String,
    val dark: Boolean,
    val colors: ThemeColors,
)

data class ThemeColors(
    val background: String,
    val surface: String,
    val surfaceVariant: String,
    val onBackground: String,
    val onSurfaceVariant: String,
    val outline: String,
    val primary: String,
    val onPrimary: String,
    val secondary: String,
    val onSecondary: String,
    val error: String,
)
