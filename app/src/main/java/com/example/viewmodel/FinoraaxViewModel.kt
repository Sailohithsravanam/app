package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FinoraaxViewModel(application: Application, private val repository: FinoraaxRepository) : AndroidViewModel(application) {

    init {
        viewModelScope.launch {
            repository.loginAndSync()
            loadChatHistory()
            loadChatSuggestions()
        }
    }

    // --- COHESIVE CORE STATES ---
    val userState: StateFlow<UserEntity?> = repository.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val transactionsState: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingsGoalsState: StateFlow<List<SavingsGoalEntity>> = repository.allSavingsGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val billsState: StateFlow<List<BillEntity>> = repository.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptionsState: StateFlow<List<SubscriptionEntity>> = repository.allSubscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val investmentsState: StateFlow<List<InvestmentEntity>> = repository.allInvestments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notificationsState: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val insightsState: StateFlow<List<FinancialInsightEntity>> = repository.allInsights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- COMPUTED STATES ---
    private val _currentMonth = MutableStateFlow("2026-06")
    val currentMonth: StateFlow<String> = _currentMonth.asStateFlow()

    val budgetsState: StateFlow<List<BudgetEntity>> = currentMonth
        .flatMapLatest { month -> repository.getBudgets(month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Subscription Leak Health Score (Calculated: 100 - sum(impact of forgotten subscriptions))
    val subHealthScoreState: StateFlow<Int> = subscriptionsState.map { subs ->
        val activeForgotten = subs.filter { it.status == "Active" && it.isForgotten }
        val sumImpact = activeForgotten.sumOf { it.scoreImpact }
        (100 - sumImpact).coerceIn(0, 100)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    // --- INTERACTIVE UI CONTEXT STATES ---
    private val _chatHistory = MutableStateFlow(
        listOf(
            Pair("Hi there! Welcome to Finoraax. I am your secure, privacy-first AI Advisor. I've analyzed your June transactions and flagged 3 continuous subscription leaks, including a $55.00/mo gym pass you haven't checked into once. How would you like me to optimize your cash flow today?", true),
        )
    )
    val chatHistory: StateFlow<List<Pair<String, Boolean>>> = _chatHistory.asStateFlow()

    private val _chatSuggestions = MutableStateFlow<List<String>>(emptyList())
    val chatSuggestions: StateFlow<List<String>> = _chatSuggestions.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _advisorAdvice = MutableStateFlow("Click 'Generate Advisor Recommendation' below to launch security audits and custom cash flow suggestions based on your local data.")
    val advisorAdvice: StateFlow<String> = _advisorAdvice.asStateFlow()

    fun loadChatHistory() {
        viewModelScope.launch {
            val history = repository.getChatHistory()
            if (history.isNotEmpty()) {
                _chatHistory.value = history.map { 
                    Pair(it.content, it.role == "assistant")
                }
            } else {
                _chatHistory.value = listOf(
                    Pair("Hi there! Welcome to Finoraax. I am your secure, privacy-first AI Advisor. I've analyzed your June transactions and flagged 3 continuous subscription leaks, including a $55.00/mo gym pass you haven't checked into once. How would you like me to optimize your cash flow today?", true)
                )
            }
        }
    }

    fun loadChatSuggestions() {
        viewModelScope.launch {
            val suggestions = repository.getChatSuggestions()
            _chatSuggestions.value = suggestions
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            if (repository.clearChatHistory()) {
                _chatHistory.value = listOf(
                    Pair("Chat history cleared. How can I help you today?", true)
                )
                loadChatSuggestions()
            }
        }
    }

    // --- DATABASE ACTIONS ---

    fun completeOnboardingStep(step: String) {
        viewModelScope.launch {
            val currentUser = repository.getUserSync() ?: UserEntity(name = "User", email = "")
            val updated = when (step) {
                "privacy" -> currentUser.copy(privacyOnboarded = true)
                "leak" -> currentUser.copy(leakDetectorOnboarded = true)
                "advisor" -> currentUser.copy(advisorOnboarded = true)
                else -> currentUser
            }
            repository.saveUser(updated)
        }
    }

    fun completeProfileRegistration(name: String, email: String, pin: String) {
        viewModelScope.launch {
            val user = UserEntity(
                id = "local_user",
                name = name,
                email = email,
                pinHash = com.example.security.SecurityUtils.hashPin(pin),
                biometricEnabled = true,
                privacyOnboarded = true,
                leakDetectorOnboarded = true,
                advisorOnboarded = true,
                sessionToken = "token_" + System.currentTimeMillis()
            )
            repository.saveUser(user)
            repository.saveUser(user) // Ensure written
        }
    }

    fun unlockVault() {
        viewModelScope.launch {
            repository.loginAndSync()
        }
    }

    fun logOut() {
        viewModelScope.launch {
            val currentUser = repository.getUserSync()
            if (currentUser != null) {
                repository.saveUser(currentUser.copy(sessionToken = null))
            }
        }
    }

    fun addTransaction(type: String, category: String, amount: Double, date: String, note: String, isRecurring: Boolean = false) {
        viewModelScope.launch {
            val transaction = TransactionEntity(
                type = type,
                category = category,
                amount = amount,
                date = date,
                note = note,
                isRecurring = isRecurring,
                isSmartCategorized = true
            )
            repository.addTransaction(transaction)

            // Auto-update standard Category budgets!
            val budgets = repository.getBudgetsSync(date.substring(0, 7))
            val matchingBudget = budgets.find { it.category.equals(category, ignoreCase = true) }
            val totalBudget = budgets.find { it.category == "All" }

            if (type == "EXPENSE") {
                if (matchingBudget != null) {
                    repository.updateBudgetSpent(matchingBudget.id, matchingBudget.spentAmount + amount)
                }
                if (totalBudget != null) {
                    repository.updateBudgetSpent(totalBudget.id, totalBudget.spentAmount + amount)
                }

                // Check for overspending and trigger notifications
                if (matchingBudget != null && (matchingBudget.spentAmount + amount) > matchingBudget.limitAmount) {
                    repository.addNotification(
                        NotificationEntity(
                            title = "Budget Overspend Limit!",
                            message = "$category is overspent! Limit: $${matchingBudget.limitAmount}, Spent: $${matchingBudget.spentAmount + amount}",
                            type = "BUDGET_ALERT"
                        )
                    )
                }
            } else {
                // If Income, check if surplus can be proposed for savings
                if (amount > 1000) {
                    repository.addNotification(
                        NotificationEntity(
                            title = "High Cash Flow Peak!",
                            message = "Captured surplus income of $${amount}. Finoraax Advisor recommends routing 15% directly into your custom emergency goal.",
                            type = "FRAUD_ALERT"
                        )
                    )
                }
            }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun addSavingsGoal(name: String, target: Double, current: Double, date: String, isEmergency: Boolean = false) {
        viewModelScope.launch {
            repository.addSavingsGoal(
                SavingsGoalEntity(
                    name = name,
                    targetAmount = target,
                    currentAmount = current,
                    targetDate = date,
                    isEmergencyFund = isEmergency
                )
            )
        }
    }

    fun updateSavingsGoal(id: Int, currentAmount: Double) {
        viewModelScope.launch {
            repository.updateSavingsGoalProgress(id, currentAmount)
        }
    }

    fun deleteSavingsGoal(id: Int) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(id)
        }
    }

    fun addBill(name: String, amount: Double, dueDate: String, category: String = "Utilities") {
        viewModelScope.launch {
            repository.addBill(
                BillEntity(name = name, amount = amount, dueDate = dueDate, isPaid = false, category = category)
            )
        }
    }

    fun payBill(id: Int) {
        viewModelScope.launch {
            val bills = repository.allBills.first()
            val billToPay = bills.find { it.id == id }
            if (billToPay != null) {
                repository.updateBillPaidStatus(id, true)
                // Also add as expense transaction
                addTransaction(
                    type = "EXPENSE",
                    category = billToPay.category,
                    amount = billToPay.amount,
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    note = "Paid bill: ${billToPay.name}"
                )
            }
        }
    }

    fun deleteBill(id: Int) {
        viewModelScope.launch {
            repository.deleteBill(id)
        }
    }

    fun cancelSubscription(id: Int) {
        viewModelScope.launch {
            repository.updateSubscriptionStatus(id, isForgotten = false, status = "Cancelled")
            val subs = repository.allSubscriptions.first()
            val target = subs.find { it.id == id }
            if (target != null) {
                repository.addNotification(
                    NotificationEntity(
                        title = "Subscription Decommissioned",
                        message = "Sucessfully cancelled ${target.name}. Saving $${target.cost} per month.",
                        type = "LEAK_ALERT"
                    )
                )
            }
        }
    }

    fun keepSubscription(id: Int) {
        viewModelScope.launch {
            // Unflag subscription as a leak (meaning the user acknowledges and relies on it)
            repository.updateSubscriptionStatus(id, isForgotten = false, status = "Active")
        }
    }

    fun addSubscription(name: String, cost: Double, cycle: String, nextRenewal: String, isForgotten: Boolean, leakReason: String) {
        viewModelScope.launch {
            val scoreImpact = if (isForgotten) 20 else 0
            repository.addSubscription(
                SubscriptionEntity(
                    name = name,
                    cost = cost,
                    billingCycle = cycle,
                    nextRenewalDate = nextRenewal,
                    isForgotten = isForgotten,
                    status = "Active",
                    leakReason = leakReason,
                    scoreImpact = scoreImpact,
                    optimizationSuggestion = "Cancel immediately if not utilized."
                )
            )
        }
    }

    fun addInvestment(name: String, type: String, initAmt: Double, currentAmt: Double, units: Double) {
        viewModelScope.launch {
            repository.addInvestment(
                InvestmentEntity(
                    name = name,
                    type = type,
                    initialAmount = initAmt,
                    currentAmount = currentAmt,
                    units = units,
                    purchaseDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                )
            )
        }
    }

    fun updateInvestmentValue(id: Int, valToSet: Double) {
        viewModelScope.launch {
            repository.updateInvestmentValue(id, valToSet)
        }
    }

    fun deleteInvestment(id: Int) {
        viewModelScope.launch {
            repository.deleteInvestment(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearNotifications()
        }
    }

    // --- STATEMENT STATEMENT IMPORTER ---
    fun importStatementFile(context: Context, uri: Uri, onResult: (String) -> Unit) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)
        val fileName = getFileName(context, uri)

        if (fileName.lowercase().endsWith(".pdf") || mimeType == "application/pdf") {
            onResult("PDF statement parsing is not supported in offline mode.")
            return
        }

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = inputStream.bufferedReader().use { it.readText() }
                if (text.isBlank()) {
                    onResult("Selected file is empty.")
                    return
                }
                viewModelScope.launch {
                    val count = importBankStatementText(text)
                    if (count > 0) {
                        onResult("Successfully imported $count records locally!")
                    } else {
                        onResult("No transactions could be parsed from the file.")
                    }
                }
            } ?: run {
                onResult("Unable to open the selected file.")
            }
        } catch (e: Exception) {
            onResult("Error reading statement file: ${e.localizedMessage}")
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = ""
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name.ifEmpty { uri.path?.substringAfterLast('/') ?: "file" }
    }

    fun importBankStatementText(rawText: String): Int {
        var importCount = 0
        val lines = rawText.split("\n")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        for (line in lines) {
            if (line.isBlank()) continue
            try {
                // Remove dates from the line to avoid extracting a date component as the amount
                var cleanLine = line
                cleanLine = cleanLine.replace(Regex("\\d{4}[-/]\\d{2}[-/]\\d{2}"), "")
                cleanLine = cleanLine.replace(Regex("\\d{2}[-/]\\d{2}[-/]\\d{4}"), "")
                cleanLine = cleanLine.replace(Regex("\\d{2}[-/]\\d{2}[-/]\\d{2}"), "")
                cleanLine = cleanLine.replace(Regex("(?i)\\b\\d{1,2}\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*"), "")
                cleanLine = cleanLine.replace(Regex("(?i)\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+\\d{1,2}"), "")

                // Try to extract an amount and category from common statements, e.g. "Grocery: 45" or "Target 120"
                val numbers = Regex("\\d+(\\.\\d+)?").findAll(cleanLine).toList()
                if (numbers.isNotEmpty()) {
                    val amount = numbers.last().value.toDouble()
                    // Determine category representation
                    var category = "Uncategorized"
                    val lower = line.lowercase()
                    when {
                        lower.contains("grocery") || lower.contains("supermarket") || lower.contains("food") -> category = "Groceries"
                        lower.contains("salary") || lower.contains("paycheck") || lower.contains("deposit") -> category = "Salary"
                        lower.contains("dining") || lower.contains("restaurant") || lower.contains("cafe") -> category = "Dining"
                        lower.contains("uber") || lower.contains("gas") || lower.contains("taxi") || lower.contains("flight") -> category = "Utilities"
                        lower.contains("netflix") || lower.contains("spotify") || lower.contains("hulu") || lower.contains("gym") -> category = "Entertainment"
                    }
                    val type = if (category == "Salary" || lower.contains("credit") || lower.contains("income")) "INCOME" else "EXPENSE"
                    val noteStr = line.trim()

                    addTransaction(
                        type = type,
                        category = category,
                        amount = amount,
                        date = today,
                        note = noteStr
                    )
                    importCount++
                }
            } catch (e: Exception) {
                // Skip unparseable line
            }
        }
        return importCount
    }

    // --- AI ADVISOR CHAT & RECOMMENDATION ACTIONS ---

    fun sendMessageToAdvisor(message: String) {
        if (message.isNotBlank()) {
            _chatHistory.value = _chatHistory.value + Pair(message, false)
            _isAnalyzing.value = true

            viewModelScope.launch {
                val currentHistory = _chatHistory.value
                _chatHistory.value = currentHistory + Pair("", true)
                val responseIndex = _chatHistory.value.lastIndex

                _isAnalyzing.value = false
                var accumulated = ""
                repository.streamChatReply(message).collect { chunk ->
                    accumulated += chunk
                    val updatedList = _chatHistory.value.toMutableList()
                    if (responseIndex in updatedList.indices) {
                        updatedList[responseIndex] = Pair(accumulated, true)
                        _chatHistory.value = updatedList
                    }
                }
                loadChatSuggestions()
            }
        }
    }

    fun generateExpertAdvice() {
        _isAnalyzing.value = true
        _advisorAdvice.value = ""
        viewModelScope.launch {
            val userVal = userState.value
            val userName = userVal?.name ?: "User"
            val txs = transactionsState.value
            val subs = subscriptionsState.value
            val budgets = budgetsState.value
            val goals = savingsGoalsState.value
            val bills = billsState.value

            val totalSpent = txs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val totalIncome = txs.filter { it.type == "INCOME" }.sumOf { it.amount }
            val leakySubs = subs.filter { sub -> sub.isForgotten && sub.status == "Active" }
            val activeBudgets = budgets.filter { it.category != "All" }
            val overspentBudgets = budgets.filter { it.spentAmount > it.limitAmount }
            val unpaidBills = bills.filter { !it.isPaid }

            val prompt = """
                You are Finoraax's Smart Financial Advisor Core.
                Conduct a financial wellness assessment on $userName's portfolio:
                - Income: ₹${"%.2f".format(totalIncome)}
                - Spending: ₹${"%.2f".format(totalSpent)}
                - Budget Summary:
                  ${if (activeBudgets.isEmpty()) "No active category budgets." else activeBudgets.joinToString("\n                  ") { "${it.category}: Spent ₹${it.spentAmount} of Limit ₹${it.limitAmount}" }}
                - Flagged Subscription Leaks:
                  ${if (leakySubs.isEmpty()) "No active leaks detected." else leakySubs.joinToString("\n                  ") { "${it.name} (Cost: ₹${it.cost}/mo, Reason: ${it.leakReason})" }}
                - Overspent Budgets:
                  ${if (overspentBudgets.isEmpty()) "No overspent budgets." else overspentBudgets.joinToString("\n                  ") { "${it.category} (Limit: ₹${it.limitAmount}, Spent: ₹${it.spentAmount})" }}
                - Savings Goals Progress:
                  ${if (goals.isEmpty()) "No active savings goals." else goals.joinToString("\n                  ") { "${it.name}: saved ₹${it.currentAmount} / target ₹${it.targetAmount} (${if (it.targetAmount > 0) (it.currentAmount / it.targetAmount * 100).toInt() else 0}%)" }}
                - Unpaid Upcoming Bills:
                  ${if (unpaidBills.isEmpty()) "No unpaid upcoming bills." else unpaidBills.joinToString("\n                  ") { "${it.name} (Amount: ₹${it.amount}, Due: ${it.dueDate})" }}

                Write an executive financial audit report. Under 3 bullet points, summarize:
                1. Subscription Leak audit (mention specific costs saved if canceled)
                2. Category Budget optimization (e.g. Dining/Groceries)
                3. Projections for saving and emergency runway.
                
                Keep response encouraging, professional, and visually elegant.
            """.trimIndent()

            _isAnalyzing.value = false
            var accumulated = ""
            repository.streamChatReply(prompt).collect { chunk ->
                accumulated += chunk
                _advisorAdvice.value = accumulated
            }
        }
    }

    // --- RE-SEED DATA UTILITY FOR CONVENIENCE ---
    fun reseedDatabase() {
        viewModelScope.launch {
            repository.resetTransactions()
            repository.clearInsights()
            AppDatabase.seedDefaultData(AppDatabase.getDatabase(getApplication()).finoraaxDao())
        }
    }

    // Class Factory to resolve AppDatabase inject
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FinoraaxViewModel::class.java)) {
                val db = AppDatabase.getDatabase(context)
                val repo = FinoraaxRepository(db.finoraaxDao())
                return FinoraaxViewModel(context.applicationContext as Application, repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
