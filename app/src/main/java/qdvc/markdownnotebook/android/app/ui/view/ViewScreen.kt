package qdvc.markdownnotebook.android.app.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.markdownnotebook.android.app.model.OpenNote
import qdvc.markdownnotebook.android.app.ui.components.rememberSyntaxColors
import qdvc.markdownnotebook.android.app.ui.theme.MonoTextStyle
import qdvc.markdownnotebook.android.app.util.MarkdownHighlighter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewScreen(note: OpenNote?) {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        if (note == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val annotated = remember(note.savedContent, note.draftContent, syntaxColors) {
            MarkdownHighlighter.highlight(note.draftContent, syntaxColors)
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = annotated,
                style = MonoTextStyle.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
