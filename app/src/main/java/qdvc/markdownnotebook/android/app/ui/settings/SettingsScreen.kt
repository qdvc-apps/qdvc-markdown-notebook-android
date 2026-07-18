package qdvc.markdownnotebook.android.app.ui.settings

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.markdownnotebook.android.app.model.DarkStyle
import qdvc.markdownnotebook.android.app.model.ThemeMode

private enum class SettingsPage { ROOT, APPEARANCE, DARK_STYLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    darkStyle: DarkStyle,
    onThemeMode: (ThemeMode) -> Unit,
    onDarkStyle: (DarkStyle) -> Unit,
    onClose: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }

    val title = when (page) {
        SettingsPage.ROOT -> "Settings"
        SettingsPage.APPEARANCE -> "Appearance"
        SettingsPage.DARK_STYLE -> "Dark Mode Style"
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
                            SettingsPage.DARK_STYLE -> SettingsPage.ROOT
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when (page) {
                SettingsPage.ROOT -> {
                    SectionHeader("Display")
                    NavRow(
                        icon = { Icon(Icons.Filled.DarkMode, null, tint = MaterialTheme.colorScheme.primary) },
                        title = "Appearance",
                        subtitle = themeMode.label,
                        onClick = { page = SettingsPage.APPEARANCE },
                    )
                    NavRow(
                        icon = { Icon(Icons.Filled.Contrast, null, tint = MaterialTheme.colorScheme.primary) },
                        title = "Dark Mode Style",
                        subtitle = darkStyle.label,
                        onClick = { page = SettingsPage.DARK_STYLE },
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

                SettingsPage.DARK_STYLE -> {
                    SectionHeader("When dark mode is active")
                    DarkStyle.entries.forEach { style ->
                        ChoiceRow(
                            label = style.label,
                            subtitle = when (style) {
                                DarkStyle.REGULAR -> "Dark grey surfaces and backgrounds."
                                DarkStyle.PURE_BLACK -> "True black for larger surfaces, best on OLED screens."
                            },
                            selected = style == darkStyle,
                            onClick = { onDarkStyle(style) },
                        )
                    }
                }
            }
        }
    }
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
