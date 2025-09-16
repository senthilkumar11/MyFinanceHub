package com.ssk.myfinancehub.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.ssk.myfinancehub.data.dao.BudgetDao
import com.ssk.myfinancehub.data.dao.TransactionDao
import com.ssk.myfinancehub.data.model.Budget
import com.ssk.myfinancehub.data.model.Converters
import com.ssk.myfinancehub.data.model.Transaction

@Database(
    entities = [Transaction::class, Budget::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: FinanceDatabase? = null

        fun getDatabase(context: Context): FinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "finance_database"
                )
                .fallbackToDestructiveMigration() // For demo purposes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
