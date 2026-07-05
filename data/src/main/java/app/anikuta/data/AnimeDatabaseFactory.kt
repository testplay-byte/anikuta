package app.anikuta.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Factory for creating the AnimeDatabase.
 * Lives in :data so the generated SQLDelight types (Animehistory, Animes) are visible.
 */
object AnimeDatabaseFactory {
    fun create(context: Context): AnimeDatabase {
        val driver = AndroidSqliteDriver(
            schema = AnimeDatabase.Schema,
            context = context,
            name = "tachiyomi.animedb",
        )
        return AnimeDatabase(
            driver = driver,
            animehistoryAdapter = AnimeDatabase.Animehistory.Adapter(
                last_seenAdapter = DateColumnAdapter,
            ),
            animesAdapter = AnimeDatabase.Animes.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = FetchTypeColumnAdapter,
            ),
        )
    }
}
