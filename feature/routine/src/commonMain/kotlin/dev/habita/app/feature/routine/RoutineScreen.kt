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
import dev.habita.app.feature.routine.ui.EditGroupScreen
import dev.habita.app.feature.routine.ui.EditTaskScreen
import dev.habita.app.feature.routine.ui.RoutineRoute
import dev.habita.app.feature.routine.ui.RoutineViewModel

/** The daily routine: today's checklist, and editing its groups and tasks. */
@Composable
fun RoutineScreen(feature: RoutineFeature, onExit: () -> Unit) {
    val viewModel = viewModel { RoutineViewModel(feature.repository, feature.checklist) }
    val route by viewModel.route.collectAsStateWithLifecycle()
    val checklist by viewModel.checklist.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val taskDraft by viewModel.taskDraft.collectAsStateWithLifecycle()
    val groupDraft by viewModel.groupDraft.collectAsStateWithLifecycle()

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = { if (!viewModel.back()) onExit() },
    )

    HabitaScreen {
        // The checklist is the root: a form slides in from the right and back out to the left.
        ScreenTransition(route, isForward = { from, to -> from == RoutineRoute.Today && to != RoutineRoute.Today }) { screen ->
            when (screen) {
                RoutineRoute.Today -> checklist?.let {
                    ChecklistScreen(
                        checklist = it,
                        editing = editing,
                        groups = groups,
                        tasks = tasks,
                        onBack = onExit,
                        onEditing = viewModel::setEditing,
                        onToggle = viewModel::toggle,
                        onEditTask = viewModel::editTask,
                        onAddTask = viewModel::addTask,
                        onMoveTask = viewModel::moveTask,
                        onEditGroup = viewModel::editGroup,
                        onAddGroup = viewModel::addGroup,
                        onMoveGroup = viewModel::moveGroup,
                    )
                }

                RoutineRoute.EditTask -> taskDraft?.let { draft ->
                    EditTaskScreen(
                        draft = draft,
                        groups = groups,
                        onBack = { viewModel.back() },
                        onChange = viewModel::update,
                        onSave = { viewModel.save(draft) },
                        onDelete = { draft.id?.let(viewModel::deleteTask) },
                    )
                }

                RoutineRoute.EditGroup -> groupDraft?.let { draft ->
                    EditGroupScreen(
                        draft = draft,
                        onBack = { viewModel.back() },
                        onChange = viewModel::update,
                        onSave = { viewModel.save(draft) },
                        onDelete = { draft.id?.let(viewModel::deleteGroup) },
                    )
                }
            }
        }
    }
}