package qdvc.markdownnotebook.android.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import qdvc.markdownnotebook.android.app.model.ThemeColors
import qdvc.markdownnotebook.android.app.model.ThemeSpec
import org.json.JSONObject

/**
 * Loads colour themes from JSON files bundled in assets/themes/. Themes are
 * data, not code: adding a new .json file there makes a new theme available.
 */
object ThemeRepository {

    // Sensible defaults if the stored selection is missing or invalid.
    const val DEFAULT_LIGHT_ID = "regular_light"
    const val DEFAULT_DARK_ID = "regular_dark"

    private const val THEMES_DIR = "themes"

    @Volatile
    private var cache: List<ThemeSpec>? = null

    /** All themes, parsed once and cached. Malformed files are skipped. */
    fun all(context: Context): List<ThemeSpec> {
        cache?.let { return it }
        val loaded = load(context)
        cache = loaded
        return loaded
    }

    fun lightThemes(context: Context): List<ThemeSpec> = all(context).filter { !it.dark }
    fun darkThemes(context: Context): List<ThemeSpec> = all(context).filter { it.dark }

    fun byId(context: Context, id: String?): ThemeSpec? =
        if (id == null) null else all(context).firstOrNull { it.id == id }

    /** The chosen light theme, falling back to the default then to any light theme. */
    fun lightOrDefault(context: Context, id: String?): ThemeSpec? =
        byId(context, id)?.takeIf { !it.dark }
            ?: byId(context, DEFAULT_LIGHT_ID)
            ?: lightThemes(context).firstOrNull()

    fun darkOrDefault(context: Context, id: String?): ThemeSpec? =
        byId(context, id)?.takeIf { it.dark }
            ?: byId(context, DEFAULT_DARK_ID)
            ?: darkThemes(context).firstOrNull()

    private fun load(context: Context): List<ThemeSpec> {
        val assets = context.assets
        val files = try {
            assets.list(THEMES_DIR)?.filter { it.endsWith(".json") } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return files.mapNotNull { fileName ->
            try {
                val text = assets.open("$THEMES_DIR/$fileName").use { it.readBytes().toString(Charsets.UTF_8) }
                parse(text)
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.dark }, { it.name.lowercase() }))
    }

    private fun parse(json: String): ThemeSpec? {
        return try {
            val root = JSONObject(json)
            val colors = root.getJSONObject("colors")
            ThemeSpec(
                id = root.getString("id"),
                name = root.getString("name"),
                dark = root.getBoolean("dark"),
                colors = ThemeColors(
                    background = colors.getString("background"),
                    surface = colors.getString("surface"),
                    surfaceVariant = colors.getString("surfaceVariant"),
                    onBackground = colors.getString("onBackground"),
                    onSurfaceVariant = colors.getString("onSurfaceVariant"),
                    outline = colors.getString("outline"),
                    primary = colors.getString("primary"),
                    onPrimary = colors.getString("onPrimary"),
                    secondary = colors.getString("secondary"),
                    onSecondary = colors.getString("onSecondary"),
                    error = colors.getString("error"),
                ),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Builds a Compose [ColorScheme] from a theme spec. */
    fun colorScheme(spec: ThemeSpec): ColorScheme {
        val c = spec.colors
        val base = if (spec.dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = hex(c.primary),
            onPrimary = hex(c.onPrimary),
            secondary = hex(c.secondary),
            onSecondary = hex(c.onSecondary),
            background = hex(c.background),
            onBackground = hex(c.onBackground),
            surface = hex(c.surface),
            onSurface = hex(c.onBackground),
            surfaceVariant = hex(c.surfaceVariant),
            onSurfaceVariant = hex(c.onSurfaceVariant),
            outline = hex(c.outline),
            error = hex(c.error),
        )
    }

    /** Parses "#RRGGBB" or "#AARRGGBB" into a Compose [Color]. */
    private fun hex(value: String): Color {
        val clean = value.removePrefix("#")
        val argb = when (clean.length) {
            6 -> 0xFF000000.toInt() or clean.toInt(16)
            8 -> clean.toLong(16).toInt()
            else -> 0xFF000000.toInt()
        }
        return Color(argb)
    }
}
