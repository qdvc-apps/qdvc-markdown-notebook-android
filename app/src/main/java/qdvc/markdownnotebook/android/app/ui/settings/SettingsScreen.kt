package qdvc.markdownnotebook.android.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.markdownnotebook.android.app.model.CustomFontSet
import qdvc.markdownnotebook.android.app.model.DarkStyle
import qdvc.markdownnotebook.android.app.model.FontVariant
import qdvc.markdownnotebook.android.app.model.LightStyle
import qdvc.markdownnotebook.android.app.model.ThemeMode
import qdvc.markdownnotebook.android.app.util.CustomFont
import qdvc.markdownnotebook.android.app.util.SystemFont

/** Sentinel id meaning "use the app's default (monospace)". */
const val DEFAULT_FONT_ID = "__default__"

/** Sentinel id meaning "use this tab's custom font set". */
const val CUSTOM_FONT_ID = "__custom__"

private enum class SettingsPage {
    ROOT, APPEARANCE, LIGHT_STYLE, DARK_STYLE, VIEW_FONT, EDIT_FONT, VIEW_CUSTOM, EDIT_CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    lightStyle: LightStyle,
    darkStyle: DarkStyle,
    systemFonts: List<SystemFont>,
    viewFontId: String?,
    editFontId: String?,
    viewCustomSet: CustomFontSet,
    editCustomSet: CustomFontSet,
    viewCustomFont: CustomFont?,
    editCustomFont: CustomFont?,
    onThemeMode: (ThemeMode) -> Unit,
    onLightStyle: (LightStyle) -> Unit,
    onDarkStyle: (DarkStyle) -> Unit,
    onViewFontId: (String) -> Unit,
    onEditFontId: (String) -> Unit,
    onSelectCustom: (forView: Boolean) -> Unit,
    onPickCustomVariant: (forView: Boolean, variant: FontVariant) -> Unit,
    onClearCustomVariant: (forView: Boolean, variant: FontVariant) -> Unit,
    onClose: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }

    val title = when (page) {
        SettingsPage.ROOT -> "Settings"
        SettingsPage.APPEARANCE -> "Appearance"
        SettingsPage.LIGHT_STYLE -> "Light Mode Style"
        SettingsPage.DARK_STYLE -> "Dark Mode Style"
        SettingsPage.VIEW_FONT -> "View Font"
        SettingsPage.EDIT_FONT -> "Edit Font"
        SettingsPage.VIEW_CUSTOM -> "Custom View Font"
        SettingsPage.EDIT_CUSTOM -> "Custom Edit Font"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        page = when (page) {
                            SettingsPage.ROOT -> { onClose(); SettingsPage.ROOT }
                            SettingsPage.APPEARANCE -> SettingsPage.ROOT
                            SettingsPage.LIGHT_STYLE -> SettingsPage.ROOT
                            SettingsPage.DARK_STYLE -> SettingsPage.ROOT
                            SettingsPage.VIEW_FONT -> SettingsPage.ROOT
                            SettingsPage.EDIT_FONT -> SettingsPage.ROOT
                            SettingsPage.VIEW_CUSTOM -> SettingsPage.VIEW_FONT
                            SettingsPage.EDIT_CUSTOM -> SettingsPage.EDIT_FONT
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        // Slide between the settings root and its submenus, matching the Browse
        // navigation animation: entering a submenu slides in from the right,
        // going back slides in from the left.
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val deeper = settingsDepth(targetState) > settingsDepth(initialState)
                if (deeper) {
                    (slideInHorizontally(tween(280)) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(280)) { it } + fadeOut())
                }
            },
            label = "settingsTransition",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                when (current) {
                    SettingsPage.ROOT -> {
                        SectionHeader("Display")
                        NavRow(
                            icon = { Icon(Icons.Filled.DarkMode, null, tint = MaterialTheme.colorScheme.primary) },
                            title = "Appearance",
                            subtitle = themeMode.label,
                            onClick = { page = SettingsPage.APPEARANCE },
                        )
                        NavRow(
                            icon = { Icon(Icons.Filled.LightMode, null, tint = MaterialTheme.colorScheme.primary) },
                            title = "Light Mode Style",
                            subtitle = lightStyle.label,
                            onClick = { page = SettingsPage.LIGHT_STYLE },
                        )
                        NavRow(
                            icon = { Icon(Icons.Filled.Contrast, null, tint = MaterialTheme.colorScheme.primary) },
                            title = "Dark Mode Style",
                            subtitle = darkStyle.label,
                            onClick = { page = SettingsPage.DARK_STYLE },
                        )
                        SectionHeader("Fonts")
                        NavRow(
                            icon = { Icon(Icons.Filled.Visibility, null, tint = MaterialTheme.colorScheme.primary) },
                            title = "View Font",
                            subtitle = fontDisplayName(viewFontId, systemFonts),
                            onClick = { page = SettingsPage.VIEW_FONT },
                        )
                        NavRow(
                            icon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                            title = "Edit Font",
                            subtitle = fontDisplayName(editFontId, systemFonts),
                            onClick = { page = SettingsPage.EDIT_FONT },
                        )
                    }

                    SettingsPage.APPEARANCE -> {
                        SectionHeader("Theme")
                        ThemeMode.entries.forEach { mode ->
                            ChoiceRow(
                                label = mode.label,
                                selected = mode == themeMode,
                                onClick = { onThemeMode(mode) },
                            )
                        }
                    }

                    SettingsPage.LIGHT_STYLE -> {
                        SectionHeader("When light mode is active")
                        LightStyle.entries.forEach { style ->
                            ChoiceRow(
                                label = style.label,
                                subtitle = when (style) {
                                    LightStyle.REGULAR -> "Warm paper tones with a sage accent."
                                    LightStyle.EVERFOREST -> "Everforest light palette (medium contrast)."
                                },
                                selected = style == lightStyle,
                                onClick = { onLightStyle(style) },
                            )
                        }
                    }

                    SettingsPage.DARK_STYLE -> {
                        SectionHeader("When dark mode is active")
                        DarkStyle.entries.forEach { style ->
                            ChoiceRow(
                                label = style.label,
                                subtitle = when (style) {
                                    DarkStyle.REGULAR -> "Dark grey surfaces and backgrounds."
                                    DarkStyle.PURE_BLACK -> "True black for larger surfaces, best on OLED screens."
                                    DarkStyle.EVERFOREST -> "Everforest dark palette (medium contrast)."
                                },
                                selected = style == darkStyle,
                                onClick = { onDarkStyle(style) },
                            )
                        }
                    }

                    SettingsPage.VIEW_FONT -> {
                        FontChoiceList(
                            header = "Font for the View tab",
                            systemFonts = systemFonts,
                            selectedId = viewFontId,
                            customSet = viewCustomSet,
                            customFont = viewCustomFont,
                            onSelect = onViewFontId,
                            onOpenCustom = {
                                onSelectCustom(true)
                                page = SettingsPage.VIEW_CUSTOM
                            },
                        )
                    }

                    SettingsPage.EDIT_FONT -> {
                        FontChoiceList(
                            header = "Font for the Edit tab",
                            systemFonts = systemFonts,
                            selectedId = editFontId,
                            customSet = editCustomSet,
                            customFont = editCustomFont,
                            onSelect = onEditFontId,
                            onOpenCustom = {
                                onSelectCustom(false)
                                page = SettingsPage.EDIT_CUSTOM
                            },
                        )
                    }

                    SettingsPage.VIEW_CUSTOM -> {
                        CustomFontVariants(
                            set = viewCustomSet,
                            customFont = viewCustomFont,
                            onPick = { onPickCustomVariant(true, it) },
                            onClear = { onClearCustomVariant(true, it) },
                        )
                    }

                    SettingsPage.EDIT_CUSTOM -> {
                        CustomFontVariants(
                            set = editCustomSet,
                            customFont = editCustomFont,
                            onPick = { onPickCustomVariant(false, it) },
                            onClear = { onClearCustomVariant(false, it) },
                        )
                    }
                }
            }
        }
    }
}

/** Root is depth 0; font pages depth 1; the custom-variant pages depth 2. */
private fun settingsDepth(page: SettingsPage): Int = when (page) {
    SettingsPage.ROOT -> 0
    SettingsPage.VIEW_CUSTOM, SettingsPage.EDIT_CUSTOM -> 2
    else -> 1
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun NavRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ChoiceRow(
    label: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

private fun fontDisplayName(id: String?, systemFonts: List<SystemFont>): String {
    if (id == null || id == DEFAULT_FONT_ID) return "Default (Monospace)"
    if (id == CUSTOM_FONT_ID) return "Custom font"
    systemFonts.firstOrNull { it.id == id }?.let { return it.displayName }
    // A system path not yet scanned; safe fallback.
    return "Default (Monospace)"
}

@Composable
private fun FontChoiceList(
    header: String,
    systemFonts: List<SystemFont>,
    selectedId: String?,
    customSet: CustomFontSet,
    customFont: CustomFont?,
    onSelect: (String) -> Unit,
    onOpenCustom: () -> Unit,
) {
    val customSelected = selectedId == CUSTOM_FONT_ID
    // Preview family for the custom row: the loaded set, or a graceful default.
    val customPreview = customFont?.fontFamily ?: FontFamily.Default

    SectionHeader(header)
    // The app default first, previewed in monospace.
    FontRow(
        name = "Default (Monospace)",
        previewFamily = FontFamily.Monospace,
        selected = selectedId == null || selectedId == DEFAULT_FONT_ID,
        onClick = { onSelect(DEFAULT_FONT_ID) },
    )
    // Custom font (opens the four-variant submenu).
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCustom)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.FontDownload,
            contentDescription = null,
            tint = if (customSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = "Custom font",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = customPreview,
                fontSize = 16.sp,
            )
            Text(
                text = if (customSet.hasAny) "Set regular, italic, bold & bold-italic files"
                else "Choose font files for each style",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        if (customSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

    if (systemFonts.isEmpty()) {
        Text(
            "No installed fonts found on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    } else {
        SectionHeader("Installed fonts")
        systemFonts.forEach { font ->
            FontRow(
                name = font.displayName,
                previewFamily = font.fontFamily,
                selected = font.id == selectedId,
                onClick = { onSelect(font.id) },
            )
        }
    }
}

@Composable
private fun CustomFontVariants(
    set: CustomFontSet,
    customFont: CustomFont?,
    onPick: (FontVariant) -> Unit,
    onClear: (FontVariant) -> Unit,
) {
    SectionHeader("Custom font files")
    Text(
        "Pick a .ttf file for each style. The View and Edit tabs use these for " +
            "the matching syntax-highlighted text (e.g. bold headings use Bold).",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    FontVariant.entries.forEach { variant ->
        val chosenName = set.nameFor(variant)
        val previewFamily = customFont?.fontFamily ?: FontFamily.Default
        val (weight, style) = when (variant) {
            FontVariant.REGULAR -> FontWeight.Normal to FontStyle.Normal
            FontVariant.ITALIC -> FontWeight.Normal to FontStyle.Italic
            FontVariant.BOLD -> FontWeight.Bold to FontStyle.Normal
            FontVariant.BOLD_ITALIC -> FontWeight.Bold to FontStyle.Italic
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onPick(variant) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.FileOpen,
                contentDescription = null,
                tint = if (chosenName != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    variant.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (chosenName != null) previewFamily else FontFamily.Default,
                    fontWeight = if (chosenName != null) weight else FontWeight.Normal,
                    fontStyle = if (chosenName != null) style else FontStyle.Normal,
                    fontSize = 16.sp,
                )
                Text(
                    text = chosenName ?: "Not set — tap to choose a file",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (chosenName != null) {
                IconButton(onClick = { onClear(variant) }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear ${variant.label}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
    if (!set.hasAny) {
        Text(
            "Until at least a Regular file is set, the Custom font falls back to " +
                "the app default (monospace).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FontRow(
    name: String,
    previewFamily: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // The name is shown in the font itself, so the list previews fonts.
            Text(
                name,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = previewFamily,
                fontSize = 16.sp,
            )
            Text(
                "AaBbCc 0123 — the quick brown fox",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = previewFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}
