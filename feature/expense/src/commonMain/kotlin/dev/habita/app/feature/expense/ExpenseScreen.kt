package dev.habita.app.feature.expense

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.habita.app.core.ui.HabitaScreen
import dev.habita.app.feature.expense.data.fileMonth
import dev.habita.app.feature.expense.data.toMarkdown
import dev.habita.app.feature.expense.ui.BudgetScreen
import dev.habita.app.feature.expense.ui.EditExpenseScreen
import dev.habita.app.feature.expense.ui.ExpenseRoute
import dev.habita.app.feature.expense.ui.ExpenseViewModel
import dev.habita.app.feature.expense.ui.MonthScreen
import dev.habita.app.feature.expense.ui.PayScreen
import dev.habita.app.feature.expense.ui.rememberFileSaver
import dev.habita.app.feature.expense.ui.rememberSmsAccess
import dev.habita.app.feature.expense.ui.rememberUpi
import kotlinx.coroutines.launch

/** The expense tracker: the month, adding and editing payments, and the budget. */
@Composable
fun ExpenseScreen(feature: ExpenseFeature, onExit: () -> Unit) {
    val viewModel = viewModel { ExpenseViewModel(feature.repository) }
    val route by viewModel.route.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val openRequest by feature.openRequest.collectAsStateWithLifecycle()
    val sms = rememberSmsAccess()
    val upi = rememberUpi()
    val payDraft by viewModel.payDraft.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver { saved ->
        viewModel.showMessage(
            when (saved) {
                true -> "Month saved. Paste it into Notion or hand it to an AI."
                false -> "Couldn't save the file. Try another place."
                null -> null
            },
        )
    }

    // Opened from a payment notification: go straight to that payment.
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        feature.consumeOpenRequest()
        when {
            request.expenseId != null -> viewModel.openEdit(request.expenseId)
            request.scan != null -> viewModel.onScanned(request.scan)
        }
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = { if (!viewModel.back()) onExit() },
    )

    HabitaScreen {
        Crossfade(route) { screen ->
            when (screen) {
                ExpenseRoute.Month -> MonthScreen(
                    month = month,
                    summary = summary,
                    pending = pending,
                    sms = sms,
                    canScan = upi.supported,
                    message = message,
                    deleted = deleted,
                    canGoBack = viewModel.canGoBack,
                    canGoForward = viewModel.canGoForward,
                    onBack = onExit,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    onAdd = { viewModel.openEdit(null) },
                    onScan = { upi.scan(viewModel::onScanned) },
                    onSettle = viewModel::settle,
                    onOpen = { viewModel.openEdit(it) },
                    onSort = viewModel::setCategory,
                    onBudget = viewModel::openBudget,
                    onImport = { scope.launch { viewModel.importSms(sms.inbox(feature.startOfThisMonth())) } },
                    onExport = { summary?.let { saveFile("habita-expenses-${fileMonth(it.month)}.md", it.toMarkdown()) } },
                    onDismissMessage = { viewModel.showMessage(null) },
                    onUndo = viewModel::undoDelete,
                    onDismissUndo = viewModel::dismissUndo,
                )

                is ExpenseRoute.Edit -> draft?.let {
                    EditExpenseScreen(
                        draft = it,
                        categories = categories.filter { category -> !category.archived },
                        onBack = { viewModel.back() },
                        onChange = viewModel::updateDraft,
                        onSave = viewModel::saveDraft,
                        onDelete = viewModel::deleteDraft,
                    )
                }

                ExpenseRoute.Pay -> payDraft?.let {
                    PayScreen(
                        draft = it,
                        categories = categories.filter { category -> !category.archived },
                        onBack = { viewModel.back() },
                        onChange = viewModel::updatePay,
                        onPay = { amount ->
                            scope.launch {
                                val (paymentId, uri) = viewModel.startPay(amount) ?: return@launch
                                upi.pay(uri) { status -> viewModel.onPayResult(paymentId, amount, status) }
                            }
                        },
                    )
                }

                ExpenseRoute.Budget -> BudgetScreen(
                    income = summary?.incomePaise ?: 0,
                    categories = categories.filter { !it.archived },
                    onBack = { viewModel.back() },
                    onSave = viewModel::saveBudget,
                )
            }
        }
    }
}