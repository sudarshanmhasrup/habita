package dev.habita.app.feature.routine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.ProgressBar
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.routine.data.Checklist
import dev.habita.app.feature.routine.data.ChecklistGroup
import dev.habita.app.feature.routine.data.ChecklistTask
import dev.habita.app.feature.routine.data.Days
import dev.habita.app.feature.routine.data.RoutineGroup
import dev.habita.app.feature.routine.data.RoutineTask

/** What is waiting for a second tap on its bin before it goes. */
private sealed interface PendingDelete {
    data class Task(val id: String) : PendingDelete

    data class Group(val id: String) : PendingDelete
}

/**
 * Today's checklist, laid out like the routine is written: each group, then its fixed tasks and
 * its adjustable ones, each a checkbox row. A tap ticks a task. Every task and group has a pencil
 * to edit it and a bin to delete it; the bin asks once more before deleting. Groups that are off
 * today come last, without checkboxes, so they can still be edited.
 */
@Composable
internal fun ChecklistScreen(
    checklist: Checklist,
    onBack: () -> Unit,
    onToggle: (taskId: String, done: Boolean) -> Unit,
    onEditTask: (RoutineTask) -> Unit,
    onDeleteTask: (taskId: String) -> Unit,
    onAddTask: (groupId: String) -> Unit,
    onEditGroup: (RoutineGroup) -> Unit,
    onDeleteGroup: (groupId: String) -> Unit,
    onAddGroup: () -> Unit,
) {
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    var pending by remember { mutableStateOf<PendingDelete?>(null) }
    val actions = RowActions(
        pending = pending,
        onPending = { pending = it },
        onEditTask = onEditTask,
        onDeleteTask = { pending = null; onDeleteTask(it) },
        onEditGroup = onEditGroup,
        onDeleteGroup = { pending = null; onDeleteGroup(it) },
        onAddTask = onAddTask,
    )
    TwoPane(
        Modifier.fillMaxSize(),
        first = {
            TopBar("Home", onBack)
            Gap(Space.inside)
            HText("Routine", type.headline)
            Gap(Space.item)
            Overview(checklist, Modifier.appear(0))
        },
        second = {
            if (wide) Gap(56.dp)
            checklist.groups.forEachIndexed { index, section ->
                GroupSection(section, on = true, actions, Modifier.appear(index + 1), onToggle)
                Gap(Space.section)
            }
            checklist.off.forEach { section ->
                GroupSection(section, on = false, actions, Modifier, onToggle)
                Gap(Space.section)
            }
            SecondaryButton("Add group", onAddGroup, Modifier.fillMaxWidth())
            Gap(Space.card)
        },
    )
}

/** Everything a row needs to edit or delete, passed down as one. */
private class RowActions(
    val pending: PendingDelete?,
    val onPending: (PendingDelete?) -> Unit,
    val onEditTask: (RoutineTask) -> Unit,
    val onDeleteTask: (String) -> Unit,
    val onEditGroup: (RoutineGroup) -> Unit,
    val onDeleteGroup: (String) -> Unit,
    val onAddTask: (String) -> Unit,
)

/** Done out of total, a bar, and what comes next. */
@Composable
private fun Overview(checklist: Checklist, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(modifier.fillMaxWidth()) {
        Overline(checklist.day.title, color = colors.accent)
        Gap(Space.inside)
        HText("${checklist.done} of ${checklist.total} done", type.title)
        Gap(Space.item)
        ProgressBar(if (checklist.total > 0) checklist.done.toFloat() / checklist.total else 0f, height = 6.dp)
        Gap(Space.item)
        val next = checklist.next
        HText(
            when {
                checklist.total == 0 -> "Nothing on the list today."
                next == null -> "All done for today. It starts again at midnight."
                else -> "Next: ${next.second.title}"
            },
            type.body,
            color = colors.muted,
        )
    }
}

/** A group's name with its edit and delete icons, then its Fixed and Adjustable tasks. */
@Composable
private fun ColumnScope.GroupSection(
    section: ChecklistGroup,
    on: Boolean,
    actions: RowActions,
    modifier: Modifier,
    onToggle: (String, Boolean) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val group = section.group
    Column(modifier.fillMaxWidth()) {
        if (actions.pending == PendingDelete.Group(group.id)) {
            ConfirmDelete(
                text = "Delete ${group.name} and its ${section.tasks.size} tasks?",
                onDelete = { actions.onDeleteGroup(group.id) },
                onCancel = { actions.onPending(null) },
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HText(group.name, type.title, color = if (on) colors.text else colors.muted)
                    HText(
                        if (on) "${section.done} of ${section.tasks.size} done" else "Off today · ${Days.label(group.days)}",
                        type.caption,
                        color = colors.muted,
                    )
                }
                RowIcon(HabitaIcons.Pencil, "Edit ${group.name}") { actions.onEditGroup(group) }
                RowIcon(HabitaIcons.Trash, "Delete ${group.name}") { actions.onPending(PendingDelete.Group(group.id)) }
            }
        }
        Gap(Space.item)
        TaskList("Fixed", section.fixed, on, actions, onToggle)
        if (section.adjustable.isNotEmpty()) {
            Gap(Space.card)
            TaskList("Adjustable", section.adjustable, on, actions, onToggle)
        }
        TextAction("+ Add task", { actions.onAddTask(group.id) }, Modifier.padding(top = Space.tiny))
    }
}

/** A heading such as "Fixed" over a card of checkbox rows. */
@Composable
private fun TaskList(title: String, tasks: List<ChecklistTask>, on: Boolean, actions: RowActions, onToggle: (String, Boolean) -> Unit) {
    if (tasks.isEmpty()) return
    Column {
        Overline(title)
        Gap(Space.inside)
        HabitaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.tiny)) {
            tasks.forEachIndexed { index, item ->
                if (index > 0) Divider(Modifier.padding(horizontal = Space.card))
                if (actions.pending == PendingDelete.Task(item.task.id)) {
                    Box(Modifier.padding(horizontal = Space.card, vertical = Space.tiny)) {
                        ConfirmDelete(
                            text = "Delete \"${item.task.title}\"?",
                            onDelete = { actions.onDeleteTask(item.task.id) },
                            onCancel = { actions.onPending(null) },
                        )
                    }
                } else {
                    TaskRow(item, on, actions, onToggle)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(item: ChecklistTask, on: Boolean, actions: RowActions, onToggle: (String, Boolean) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val task = item.task
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (on) {
                    Modifier
                        .clickable(role = Role.Checkbox) { onToggle(task.id, !item.done) }
                        .semantics(mergeDescendants = true) { stateDescription = if (item.done) "Done" else "Not done" }
                } else {
                    Modifier
                },
            )
            .padding(start = Space.card, end = Space.tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            CheckBox(item.done)
            Gap(Space.item)
        }
        HText(
            task.title,
            if (item.done) type.body.copy(textDecoration = TextDecoration.LineThrough) else type.body,
            color = if (item.done || !on) colors.muted else colors.text,
            modifier = Modifier.weight(1f).padding(vertical = Space.item),
        )
        RowIcon(HabitaIcons.Pencil, "Edit") { actions.onEditTask(task) }
        RowIcon(HabitaIcons.Trash, "Delete") { actions.onPending(PendingDelete.Task(task.id)) }
    }
}

/** A square checkbox, filled with a tick once done. */
@Composable
private fun CheckBox(done: Boolean) {
    val colors = HabitaTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(24.dp)
            .clip(shape)
            .then(if (done) Modifier.background(colors.accent) else Modifier.border(1.5.dp, colors.muted, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) HIcon(HabitaIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onAccent)
    }
}

/** "Delete …?" with Delete and Cancel, in place of the row being deleted. */
@Composable
private fun ConfirmDelete(text: String, onDelete: () -> Unit, onCancel: () -> Unit) {
    val colors = HabitaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        HText(text, HabitaTheme.typography.label, modifier = Modifier.weight(1f), maxLines = 2)
        TextAction("Cancel", onCancel, color = colors.muted)
        TextAction("Delete", onDelete, color = colors.error)
    }
}

@Composable
private fun RowIcon(icon: ImageVector, label: String, tint: Color = HabitaTheme.colors.muted, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        HIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
    }
}