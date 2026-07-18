package qdvc.markdownnotebook.android.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import qdvc.markdownnotebook.android.app.model.ThemeMode
import qdvc.markdownnotebook.android.app.model.FontVariant
import qdvc.markdownnotebook.android.app.model.Tab
import qdvc.markdownnotebook.android.app.ui.browse.BrowseScreen
import qdvc.markdownnotebook.android.app.ui.components.BottomBar
import qdvc.markdownnotebook.android.app.ui.edit.EditScreen
import qdvc.markdownnotebook.android.app.ui.jump.JumpScreen
import qdvc.markdownnotebook.android.app.ui.settings.SettingsScreen
import qdvc.markdownnotebook.android.app.ui.theme.MarkdownNotesTheme
import qdvc.markdownnotebook.android.app.ui.view.ViewScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    // Set just before launching the font picker; invoked with the picked file.
    private var onFontPicked: ((Uri) -> Unit)? = null

    // SAF folder picker. On result we persist read/write permission and add the
    // folder as a workspace.
    private val openFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                }
                val name = DocumentFile.fromTreeUri(this, uri)?.name ?: uri.lastPathSegment ?: "Workspace"
                vm.addWorkspace(uri.toString(), name)
            }
        }

    // Font file picker. We accept any file and validate it as a font on copy;
    // the file is copied into app storage immediately, so no persistable URI
    // permission is needed.
    private val openFontFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) onFontPicked?.invoke(uri)
            onFontPicked = null
        }

    /** Launches the system file picker to choose a font file. */
    fun pickFontFile(onResult: (Uri) -> Unit) {
        onFontPicked = onResult
        // Common font MIME types plus a wildcard, since many providers report
        // fonts as application/octet-stream.
        openFontFile.launch(
            arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by vm.themeMode.collectAsState()
            val darkStyle by vm.darkStyle.collectAsState()

            // Resolve whether the app is currently showing a dark theme so the
            // status-bar / nav-bar icons get the right contrast.
            val darkTheme = when (themeMode) {
                ThemeMode.AUTOMATIC -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MarkdownNotesTheme(themeMode = themeMode, darkStyle = darkStyle) {
                SystemBars(darkTheme = darkTheme)
                AppRoot(
                    vm = vm,
                    onPickFolder = { openFolder.launch(null) },
                    onPickCustomVariant = { forView, variant ->
                        pickFontFile { uri ->
                            vm.setCustomFontVariant(forView, variant, uri)
                        }
                    },
                )
            }
        }
    }
}

/**
 * Themes the system bars. The status bar is coloured to match the app's top
 * app bars, and the navigation bar (back / home / menu) is coloured to match
 * the app's bottom navigation bar — both use the surface colour, so the system
 * bars blend into the app's own bars. Icon contrast follows the light/dark
 * theme so the bars never render as plain black.
 */
@Composable
private fun SystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    // Top app bars use `surface`; the bottom NavigationBar also uses `surface`
    // (set explicitly in BottomBar), so a single surface colour matches both.
    val barColor = MaterialTheme.colorScheme.surface.toArgb()
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            val controller = WindowInsetsControllerCompat(window, view)
            // Light (dark = false) theme -> dark icons; dark theme -> light icons.
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

@Composable
private fun AppRoot(
    vm: AppViewModel,
    onPickFolder: () -> Unit,
    onPickCustomVariant: (forView: Boolean, variant: FontVariant) -> Unit,
) {
    val workspaces by vm.workspaces.collectAsState()
    val browse by vm.browse.collectAsState()
    val openNotes by vm.openNotes.collectAsState()
    val currentTab by vm.currentTab.collectAsState()
    val currentNoteUri by vm.currentNoteUri.collectAsState()
    val viewFontId by vm.viewFontId.collectAsState()
    val editFontId by vm.editFontId.collectAsState()
    val systemFonts by vm.systemFonts.collectAsState()
    val viewCustomSet by vm.viewCustomFontSet.collectAsState()
    val editCustomSet by vm.editCustomFontSet.collectAsState()
    val viewCustomFont by vm.viewCustomFont.collectAsState()
    val editCustomFont by vm.editCustomFont.collectAsState()

    var showSettings by remember { mutableStateOf(false) }

    val currentNote = openNotes.firstOrNull { it.documentUri == currentNoteUri }
    val noteOpen = currentNoteUri != null

    if (showSettings) {
        // System back closes Settings and returns to the app.
        BackHandler(enabled = true) { showSettings = false }
        SettingsScreen(
            themeMode = vm.themeMode.collectAsState().value,
            darkStyle = vm.darkStyle.collectAsState().value,
            systemFonts = systemFonts,
            viewFontId = viewFontId,
            editFontId = editFontId,
            viewCustomSet = viewCustomSet,
            editCustomSet = editCustomSet,
            viewCustomFont = viewCustomFont,
            editCustomFont = editCustomFont,
            onThemeMode = vm::setThemeMode,
            onDarkStyle = vm::setDarkStyle,
            onViewFontId = vm::setViewFontId,
            onEditFontId = vm::setEditFontId,
            onSelectCustom = vm::selectCustomFont,
            onPickCustomVariant = onPickCustomVariant,
            onClearCustomVariant = { forView, variant ->
                vm.clearCustomFontVariant(forView, variant)
            },
            onClose = { showSettings = false },
        )
        return
    }

    // On the Browse tab, when a toolbar back button is showing (i.e. we're
    // inside a folder), the system back button mirrors it instead of exiting.
    val browseHasBack = currentTab == Tab.BROWSE && browse.stack.isNotEmpty()
    BackHandler(enabled = browseHasBack) { vm.browseUp() }

    Scaffold(
        bottomBar = {
            BottomBar(
                current = currentTab,
                noteOpen = noteOpen,
                onSelect = vm::selectTab,
            )
        },
    ) { padding ->
        // Apply only the bottom inset here (nav bar + system nav). Each screen's
        // own TopAppBar consumes the top status-bar inset, so applying the full
        // padding would double it.
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (currentTab) {
                Tab.BROWSE -> BrowseScreen(
                    workspaces = workspaces,
                    browse = browse,
                    onAddWorkspace = onPickFolder,
                    onRemoveWorkspace = vm::removeWorkspace,
                    onOpenWorkspace = vm::openWorkspace,
                    onOpenSubFolder = vm::openSubFolder,
                    onOpenNote = vm::openNote,
                    onBrowseUp = vm::browseUp,
                    onCreateNote = vm::createNoteInCurrentFolder,
                    onOpenSettings = { showSettings = true },
                )

                Tab.VIEW -> ViewScreen(
                    note = currentNote,
                    fontFamily = vm.fontFamilyFor(viewFontId, forView = true),
                )

                Tab.EDIT -> EditScreen(
                    note = currentNote,
                    fontFamily = vm.fontFamilyFor(editFontId, forView = false),
                    onDraftChange = { draft ->
                        currentNoteUri?.let { vm.updateDraft(it, draft) }
                    },
                    onSave = { currentNoteUri?.let { vm.saveNote(it) } },
                )

                Tab.JUMP -> JumpScreen(
                    notes = openNotes,
                    currentUri = currentNoteUri,
                    onSelect = { uri ->
                        vm.setCurrentNote(uri)
                        vm.selectTab(Tab.VIEW)
                    },
                    onCloseConfirmed = vm::closeNote,
                    onMove = vm::moveOpenNote,
                )
            }
        }
    }
}
