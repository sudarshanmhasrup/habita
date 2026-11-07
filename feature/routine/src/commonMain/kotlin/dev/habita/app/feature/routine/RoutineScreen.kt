package dev.habita.app.feature.routine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.habita.app.core.ui.HabitaScreen
import dev.habita.app.core.ui.ScreenTransition
import dev.habita.app.feature.routine.ui.ChecklistScreen
import dev.habita.app.feature.routine.ui.EditRoutineScreen
import dev.habita.app.feature.routine.ui.EditSectionScreen
import dev.habita.app.feature.routine.ui.EditTaskScreen
import dev.habita.app.feature.routine.ui.RoutineRoute
import dev.habita.app.feature.routine.ui.RoutineViewModel

/** The daily routine: today's checklist, and behind Edit, arranging and changing it. */
@Composable
fun RoutineScreen(feature: RoutineFeature, onExit: () -> Unit) {
    val viewModel = viewModel { RoutineViewModel(feature.repository, feature.checklist) }
    val route by viewModel.route.collectAsStateWithLifecycle()
    val checklist by viewModel.checklist.collectAsStateWithLifecycle()
    val taskDraft by viewModel.taskDraft.collectAsStateWithLifecycle()
    val sectionDraft by viewModel.sectionDraft.collectAsStateWithLifecycle()
    val editFrom by viewModel.editFrom.collectAsStateWithLifecycle()
    val canUndoReset by viewModel.canUndoReset.collectAsStateWithLifecycle()

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = { if (!viewModel.back()) onExit() },
    )

    HabitaScreen {
        // Deeper screens slide in from the right: checklist, then the edit list, then a form.
        ScreenTransition(route, isForward = { from, to -> to.depth > from.depth }) { screen ->
            when (screen) {
                RoutineRoute.Today -> checklist?.let {
                    ChecklistScreen(
                        checklist = it,
                        canUndoReset = canUndoReset,
                        onBack = onExit,
                        onEdit = { group -> viewModel.edit(group) },
                        onToggle = viewModel::toggle,
                        onReset = viewModel::reset,
                        onUndoReset = viewModel::undoReset,
                    )
                }

                RoutineRoute.Edit -> checklist?.let {
                    EditRoutineScreen(
                        sections = it.groups,
                        startAt = editFrom,
                        onDone = { viewModel.back() },
                        onEditTask = viewModel::editTask,
                        onDeleteTask = viewModel::deleteTask,
                        onAddTask = viewModel::addTask,
                        onEditSection = viewModel::editSection,
                        onDeleteSection = viewModel::deleteSection,
                        onAddSection = viewModel::addSection,
                        onSaveOrder = viewModel::saveOrder,
                    )
                }

                RoutineRoute.EditTask -> taskDraft?.let { draft ->
                    EditTaskScreen(
                        draft = draft,
                        onBack = { viewModel.back() },
                        onChange = viewModel::update,
                        onSave = { viewModel.save(draft) },
                        onDelete = { draft.id?.let(viewModel::deleteTask) },
                    )
                }

                RoutineRoute.EditSection -> sectionDraft?.let { draft ->
                    EditSectionScreen(
                        draft = draft,
                        onBack = { viewModel.back() },
                        onChange = viewModel::update,
                        onSave = { viewModel.save(draft) },
                        onDelete = { draft.id?.let(viewModel::deleteSection) },
                    )
                }
            }
        }
    }
}

/** How deep a screen is, so transitions know which way to slide. */
private val RoutineRoute.depth: Int
    get() = when (this) {
        RoutineRoute.Today -> 0
        RoutineRoute.Edit -> 1
        RoutineRoute.EditTask, RoutineRoute.EditSection -> 2
    }