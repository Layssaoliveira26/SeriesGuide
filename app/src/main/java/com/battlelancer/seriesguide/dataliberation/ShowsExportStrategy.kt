// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 SeriesGuide Contributors
package com.battlelancer.seriesguide.dataliberation
import com.battlelancer.seriesguide.dataliberation.model.Episode
import com.battlelancer.seriesguide.dataliberation.model.Season
import com.battlelancer.seriesguide.dataliberation.model.Show
import com.battlelancer.seriesguide.shows.database.SgEpisode2Helper
import com.battlelancer.seriesguide.shows.database.SgSeason2Helper
import com.battlelancer.seriesguide.shows.database.SgShow2
import com.battlelancer.seriesguide.shows.database.SgShow2Helper
import com.battlelancer.seriesguide.shows.episodes.EpisodeTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.OutputStream

// Responsável especificamente pela exportação de Séries

class ShowsExportStrategy (
    private val sgShow2Helper: SgShow2Helper,
    private val sgSeason2Helper: SgSeason2Helper,
    private val sgEpisode2Helper: SgEpisode2Helper,
) : ExportStrategy {

     @Throws(IOException::class)
    override suspend fun writeToStream(
        coroutineScope: CoroutineScope,
        out: OutputStream,
        isFullDump: Boolean,
        onProgressUpdate: suspend (total: Int, completed: Int) -> Unit
    ) {
        val transform: (SgShow2) -> Show = { sgShow ->
            val show = Show()
            show.tmdb_id = sgShow.tmdbId
            show.tvdb_id = sgShow.tvdbId
            show.title = sgShow.title
            show.favorite = sgShow.favorite
            show.notify = sgShow.notify
            show.hidden = sgShow.hidden
            show.language = sgShow.language
            show.release_time = sgShow.releaseTimeOrDefault
            show.release_weekday = sgShow.releaseWeekDayOrDefault
            show.release_timezone = sgShow.releaseTimeZone
            show.country = sgShow.releaseCountry
            show.custom_release_time = sgShow.customReleaseTime
            show.custom_release_day_offset = sgShow.customReleaseDayOffset
            show.custom_release_timezone = sgShow.customReleaseTimeZone
            show.last_watched_ms = sgShow.lastWatchedMs
            show.poster = sgShow.poster
            show.content_rating = sgShow.contentRating
            show.status = DataLiberationTools.decodeShowStatus(sgShow.statusOrUnknown)
            show.runtime = sgShow.runtime ?: 0
            show.network = sgShow.network
            show.imdb_id = sgShow.imdbId
            show.trakt_id = sgShow.traktId
            show.first_aired = sgShow.firstRelease
            show.rating_user = sgShow.ratingUser
            show.user_note = sgShow.userNote?.ifBlank { null }
            show.user_note_trakt_id = sgShow.userNoteTraktId
            if (isFullDump) {
                show.overview = sgShow.overview
                show.rating_tmdb = sgShow.ratingTmdb
                show.rating_tmdb_votes = sgShow.ratingTmdbVotes
                show.rating = sgShow.ratingTrakt
                show.rating_votes = sgShow.ratingTraktVotes
                show.genres = sgShow.genres
            }

            show.seasons = getSeasons(sgShow.id, isFullDump)
            show
        }

        val shows = sgShow2Helper.getShowsForExport()

        coroutineScope.ensureActive()

         SgJsonWriter(
            itemsToTransform = shows,
            outputClass = Show::class.java,
            outputStream = out,
            transform = transform
        ).write { total, done ->
            onProgressUpdate(total, done)
        }
    }
    private fun getSeasons(showId: Long, isFullDump: Boolean): List<Season> {
        val list = ArrayList<Season>()
        val seasons = sgSeason2Helper.getSeasonsForExport(showId)
        for (sgSeason in seasons) {
            val season = Season()
            season.tmdb_id = sgSeason.tmdbId
            season.tvdb_id = sgSeason.tvdbId
            season.season = sgSeason.number
            season.episodes = getEpisodes(sgSeason.id, isFullDump)
            if (season.episodes.isNotEmpty()) {
                list.add(season)
            }
        }
        return list
    }
    private fun getEpisodes(seasonId: Long, isFullDump: Boolean): List<Episode> {
        val list = ArrayList<Episode>()
        val episodes = sgEpisode2Helper.getEpisodesForExport(seasonId)
        for (episodeDb in episodes) {
            val episodeExport = Episode()
            episodeExport.tmdb_id = episodeDb.tmdbId
            episodeExport.tvdb_id = episodeDb.tvdbId
            episodeExport.episode = episodeDb.number
            episodeExport.episode_absolute = episodeDb.absoluteNumber
            episodeExport.episode_dvd = episodeDb.dvdNumber
            val episodeFlag = episodeDb.watched
            episodeExport.watched = EpisodeTools.isWatched(episodeFlag)
            episodeExport.skipped = EpisodeTools.isSkipped(episodeFlag)
            episodeExport.plays = episodeDb.playsOrZero
            episodeExport.collected = episodeDb.collected
            episodeExport.title = episodeDb.title
            episodeExport.first_aired = episodeDb.firstReleasedMs
            episodeExport.rating_user = episodeDb.ratingUser
            if (isFullDump) {
                episodeExport.overview = episodeDb.overview
                episodeExport.image = episodeDb.image
                episodeExport.writers = episodeDb.writers
                episodeExport.gueststars = episodeDb.guestStars
                episodeExport.directors = episodeDb.directors
                episodeExport.rating_tmdb = episodeDb.ratingTmdb
                episodeExport.rating_tmdb_votes = episodeDb.ratingTmdbVotes
                episodeExport.rating = episodeDb.ratingTrakt
                episodeExport.rating_votes = episodeDb.ratingTraktVotes
            }
            list.add(episodeExport)
        }
        return list
    }
}
