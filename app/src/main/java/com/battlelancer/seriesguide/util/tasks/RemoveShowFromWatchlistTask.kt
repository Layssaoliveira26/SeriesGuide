// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2016 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.util.tasks

import android.content.Context
import com.battlelancer.seriesguide.R
import com.battlelancer.seriesguide.services.TrackingService
import com.battlelancer.seriesguide.services.models.ServiceResult

class RemoveShowFromWatchlistTask(
    context: Context,
    showTmdbId: Int
) : BaseShowActionTask(context, showTmdbId) {

    override suspend fun sendToService(
        service: TrackingService
    ): ServiceResult<Unit> {
        return service.removeShowFromWatchlist(showTmdbId)
    }

    override val successTextResId: Int
        get() = R.string.watchlist_removed
}
