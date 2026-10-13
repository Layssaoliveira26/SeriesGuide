// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.services

import com.battlelancer.seriesguide.services.models.ServiceResult
import com.battlelancer.seriesguide.services.models.SyncResponse
import com.uwetrottmann.trakt5.enums.Rating

interface TrackingService {
    suspend fun addShowToWatchlist(tmdbId: Int): ServiceResult<SyncResponse>
    suspend fun removeShowFromWatchlist(tmdbId: Int): ServiceResult<SyncResponse>
    suspend fun rateShow(tmdbId: Int, rating: Rating?): ServiceResult<SyncResponse>
    suspend fun rateEpisode(
        showTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        rating: Rating?
    ): ServiceResult<SyncResponse>
    suspend fun rateMovie(tmdbId: Int, rating: Rating?): ServiceResult<SyncResponse>
}