package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinoraaxDao {

    // --- USER PROFILE ---
    @Query("SELECT * FROM users LIMIT 1")
    fun getUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUserSync(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // --- TRANSACTIONS ---
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactionsByForce()

    // --- BUDGETS ---
    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear")
    fun getBudgetsFlow(monthYear: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear")
    suspend fun getBudgetsSync(monthYear: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Query("UPDATE budgets SET spentAmount = :spent WHERE id = :id")
    suspend fun updateBudgetSpent(id: Int, spent: Double)

    // --- SAVINGS GOALS ---
    @Query("SELECT * FROM savings_goals")
    fun getAllSavingsGoalsFlow(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals")
    suspend fun getAllSavingsGoalsSync(): List<SavingsGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity)

    @Query("UPDATE savings_goals SET currentAmount = :currentAmount WHERE id = :id")
    suspend fun updateSavingsGoalProgress(id: Int, currentAmount: Double)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteSavingsGoalById(id: Int)

    // --- BILLS AND PAYMENTS ---
    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    fun getAllBillsFlow(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    suspend fun getAllBillsSync(): List<BillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity)

    @Query("UPDATE bills SET isPaid = :isPaid WHERE id = :id")
    suspend fun updateBillPaidStatus(id: Int, isPaid: Boolean)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteBillById(id: Int)

    // --- SUBSCRIPTIONS (LEAK DETECTOR) ---
    @Query("SELECT * FROM subscriptions ORDER BY cost DESC")
    fun getAllSubscriptionsFlow(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY cost DESC")
    suspend fun getAllSubscriptionsSync(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET isForgotten = :isForgotten, status = :status WHERE id = :id")
    suspend fun updateSubscriptionStatus(id: Int, isForgotten: Boolean, status: String)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionById(id: Int)

    // --- INVESTMENTS ---
    @Query("SELECT * FROM investments")
    fun getAllInvestmentsFlow(): Flow<List<InvestmentEntity>>

    @Query("SELECT * FROM investments")
    suspend fun getAllInvestmentsSync(): List<InvestmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: InvestmentEntity)

    @Query("UPDATE investments SET currentAmount = :currentAmount WHERE id = :id")
    suspend fun updateInvestmentCurrentValue(id: Int, currentAmount: Double)

    @Query("DELETE FROM investments WHERE id = :id")
    suspend fun deleteInvestmentById(id: Int)

    // --- NOTIFICATIONS ---
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()

    // --- FINANCIAL INSIGHTS ---
    @Query("SELECT * FROM financial_insights ORDER BY timestamp DESC")
    fun getAllInsightsFlow(): Flow<List<FinancialInsightEntity>>

    @Query("SELECT * FROM financial_insights ORDER BY timestamp DESC")
    suspend fun getAllInsightsSync(): List<FinancialInsightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsight(insight: FinancialInsightEntity)

    @Query("DELETE FROM financial_insights")
    suspend fun clearAllInsights()
}
