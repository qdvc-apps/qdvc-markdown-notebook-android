package qdvc.markdownnotebook.android.app.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.markdownnotebook.android.app.model.FontSizes
import qdvc.markdownnotebook.android.app.model.OpenNote
import qdvc.markdownnotebook.android.app.ui.components.MarkdownVisualTransformation
import qdvc.markdownnotebook.android.app.ui.components.rememberSyntaxColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    note: OpenNote?,
    fontFamily: FontFamily,
    fontSize: Float,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val syntaxColors = rememberSyntaxColors()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = note?.displayName ?: "No note open",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    // Save shown only when there are unsaved changes.
                    if (note != null && note.hasUnsavedChanges) {
                        IconButton(onClick = onSave) {
                            Icon(Icons.Filled.Save, contentDescription = "Save note")
                        }
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
        if (note == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        // Keep cursor state locally; push text changes up to the ViewModel.
        val transformation = remember(syntaxColors, fontFamily, fontSize) {
            MarkdownVisualTransformation(syntaxColors, fontFamily, fontSize)
        }
        val fieldValue = remember(note.documentUri) {
            androidx.compose.runtime.mutableStateOf(TextFieldValue(note.draftContent))
        }
        // If the draft was reset externally (e.g. after save), re-sync text.
        if (fieldValue.value.text != note.draftContent &&
            note.draftContent == note.savedContent
        ) {
            fieldValue.value = fieldValue.value.copy(text = note.draftContent)
        }

        // imePadding shrinks the editor's viewport when the keyboard is up, so
        // the whole note stays scrollable and reachable above the keyboard.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            BasicTextField(
                value = fieldValue.value,
                onValueChange = {
                    fieldValue.value = it
                    onDraftChange(it.text)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = fontFamily,
                    fontSize = fontSize.sp,
                    lineHeight = FontSizes.lineHeightFor(fontSize).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = transformation,
            )
        }
    }
}
