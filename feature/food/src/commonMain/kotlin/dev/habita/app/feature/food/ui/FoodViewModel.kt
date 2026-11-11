package dev.habita.app.feature.food.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.feature.food.FoodFeature
import dev.habita.app.feature.food.data.FoodPlan
import dev.habita.app.feature.food.data.FoodSpend
import dev.habita.app.feature.food.data.ItemStock
import dev.habita.app.feature.food.data.Order
import dev.habita.app.feature.food.data.OrderResult
import dev.habita.app.feature.food.data.OrderText
import dev.habita.app.feature.food.data.PlanResult
import dev.habita.app.feature.food.data.Refill
import dev.habita.app.feature.food.data.RefillLine
import dev.habita.app.feature.food.data.dateLabel
import dev.habita.app.feature.food.data.parseDate
import dev.habita.app.feature.food.data.trim
import dev.habita.app.feature.food.data.whenLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface FoodRoute {
    /** The day's meals. */
    data object Today : FoodRoute

    /** One meal's recipe. */
    data class Recipe(val mealId: String) : FoodRoute

    data object Pantry : FoodRoute

    data object Refill : FoodRoute

    /** Writing or pasting the plan. */
    data object Plan : FoodRoute

    /** Pasting an order to log. */
    data object Order : FoodRoute
}

internal class FoodViewModel(private val feature: FoodFeature) : ViewModel() {
    private val repository = feature.repository

    private val _route = MutableStateFlow<FoodRoute>(FoodRoute.Today)
    val route: StateFlow<FoodRoute> = _route.asStateFlow()

    val plan: StateFlow<FoodPlan?> = repository.plan.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val planText: StateFlow<String?> = repository.planText.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val pantry: StateFlow<List<ItemStock>?> = feature.pantry.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val made: StateFlow<Map<Int, Set<String>>> = feature.made.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val unlogged: StateFlow<List<Int>> = feature.unlogged.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val day: StateFlow<Int?> = feature.day.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val refillQuestion: StateFlow<String?> = feature.refillQuestion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val spend: StateFlow<FoodSpend?> = feature.foodSpend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Packs changed by hand on the refill screen, by item id; 0 leaves an item out. */
    private val packChanges = MutableStateFlow<Map<String, Int>>(emptyMap())

    val refillLines: StateFlow<List<RefillLine>> = combine(feature.pantry, packChanges) { pantry, changes ->
        pantry?.let(Refill::lines).orEmpty().map { line -> changes[line.stock.item.id]?.let { line.copy(packs = it) } ?: line }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _planProblems = MutableStateFlow<List<String>>(emptyList())
    val planProblems: StateFlow<List<String>> = _planProblems.asStateFlow()

    private val _orderResult = MutableStateFlow<OrderResult?>(null)

    /** The pasted order as read, before it is logged. */
    val orderResult: StateFlow<OrderResult?> = _orderResult.asStateFlow()

    /** Whether the refill request was copied, so the screen can say what's next. */
    private val _copied = MutableStateFlow(false)
    val copied: StateFlow<Boolean> = _copied.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun open(route: FoodRoute) {
        if (route == FoodRoute.Refill) {
            packChanges.value = emptyMap()
            _copied.value = false
        }
        if (route == FoodRoute.Order) _orderResult.value = null
        if (route == FoodRoute.Plan) _planProblems.value = emptyList()
        _route.value = route
    }

    /** One step back; `false` when already on the day's meals, so the caller leaves food. */
    fun back(): Boolean {
        val current = _route.value
        _route.value = when (current) {
            FoodRoute.Today -> return false
            FoodRoute.Order -> if (_copied.value) FoodRoute.Refill else FoodRoute.Pantry
            else -> FoodRoute.Today
        }
        return true
    }

    fun showMessage(text: String?) {
        _message.value = text
    }

    /** On [day], exactly the meals in [mealIds] were made. */
    fun logDay(day: Int, mealIds: Set<String>) {
        val total = plan.value?.meals?.size ?: 0
        viewModelScope.launch {
            repository.logDay(day, mealIds)
            _message.value = when {
                mealIds.isEmpty() -> "${dayName(day)}: nothing made. The pantry stays as it was."
                mealIds.size == total -> "${dayName(day)}: all $total meals made."
                else -> "${dayName(day)}: ${mealIds.size} of $total meals made."
            }
        }
    }

    private fun dayName(day: Int): String = if (day == this.day.value) "Today" else dateLabel(day)

    /** Opened a new pack of an item on [openedOn], today or a few days back. */
    fun opened(stock: ItemStock, openedOn: Int) {
        val today = day.value ?: return
        viewModelScope.launch {
            repository.opened(stock.item.id, openedOn)
            val finishBy = stock.item.openDays?.let { whenLabel(openedOn + it, today) }
            _message.value = "${stock.item.name}: opened${finishBy?.let { ", finish it by $it" }.orEmpty()}."
        }
    }

    /**
     * The pack in use is best before the date in [text], such as "14/11"; blank clears it. Returns
     * `false` when the date doesn't read, so the field can say so.
     */
    fun expires(stock: ItemStock, text: String): Boolean {
        val today = day.value ?: return false
        val date = if (text.isBlank()) null else parseDate(text, today) ?: return false
        viewModelScope.launch {
            repository.expires(stock.item.id, date)
            _message.value = date?.let { "${stock.item.name}: best before ${dateLabel(it)}." } ?: "${stock.item.name}: best-before date cleared."
        }
        return true
    }

    /** The pack in use is finished: its opened day and best-before date are cleared. */
    fun finished(stock: ItemStock) {
        viewModelScope.launch {
            repository.finished(stock.item.id)
            _message.value = "${stock.item.name}: pack finished."
        }
    }

    /** [packs] of an item are left right now. */
    fun count(stock: ItemStock, packs: Double) {
        viewModelScope.launch {
            repository.count(stock.item.id, packs * stock.item.packSize)
            _message.value = "${stock.item.name}: you have ${trim(packs)} now."
        }
    }

    /** Bought [packs] of an item outside an order, such as at the market. */
    fun bought(stock: ItemStock, packs: Double) {
        viewModelScope.launch {
            repository.add(listOf(stock.item.id to packs * stock.item.packSize), note = "Bought")
            _message.value = "Added ${trim(packs)} to ${stock.item.name}."
        }
    }

    fun savePlan(text: String) {
        viewModelScope.launch {
            when (val result = repository.savePlan(text)) {
                is PlanResult.Ok -> {
                    _planProblems.value = emptyList()
                    _message.value = "Plan saved: ${result.plan.meals.size} meals, ${result.plan.items.size} items."
                    _route.value = FoodRoute.Today
                }
                is PlanResult.Error -> _planProblems.value = result.problems
            }
        }
    }

    fun setPacks(itemId: String, packs: Int) {
        packChanges.value = packChanges.value + (itemId to packs.coerceAtLeast(0))
    }

    /** The request for Claude, with the packs as set on the screen. */
    suspend fun requestText(): String {
        val lines = refillLines.value.filter { it.packs > 0 }
        return Refill.request(lines, spend.value, feature.day.first())
    }

    fun markCopied() {
        _copied.value = true
    }

    /** "Not now": asks again tomorrow. */
    fun snooze() {
        viewModelScope.launch {
            feature.snoozeRefill()
            _message.value = "Okay, I'll ask again tomorrow."
            _route.value = FoodRoute.Today
        }
    }

    fun readOrder(text: String) {
        _orderResult.value = if (text.isBlank()) null else OrderText.parse(text, plan.value)
    }

    private val _logging = MutableStateFlow(false)

    /** Whether an order is being logged, so a second tap can't log it twice. */
    val logging: StateFlow<Boolean> = _logging.asStateFlow()

    fun logOrder(order: Order) {
        if (_logging.value) return
        _logging.value = true
        viewModelScope.launch {
            try {
                _message.value = feature.logOrder(order)
                _orderResult.value = null
                _copied.value = false
                _route.value = FoodRoute.Pantry
            } finally {
                _logging.value = false
            }
        }
    }
}