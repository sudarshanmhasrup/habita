package dev.habita.app.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** Stores databases in the app's private storage. */
class IosDatabaseFactory : DatabaseFactory {
    override fun open(name: String, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver =
        NativeSqliteDriver(schema, name)
}