package qdvc.markdownnotebook.android.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import qdvc.markdownnotebook.android.app.model.CustomFontSet
import qdvc.markdownnotebook.android.app.model.FontSizes
import qdvc.markdownnotebook.android.app.model.FontVariant
import qdvc.markdownnotebook.android.app.model.PersistedOpenNote
import qdvc.markdownnotebook.android.app.model.ThemeMode
import qdvc.markdownnotebook.android.app.model.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists user settings and the list of workspaces. Workspaces are stored as
 * a set of "uri\u0001name" strings plus an ordering list so the home screen
 * keeps a stable order.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LIGHT_THEME = stringPreferencesKey("light_theme_id")
        val DARK_THEME = stringPreferencesKey("dark_theme_id")
        val VIEW_FONT = stringPreferencesKey("view_font")
        val EDIT_FONT = stringPreferencesKey("edit_font")
        val VIEW_FONT_SIZE = floatPreferencesKey("view_font_size")
        val EDIT_FONT_SIZE = floatPreferencesKey("edit_font_size")
        val WORKSPACES = stringSetPreferencesKey("workspaces")
        val WORKSPACE_ORDER = stringPreferencesKey("workspace_order")
        val OPEN_NOTES = stringPreferencesKey("open_notes")
        val CURRENT_NOTE = stringPreferencesKey("current_note")

        // Custom font slot display names (one per tab+variant). Presence of a
        // name mirrors the presence of the copied font file in app storage.
        val VIEW_FONT_REGULAR = stringPreferencesKey("view_font_regular_name")
        val VIEW_FONT_ITALIC = stringPreferencesKey("view_font_italic_name")
        val VIEW_FONT_BOLD = stringPreferencesKey("view_font_bold_name")
        val VIEW_FONT_BOLD_ITALIC = stringPreferencesKey("view_font_bold_italic_name")
        val EDIT_FONT_REGULAR = stringPreferencesKey("edit_font_regular_name")
        val EDIT_FONT_ITALIC = stringPreferencesKey("edit_font_italic_name")
        val EDIT_FONT_BOLD = stringPreferencesKey("edit_font_bold_name")
        val EDIT_FONT_BOLD_ITALIC = stringPreferencesKey("edit_font_bold_italic_name")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.fromName(it[Keys.THEME_MODE])
    }

    val lightThemeId: Flow<String> = context.dataStore.data.map {
        it[Keys.LIGHT_THEME] ?: ThemeRepository.DEFAULT_LIGHT_ID
    }

    val darkThemeId: Flow<String> = context.dataStore.data.map {
        it[Keys.DARK_THEME] ?: ThemeRepository.DEFAULT_DARK_ID
    }

    // Font selection is stored as the font's id: the default sentinel, a system
    // font's file path, or the custom sentinel (see CUSTOM_FONT_ID) meaning
    // "use this tab's custom font set".
    val viewFontId: Flow<String?> = context.dataStore.data.map { it[Keys.VIEW_FONT] }
    val editFontId: Flow<String?> = context.dataStore.data.map { it[Keys.EDIT_FONT] }

    val viewFontSize: Flow<Float> = context.dataStore.data.map {
        it[Keys.VIEW_FONT_SIZE] ?: FontSizes.DEFAULT
    }
    val editFontSize: Flow<Float> = context.dataStore.data.map {
        it[Keys.EDIT_FONT_SIZE] ?: FontSizes.DEFAULT
    }

    val viewCustomFontSet: Flow<CustomFontSet> = context.dataStore.data.map { prefs ->
        CustomFontSet(
            regularName = prefs[Keys.VIEW_FONT_REGULAR],
            italicName = prefs[Keys.VIEW_FONT_ITALIC],
            boldName = prefs[Keys.VIEW_FONT_BOLD],
            boldItalicName = prefs[Keys.VIEW_FONT_BOLD_ITALIC],
        )
    }
    val editCustomFontSet: Flow<CustomFontSet> = context.dataStore.data.map { prefs ->
        CustomFontSet(
            regularName = prefs[Keys.EDIT_FONT_REGULAR],
            italicName = prefs[Keys.EDIT_FONT_ITALIC],
            boldName = prefs[Keys.EDIT_FONT_BOLD],
            boldItalicName = prefs[Keys.EDIT_FONT_BOLD_ITALIC],
        )
    }

    val workspaces: Flow<List<Workspace>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.WORKSPACES] ?: emptySet()
        val byUri = raw.mapNotNull { decode(it) }.associateBy { it.treeUri }
        val order = prefs[Keys.WORKSPACE_ORDER]?.split("\u0001")?.filter { it.isNotEmpty() }
            ?: emptyList()
        val ordered = order.mapNotNull { byUri[it] }
        val leftovers = byUri.values.filter { it.treeUri !in order }
        ordered + leftovers
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLightThemeId(id: String) {
        context.dataStore.edit { it[Keys.LIGHT_THEME] = id }
    }

    suspend fun setDarkThemeId(id: String) {
        context.dataStore.edit { it[Keys.DARK_THEME] = id }
    }

    suspend fun setViewFontId(id: String) {
        context.dataStore.edit { it[Keys.VIEW_FONT] = id }
    }

    suspend fun setEditFontId(id: String) {
        context.dataStore.edit { it[Keys.EDIT_FONT] = id }
    }

    /** Sets the View or Edit font size (sp). Passing null restores the default. */
    suspend fun setFontSize(forView: Boolean, sizeSp: Float?) {
        val key = if (forView) Keys.VIEW_FONT_SIZE else Keys.EDIT_FONT_SIZE
        context.dataStore.edit { prefs ->
            if (sizeSp == null) prefs.remove(key) else prefs[key] = sizeSp
        }
    }

    /** Records or clears the stored display name for one custom-font slot. */
    suspend fun setCustomFontVariantName(forView: Boolean, variant: FontVariant, name: String?) {
        val key = when (forView) {
            true -> when (variant) {
                FontVariant.REGULAR -> Keys.VIEW_FONT_REGULAR
                FontVariant.ITALIC -> Keys.VIEW_FONT_ITALIC
                FontVariant.BOLD -> Keys.VIEW_FONT_BOLD
                FontVariant.BOLD_ITALIC -> Keys.VIEW_FONT_BOLD_ITALIC
            }
            false -> when (variant) {
                FontVariant.REGULAR -> Keys.EDIT_FONT_REGULAR
                FontVariant.ITALIC -> Keys.EDIT_FONT_ITALIC
                FontVariant.BOLD -> Keys.EDIT_FONT_BOLD
                FontVariant.BOLD_ITALIC -> Keys.EDIT_FONT_BOLD_ITALIC
            }
        }
        context.dataStore.edit { prefs ->
            if (name == null) prefs.remove(key) else prefs[key] = name
        }
    }

    suspend fun addWorkspace(workspace: Workspace) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.WORKSPACES] ?: emptySet()).toMutableSet()
            // Replace any existing entry for the same uri.
            current.removeAll { decode(it)?.treeUri == workspace.treeUri }
            current.add(encode(workspace))
            prefs[Keys.WORKSPACES] = current

            val order = (prefs[Keys.WORKSPACE_ORDER]?.split("\u0001")
                ?.filter { it.isNotEmpty() } ?: emptyList()).toMutableList()
            if (workspace.treeUri !in order) order.add(workspace.treeUri)
            prefs[Keys.WORKSPACE_ORDER] = order.joinToString("\u0001")
        }
    }

    suspend fun removeWorkspace(treeUri: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.WORKSPACES] ?: emptySet()).toMutableSet()
            current.removeAll { decode(it)?.treeUri == treeUri }
            prefs[Keys.WORKSPACES] = current

            val order = (prefs[Keys.WORKSPACE_ORDER]?.split("\u0001")
                ?.filter { it.isNotEmpty() } ?: emptyList()).toMutableList()
            order.remove(treeUri)
            prefs[Keys.WORKSPACE_ORDER] = order.joinToString("\u0001")
        }
    }

    /**
     * Saves the ordered list of open notes and which one is current. Only their
     * identity is stored (uri, display name, workspace); content is re-read from
     * disk on restore so we never persist stale copies of file contents.
     */
    suspend fun saveOpenNotes(notes: List<PersistedOpenNote>, currentUri: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OPEN_NOTES] = notes.joinToString("\u0002") {
                "${it.documentUri}\u0001${it.displayName}\u0001${it.workspaceName}"
            }
            if (currentUri != null) prefs[Keys.CURRENT_NOTE] = currentUri
            else prefs.remove(Keys.CURRENT_NOTE)
        }
    }

    /** One-shot read of the persisted open notes (in saved order). */
    suspend fun loadOpenNotes(): List<PersistedOpenNote> {
        val prefs = context.dataStore.data.first()
        val raw = prefs[Keys.OPEN_NOTES] ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split("\u0002").mapNotNull { record ->
            val parts = record.split("\u0001")
            if (parts.size < 3) return@mapNotNull null
            PersistedOpenNote(parts[0], parts[1], parts[2])
        }
    }

    /** One-shot read of the persisted current-note uri, if any. */
    suspend fun loadCurrentNoteUri(): String? =
        context.dataStore.data.first()[Keys.CURRENT_NOTE]

    private fun encode(w: Workspace) = "${w.treeUri}\u0001${w.name}"
    private fun decode(s: String): Workspace? {
        val parts = s.split("\u0001", limit = 2)
        if (parts.size < 2) return null
        return Workspace(parts[0], parts[1])
    }
}
