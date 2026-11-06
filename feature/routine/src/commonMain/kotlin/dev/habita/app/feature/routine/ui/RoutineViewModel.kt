package dev.habita.app.feature.routine.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.feature.routine.data.Checklist
import dev.habita.app.feature.routine.data.Days
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

    data object EditTask : RoutineRoute

    data object EditGroup : RoutineRoute
}

/** The form for adding ([id] `null`) or editing a task. */
internal data class TaskDraft(val id: String?, val groupId: String, val title: String = "", val adjustable: Boolean = false)

/** The form for adding ([id] `null`) or editing a group. */
internal data class GroupDraft(val id: String?, val name: String = "", val days: Int = Days.EVERY_DAY)

internal class RoutineViewModel(private val repository: RoutineRepository, checklist: Flow<Checklist>) : ViewModel() {
    private val _route = MutableStateFlow<RoutineRoute>(RoutineRoute.Today)
    val route: StateFlow<RoutineRoute> = _route.asStateFlow()

    private val _taskDraft = MutableStateFlow<TaskDraft?>(null)
    val taskDraft: StateFlow<TaskDraft?> = _taskDraft.asStateFlow()

    private val _groupDraft = MutableStateFlow<GroupDraft?>(null)
    val groupDraft: StateFlow<GroupDraft?> = _groupDraft.asStateFlow()

    val checklist: StateFlow<Checklist?> = checklist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Kept up to date while the screen exists, since moving and deleting read them directly.

    /** Every group in order, including the ones that are off today. */
    val groups = repository.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggle(taskId: String, done: Boolean) {
        val day = checklist.value?.day ?: return
        viewModelScope.launch { repository.setDone(day.key, taskId, done) }
    }

    fun addTask(groupId: String) {
        open(TaskDraft(null, groupId))
    }

    fun editTask(task: RoutineTask) {
        open(TaskDraft(task.id, task.groupId, task.title, task.adjustable))
    }

    fun addGroup() {
        open(GroupDraft(null))
    }

    fun editGroup(group: RoutineGroup) {
        open(GroupDraft(group.id, group.name, group.days))
    }

    private fun open(draft: TaskDraft) {
        _taskDraft.value = draft
        _route.value = RoutineRoute.EditTask
    }

    private fun open(draft: GroupDraft) {
        _groupDraft.value = draft
        _route.value = RoutineRoute.EditGroup
    }

    fun update(draft: TaskDraft) {
        _taskDraft.value = draft
    }

    fun update(draft: GroupDraft) {
        _groupDraft.value = draft
    }

    fun save(draft: TaskDraft) {
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            repository.saveTask(RoutineTask(draft.id.orEmpty(), draft.groupId, draft.title, draft.adjustable, 0))
        }
        back()
    }

    fun save(draft: GroupDraft) {
        if (draft.name.isBlank()) return
        viewModelScope.launch { repository.saveGroup(RoutineGroup(draft.id.orEmpty(), draft.name, 0, draft.days)) }
        back()
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { repository.deleteTask(taskId) }
        // From the list this does nothing; from the form it closes the form.
        back()
    }

    fun deleteGroup(groupId: String) {
        val group = groups.value.firstOrNull { it.id == groupId } ?: return
        viewModelScope.launch { repository.deleteGroup(group, tasks.value) }
        back()
    }

    /** Moves a task up ([by] -1) or down ([by] 1) within its group. */
    fun moveTask(taskId: String, by: Int) {
        val task = tasks.value.firstOrNull { it.id == taskId } ?: return
        val siblings = tasks.value.filter { it.groupId == task.groupId }.sortedWith(compareBy({ it.position }, { it.id }))
        viewModelScope.launch { repository.moveTask(task, siblings, by) }
    }

    fun moveGroup(groupId: String, by: Int) {
        val group = groups.value.firstOrNull { it.id == groupId } ?: return
        viewModelScope.launch { repository.moveGroup(group, groups.value, by) }
    }

    /** Goes back one screen; `false` when already on the checklist. */
    fun back(): Boolean {
        if (_route.value == RoutineRoute.Today) return false
        _route.value = RoutineRoute.Today
        return true
    }
}