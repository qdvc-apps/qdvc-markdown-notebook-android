package qdvc.markdownnotebook.android.app.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import qdvc.markdownnotebook.android.app.model.FontVariant
import java.io.File

/**
 * A font available to the app, discovered in the system font directories.
 */
data class SystemFont(
    /** Stable id we persist (the font file's absolute path). */
    val id: String,
    /** Human-readable name derived from the file name. */
    val displayName: String,
    /** The loaded Compose font family for previewing and rendering. */
    val fontFamily: FontFamily,
)

/**
 * A loaded custom font built from the copied variant files for one tab. The
 * [fontFamily] contains whichever style variants exist, so the syntax
 * highlighter's bold / italic / bold-italic spans render in the matching face.
 * [displayNames] maps each present variant to the original file name.
 */
data class CustomFont(
    val fontFamily: FontFamily,
    val displayNames: Map<FontVariant, String>,
)

object SystemFonts {

    private val fontDirs = listOf(
        "/system/fonts",
        "/product/fonts",
        "/system/font",
        "/data/fonts",
    )

    /**
     * Returns the list of usable device fonts, de-duplicated by display name and
     * sorted alphabetically. Any file that fails to load is skipped. This is
     * pure I/O and safe to call off the main thread.
     */
    fun discover(): List<SystemFont> {
        val seen = HashSet<String>()
        val result = mutableListOf<SystemFont>()
        for (dirPath in fontDirs) {
            val dir = File(dirPath)
            if (!dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (file in files) {
                val lower = file.name.lowercase()
                if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue
                val name = prettifyName(file.name)
                if (!seen.add(name)) continue
                val family = try {
                    val tf = Typeface.createFromFile(file)
                    FontFamily(tf)
                } catch (e: Exception) {
                    continue
                }
                result.add(SystemFont(file.absolutePath, name, family))
            }
        }
        return result.sortedBy { it.displayName.lowercase() }
    }

    // ---- Custom fonts: up to 8 files copied into app storage ----
    //
    // Exactly one file per (tab, variant) slot, at a fixed path. Re-picking a
    // slot overwrites its file; clearing deletes it. Because the files live in
    // the app's own storage, nothing external can go missing between sessions.

    private fun customDir(context: Context): File =
        File(context.filesDir, "custom_fonts").apply { mkdirs() }

    private fun slotFile(context: Context, forView: Boolean, variant: FontVariant): File {
        val tab = if (forView) "view" else "edit"
        return File(customDir(context), "${tab}_${variant.name.lowercase()}.ttf")
    }

    /**
     * Copies the font at [uri] into the fixed slot for (tab, variant), replacing
     * any existing file. Returns the original file name on success, or null if
     * the file can't be read or isn't a usable font.
     */
    fun copyIntoSlot(context: Context, uri: Uri, forView: Boolean, variant: FontVariant): String? {
        return try {
            // Validate it's a real font first.
            val valid = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val tf = Typeface.Builder(pfd.fileDescriptor).build()
                tf != null && tf != Typeface.DEFAULT
            } ?: false
            if (!valid) return null

            val target = slotFile(context, forView, variant)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val name = queryDisplayName(context, uri)?.let { prettifyName(it) } ?: variant.label
            // Store the original name alongside the file for display.
            File(target.parentFile, "${target.nameWithoutExtension}.name").writeText(name)
            name
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes the file (and stored name) for one slot. */
    fun clearSlot(context: Context, forView: Boolean, variant: FontVariant) {
        val target = slotFile(context, forView, variant)
        target.delete()
        File(target.parentFile, "${target.nameWithoutExtension}.name").delete()
    }

    /** The display name stored for a slot, or null if the slot is empty. */
    fun slotName(context: Context, forView: Boolean, variant: FontVariant): String? {
        val target = slotFile(context, forView, variant)
        if (!target.exists()) return null
        val nameFile = File(target.parentFile, "${target.nameWithoutExtension}.name")
        return if (nameFile.exists()) nameFile.readText() else target.name
    }

    /**
     * Builds a [CustomFont] for one tab from whichever slot files exist, or
     * null if none do.
     */
    fun loadCustomFont(context: Context, forView: Boolean): CustomFont? {
        val fonts = mutableListOf<Font>()
        val names = mutableMapOf<FontVariant, String>()
        for (variant in FontVariant.entries) {
            val file = slotFile(context, forView, variant)
            if (!file.exists()) continue
            val (weight, style) = variant.weightAndStyle()
            try {
                fonts.add(Font(file, weight = weight, style = style))
                names[variant] = slotName(context, forView, variant) ?: variant.label
            } catch (e: Exception) {
                // Skip a slot that fails to load.
            }
        }
        if (fonts.isEmpty()) return null
        return CustomFont(FontFamily(fonts), names)
    }

    private fun FontVariant.weightAndStyle(): Pair<FontWeight, FontStyle> = when (this) {
        FontVariant.REGULAR -> FontWeight.Normal to FontStyle.Normal
        FontVariant.ITALIC -> FontWeight.Normal to FontStyle.Italic
        FontVariant.BOLD -> FontWeight.Bold to FontStyle.Normal
        FontVariant.BOLD_ITALIC -> FontWeight.Bold to FontStyle.Italic
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Turns "NotoSerif-BoldItalic.ttf" into "Noto Serif Bold Italic". */
    private fun prettifyName(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
            .replace('_', '-')
        // Split CamelCase and hyphen groups into words.
        val spaced = base
            .replace("-", " ")
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .replace(Regex("(?<=[A-Za-z])(?=[0-9])"), " ")
            .trim()
        return spaced.split(Regex("\\s+")).joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }
}
