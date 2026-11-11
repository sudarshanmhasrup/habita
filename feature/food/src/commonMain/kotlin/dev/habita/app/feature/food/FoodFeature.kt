package dev.habita.app.feature.food

import dev.habita.app.core.Core
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.food.data.DayLog
import dev.habita.app.feature.food.data.FoodRepository
import dev.habita.app.feature.food.data.FoodSpend
import dev.habita.app.feature.food.data.ItemStock
import dev.habita.app.feature.food.data.Meal
import dev.habita.app.feature.food.data.Order
import dev.habita.app.feature.food.data.Pantry
import dev.habita.app.feature.food.data.Refill
import dev.habita.app.feature.food.data.dayOf
import dev.habita.app.feature.food.data.utcOffsetMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * How food reaches the expense tracker without the two features knowing each other: the app
 * passes one in. Leave it out and orders only update the pantry.
 */
interface FoodMoney {
    /** Food spending this budget month against its limit, for the refill request. */
    val foodSpend: Flow<FoodSpend?>

    /**
     * Logs a paid order as a payment split by category (Food, Household…), and returns a short
     * line saying what happened, such as "Split the ₹1,259 payment from your bank SMS".
     */
    suspend fun logOrder(order: Order, at: Long): String
}

/**
 * Food: the day's meals with their recipes, the pantry worked out from the meals made, and refills that
 * ask first. Habita doesn't order anything itself: a refill copies a request for Claude, which
 * fills the cheapest cart for the user to pay, and the order comes back as text to log here.
 * Create it once at startup.
 */
class FoodFeature(core: Core, internal val money: FoodMoney? = null) {
    internal val repository = FoodRepository(core)

    /**
     * Every screen shares one copy of each flow below: the pantry is worked out once per change,
     * not once per collector. They stop a few seconds after the last collector leaves.
     */
    private val sharing = SharingStarted.WhileSubscribed(5_000)

    /** The local day and minute of the day, moving on while the app is open. */
    private val clock: Flow<Pair<Int, Int>> = flow {
        while (true) {
            val now = nowMillis()
            emit(dayOf(now) to minuteOfDay(now))
            delay(60_000)
        }
    }.distinctUntilChanged().shareIn(core.scope, sharing, replay = 1)

    internal val day: Flow<Int> = clock.map { it.first }.distinctUntilChanged().shareIn(core.scope, sharing, replay = 1)

    /** The logged days: day → ids of the meals made. */
    internal val made: Flow<Map<Int, Set<String>>> = repository.made.shareIn(core.scope, sharing, replay = 1)

    /** The pantry as of today, or `null` before there is a plan. */
    internal val pantry: Flow<List<ItemStock>?> =
        combine(repository.plan, repository.stockEvents, made, repository.packDates, day) { plan, events, made, packs, day ->
            plan?.let { Pantry.of(it, events, day, made, packs.opened, packs.expires) }
        }.shareIn(core.scope, sharing, replay = 1)

    /** Past days the app asks about because they weren't logged, oldest first. */
    internal val unlogged: Flow<List<Int>> = combine(repository.stockEvents, made, day) { events, made, day ->
        DayLog.unlogged(made, events.minOfOrNull { it.day }, day)
    }.shareIn(core.scope, sharing, replay = 1)

    /** The refill question while it should be asked: app items about to run out, not snoozed. */
    internal val refillQuestion: Flow<String?> = combine(pantry, repository.snoozedThrough, day) { pantry, snoozed, day ->
        val due = pantry?.let(Refill::due).orEmpty()
        if (due.isEmpty() || day <= snoozed) null else Refill.question(due, day)
    }.shareIn(core.scope, sharing, replay = 1)

    internal val foodSpend: Flow<FoodSpend?> = money?.foodSpend ?: flowOf(null)

    private val _openRequest = MutableStateFlow<FoodOpen?>(null)

    /** A request from outside the app, such as a notification, to show food. */
    val openRequest: StateFlow<FoodOpen?> = _openRequest.asStateFlow()

    /** Food at a glance, for the home screen; [FoodToday.hasPlan] is false before there is a plan. */
    val today: Flow<FoodToday> = combine(repository.plan, pantry, refillQuestion, clock) { plan, pantry, question, (_, minute) ->
            plan ?: return@combine FoodToday.NO_PLAN
            val next = nextMeal(plan.meals, minute)
            FoodToday(
                hasPlan = true,
                nextMeal = next?.name,
                nextMealTime = next?.time,
                nextMealProtein = next?.proteinGrams,
                proteinGrams = plan.proteinGrams,
                calories = plan.calories,
                refillQuestion = question,
                runningLow = pantry?.let(Refill::lines)?.size ?: 0,
            )
        }.shareIn(core.scope, sharing, replay = 1)

    /** The refill question if it should be asked now, for a notification. */
    suspend fun refillQuestionNow(): String? = refillQuestion.first()

    /** "Not now": no refill questions until tomorrow. */
    suspend fun snoozeRefill() {
        repository.snoozeThrough(dayOf(nowMillis()))
    }

    /** Shows food next time the app is in front: the refill when [refill] is set. */
    fun requestOpen(refill: Boolean) {
        _openRequest.value = FoodOpen(refill, nowMillis())
    }

    internal fun consumeOpenRequest() {
        _openRequest.value = null
    }

    /**
     * Logs a paid order: pantry items get their packs added, with the price paid per pack, and the
     * money goes to the expense tracker split by category. Returns what happened, for a message.
     */
    internal suspend fun logOrder(order: Order): String {
        val plan = repository.plan.first()
        val tracked = order.lines.mapNotNull { line ->
            val item = line.itemId?.let { plan?.item(it) } ?: return@mapNotNull null
            Triple(item.id, line.quantity * item.packSize, if (line.quantity > 0) (line.pricePaise / line.quantity).toLong() else line.pricePaise)
        }
        if (tracked.isNotEmpty()) {
            repository.add(
                entries = tracked.map { it.first to it.second },
                pricesPaise = tracked.associate { it.first to it.third },
                shop = order.shop,
                note = "${order.shop} order",
            )
        }
        val money = money?.logOrder(order, nowMillis())
        val stock = if (tracked.isEmpty()) "No pantry items in it." else "Added ${tracked.size} item${if (tracked.size == 1) "" else "s"} to the pantry."
        return listOfNotNull(stock, money).joinToString(" ")
    }

    private companion object {
        fun minuteOfDay(now: Long): Int = ((now + utcOffsetMillis(now)).mod(24 * 60 * 60 * 1000L) / 60_000).toInt()

        /** The next meal by its time, after the last one the first again; in plan order without times. */
        fun nextMeal(meals: List<Meal>, minute: Int): Meal? {
            val timed = meals.mapNotNull { meal -> meal.time?.let(::minuteOf)?.let { meal to it } }
            if (timed.isEmpty()) return meals.firstOrNull()
            return timed.filter { it.second > minute - 30 }.minByOrNull { it.second }?.first ?: timed.minBy { it.second }.first
        }

        /** "13:30" or "1:30 PM" as minutes of the day. */
        fun minuteOf(time: String): Int? {
            val match = Regex("(\\d{1,2})[:.](\\d{2})\\s*([AaPp][Mm])?").find(time) ?: return null
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            when (match.groupValues[3].lowercase()) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
        }
    }
}

/** Open food: on the refill when [refill] is set. [at] tells repeats apart. */
data class FoodOpen(val refill: Boolean, val at: Long)

/**
 * Food at a glance: the next meal, the day's totals, and the refill question while it stands.
 * [hasPlan] is false until a plan is saved.
 */
data class FoodToday(
    val hasPlan: Boolean,
    val nextMeal: String?,
    val nextMealTime: String?,
    val nextMealProtein: Int?,
    val proteinGrams: Int,
    val calories: Int,
    val refillQuestion: String?,
    val runningLow: Int,
) {
    internal companion object {
        val NO_PLAN = FoodToday(false, null, null, null, 0, 0, null, 0)
    }
}