package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = "local_user",
    val name: String,
    val email: String,
    val pinHash: String = "1234",
    val biometricEnabled: Boolean = false,
    val privacyOnboarded: Boolean = false,
    val leakDetectorOnboarded: Boolean = false,
    val advisorOnboarded: Boolean = false,
    val sessionToken: String? = null,
    val lastLoginTimestamp: Long = 0L
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // INCOME or EXPENSE
    val category: String, // e.g. "Groceries", "Salary", "KPI Subscription", "UPI Autopay"
    val amount: Double,
    val date: String, // YYYY-MM-DD
    val note: String,
    val isRecurring: Boolean = false,
    val isSmartCategorized: Boolean = false
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // "All" or categories like "Groceries", "Entertainment", "Utility"
    val limitAmount: Double,
    val spentAmount: Double,
    val monthYear: String // YYYY-MM
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val targetDate: String = "2026-12-31", // YYYY-MM-DD
    val isEmergencyFund: Boolean = false
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val amount: Double,
    val dueDate: String, // YYYY-MM-DD
    val isPaid: Boolean = false,
    val category: String = "Utilities"
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val cost: Double,
    val billingCycle: String, // "Monthly", "Yearly"
    val nextRenewalDate: String, // YYYY-MM-DD
    val isForgotten: Boolean = false, // Forgotten leak status
    val status: String = "Active", // "Active", "Cancelled", "Suspended"
    val leakReason: String = "", // e.g. "Forgotten after free trial", "Unused OTT"
    val optimizationSuggestion: String = "",
    val scoreImpact: Int = 15 // Deduct score from Subscription Health Score
)

@Entity(tableName = "investments")
data class InvestmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // Stock, Crypto, Mutual Fund, Real Estate
    val initialAmount: Double,
    val currentAmount: Double,
    val units: Double,
    val purchaseDate: String
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val type: String, // "BUDGET_ALERT", "LEAK_ALERT", "BILL_REMINDER", "FRAUD_ALERT"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "financial_insights")
data class FinancialInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val type: String, // "INSIGHT", "ADVICE", "ALERT"
    val timestamp: Long = System.currentTimeMillis()
)
