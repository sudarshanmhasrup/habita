package dev.habita.app.feature.routine.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.feature.routine.data.ChecklistGroup
import dev.habita.app.feature.routine.data.RoutineGroup
import dev.habita.app.feature.routine.data.RoutineTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** One line of the editable list: a section's header or one of its tasks. */
private sealed interface Line {
    val key: String

    data class Header(val group: RoutineGroup) : Line {
        override val key = "g:${group.id}"
    }

    data class Task(val task: RoutineTask) : Line {
        override val key = "t:${task.id}"
    }
}

/** The lines being dragged (a task, or a header with its tasks), where they started, and how far. */
private data class Drag(val keys: List<String>, val isSection: Boolean, val offset: Float)

/**
 * Changing the routine. Every task and section header has a handle: drag a task anywhere, into
 * another section too, and drag a header to move its whole section. Tap a task to rename it and
 * a header to make it Fixed or Adjustable; the bin deletes after asking once more.
 */
@Composable
internal fun EditRoutineScreen(
    sections: List<ChecklistGroup>,
    startAt: String?,
    onDone: () -> Unit,
    onEditTask: (RoutineTask) -> Unit,
    onDeleteTask: (taskId: String) -> Unit,
    onAddTask: (groupId: String) -> Unit,
    onEditSection: (RoutineGroup) -> Unit,
    onDeleteSection: (groupId: String) -> Unit,
    onAddSection: () -> Unit,
    onSaveOrder: (List<Pair<RoutineGroup, List<RoutineTask>>>) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val scroll = rememberScrollState()
    val saved = remember(sections) { sections.flatMap { section -> listOf(Line.Header(section.group)) + section.tasks.map { Line.Task(it.task) } } }
    // A dropped order shows straight away, before the saved one comes back.
    var dropped by remember(saved) { mutableStateOf<List<Line>?>(null) }
    val lines = dropped ?: saved
    val heights = remember { mutableStateMapOf<String, Int>() }
    var drag by remember { mutableStateOf<Drag?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var viewport by remember { mutableStateOf(0) }
    var listTop by remember { mutableStateOf(0f) }

    // Opened from a section's Edit: scroll to that section once it's laid out. Only the first
    // time, so coming back from renaming a task keeps the list where it was.
    var scrolledToStart by remember { mutableStateOf(false) }
    LaunchedEffect(startAt) {
        if (startAt == null || scrolledToStart) return@LaunchedEffect
        val key = "g:$startAt"
        snapshotFlow { heights[key] to listTop }.first { (height, top) -> height != null && top > 0f }
        scroll.scrollTo((listTop + (topsOf(lines, heights)[key] ?: 0)).toInt())
        scrolledToStart = true
    }

    val preview = drag?.let { previewOrder(lines, it, heights) } ?: lines
    val tops = topsOf(lines, heights)
    val previewTops = topsOf(preview, heights)

    // Scrolls while a dragged row is held near the top or bottom, so it can go anywhere.
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    LaunchedEffect(drag != null) {
        // Reads the live drag each time, never a copy from the last redraw: once the finger
        // lifts, the drag is gone and this stops, rather than bringing it back.
        while (true) {
            val now = drag ?: break
            val top = listTop + (topsOf(lines, heights)[now.keys.first()] ?: 0) + now.offset - scroll.value
            val bottom = top + now.keys.sumOf { heights[it] ?: 0 }
            val speed = when {
                top < edge -> -(edge - top) / 4
                bottom > viewport - edge -> (bottom - (viewport - edge)) / 4
                else -> 0f
            }
            if (speed != 0f) {
                val moved = scroll.scrollBy(speed.coerceIn(-40f, 40f))
                drag = drag?.let { it.copy(offset = it.offset + moved) }
            }
            delay(16)
        }
    }

    fun drop() {
        val finished = drag ?: return
        drag = null
        val order = previewOrder(lines, finished, heights)
        if (order.map { it.key } == lines.map { it.key }) return
        dropped = order
        onSaveOrder(sectionsOf(order))
    }

    Column(Modifier.fillMaxSize()) {
        // Done stays at the top while the list scrolls, so leaving never means scrolling back up.
        TopBar(null, {}, Modifier.padding(start = Space.screen, end = Space.screen, top = Space.inside)) {
            TextAction("Done", onDone)
        }
        Column(
            Modifier
                .weight(1f)
                .onSizeChanged { viewport = it.height }
                .verticalScroll(scroll, enabled = drag == null)
                .padding(horizontal = Space.screen, vertical = Space.inside),
        ) {
            HText("Edit routine", type.headline)
            Gap(Space.tiny)
            HText(
                "Drag a task by its handle, into another section too. Drag a section's header to move the whole section.",
                type.body,
                color = colors.muted,
            )
            Gap(Space.card)
            Column(Modifier.fillMaxWidth().onGloballyPositioned { listTop = it.positionInParent().y }) {
                lines.forEach { line ->
                    key(line.key) {
                        val dragging = drag?.keys?.contains(line.key) == true
                        val shift by animateFloatAsState(
                            targetValue = if (drag == null) 0f else ((previewTops[line.key] ?: 0) - (tops[line.key] ?: 0)).toFloat(),
                            animationSpec = if (drag == null) snap() else tween(150),
                            label = "shift",
                        )
                        val handle = Modifier.pointerInput(line.key, lines) {
                            detectDragGestures(
                                onDragStart = {
                                    pendingDelete = null
                                    drag = startDrag(lines, line)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag = drag?.let { it.copy(offset = it.offset + amount.y) }
                                },
                                onDragEnd = { drop() },
                                onDragCancel = { drag = null },
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { heights[line.key] = it.height }
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) drag?.offset ?: 0f else shift },
                        ) {
                            when (line) {
                                is Line.Header -> HeaderLine(
                                    group = line.group,
                                    first = line == lines.first(),
                                    lifted = dragging,
                                    handle = handle,
                                    confirming = pendingDelete == line.key,
                                    onEdit = { onEditSection(line.group) },
                                    onAddTask = { onAddTask(line.group.id) },
                                    onAskDelete = { pendingDelete = line.key },
                                    onDelete = { pendingDelete = null; onDeleteSection(line.group.id) },
                                    onCancel = { pendingDelete = null },
                                    count = lines.dropWhile { it != line }.drop(1).takeWhile { it is Line.Task }.size,
                                )

                                is Line.Task -> TaskLine(
                                    task = line.task,
                                    lifted = dragging,
                                    handle = handle,
                                    confirming = pendingDelete == line.key,
                                    onEdit = { onEditTask(line.task) },
                                    onAskDelete = { pendingDelete = line.key },
                                    onDelete = { pendingDelete = null; onDeleteTask(line.task.id) },
                                    onCancel = { pendingDelete = null },
                                )
                            }
                        }
                    }
                }
            }
            Gap(Space.card)
            SecondaryButton("Add section", onAddSection, Modifier.fillMaxWidth())
            Gap(Space.card)
        }
    }
}

@Composable
private fun HeaderLine(
    group: RoutineGroup,
    first: Boolean,
    lifted: Boolean,
    handle: Modifier,
    confirming: Boolean,
    count: Int,
    onEdit: () -> Unit,
    onAddTask: () -> Unit,
    onAskDelete: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = HabitaTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = if (first) 0.dp else Space.section)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(if (lifted) Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).background(colors.card, RoundedCornerShape(12.dp)) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (confirming) {
                Confirm("Delete this ${group.name} section and its $count tasks?", onDelete, onCancel)
                return@Row
            }
            Handle(handle, "Move this ${group.name} section")
            Box(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Change section", onClick = onEdit).padding(vertical = Space.item)) {
                Overline(group.name, color = colors.accent)
            }
            TextAction("+ Task", onAddTask)
            Icon(HabitaIcons.Trash, "Delete this ${group.name} section", onAskDelete)
        }
    }
}

@Composable
private fun TaskLine(
    task: RoutineTask,
    lifted: Boolean,
    handle: Modifier,
    confirming: Boolean,
    onEdit: () -> Unit,
    onAskDelete: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (lifted) Modifier.shadow(8.dp, shape) else Modifier)
            .background(colors.card, shape)
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (confirming) {
            Box(Modifier.padding(horizontal = Space.item)) { Confirm("Delete \"${task.title}\"?", onDelete, onCancel) }
            return@Row
        }
        Handle(handle, "Move ${task.title}")
        HText(
            task.title,
            HabitaTheme.typography.body,
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Rename", onClick = onEdit)
                .padding(vertical = Space.item),
        )
        Icon(HabitaIcons.Trash, "Delete ${task.title}", onAskDelete)
    }
}

/** The grip a row is dragged by. Dragging here never scrolls the page. */
@Composable
private fun Handle(handle: Modifier, label: String) {
    Box(
        handle
            .size(48.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        HIcon(HabitaIcons.Grip, contentDescription = null, modifier = Modifier.size(20.dp), tint = HabitaTheme.colors.muted)
    }
}

@Composable
private fun Icon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        HIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = HabitaTheme.colors.muted)
    }
}

/** "Delete …?" with Cancel and Delete, in place of the row being deleted. */
@Composable
private fun Confirm(text: String, onDelete: () -> Unit, onCancel: () -> Unit) {
    val colors = HabitaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        HText(text, HabitaTheme.typography.label, modifier = Modifier.weight(1f), maxLines = 2)
        TextAction("Cancel", onCancel, color = colors.muted)
        TextAction("Delete", onDelete, color = colors.error)
    }
}

/** A task drags alone; a header drags with every task under it. */
private fun startDrag(lines: List<Line>, line: Line): Drag = when (line) {
    is Line.Task -> Drag(listOf(line.key), isSection = false, offset = 0f)
    is Line.Header -> {
        val from = lines.indexOf(line)
        val section = listOf(line) + lines.drop(from + 1).takeWhile { it is Line.Task }
        Drag(section.map { it.key }, isSection = true, offset = 0f)
    }
}

/** Where every line's top is, stacked in [lines] order. */
private fun topsOf(lines: List<Line>, heights: Map<String, Int>): Map<String, Int> {
    var top = 0
    return lines.associate { line -> (line.key to top).also { top += heights[line.key] ?: 0 } }
}

/**
 * [lines] with the dragged ones moved to where they are held: a task between any two lines after
 * the first header, a section between two sections.
 */
private fun previewOrder(lines: List<Line>, drag: Drag, heights: Map<String, Int>): List<Line> {
    val moving = lines.filter { it.key in drag.keys }
    val rest = lines.filter { it.key !in drag.keys }
    val start = topsOf(lines, heights)[drag.keys.first()] ?: 0
    val center = start + drag.offset + moving.sumOf { heights[it.key] ?: 0 } / 2f
    val restTops = topsOf(rest, heights)
    if (!drag.isSection) {
        // After the first header at least, so every task stays in a section.
        val before = rest.count { (restTops[it.key] ?: 0) + (heights[it.key] ?: 0) / 2f < center }
        val at = before.coerceIn(if (rest.firstOrNull() is Line.Header) 1 else 0, rest.size)
        return rest.take(at) + moving + rest.drop(at)
    }
    // Sections move between sections: count the ones whose middle is above the dragged one's.
    val blocks = mutableListOf<MutableList<Line>>()
    rest.forEach { if (it is Line.Header || blocks.isEmpty()) blocks += mutableListOf(it) else blocks.last() += it }
    val before = blocks.count { block ->
        val top = restTops[block.first().key] ?: 0
        top + block.sumOf { heights[it.key] ?: 0 } / 2f < center
    }
    return blocks.take(before).flatten() + moving + blocks.drop(before).flatten()
}

/** The lines back as sections, each header with the tasks under it. */
private fun sectionsOf(lines: List<Line>): List<Pair<RoutineGroup, List<RoutineTask>>> {
    val result = mutableListOf<Pair<RoutineGroup, MutableList<RoutineTask>>>()
    lines.forEach { line ->
        when (line) {
            is Line.Header -> result += line.group to mutableListOf()
            is Line.Task -> result.lastOrNull()?.second?.add(line.task)
        }
    }
    return result
}