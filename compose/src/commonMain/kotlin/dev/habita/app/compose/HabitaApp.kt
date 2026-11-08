package dev.habita.app.compose

import dev.habita.app.core.AppConfig
import dev.habita.app.core.Core
import dev.habita.app.core.data.DatabaseFactory
import dev.habita.app.core.update.AppInstaller
import dev.habita.app.core.update.AppUpdates
import dev.habita.app.feature.expense.ExpenseFeature
import dev.habita.app.feature.expense.OrderPart
import dev.habita.app.feature.expense.PaidBy
import dev.habita.app.feature.expense.data.OrderOutcome
import dev.habita.app.feature.food.FoodFeature
import dev.habita.app.feature.food.FoodMoney
import dev.habita.app.feature.food.data.FoodSpend
import dev.habita.app.feature.food.data.Order
import dev.habita.app.feature.food.data.OrderPayment
import dev.habita.app.feature.food.data.Refill
import dev.habita.app.feature.food.data.trim
import dev.habita.app.feature.gym.GymFeature
import dev.habita.app.feature.routine.RoutineFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The app's object graph. Each platform entry point creates one and keeps it for the life of the
 * process.
 */
class HabitaApp(config: AppConfig, databases: DatabaseFactory, installer: AppInstaller? = null) {
    val core = Core(config, databases)
    val gym = GymFeature(core)
    val expense = ExpenseFeature(core)
    val routine = RoutineFeature(core)
    val food = FoodFeature(core, ExpenseMoney(expense))

    /** Updates from the Habita server, where the platform can install them (Android). */
    val updates: AppUpdates? = installer?.let { AppUpdates(core, it) }

    init {
        core.sync.start()
        updates?.start()
    }
}

/**
 * Food's orders and budget go through the expense tracker: an order becomes a payment split by
 * category, and the refill request shows what's left of the Food limit.
 */
private class ExpenseMoney(private val expense: ExpenseFeature) : FoodMoney {
    override val foodSpend: Flow<FoodSpend?> = expense.spend("food").map { spend ->
        spend?.let { FoodSpend(it.spentPaise, it.category.limitPaise) }
    }

    override suspend fun logOrder(order: Order, at: Long): String {
        val parts = order.lines.map { line ->
            val quantity = if (line.quantity == 1.0) "" else " ×${trim(line.quantity)}"
            OrderPart(line.category, line.pricePaise, line.name + quantity)
        }
        val paidBy = when (order.payment) {
            OrderPayment.BANK -> PaidBy.BANK
            OrderPayment.WALLET -> PaidBy.WALLET
            OrderPayment.CASH -> PaidBy.CASH
        }
        val total = Refill.rupees(order.totalPaise)
        return when (expense.recordOrder("${order.shop} order", at, parts, paidBy)) {
            is OrderOutcome.Matched -> "Split the $total payment from your bank SMS in Expenses."
            is OrderOutcome.Added -> "Added the $total payment to Expenses, split by category."
            OrderOutcome.Waiting -> "The $total payment will be split when its bank SMS comes in."
        }
    }
}