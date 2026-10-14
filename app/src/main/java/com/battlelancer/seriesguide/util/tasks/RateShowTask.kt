// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2015 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.util.tasks

import android.content.Context
import com.battlelancer.seriesguide.provider.SgRoomDatabase
import com.battlelancer.seriesguide.services.TrackingService
import com.battlelancer.seriesguide.services.models.ServiceResult
import com.uwetrottmann.trakt5.enums.Rating

/**
 * See [BaseRateItemTask]
 */
class RateShowTask(
    context: Context,
    rating: Rating?,
    private val showId: Long
) : BaseRateItemTask(context, rating) {

    private var showTmdbId = 0

    override fun loadTraktIds(): Boolean {
        showTmdbId = SgRoomDatabase.getInstance(context).sgShow2Helper()
            .getShowTmdbId(showId)
        return showTmdbId != 0
    }

    override suspend fun sendToService(
        service: TrackingService
    ): ServiceResult<Unit> {
        return service.rateShow(showTmdbId, rating?.value)
    }

    override fun doDatabaseUpdate(): Boolean {
        val rowsUpdated = SgRoomDatabase.getInstance(context).sgShow2Helper()
            .updateUserRating(showId, rating?.value ?: 0)
        return rowsUpdated > 0
    }
}
