package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.security.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        BillEntity::class,
        SubscriptionEntity::class,
        InvestmentEntity::class,
        NotificationEntity::class,
        FinancialInsightEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun finoraaxDao(): FinoraaxDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finoraax_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // We can trigger an asynchronous seed on database creation
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    seedDefaultData(database.finoraaxDao())
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaultData(dao: FinoraaxDao) {
            // Check if already seeded
            if (dao.getUserSync() != null) return

            // 1. Seed User settings
            dao.insertUser(
                UserEntity(
                    id = "local_user",
                    name = "User",
                    email = "user@example.com",
                    pinHash = SecurityUtils.hashPin("1234"),
                    biometricEnabled = true,
                    privacyOnboarded = true,
                    leakDetectorOnboarded = true,
                    advisorOnboarded = true
                )
            )

            // 2. Seed default budgets for local YYYY-MM
            val months = listOf("2026-06", "2026-07")
            for (m in months) {
                dao.insertBudget(BudgetEntity(category = "All", limitAmount = 3000.0, spentAmount = 1450.0, monthYear = m))
                dao.insertBudget(BudgetEntity(category = "Groceries", limitAmount = 500.0, spentAmount = 320.0, monthYear = m))
                dao.insertBudget(BudgetEntity(category = "Entertainment", limitAmount = 250.0, spentAmount = 180.0, monthYear = m))
                dao.insertBudget(BudgetEntity(category = "Utilities", limitAmount = 400.0, spentAmount = 270.0, monthYear = m))
                dao.insertBudget(BudgetEntity(category = "Dining", limitAmount = 500.0, spentAmount = 425.0, monthYear = m))
            }

            // 3. Seed default Savings Goals
            dao.insertSavingsGoal(SavingsGoalEntity(name = "Emergency Fund 6 Months", targetAmount = 15000.0, currentAmount = 8400.0, targetDate = "2026-12-31", isEmergencyFund = true))
            dao.insertSavingsGoal(SavingsGoalEntity(name = "New Laptop", targetAmount = 1800.0, currentAmount = 1200.0, targetDate = "2026-09-15", isEmergencyFund = false))
            dao.insertSavingsGoal(SavingsGoalEntity(name = "Tesla Down Payment", targetAmount = 10000.0, currentAmount = 2500.0, targetDate = "2027-06-01", isEmergencyFund = false))

            // 4. Seed default Bills
            dao.insertBill(BillEntity(name = "Electricity Grid bill", amount = 124.50, dueDate = "2026-06-18", isPaid = false, category = "Utilities"))
            dao.insertBill(BillEntity(name = "High-speed Fiber Wifi", amount = 65.00, dueDate = "2026-06-21", isPaid = true, category = "Utilities"))
            dao.insertBill(BillEntity(name = "Premium Mobile Recharge", amount = 29.99, dueDate = "2026-06-25", isPaid = false, category = "Utilities"))
            dao.insertBill(BillEntity(name = "Home Insurance policy", amount = 89.00, dueDate = "2026-07-02", isPaid = false, category = "Utilities"))

            // 5. Seed default Subscriptions (with leaky / forgotten ones!)
            dao.insertSubscription(
                SubscriptionEntity(
                    name = "Netflix Premium OTT",
                    cost = 19.99,
                    billingCycle = "Monthly",
                    nextRenewalDate = "2026-06-18",
                    isForgotten = false,
                    status = "Active",
                    leakReason = "High usage shared with family",
                    optimizationSuggestion = "Downgrade to Standard HD to save $4.50 monthly."
                )
            )
            dao.insertSubscription(
                SubscriptionEntity(
                    name = "Abandoned Premium Gym Pass",
                    cost = 55.00,
                    billingCycle = "Monthly",
                    nextRenewalDate = "2026-06-22",
                    isForgotten = true,
                    status = "Active",
                    leakReason = "0 Check-ins in last 60 days",
                    optimizationSuggestion = "Cancel immediately. Subscription is a continuous financial leak.",
                    scoreImpact = 35
                )
            )
            dao.insertSubscription(
                SubscriptionEntity(
                    name = "Spotify Premium Duo",
                    cost = 14.99,
                    billingCycle = "Monthly",
                    nextRenewalDate = "2026-06-29",
                    isForgotten = false,
                    status = "Active",
                    leakReason = "Highly active musical streaming",
                    optimizationSuggestion = "Switch to Spotify Family if more users join."
                )
            )
            dao.insertSubscription(
                SubscriptionEntity(
                    name = "AWS Test Server Node",
                    cost = 34.50,
                    billingCycle = "Monthly",
                    nextRenewalDate = "2026-06-19",
                    isForgotten = true,
                    status = "Active",
                    leakReason = "Unused testing API instance left active",
                    optimizationSuggestion = "Decommission node and cancel billing. Unused infrastructure leak.",
                    scoreImpact = 25
                )
            )
            dao.insertSubscription(
                SubscriptionEntity(
                    name = "Premium OTT Cooking Channel",
                    cost = 9.99,
                    billingCycle = "Monthly",
                    nextRenewalDate = "2026-06-20",
                    isForgotten = true,
                    status = "Active",
                    leakReason = "Unopened app in 90 days. Trial rolled over fully.",
                    optimizationSuggestion = "Cancel now. Save $120.00 annually.",
                    scoreImpact = 20
                )
            )

            // 6. Seed default Investment Assets
            dao.insertInvestment(InvestmentEntity(name = "S&P 500 Index Fund (VOO)", type = "Stock", initialAmount = 4500.0, currentAmount = 4850.0, units = 10.5, purchaseDate = "2026-01-10"))
            dao.insertInvestment(InvestmentEntity(name = "Bitcoin ETF", type = "Crypto", initialAmount = 2000.0, currentAmount = 2150.0, units = 0.035, purchaseDate = "2026-03-05"))
            dao.insertInvestment(InvestmentEntity(name = "Govt Bonds Series-A", type = "Bonds", initialAmount = 1500.0, currentAmount = 1520.0, units = 15.0, purchaseDate = "2026-02-14"))

            // 7. Seed Transactions
            dao.insertTransaction(TransactionEntity(type = "INCOME", category = "Salary", amount = 4200.0, date = "2026-06-01", note = "Corporate Tech Base", isRecurring = true))
            dao.insertTransaction(TransactionEntity(type = "EXPENSE", category = "Groceries", amount = 145.20, date = "2026-06-02", note = "WholeFoods Market Checkout"))
            dao.insertTransaction(TransactionEntity(type = "EXPENSE", category = "Dining", amount = 85.00, date = "2026-06-04", note = "Taco Bistro dine-out"))
            dao.insertTransaction(TransactionEntity(type = "EXPENSE", category = "Entertainment", amount = 19.99, date = "2026-06-05", note = "Netflix Premium OTT charge"))
            dao.insertTransaction(TransactionEntity(type = "EXPENSE", category = "Utilities", amount = 65.00, date = "2026-06-06", note = "High-speed Fiber Wifi bill"))
            dao.insertTransaction(TransactionEntity(type = "EXPENSE", category = "Groceries", amount = 34.50, date = "2026-06-08", note = "Fresh Farm Milk and bread"))
            dao.insertTransaction(TransactionEntity(type = "EXPENSE", category = "Leaky Subscription", amount = 55.00, date = "2026-06-10", note = "Gym Pass autopay Autodebit", isRecurring = true))
            dao.insertTransaction(TransactionEntity(type = "EXPENSE", category = "Dining", amount = 112.50, date = "2026-06-11", note = "Sake Bar weekend meal"))
            dao.insertTransaction(TransactionEntity(type = "INCOME", category = "Investment Returns", amount = 220.00, date = "2026-06-12", note = "Dividends paid out"))
            dao.insertTransaction(TransactionEntity(type = "EXPENSE", category = "Unused Node", amount = 34.50, date = "2026-06-12", note = "AWS forgotten servers Autodebit", isRecurring = true))

            // 8. Seed insights
            dao.insertInsight(
                FinancialInsightEntity(
                    title = "Urgent: Direct Subscription Leaks Identified",
                    content = "We have discovered 3 active subscription channels that show zero engagement in the last 60 days. Cancelling Gym Pass and AWS unused test servers yields $89.50 in monthly budget relief.",
                    type = "ALERT"
                )
            )
            dao.insertInsight(
                FinancialInsightEntity(
                    title = "Robust Cash Flow Forecast",
                    content = "Your forecast indicates that you will complete June 2026 with a positive cash cushion of over $2,100. Consider channeling $500 of this surplus into your emergency fund.",
                    type = "INSIGHT"
                )
            )

            // 9. Seed notifications
            dao.insertNotification(
                NotificationEntity(
                    title = "Budget Threshold Alert",
                    message = "Dining out limit is at 85% capacity. Limit remaining is $75 for June 2026.",
                    type = "BUDGET_ALERT"
                )
            )
            dao.insertNotification(
                NotificationEntity(
                    title = "Subscription Autopay Approaching",
                    message = "AWS forgotten servers billing of $34.50 is scheduled on Jun 19, 2026.",
                    type = "LEAK_ALERT"
                )
            )
        }
    }
}
