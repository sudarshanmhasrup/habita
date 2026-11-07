package dev.habita.app.feature.routine.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.feature.routine.data.Checklist
import dev.habita.app.feature.routine.data.Sections
import dev.habita.app.feature.routine.data.RoutineGroup
import dev.habita.app.feature.routine.data.RoutineRepository
import dev.habita.app.feature.routine.data.RoutineTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The screens of the routine. */
internal sealed interface RoutineRoute {
    data object Today : RoutineRoute

    /** The list with handles, bins and add buttons. */
    data object Edit : RoutineRoute

    data object EditTask : RoutineRoute

    data object EditSection : RoutineRoute
}

/** The form for adding ([id] `null`) or renaming a task in section [groupId]. */
internal data class TaskDraft(val id: String?, val groupId: String, val title: String = "")

/** The form for adding ([id] `null`) or changing a section. */
internal data class SectionDraft(val id: String?, val adjustable: Boolean = false)

internal class RoutineViewModel(private val repository: RoutineRepository, checklist: Flow<Checklist>) : ViewModel() {
    private val _route = MutableStateFlow<RoutineRoute>(RoutineRoute.Today)
    val route: StateFlow<RoutineRoute> = _route.asStateFlow()

    private val _taskDraft = MutableStateFlow<TaskDraft?>(null)
    val taskDraft: StateFlow<TaskDraft?> = _taskDraft.asStateFlow()

    private val _sectionDraft = MutableStateFlow<SectionDraft?>(null)
    val sectionDraft: StateFlow<SectionDraft?> = _sectionDraft.asStateFlow()

    val checklist: StateFlow<Checklist?> = checklist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Kept up to date while the screen exists, since saving and deleting read them directly.
    private val groups = repository.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggle(taskId: String, done: Boolean) {
        val period = checklist.value?.period ?: return
        viewModelScope.launch { repository.setDone(period.key, taskId, done) }
    }

    /** Unticks everything, starting a new round. */
    fun reset() {
        viewModelScope.launch { repository.reset() }
    }

    fun edit() {
        _route.value = RoutineRoute.Edit
    }

    fun addTask(groupId: String) = open(TaskDraft(null, groupId))

    fun editTask(task: RoutineTask) = open(TaskDraft(task.id, task.groupId, task.title))

    fun addSection() = open(SectionDraft(null))

    fun editSection(group: RoutineGroup) = open(SectionDraft(group.id, Sections.isAdjustable(group)))

    private fun open(draft: TaskDraft) {
        _taskDraft.value = draft
        _route.value = RoutineRoute.EditTask
    }

    private fun open(draft: SectionDraft) {
        _sectionDraft.value = draft
        _route.value = RoutineRoute.EditSection
    }

    fun update(draft: TaskDraft) {
        _taskDraft.value = draft
    }

    fun update(draft: SectionDraft) {
        _sectionDraft.value = draft
    }

    fun save(draft: TaskDraft) {
        if (draft.title.isBlank()) return
        val group = groups.value.firstOrNull { it.id == draft.groupId } ?: return
        viewModelScope.launch {
            repository.saveTask(RoutineTask(draft.id.orEmpty(), draft.groupId, draft.title, Sections.isAdjustable(group), 0))
        }
        back()
    }

    fun save(draft: SectionDraft) {
        viewModelScope.launch { repository.saveSection(draft.id, draft.adjustable, tasks.value) }
        back()
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { repository.deleteTask(taskId) }
        // From the list this changes nothing; from the form it goes back to the list.
        if (_route.value == RoutineRoute.EditTask) back()
    }

    fun deleteSection(groupId: String) {
        val group = groups.value.firstOrNull { it.id == groupId } ?: return
        viewModelScope.launch { repository.deleteGroup(group, tasks.value) }
        if (_route.value == RoutineRoute.EditSection) back()
    }

    /** Saves the order dragged into place in the edit list. */
    fun saveOrder(order: List<Pair<RoutineGroup, List<RoutineTask>>>) {
        viewModelScope.launch { repository.saveOrder(order) }
    }

    /** Goes back one screen: a form to the edit list, the edit list to the checklist. */
    fun back(): Boolean {
        _route.value = when (_route.value) {
            RoutineRoute.Today -> return false
            RoutineRoute.Edit -> RoutineRoute.Today
            RoutineRoute.EditTask, RoutineRoute.EditSection -> RoutineRoute.Edit
        }
        return true
    }
}