// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2015 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.util.tasks

import android.content.Context
import com.battlelancer.seriesguide.movies.details.MovieDetailsFragment
import com.battlelancer.seriesguide.provider.SgRoomDatabase
import com.battlelancer.seriesguide.services.TrackingService
import com.battlelancer.seriesguide.services.models.ServiceResult
import com.battlelancer.seriesguide.services.models.SyncResponse
import com.uwetrottmann.trakt5.enums.Rating
import org.greenrobot.eventbus.EventBus

/**
 * See [BaseRateItemTask]
 */
class RateMovieTask(
    context: Context,
    rating: Rating?,
    private val movieTmdbId: Int
) : BaseRateItemTask(context, rating) {

    /**
     * The movie TMDB ID is already known, nothing to load.
     */
    override fun loadTraktIds(): Boolean = true

    override suspend fun sendToService(
        service: TrackingService
    ): ServiceResult<SyncResponse> {
        return service.rateMovie(movieTmdbId, rating)
    }

    override fun doDatabaseUpdate(): Boolean {
        val rowsUpdated = SgRoomDatabase.getInstance(context).movieHelper()
            .updateUserRating(movieTmdbId, rating?.value ?: 0)
        return rowsUpdated > 0
    }

    override fun onPostExecute(result: Int) {
        super.onPostExecute(result)

        // post event so movie UI reloads (it is not listening to database changes)
        EventBus.getDefault().post(MovieDetailsFragment.MovieChangedEvent(movieTmdbId))
    }

}