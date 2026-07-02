import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.example.project.sharedLogic.data.sqldelight.com.example.db.AppDatabase


fun createDriver(): SqlDriver {
    return NativeSqliteDriver(
        AppDatabase.Schema,
        "checklist.db"
    )
}