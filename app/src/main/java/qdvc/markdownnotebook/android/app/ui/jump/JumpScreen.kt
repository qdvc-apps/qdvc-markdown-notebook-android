package qdvc.markdownnotebook.android.app.ui.jump

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.markdownnotebook.android.app.model.OpenNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpScreen(
    notes: List<OpenNote>,
    currentUri: String?,
    onSelect: (String) -> Unit,
    onCloseConfirmed: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var reordering by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf<OpenNote?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Open notes") },
                actions = {
                    IconButton(onClick = { reordering = !reordering }) {
                        Icon(
                            if (reordering) Icons.Filled.Check else Icons.Filled.SwapVert,
                            contentDescription = if (reordering) "Done reordering" else "Rearrange notes",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No notes open yet.\nOpen one from Browse to jump between notes here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            itemsIndexed(notes, key = { _, n -> n.documentUri }) { index, note ->
                JumpRow(
                    note = note,
                    isCurrent = note.documentUri == currentUri,
                    reordering = reordering,
                    canMoveUp = index > 0,
                    canMoveDown = index < notes.lastIndex,
                    onSelect = { if (!reordering) onSelect(note.documentUri) },
                    onRequestClose = {
                        if (note.hasUnsavedChanges) pendingClose = note
                        else onCloseConfirmed(note.documentUri)
                    },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                )
            }
        }
    }

    pendingClose?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingClose = null },
            title = { Text("Unsaved changes") },
            text = {
                Text("\"${note.displayName}\" has unsaved changes. Close it and discard your edits?")
            },
            confirmButton = {
                TextButton(onClick = {
                    onCloseConfirmed(note.documentUri)
                    pendingClose = null
                }) {
                    Text("Close anyway", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClose = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun JumpRow(
    note: OpenNote,
    isCurrent: Boolean,
    reordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onRequestClose: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val revealWidthDp = 96.dp
    val revealWidthPx = with(LocalDensity.current) { revealWidthDp.toPx() }
    var revealed by remember(note.documentUri) { mutableStateOf(false) }
    val offset by animateFloatAsState(
        targetValue = if (revealed && !reordering) -revealWidthPx else 0f,
        label = "swipeOffset",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clipToBounds()
    ) {
        // Red close action behind the row: an X above a "Close" label.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .width(revealWidthDp)
                    .fillMaxSize()
                    .clickable(onClick = onRequestClose),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                )
                Text(
                    "Close",
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Foreground row, draggable horizontally to reveal the close button.
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offset }
                .background(
                    if (isCurrent) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface
                )
                .then(
                    if (!reordering) Modifier.pointerInput(note.documentUri) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount < -8f) revealed = true
                                else if (dragAmount > 8f) revealed = false
                            },
                        )
                    } else Modifier
                )
                .clickable(enabled = !reordering) {
                    if (revealed) revealed = false else onSelect()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isCurrent) {
                Icon(
                    Icons.Filled.Circle,
                    contentDescription = "Current note",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(10.dp),
                )
            } else {
                Box(Modifier.size(10.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = note.displayName + if (note.hasUnsavedChanges) " •" else "",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                )
                Text(
                    text = note.workspaceName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }

            if (reordering) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
            }
        }
    }
}

