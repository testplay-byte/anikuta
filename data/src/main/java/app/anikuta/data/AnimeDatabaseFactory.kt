package app.anikuta.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dataanime.Animehistory
import dataanime.Animes

/**
 * Factory for creating the AnimeDatabase.
 *
 * SQLDelight generates table classes in a package matching the directory name
 * relative to srcDir (e.g. `dataanime/` → package `dataanime`), NOT prefixed
 * with the packageName. The packageName only applies to the Database class itself.
 *
 * So: AnimeDatabase is in `app.anikuta.data`, but Animehistory/Animes are in `dataanime`.
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
            animehistoryAdapter = Animehistory.Adapter(
                last_seenAdapter = DateColumnAdapter,
            ),
            animesAdapter = Animes.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = FetchTypeColumnAdapter,
            ),
        )
    }
}
