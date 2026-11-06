package dev.habita.app.feature.routine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.ControlShape
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.Pill
import dev.habita.app.core.ui.ProgressBar
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.routine.data.Checklist
import dev.habita.app.feature.routine.data.Days
import dev.habita.app.feature.routine.data.RoutineGroup
import dev.habita.app.feature.routine.data.RoutineTask

/**
 * Today's checklist: how far along the day is, then each group that shows today with its tasks.
 * A tap ticks a task. In edit mode every group shows, off days included, and a tap opens the task
 * or group instead, with arrows to reorder.
 */
@Composable
internal fun ChecklistScreen(
    checklist: Checklist,
    editing: Boolean,
    groups: List<RoutineGroup>,
    tasks: List<RoutineTask>,
    onBack: () -> Unit,
    onEditing: (Boolean) -> Unit,
    onToggle: (taskId: String, done: Boolean) -> Unit,
    onEditTask: (RoutineTask) -> Unit,
    onAddTask: (groupId: String) -> Unit,
    onMoveTask: (taskId: String, by: Int) -> Unit,
    onEditGroup: (RoutineGroup) -> Unit,
    onAddGroup: () -> Unit,
    onMoveGroup: (groupId: String, by: Int) -> Unit,
) {
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    TwoPane(
        Modifier.fillMaxSize(),
        first = {
            TopBar("Home", onBack) {
                TextAction(if (editing) "Done" else "Edit", { onEditing(!editing) })
            }
            Gap(Space.inside)
            HText(if (editing) "Edit routine" else "Routine", type.headline)
            Gap(Space.item)
            Overview(checklist, editing, Modifier.appear(0))
        },
        second = {
            if (wide) Gap(56.dp)
            if (editing) {
                val byGroup = tasks.groupBy { it.groupId }
                groups.forEachIndexed { index, group ->
                    EditGroupSection(
                        group = group,
                        tasks = byGroup[group.id].orEmpty(),
                        first = index == 0,
                        last = index == groups.lastIndex,
                        onEditGroup = onEditGroup,
                        onMoveGroup = onMoveGroup,
                        onEditTask = onEditTask,
                        onAddTask = onAddTask,
                        onMoveTask = onMoveTask,
                    )
                    Gap(Space.section)
                }
                SecondaryButton("Add group", onAddGroup, Modifier.fillMaxWidth())
            } else {
                checklist.groups.forEachIndexed { index, section ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Overline(section.group.name, Modifier.weight(1f))
                        HText("${section.done}/${section.tasks.size}", type.caption, color = HabitaTheme.colors.muted)
                    }
                    Gap(Space.inside)
                    HabitaCard(Modifier.fillMaxWidth().appear(index + 1), contentPadding = PaddingValues(vertical = Space.tiny)) {
                        section.tasks.forEachIndexed { row, item ->
                            if (row > 0) Divider(Modifier.padding(horizontal = Space.card))
                            TaskRow(item.task, item.done, onClick = { onToggle(item.task.id, !item.done) })
                        }
                        if (section.tasks.isEmpty()) {
                            HText("No tasks yet.", type.body, color = HabitaTheme.colors.muted, modifier = Modifier.padding(Space.card))
                        }
                    }
                    Gap(Space.section)
                }
            }
            Gap(Space.card)
        },
    )
}

/** Done out of total, a bar, and what comes next. */
@Composable
private fun Overview(checklist: Checklist, editing: Boolean, modifier: Modifier) {
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
                editing -> "Tap a task or group to change it. Arrows move it."
                checklist.total == 0 -> "Nothing on the list today."
                next == null -> "All done for today. It starts again at midnight."
                else -> "Next: ${next.second.title}"
            },
            type.body,
            color = colors.muted,
        )
        if (!editing && checklist.off.isNotEmpty()) {
            Gap(Space.tiny)
            HText("Off today: " + checklist.off.joinToString { it.name }, type.caption, color = colors.muted)
        }
    }
}

/** A task with its tick, struck through once done. */
@Composable
private fun TaskRow(task: RoutineTask, done: Boolean, onClick: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = if (done) "Done" else "Not done" }
            .padding(horizontal = Space.card, vertical = Space.item),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(if (done) Modifier.background(colors.accent) else Modifier.border(1.5.dp, colors.border, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) HIcon(HabitaIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onAccent)
        }
        Gap(Space.item)
        HText(
            task.title,
            if (done) type.body.copy(textDecoration = TextDecoration.LineThrough) else type.body,
            color = if (done) colors.muted else colors.text,
            modifier = Modifier.weight(1f),
        )
        if (task.adjustable) {
            Gap(Space.inside)
            Pill("Adjustable", color = colors.muted)
        }
    }
}

@Composable
private fun EditGroupSection(
    group: RoutineGroup,
    tasks: List<RoutineTask>,
    first: Boolean,
    last: Boolean,
    onEditGroup: (RoutineGroup) -> Unit,
    onMoveGroup: (String, Int) -> Unit,
    onEditTask: (RoutineTask) -> Unit,
    onAddTask: (String) -> Unit,
    onMoveTask: (String, Int) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val sorted = tasks.sortedWith(compareBy({ it.position }, { it.id }))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(ControlShape)
                    .clickable(role = Role.Button, onClickLabel = "Edit ${group.name}") { onEditGroup(group) }
                    .padding(vertical = Space.tiny),
            ) {
                Overline(group.name, color = colors.accent)
                HText(Days.label(group.days), type.caption, color = colors.muted)
            }
            Arrow(HabitaIcons.ChevronUp, "Move ${group.name} up", enabled = !first) { onMoveGroup(group.id, -1) }
            Arrow(HabitaIcons.ChevronDown, "Move ${group.name} down", enabled = !last) { onMoveGroup(group.id, 1) }
        }
        Gap(Space.inside)
        HabitaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.tiny)) {
            sorted.forEachIndexed { index, task ->
                if (index > 0) Divider(Modifier.padding(horizontal = Space.card))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClickLabel = "Edit") { onEditTask(task) }
                        .padding(start = Space.card, end = Space.tiny),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HText(task.title, type.body, modifier = Modifier.weight(1f).padding(vertical = Space.item))
                    if (task.adjustable) Pill("Adjustable", color = colors.muted)
                    Arrow(HabitaIcons.ChevronUp, "Move up", enabled = index > 0) { onMoveTask(task.id, -1) }
                    Arrow(HabitaIcons.ChevronDown, "Move down", enabled = index < sorted.lastIndex) { onMoveTask(task.id, 1) }
                }
            }
            if (sorted.isNotEmpty()) Divider(Modifier.padding(horizontal = Space.card))
            TextAction("+ Add task", { onAddTask(group.id) }, Modifier.padding(horizontal = Space.inside, vertical = Space.tiny))
        }
    }
}

@Composable
private fun Arrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.25f)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        HIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = HabitaTheme.colors.muted)
    }
}