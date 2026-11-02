package dev.habita.app.core.data

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** Stores databases in the app's private storage. */
class AndroidDatabaseFactory(private val context: Context) : DatabaseFactory {
    override fun open(name: String, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver =
        AndroidSqliteDriver(schema, context, name)
}