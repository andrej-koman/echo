package dev.andrej.echo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.R
import dev.andrej.echo.data.DictionaryRepository
import dev.andrej.echo.data.DictionaryTerm
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DictionaryViewModel(private val dictionary: DictionaryRepository) : ViewModel() {

    val terms: StateFlow<List<DictionaryTerm>> = dictionary.terms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A new term without [id], a rename with one. Blank text is ignored. */
    fun save(id: Long?, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { if (id == null) dictionary.add(text) else dictionary.rename(id, text) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dictionary.delete(id) }
    }
}

@Composable
fun DictionaryScreen(
    terms: List<DictionaryTerm>,
    onSave: (id: Long?, text: String) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    val submit = {
        onSave(editingId, text)
        text = ""
        editingId = null
    }

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Dictionary",
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_left,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EchoTheme.spacing.gutterScreen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            Text(
                text = "Names and words Echo should spell your way — people, places, jargon. " +
                    "Used for new transcriptions.",
                style = EchoTheme.typography.bodySm,
                color = EchoTheme.colors.textSecondary,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TermField(
                    value = text,
                    onValueChange = { text = it },
                    onDone = submit,
                    modifier = Modifier.weight(1f),
                )
                EchoButton(
                    text = if (editingId == null) "Add" else "Save",
                    onClick = submit,
                    size = ButtonSize.Sm,
                    enabled = text.isNotBlank(),
                )
                if (editingId != null) {
                    EchoButton(
                        text = "Cancel",
                        onClick = {
                            text = ""
                            editingId = null
                        },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Sm,
                    )
                }
            }

            if (terms.isNotEmpty()) {
                EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
                    Column(modifier = Modifier.padding(horizontal = EchoTheme.spacing.s4)) {
                        terms.forEachIndexed { index, term ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingId = term.id
                                        text = term.term
                                    }
                                    .padding(vertical = EchoTheme.spacing.s3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = term.term,
                                    style = EchoTheme.typography.body,
                                    color = if (term.id == editingId) {
                                        EchoTheme.colors.textAccent
                                    } else {
                                        EchoTheme.colors.textPrimary
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                EchoIconButton(
                                    iconRes = R.drawable.ic_trash,
                                    contentDescription = "Delete ${term.term}",
                                    tint = EchoTheme.colors.textTertiary,
                                    onClick = {
                                        if (term.id == editingId) {
                                            editingId = null
                                            text = ""
                                        }
                                        onDelete(term.id)
                                    },
                                )
                            }
                            if (index != terms.lastIndex) {
                                HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TermField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EchoTheme.colors
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(EchoTheme.radii.pill)
            .background(colors.surfaceCard)
            .hairline(colors.borderDefault, cornerRadius = 21.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(text = "Add a name or word", style = EchoTheme.typography.bodySm, color = colors.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = EchoTheme.typography.bodySm.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.textAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
