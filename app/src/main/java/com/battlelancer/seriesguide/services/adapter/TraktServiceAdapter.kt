// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.services.adapter

import android.content.Context
import com.battlelancer.seriesguide.SgApp
import com.battlelancer.seriesguide.services.TrackingService
import com.battlelancer.seriesguide.services.models.ErrorType
import com.battlelancer.seriesguide.services.models.ServiceResult
import com.battlelancer.seriesguide.traktapi.TraktCredentials
import com.battlelancer.seriesguide.traktapi.TraktTools4
import com.uwetrottmann.trakt5.entities.SyncResponse as TraktSyncResponse
import com.uwetrottmann.trakt5.enums.Rating

/**
 * Adaptador concreto (Padrão GoF Adapter) que implementa [TrackingService] delegando para [TraktTools4].
 * Encapsula a conversão de parâmetros de domínio para tipos da biblioteca Trakt e o tratamento de erros.
 */
class TraktServiceAdapter internal constructor(
    private val context: Context,
    private val traktTools: TraktTools4
) : TrackingService {

    companion object {
        fun create(context: Context): TrackingService {
            val applicationContext = context.applicationContext
            return TraktServiceAdapter(
                applicationContext,
                TraktTools4(
                    SgApp.getServicesComponent(applicationContext).trakt()
                )
            )
        }
    }

    override suspend fun addShowToWatchlist(tmdbId: Int): ServiceResult<Unit> {
        return execute { traktTools.addShowToWatchlist(tmdbId) }
    }

    override suspend fun removeShowFromWatchlist(tmdbId: Int): ServiceResult<Unit> {
        return execute { traktTools.removeShowFromWatchlist(tmdbId) }
    }

    override suspend fun rateShow(tmdbId: Int, rating: Int?): ServiceResult<Unit> {
        val traktRating = Rating.fromValue(rating)
        return execute { traktTools.rateShow(tmdbId, traktRating) }
    }

    override suspend fun rateEpisode(
        showTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        rating: Int?
    ): ServiceResult<Unit> {
        val traktRating = Rating.fromValue(rating)
        return execute {
            traktTools.rateEpisode(showTmdbId, seasonNumber, episodeNumber, traktRating)
        }
    }

    override suspend fun rateMovie(tmdbId: Int, rating: Int?): ServiceResult<Unit> {
        val traktRating = Rating.fromValue(rating)
        return execute { traktTools.rateMovie(tmdbId, traktRating) }
    }

    private suspend fun execute(
        request: suspend () -> TraktTools4.TraktNonNullResponse<TraktSyncResponse>
    ): ServiceResult<Unit> {
        if (!isTraktAuthenticated()) {
            return ServiceResult.failure(ErrorType.UNAUTHORIZED)
        }

        return when (val response = request()) {
            is TraktTools4.TraktNonNullResponse.Success -> {
                if (TraktTools4.isNotFound(response.data)) {
                    ServiceResult.failure(ErrorType.NOT_FOUND)
                } else {
                    ServiceResult.success(Unit)
                }
            }

            is TraktTools4.TraktErrorResponse.IsUnauthorized -> {
                TraktCredentials.get(context).setCredentialsInvalid()
                ServiceResult.failure(ErrorType.UNAUTHORIZED)
            }

            is TraktTools4.TraktErrorResponse.IsAccountLimitExceeded -> {
                ServiceResult.failure(ErrorType.RATE_LIMIT)
            }

            is TraktTools4.TraktErrorResponse.IsAccountLocked -> {
                ServiceResult.failure(ErrorType.ACCOUNT_LOCKED)
            }

            is TraktTools4.TraktErrorResponse.IsNotVip -> {
                ServiceResult.failure(ErrorType.NOT_VIP)
            }

            is TraktTools4.TraktErrorResponse.Other -> {
                ServiceResult.failure(ErrorType.OTHER)
            }
        }
    }

    private fun isTraktAuthenticated(): Boolean {
        return TraktCredentials.get(context).hasCredentials()
    }
}
