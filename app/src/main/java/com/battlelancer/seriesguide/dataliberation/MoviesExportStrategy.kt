// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 SeriesGuide Contributors

package com.battlelancer.seriesguide.dataliberation

import com.battlelancer.seriesguide.dataliberation.model.Movie
import com.battlelancer.seriesguide.movies.database.MovieHelper
import com.battlelancer.seriesguide.movies.database.SgMovie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.OutputStream

// Responsável especificamente pela exportação de Filmes

class MoviesExportStrategy(
    private val movieHelper : MovieHelper 
) : ExportStrategy {
 @Throws(IOException::class)
    override suspend fun writeToStream(
        coroutineScope: CoroutineScope,
        out: OutputStream,
        isFullDump: Boolean,
        onProgressUpdate: suspend (total: Int, completed: Int) -> Unit
    ) {
        val transform: (SgMovie) -> Movie = { sgMovie ->
            val movie = Movie()
            movie.tmdb_id = sgMovie.tmdbId
            movie.imdb_id = sgMovie.imdbId
            movie.title = sgMovie.title
            movie.released_utc_ms = sgMovie.releasedMsOrDefault
            movie.runtime_min = sgMovie.runtimeMinOrDefault
            movie.poster = sgMovie.poster
            movie.in_collection = sgMovie.inCollectionOrDefault
            movie.in_watchlist = sgMovie.inWatchlistOrDefault
            movie.watched = sgMovie.watchedOrDefault
            movie.plays = sgMovie.playsOrDefault
            movie.last_updated_ms = sgMovie.lastUpdatedOrDefault
            if (isFullDump) {
                movie.overview = sgMovie.overview
            }
            movie
        }
        val movies = movieHelper.getMoviesForExport()
        coroutineScope.ensureActive()
        SgJsonWriter(
            itemsToTransform = movies,
            outputClass = Movie::class.java,
            outputStream = out,
            transform = transform
        ).write { total, done ->
            onProgressUpdate(total, done)
        }
    }    
}