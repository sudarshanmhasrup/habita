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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.routine.data.Checklist
import dev.habita.app.feature.routine.data.ChecklistGroup
import dev.habita.app.feature.routine.data.ChecklistTask
import dev.habita.app.feature.routine.data.dayOf

/**
 * Today's checklist, laid out like the routine is written: the sections in order, each headed
 * Fixed or Adjustable, each task a checkbox row. A tap ticks a task. Nothing else is on the page:
 * changing the routine happens behind Edit ([EditRoutineScreen]).
 */
@Composable
internal fun ChecklistScreen(
    checklist: Checklist,
    onBack: () -> Unit,
    onEdit: (groupId: String?) -> Unit,
    onToggle: (taskId: String, done: Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    TwoPane(
        Modifier.fillMaxSize(),
        first = {
            TopBar("Home", onBack) { TextAction("Edit", { onEdit(null) }) }
            Gap(Space.inside)
            HText("Routine", type.headline)
            Gap(Space.tiny)
            HText("Keep an eye on these and get them done ASAP. Reset when you start over.", type.body, color = colors.muted)
            Gap(Space.item)
            Overview(checklist, onReset, Modifier.appear(0))
        },
        second = {
            if (wide) Gap(56.dp)
            if (checklist.groups.isEmpty()) {
                HText("Nothing on the list. Tap Edit to add a section.", type.body, color = colors.muted)
            }
            checklist.groups.forEachIndexed { index, section ->
                Section(section, Modifier.appear(index + 1), onToggle) { onEdit(section.group.id) }
                Gap(Space.section)
            }
        },
    )
}

/** Done out of total, a bar, what comes next, and Reset, which asks once before unticking. */
@Composable
private fun Overview(checklist: Checklist, onReset: () -> Unit, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var confirming by remember(checklist.period) { mutableStateOf(false) }
    HabitaCard(modifier.fillMaxWidth()) {
        Overline("Since ${dayOf(checklist.period.startedAt).title}", color = colors.accent)
        Gap(Space.inside)
        if (confirming) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                HText("Untick all ${checklist.total} tasks?", type.label, modifier = Modifier.weight(1f))
                TextAction("Cancel", { confirming = false }, color = colors.muted)
                TextAction("Reset", { confirming = false; onReset() }, color = colors.error)
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                HText("${checklist.done} of ${checklist.total} done", type.title, modifier = Modifier.weight(1f))
                if (checklist.done > 0) TextAction("Reset", { confirming = true })
            }
        }
        Gap(Space.item)
        ProgressBar(if (checklist.total > 0) checklist.done.toFloat() / checklist.total else 0f, height = 6.dp)
        Gap(Space.item)
        val next = checklist.next
        HText(
            when {
                checklist.total == 0 -> "Nothing on the list today."
                next == null -> "All done. Tap Reset to start again."
                else -> "Next: ${next.second.title}"
            },
            type.body,
            color = colors.muted,
        )
    }
}

/** "Fixed" or "Adjustable" over a card of checkbox rows. */
@Composable
private fun Section(section: ChecklistGroup, modifier: Modifier, onToggle: (String, Boolean) -> Unit, onEdit: () -> Unit) {
    val colors = HabitaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(section.group.name, Modifier.weight(1f))
            HText("${section.done} of ${section.tasks.size}", HabitaTheme.typography.caption, color = colors.muted)
            Gap(Space.item)
            // Opens the edit list at this section, so a long routine needs no scrolling back up.
            TextAction("Edit", onEdit, Modifier.heightIn(min = 40.dp))
        }
        Gap(Space.inside)
        if (section.tasks.isEmpty()) {
            HText("No tasks yet.", HabitaTheme.typography.caption, color = colors.muted)
            return@Column
        }
        HabitaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.tiny)) {
            section.tasks.forEachIndexed { index, item ->
                if (index > 0) Divider(Modifier.padding(horizontal = Space.card))
                TaskRow(item, onToggle)
            }
        }
    }
}

@Composable
private fun TaskRow(item: ChecklistTask, onToggle: (String, Boolean) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val task = item.task
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Checkbox) { onToggle(task.id, !item.done) }
            .semantics(mergeDescendants = true) { stateDescription = if (item.done) "Done" else "Not done" }
            .padding(horizontal = Space.card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(item.done)
        Gap(Space.item)
        HText(
            task.title,
            if (item.done) type.body.copy(textDecoration = TextDecoration.LineThrough) else type.body,
            color = if (item.done) colors.muted else colors.text,
            modifier = Modifier.weight(1f).padding(vertical = Space.item),
        )
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