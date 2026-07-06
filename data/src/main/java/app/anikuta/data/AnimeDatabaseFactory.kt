package app.anikuta.data

import android.content.Context
import android.util.Log
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dataanime.Animehistory
import dataanime.Animes

/**
 * Factory for creating the AnimeDatabase.
 * Includes logging for debugging.
 */
object AnimeDatabaseFactory {
    private const val TAG = "AnimeDBFactory"

    fun create(context: Context): AnimeDatabase {
        Log.d(TAG, "Creating AnimeDatabase...")
        try {
            val driver = AndroidSqliteDriver(
                schema = AnimeDatabase.Schema,
                context = context,
                name = "tachiyomi.animedb",
            )
            Log.d(TAG, "Driver created. Schema version: ${AnimeDatabase.Schema.version}")

            val db = AnimeDatabase(
                driver = driver,
                animehistoryAdapter = Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                animesAdapter = Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = FetchTypeColumnAdapter,
                ),
            )
            Log.d(TAG, "✅ AnimeDatabase created successfully")
            return db
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create AnimeDatabase", e)
            throw e
        }
    }
}
