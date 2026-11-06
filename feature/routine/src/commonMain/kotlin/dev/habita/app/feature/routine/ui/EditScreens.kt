package dev.habita.app.feature.routine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import dev.habita.app.feature.routine.data.Days
import dev.habita.app.feature.routine.data.RoutineGroup

/** Adding or editing a task: its name, its group, and whether it's fixed or adjustable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditTaskScreen(
    draft: TaskDraft,
    groups: List<RoutineGroup>,
    onBack: () -> Unit,
    onChange: (TaskDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    EditForm(
        title = if (draft.id == null) "Add task" else "Edit task",
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
        Gap(Space.section)
        Overline("Group")
        Gap(Space.item)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.inside), verticalArrangement = Arrangement.spacedBy(Space.inside)) {
            groups.forEach { group ->
                Chip(group.name, selected = group.id == draft.groupId, onClick = { onChange(draft.copy(groupId = group.id)) })
            }
        }
        Gap(Space.section)
        Overline("Timing")
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
            Chip("Fixed", selected = !draft.adjustable, onClick = { onChange(draft.copy(adjustable = false)) }, modifier = Modifier.weight(1f))
            Chip("Adjustable", selected = draft.adjustable, onClick = { onChange(draft.copy(adjustable = true)) }, modifier = Modifier.weight(1f))
        }
    }
}

/** Adding or editing a group: its name and the days it shows on. */
@Composable
internal fun EditGroupScreen(
    draft: GroupDraft,
    onBack: () -> Unit,
    onChange: (GroupDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    EditForm(
        title = if (draft.id == null) "Add group" else "Edit group",
        isNew = draft.id == null,
        canSave = draft.name.isNotBlank(),
        deleteText = "Delete group and its tasks",
        onBack = onBack,
        onSave = onSave,
        onDelete = onDelete,
    ) { focus ->
        LabeledField(
            label = "Name",
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (draft.name.isNotBlank()) onSave() }),
            fieldModifier = Modifier.focusRequester(focus),
        )
        Gap(Space.section)
        Overline("Shows on")
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.tiny)) {
            Days.letters.forEachIndexed { index, letter ->
                val bit = 1 shl index
                Chip(
                    letter,
                    selected = draft.days and bit != 0,
                    onClick = { onChange(draft.copy(days = draft.days xor bit)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Gap(Space.inside)
        HText(Days.label(draft.days), type.caption, color = colors.muted)
    }
}

/** The frame both forms share: title, fields, delete for an existing item, and Save at the bottom. */
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
    LaunchedEffect(Unit) { if (isNew) focus.requestFocus() }
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