package dev.habita.app.feature.routine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TopBar

/** Adding or renaming a task. Where it sits is changed by dragging it in the edit list. */
@Composable
internal fun EditTaskScreen(
    draft: TaskDraft,
    onBack: () -> Unit,
    onChange: (TaskDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    EditForm(
        title = if (draft.id == null) "Add task" else "Rename task",
        isNew = draft.id == null,
        canSave = draft.title.isNotBlank(),
        deleteText = "Delete task",
        onBack = onBack,
        onSave = onSave,
        onDelete = onDelete,
    ) { focus ->
        LabeledField(
            label = "Task",
            value = draft.title,
            onValueChange = { onChange(draft.copy(title = it)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (draft.title.isNotBlank()) onSave() }),
            fieldModifier = Modifier.focusRequester(focus),
        )
    }
}

/** Adding a section, or switching one between Fixed and Adjustable. */
@Composable
internal fun EditSectionScreen(
    draft: SectionDraft,
    onBack: () -> Unit,
    onChange: (SectionDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    EditForm(
        title = if (draft.id == null) "Add section" else "Change section",
        isNew = draft.id == null,
        canSave = true,
        deleteText = "Delete section and its tasks",
        onBack = onBack,
        onSave = onSave,
        onDelete = onDelete,
    ) { _ ->
        Overline("Kind")
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
            Chip("Fixed", selected = !draft.adjustable, onClick = { onChange(draft.copy(adjustable = false)) }, modifier = Modifier.weight(1f))
            Chip("Adjustable", selected = draft.adjustable, onClick = { onChange(draft.copy(adjustable = true)) }, modifier = Modifier.weight(1f))
        }
        Gap(Space.inside)
        HText(
            if (draft.adjustable) "Tasks that can move around the day." else "Tasks done in this order, at this point of the day.",
            type.caption,
            color = colors.muted,
        )
    }
}

/**
 * The frame both forms share: title, fields, then for an existing item a button to delete it,
 * which asks once more, and Save at the bottom.
 */
@Composable
private fun EditForm(
    title: String,
    isNew: Boolean,
    canSave: Boolean,
    deleteText: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    fields: @Composable ColumnScope.(FocusRequester) -> Unit,
) {
    val colors = HabitaTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    // Something new starts with the keyboard up on its name.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isNew) runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.inside),
        ) {
            TopBar("Routine", onBack)
            Gap(Space.inside)
            HText(title, HabitaTheme.typography.headline)
            Gap(Space.card)
            fields(focus)
            if (!isNew) {
                Gap(Space.section)
                SecondaryButton(
                    text = if (confirmDelete) "Tap again to delete" else deleteText,
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.error,
                )
            }
            Gap(Space.card)
        }
        PrimaryButton(
            text = "Save",
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item),
        )
    }
}