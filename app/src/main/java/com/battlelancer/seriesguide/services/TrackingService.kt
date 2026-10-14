// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.services

import com.battlelancer.seriesguide.services.models.ServiceResult

/**
 * Interface Target (Padrão GoF Adapter) para serviços de rastreamento e avaliação de produções.
 * Define um contrato agnóstico e independente de bibliotecas ou SDKs de terceiros.
 */
interface TrackingService {
    suspend fun addShowToWatchlist(tmdbId: Int): ServiceResult<Unit>
    suspend fun removeShowFromWatchlist(tmdbId: Int): ServiceResult<Unit>
    suspend fun rateShow(tmdbId: Int, rating: Int?): ServiceResult<Unit>
    suspend fun rateEpisode(
        showTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        rating: Int?
    ): ServiceResult<Unit>
    suspend fun rateMovie(tmdbId: Int, rating: Int?): ServiceResult<Unit>
}
