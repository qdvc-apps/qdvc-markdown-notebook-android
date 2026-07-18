package qdvc.markdownnotebook.android.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import qdvc.markdownnotebook.android.app.model.DarkStyle
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
        val DARK_STYLE = stringPreferencesKey("dark_style")
        val VIEW_FONT = stringPreferencesKey("view_font")
        val EDIT_FONT = stringPreferencesKey("edit_font")
        val WORKSPACES = stringSetPreferencesKey("workspaces")
        val WORKSPACE_ORDER = stringPreferencesKey("workspace_order")
        val OPEN_NOTES = stringPreferencesKey("open_notes")
        val CURRENT_NOTE = stringPreferencesKey("current_note")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.fromName(it[Keys.THEME_MODE])
    }

    val darkStyle: Flow<DarkStyle> = context.dataStore.data.map {
        DarkStyle.fromName(it[Keys.DARK_STYLE])
    }

    // Font selection is stored as the font's id (its file path). A null/absent
    // value means "use the app default".
    val viewFontId: Flow<String?> = context.dataStore.data.map { it[Keys.VIEW_FONT] }
    val editFontId: Flow<String?> = context.dataStore.data.map { it[Keys.EDIT_FONT] }

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

    suspend fun setDarkStyle(style: DarkStyle) {
        context.dataStore.edit { it[Keys.DARK_STYLE] = style.name }
    }

    suspend fun setViewFontId(id: String) {
        context.dataStore.edit { it[Keys.VIEW_FONT] = id }
    }

    suspend fun setEditFontId(id: String) {
        context.dataStore.edit { it[Keys.EDIT_FONT] = id }
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
