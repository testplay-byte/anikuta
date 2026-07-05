package app.anikuta.data.anilist.api

/**
 * AniList GraphQL queries for the home page.
 * Based on the AniList API v2: https://docs.anilist.co/
 *
 * Each query returns the fields needed for the home page sections.
 */
object AniListQueries {

    val trending = """
        query Trending(${'$'}page: Int = 1, ${'$'}perPage: Int = 20) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: TRENDING_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              bannerImage
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
            }
          }
        }
    """.trimIndent()

    val popular = """
        query Popular(${'$'}page: Int = 1, ${'$'}perPage: Int = 20) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: POPULARITY_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              bannerImage
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
            }
          }
        }
    """.trimIndent()

    val freshlyUpdated = """
        query FreshlyUpdated(${'$'}page: Int = 1, ${'$'}perPage: Int = 20) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, status: RELEASING, sort: UPDATED_AT_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              bannerImage
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
              nextAiringEpisode { airingAt episode timeUntilAiring }
            }
          }
        }
    """.trimIndent()

    val genres = """
        query Genres {
          GenreCollection
        }
    """.trimIndent()

    val browseByGenre = """
        query BrowseByGenre(${'$'}genre: String, ${'$'}page: Int = 1, ${'$'}perPage: Int = 20) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, genre: ${'$'}genre, sort: POPULARITY_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
            }
          }
        }
    """.trimIndent()

    val airingSchedule = """
        query AiringSchedule(${'$'}airingAt: Int) {
          Page(page: 1, perPage: 50) {
            airingSchedules(airingAt_lesser: ${'$'}airingAt) {
              airingAt
              episode
              media {
                id
                title { romaji english native }
                coverImage { large medium }
                episodes
              }
            }
          }
        }
    """.trimIndent()

    val animeDetails = """
        query AnimeDetails(${'$'}id: Int) {
          Media(id: ${'$'}id, type: ANIME) {
            id
            idMal
            title { romaji english native }
            coverImage { extraLarge large medium color }
            bannerImage
            description
            averageScore
            meanScore
            episodes
            duration
            genres
            season
            seasonYear
            format
            status
            studios { nodes { name isAnimationStudio } }
            nextAiringEpisode { airingAt episode timeUntilAiring }
            relations {
              edges { relationType node { id title { romaji english } type } }
            }
          }
        }
    """.trimIndent()
}
