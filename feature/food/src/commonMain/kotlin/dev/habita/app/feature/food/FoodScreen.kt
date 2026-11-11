package dev.habita.app.feature.food

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaScreen
import dev.habita.app.core.ui.ScreenTransition
import dev.habita.app.core.ui.TextAction
import dev.habita.app.feature.food.ui.FoodRoute
import dev.habita.app.feature.food.ui.FoodViewModel
import dev.habita.app.feature.food.ui.MealsScreen
import dev.habita.app.feature.food.ui.OrderScreen
import dev.habita.app.feature.food.ui.PantryScreen
import dev.habita.app.feature.food.ui.PlanScreen
import dev.habita.app.feature.food.ui.RecipeScreen
import dev.habita.app.feature.food.ui.RefillScreen
import dev.habita.app.feature.food.ui.rememberTextCopier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Food: the day's meals and recipes, the pantry, refills, and the plan behind them. */
@Composable
fun FoodScreen(feature: FoodFeature, onExit: () -> Unit) {
    val viewModel = viewModel { FoodViewModel(feature) }
    val route by viewModel.route.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val planText by viewModel.planText.collectAsStateWithLifecycle()
    val pantry by viewModel.pantry.collectAsStateWithLifecycle()
    val made by viewModel.made.collectAsStateWithLifecycle()
    val unlogged by viewModel.unlogged.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()
    val question by viewModel.refillQuestion.collectAsStateWithLifecycle()
    val spend by viewModel.spend.collectAsStateWithLifecycle()
    val lines by viewModel.refillLines.collectAsStateWithLifecycle()
    val copied by viewModel.copied.collectAsStateWithLifecycle()
    val planProblems by viewModel.planProblems.collectAsStateWithLifecycle()
    val orderResult by viewModel.orderResult.collectAsStateWithLifecycle()
    val logging by viewModel.logging.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val openRequest by feature.openRequest.collectAsStateWithLifecycle()
    val copy = rememberTextCopier()
    val scope = rememberCoroutineScope()

    // Opened from the refill notification: straight to the refill.
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        if (request.refill) viewModel.open(FoodRoute.Refill)
        feature.consumeOpenRequest()
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(5_000)
            viewModel.showMessage(null)
        }
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = { if (!viewModel.back()) onExit() },
    )

    HabitaScreen {
        ScreenTransition(route, isForward = { from, to -> to.depth > from.depth }) { screen ->
            when (screen) {
                FoodRoute.Today -> MealsScreen(
                    plan = plan,
                    today = day,
                    made = made,
                    unlogged = unlogged,
                    pantry = pantry,
                    onLogDay = viewModel::logDay,
                    refillQuestion = question,
                    onBack = onExit,
                    onMeal = { viewModel.open(FoodRoute.Recipe(it.id)) },
                    onPantry = { viewModel.open(FoodRoute.Pantry) },
                    onRefill = { viewModel.open(FoodRoute.Refill) },
                    onPlan = { viewModel.open(FoodRoute.Plan) },
                )

                is FoodRoute.Recipe -> plan?.meals?.firstOrNull { it.id == screen.mealId }?.let { meal ->
                    RecipeScreen(meal, onBack = { viewModel.back() })
                }

                FoodRoute.Pantry -> {
                    val today = day
                    val items = pantry
                    if (today != null && items != null) {
                        PantryScreen(
                            pantry = items,
                            today = today,
                            refillQuestion = question,
                            onBack = { viewModel.back() },
                            onCount = viewModel::count,
                            onBought = viewModel::bought,
                            onOpened = viewModel::opened,
                            onExpires = viewModel::expires,
                            onFinished = viewModel::finished,
                            onRefill = { viewModel.open(FoodRoute.Refill) },
                            onLogOrder = { viewModel.open(FoodRoute.Order) },
                        )
                    }
                }

                FoodRoute.Refill -> day?.let { today ->
                    RefillScreen(
                        lines = lines,
                        spend = spend,
                        today = today,
                        copied = copied,
                        onBack = { viewModel.back() },
                        onPacks = viewModel::setPacks,
                        onCopy = {
                            scope.launch {
                                copy(viewModel.requestText())
                                viewModel.markCopied()
                            }
                        },
                        onNotNow = viewModel::snooze,
                        onLogOrder = { viewModel.open(FoodRoute.Order) },
                    )
                }

                FoodRoute.Plan -> PlanScreen(planText, planProblems, onBack = { viewModel.back() }, onSave = viewModel::savePlan)

                FoodRoute.Order -> OrderScreen(orderResult, logging, onBack = { viewModel.back() }, onRead = viewModel::readOrder, onLog = viewModel::logOrder)
            }
        }
        message?.let { text -> MessageCard(text, Modifier.align(Alignment.BottomCenter)) { viewModel.showMessage(null) } }
    }
}

/** A short note about what just happened, at the bottom, gone after a few seconds. */
@Composable
private fun MessageCard(text: String, modifier: Modifier, onDismiss: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(Space.screen)) {
        HabitaCard(Modifier.fillMaxWidth(), color = HabitaTheme.colors.inverseCard) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HText(text, HabitaTheme.typography.body, color = HabitaTheme.colors.onInverseCard, modifier = Modifier.weight(1f))
                TextAction("OK", onDismiss, color = HabitaTheme.colors.onInverseCard)
            }
        }
    }
}

/** How deep a screen is, so transitions know which way to slide. */
private val FoodRoute.depth: Int
    get() = when (this) {
        FoodRoute.Today -> 0
        is FoodRoute.Recipe, FoodRoute.Pantry, FoodRoute.Refill, FoodRoute.Plan -> 1
        FoodRoute.Order -> 2
    }