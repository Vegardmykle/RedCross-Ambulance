

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.example.project.sharedLogic.data.sqldelight.com.example.db.AppDatabase

fun createDriver(context: Context): SqlDriver {
    return AndroidSqliteDriver(
        AppDatabase.Schema,
        context,
        "checklist.db"
    )
}