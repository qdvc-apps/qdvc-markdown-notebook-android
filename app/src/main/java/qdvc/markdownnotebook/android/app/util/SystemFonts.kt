package qdvc.markdownnotebook.android.app.util

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * Discovers the font files actually installed on the device by scanning the
 * standard system font directories, so the font pickers can list and preview
 * real fonts (e.g. "Roboto", "NotoSerif") rather than generic family names.
 */
data class SystemFont(
    /** Stable id we persist (the font file's absolute path). */
    val id: String,
    /** Human-readable name derived from the file name. */
    val displayName: String,
    /** The loaded Compose font family for previewing and rendering. */
    val fontFamily: FontFamily,
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
