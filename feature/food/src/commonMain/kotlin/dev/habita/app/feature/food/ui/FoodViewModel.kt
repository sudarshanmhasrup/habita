package dev.habita.app.feature.food.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.feature.food.FoodFeature
import dev.habita.app.feature.food.data.FoodPlan
import dev.habita.app.feature.food.data.FoodSpend
import dev.habita.app.feature.food.data.ItemStock
import dev.habita.app.feature.food.data.Meal
import dev.habita.app.feature.food.data.Order
import dev.habita.app.feature.food.data.OrderResult
import dev.habita.app.feature.food.data.OrderText
import dev.habita.app.feature.food.data.PlanResult
import dev.habita.app.feature.food.data.Refill
import dev.habita.app.feature.food.data.RefillLine
import dev.habita.app.feature.food.data.trim
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

    /** The meal wasn't eaten today: its ingredients go back in the pantry. */
    fun skip(meal: Meal) {
        val tracked = meal.ingredients.mapNotNull { ingredient -> ingredient.itemId?.let { it to ingredient.amount!! } }
        viewModelScope.launch {
            repository.add(tracked, note = "Skipped ${meal.name}")
            _message.value = "Put ${meal.name}'s ingredients back in the pantry."
        }
    }

    /** [packs] of an item are left right now. */
    fun count(stock: ItemStock, packs: Double) {
        viewModelScope.launch {
            repository.count(stock.item.id, packs * stock.item.packSize)
            _message.value = "${stock.item.name}: ${trim(packs)} left."
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

    fun logOrder(order: Order) {
        viewModelScope.launch {
            _message.value = feature.logOrder(order)
            _orderResult.value = null
            _copied.value = false
            _route.value = FoodRoute.Pantry
        }
    }
}