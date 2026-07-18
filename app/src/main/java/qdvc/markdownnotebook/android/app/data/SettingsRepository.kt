package qdvc.markdownnotebook.android.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import qdvc.markdownnotebook.android.app.model.AppFont
import qdvc.markdownnotebook.android.app.model.DarkStyle
import qdvc.markdownnotebook.android.app.model.ThemeMode
import qdvc.markdownnotebook.android.app.model.Workspace
import kotlinx.coroutines.flow.Flow
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
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.fromName(it[Keys.THEME_MODE])
    }

    val darkStyle: Flow<DarkStyle> = context.dataStore.data.map {
        DarkStyle.fromName(it[Keys.DARK_STYLE])
    }

    val viewFont: Flow<AppFont> = context.dataStore.data.map {
        AppFont.fromName(it[Keys.VIEW_FONT])
    }

    val editFont: Flow<AppFont> = context.dataStore.data.map {
        AppFont.fromName(it[Keys.EDIT_FONT])
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

    suspend fun setDarkStyle(style: DarkStyle) {
        context.dataStore.edit { it[Keys.DARK_STYLE] = style.name }
    }

    suspend fun setViewFont(font: AppFont) {
        context.dataStore.edit { it[Keys.VIEW_FONT] = font.name }
    }

    suspend fun setEditFont(font: AppFont) {
        context.dataStore.edit { it[Keys.EDIT_FONT] = font.name }
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

    private fun encode(w: Workspace) = "${w.treeUri}\u0001${w.name}"
    private fun decode(s: String): Workspace? {
        val parts = s.split("\u0001", limit = 2)
        if (parts.size < 2) return null
        return Workspace(parts[0], parts[1])
    }
}
