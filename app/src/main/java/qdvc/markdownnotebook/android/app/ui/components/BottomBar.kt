package qdvc.markdownnotebook.android.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import qdvc.markdownnotebook.android.app.model.Tab

@Composable
fun BottomBar(
    current: Tab,
    noteOpen: Boolean,
    onSelect: (Tab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        item(Tab.BROWSE, "Browse", Icons.Filled.FolderOpen, current, true, onSelect)
        item(Tab.VIEW, "View", Icons.Filled.Visibility, current, noteOpen, onSelect)
        item(Tab.EDIT, "Edit", Icons.Filled.Edit, current, noteOpen, onSelect)
        item(Tab.JUMP, "Jump", Icons.Outlined.Layers, current, true, onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.item(
    tab: Tab,
    label: String,
    icon: ImageVector,
    current: Tab,
    enabled: Boolean,
    onSelect: (Tab) -> Unit,
) {
    NavigationBarItem(
        selected = current == tab,
        enabled = enabled,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledIconColor = MaterialTheme.colorScheme.outline,
            disabledTextColor = MaterialTheme.colorScheme.outline,
        ),
    )
}
