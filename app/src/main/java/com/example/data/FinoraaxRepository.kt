package com.example.data

import android.util.Log
import com.example.service.BackendClient
import com.example.service.GeminiClient
import com.example.service.LoginRequest
import com.example.service.RegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinoraaxRepository(private val dao: FinoraaxDao) {

    private val backendApi = BackendClient.apiService
    private val scope = CoroutineScope(Dispatchers.IO)

    private suspend fun getAuthHeader(): String? {
        val user = dao.getUserSync()
        val token = user?.sessionToken
        return if (token != null) "Bearer $token" else null
    }

    // --- SYNCHRONIZATION SERVICE ---
    suspend fun loginAndSync() {
        val user = dao.getUserSync() ?: return
        scope.launch {
            try {
                val loginReq = LoginRequest(email = user.email, pinHash = user.pinHash)
                val resp = backendApi.login(loginReq)
                
                // Update local session token
                dao.insertUser(user.copy(sessionToken = resp.sessionToken))
                
                // Pull all records from server
                syncAllData()
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error logging in to backend: ${e.localizedMessage}")
                // Fallback: Attempt sync with existing token
                syncAllData()
            }
        }
    }

    suspend fun syncAllData() {
        val header = getAuthHeader() ?: return
        withContext(Dispatchers.IO) {
            try {
                // 1. Sync User Profile
                val serverUser = backendApi.getProfile(header)
                dao.insertUser(serverUser)

                // 2. Sync Transactions
                val serverTxs = backendApi.getTransactions(header)
                for (tx in serverTxs) {
                    dao.insertTransaction(tx)
                }

                // 3. Sync Budgets
                val serverBudgets = backendApi.getBudgets(header, "2026-06")
                for (b in serverBudgets) {
                    dao.insertBudget(b)
                }

                // 4. Sync Savings Goals
                val serverGoals = backendApi.getSavingsGoals(header)
                for (g in serverGoals) {
                    dao.insertSavingsGoal(g)
                }

                // 5. Sync Bills
                val serverBills = backendApi.getBills(header)
                for (b in serverBills) {
                    dao.insertBill(b)
                }

                // 6. Sync Subscriptions
                val serverSubs = backendApi.getSubscriptions(header)
                for (s in serverSubs) {
                    dao.insertSubscription(s)
                }

                // 7. Sync Investments
                val serverInvestments = backendApi.getInvestments(header)
                for (i in serverInvestments) {
                    dao.insertInvestment(i)
                }

                // 8. Sync Notifications
                val serverNotifications = backendApi.getNotifications(header)
                for (n in serverNotifications) {
                    dao.insertNotification(n)
                }
                
                Log.d("FinoraaxRepository", "Bi-directional sync successfully completed with server!")
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Background data sync failed: ${e.localizedMessage}")
            }
        }
    }

    // --- USER PROFILE ---
    val userFlow: Flow<UserEntity?> = dao.getUserFlow()

    suspend fun getUserSync(): UserEntity? {
        return dao.getUserSync()
    }

    suspend fun saveUser(user: UserEntity) {
        dao.insertUser(user)
        scope.launch {
            try {
                val registerReq = RegisterRequest(
                    name = user.name,
                    email = user.email,
                    pinHash = user.pinHash
                )
                val resp = backendApi.register(registerReq)
                
                // Save updated session token locally
                if (resp.sessionToken != user.sessionToken) {
                    dao.insertUser(user.copy(sessionToken = resp.sessionToken))
                }
                
                // Immediately trigger background data synchronization
                syncAllData()
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error registering user to server: ${e.localizedMessage}")
            }
        }
    }

    // --- TRANSACTIONS ---
    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactionsFlow()
    suspend fun getAllTransactionsSync(): List<TransactionEntity> = dao.getAllTransactionsSync()
    
    suspend fun addTransaction(transaction: TransactionEntity): Long {
        val id = dao.insertTransaction(transaction)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.addTransaction(header, transaction.copy(id = id))
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing added transaction: ${e.localizedMessage}")
            }
        }
        return id
    }

    suspend fun deleteTransaction(id: Long) {
        dao.deleteTransactionById(id)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.deleteTransaction(header, id)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing deleted transaction: ${e.localizedMessage}")
            }
        }
    }

    suspend fun resetTransactions() {
        dao.clearAllTransactionsByForce()
    }

    // --- BUDGETS ---
    fun getBudgets(monthYear: String): Flow<List<BudgetEntity>> = dao.getBudgetsFlow(monthYear)
    suspend fun getBudgetsSync(monthYear: String): List<BudgetEntity> = dao.getBudgetsSync(monthYear)
    
    suspend fun addBudget(budget: BudgetEntity) {
        dao.insertBudget(budget)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.addBudget(header, budget)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing budget addition: ${e.localizedMessage}")
            }
        }
    }

    suspend fun updateBudgetSpent(id: Int, spent: Double) {
        dao.updateBudgetSpent(id, spent)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.updateBudget(header, id, mapOf("spentAmount" to spent))
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing updated budget spent: ${e.localizedMessage}")
            }
        }
    }

    // --- SAVINGS GOALS ---
    val allSavingsGoals: Flow<List<SavingsGoalEntity>> = dao.getAllSavingsGoalsFlow()
    
    suspend fun addSavingsGoal(goal: SavingsGoalEntity) {
        dao.insertSavingsGoal(goal)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.addSavingsGoal(header, goal)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing savings goal addition: ${e.localizedMessage}")
            }
        }
    }

    suspend fun updateSavingsGoalProgress(id: Int, currentAmount: Double) {
        dao.updateSavingsGoalProgress(id, currentAmount)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.updateSavingsGoal(header, id, mapOf("currentAmount" to currentAmount))
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing savings goal progress: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteSavingsGoal(id: Int) {
        dao.deleteSavingsGoalById(id)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.deleteSavingsGoal(header, id)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing deleted savings goal: ${e.localizedMessage}")
            }
        }
    }

    // --- BILLS AND PAYMENTS ---
    val allBills: Flow<List<BillEntity>> = dao.getAllBillsFlow()
    
    suspend fun addBill(bill: BillEntity) {
        dao.insertBill(bill)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.addBill(header, bill)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing bill addition: ${e.localizedMessage}")
            }
        }
    }

    suspend fun updateBillPaidStatus(id: Int, isPaid: Boolean) {
        dao.updateBillPaidStatus(id, isPaid)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.updateBill(header, id, mapOf("isPaid" to isPaid))
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing bill paid status: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteBill(id: Int) {
        dao.deleteBillById(id)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.deleteBill(header, id)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing deleted bill: ${e.localizedMessage}")
            }
        }
    }

    // --- SUBSCRIPTIONS (LEAK DETECTOR) ---
    val allSubscriptions: Flow<List<SubscriptionEntity>> = dao.getAllSubscriptionsFlow()
    
    suspend fun addSubscription(subscription: SubscriptionEntity) {
        dao.insertSubscription(subscription)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.addSubscription(header, subscription)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing subscription addition: ${e.localizedMessage}")
            }
        }
    }

    suspend fun updateSubscriptionStatus(id: Int, isForgotten: Boolean, status: String) {
        dao.updateSubscriptionStatus(id, isForgotten, status)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.updateSubscription(
                    header, 
                    id, 
                    mapOf("isForgotten" to isForgotten.toString(), "status" to status)
                )
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing subscription status update: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteSubscription(id: Int) {
        dao.deleteSubscriptionById(id)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.deleteSubscription(header, id)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing deleted subscription: ${e.localizedMessage}")
            }
        }
    }

    suspend fun getAllSubscriptionsSync(): List<SubscriptionEntity> = dao.getAllSubscriptionsSync()

    // --- INVESTMENTS ---
    val allInvestments: Flow<List<InvestmentEntity>> = dao.getAllInvestmentsFlow()
    
    suspend fun addInvestment(investment: InvestmentEntity) {
        dao.insertInvestment(investment)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.addInvestment(header, investment)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing investment addition: ${e.localizedMessage}")
            }
        }
    }

    suspend fun updateInvestmentValue(id: Int, currentAmount: Double) {
        dao.updateInvestmentCurrentValue(id, currentAmount)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.updateInvestment(header, id, mapOf("currentAmount" to currentAmount))
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing investment value update: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteInvestment(id: Int) {
        dao.deleteInvestmentById(id)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.deleteInvestment(header, id)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing deleted investment: ${e.localizedMessage}")
            }
        }
    }

    // --- NOTIFICATIONS ---
    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotificationsFlow()
    
    suspend fun addNotification(notification: NotificationEntity) {
        dao.insertNotification(notification)
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.addNotification(header, notification)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing notification: ${e.localizedMessage}")
            }
        }
    }

    suspend fun markNotificationAsRead(id: Long) {
        dao.markNotificationAsRead(id)
    }

    suspend fun clearNotifications() {
        dao.clearAllNotifications()
        scope.launch {
            try {
                val header = getAuthHeader() ?: return@launch
                backendApi.clearNotifications(header)
            } catch (e: Exception) {
                Log.e("FinoraaxRepository", "Error syncing cleared notifications: ${e.localizedMessage}")
            }
        }
    }

    // --- INSIGHTS ---
    val allInsights: Flow<List<FinancialInsightEntity>> = dao.getAllInsightsFlow()
    
    suspend fun addInsight(insight: FinancialInsightEntity) {
        dao.insertInsight(insight)
    }

    suspend fun clearInsights() {
        dao.clearAllInsights()
    }

    // --- GEMINI DIRECT ADVICE LAYER ---
    suspend fun getFinancialAdvice(prompt: String, systemPrompt: String? = null): String {
        return GeminiClient.generateAdvice(prompt, systemPrompt)
    }

    // --- CHATBOT & ADVISOR ENDPOINTS (CHATGPT OVER BACKEND) ---
    suspend fun getChatHistory(): List<ChatMessage> {
        val header = getAuthHeader() ?: return emptyList()
        return try {
            backendApi.getChatHistory(header)
        } catch (e: Exception) {
            Log.e("FinoraaxRepository", "Error loading chat history: ${e.localizedMessage}")
            emptyList()
        }
    }

    suspend fun clearChatHistory(): Boolean {
        val header = getAuthHeader() ?: return false
        return try {
            backendApi.clearChatHistory(header).status == "success"
        } catch (e: Exception) {
            Log.e("FinoraaxRepository", "Error clearing chat history: ${e.localizedMessage}")
            false
        }
    }

    suspend fun getChatSuggestions(): List<String> {
        val header = getAuthHeader() ?: return emptyList()
        return try {
            backendApi.getChatSuggestions(header)
        } catch (e: Exception) {
            Log.e("FinoraaxRepository", "Error fetching chat suggestions: ${e.localizedMessage}")
            emptyList()
        }
    }

    suspend fun streamChatReply(prompt: String): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val header = getAuthHeader() ?: "Bearer token_local"
        try {
            val responseBody = backendApi.streamMessage(header, ChatRequest(prompt))
            responseBody.byteStream().bufferedReader().use { reader ->
                val buffer = CharArray(1024)
                var bytesRead: Int
                while (reader.read(buffer).also { bytesRead = it } != -1) {
                    val chunk = String(buffer, 0, bytesRead)
                    emit(chunk)
                }
            }
        } catch (e: Exception) {
            Log.e("FinoraaxRepository", "Error streaming chat: ${e.localizedMessage}")
            // Fallback word by word locally
            val fallback = GeminiClient.generateAdvice(prompt, null)
            val words = fallback.split(" ")
            for (word in words) {
                kotlinx.coroutines.delay(50)
                emit("$word ")
            }
        }
    }
}
