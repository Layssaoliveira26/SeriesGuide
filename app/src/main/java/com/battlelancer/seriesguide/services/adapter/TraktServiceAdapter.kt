// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.services.adapter

import android.content.Context
import com.battlelancer.seriesguide.SgApp
import com.battlelancer.seriesguide.services.models.ErrorType
import com.battlelancer.seriesguide.services.models.ServiceResult
import com.battlelancer.seriesguide.services.models.SyncResponse
import com.battlelancer.seriesguide.traktapi.TraktCredentials
import com.battlelancer.seriesguide.traktapi.TraktTools4
import com.uwetrottmann.trakt5.entities.SyncResponse as TraktSyncResponse
import com.uwetrottmann.trakt5.enums.Rating

class TraktServiceAdapter private constructor(
    private val context: Context,
    private val traktTools: TraktTools4
) {

    companion object {
        fun create(context: Context): TraktServiceAdapter {
            val applicationContext = context.applicationContext
            return TraktServiceAdapter(
                applicationContext,
                TraktTools4(
                    SgApp.getServicesComponent(applicationContext).trakt()
                )
            )
        }
    }

    suspend fun addShowToWatchlist(
        tmdbId: Int
    ): ServiceResult<SyncResponse> {
        return execute<TraktSyncResponse>(
            request = {
                traktTools.addShowToWatchlist(tmdbId)
            }
        ) { response ->
            SyncResponse(
                added = response.added?.shows ?: 0,
                notFound = response.notFoundCount()
            )
        }
    }

    suspend fun removeShowFromWatchlist(
        tmdbId: Int
    ): ServiceResult<SyncResponse> {
        return execute<TraktSyncResponse>(
            request = {
                traktTools.removeShowFromWatchlist(tmdbId)
            }
        ) { response ->
            SyncResponse(
                deleted = response.deleted?.shows ?: 0,
                notFound = response.notFoundCount()
            )
        }
    }

    suspend fun rateShow(
        tmdbId: Int,
        rating: Rating?
    ): ServiceResult<SyncResponse> {
        return execute<TraktSyncResponse>(
            request = {
                traktTools.rateShow(tmdbId, rating)
            }
        ) { response ->
            SyncResponse(
                added = response.added?.shows ?: 0,
                notFound = response.notFoundCount()
            )
        }
    }

    suspend fun rateEpisode(
        showTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        rating: Rating?
    ): ServiceResult<SyncResponse> {
        return execute<TraktSyncResponse>(
            request = {
                traktTools.rateEpisode(
                    showTmdbId,
                    seasonNumber,
                    episodeNumber,
                    rating
                )
            }
        ) { response ->
            SyncResponse(
                added = response.added?.episodes ?: 0,
                notFound = response.notFoundCount()
            )
        }
    }

    suspend fun rateMovie(
        tmdbId: Int,
        rating: Rating?
    ): ServiceResult<SyncResponse> {
        return execute<TraktSyncResponse>(
            request = {
                traktTools.rateMovie(tmdbId, rating)
            }
        ) { response ->
            SyncResponse(
                added = response.added?.movies ?: 0,
                notFound = response.notFoundCount()
            )
        }
    }

    private suspend fun <T> execute(
        request: suspend () -> TraktTools4.TraktNonNullResponse<T>,
        transform: (T) -> SyncResponse
    ): ServiceResult<SyncResponse> {
        if (!isTraktAuthenticated()) {
            return ServiceResult.failure(ErrorType.UNAUTHORIZED)
        }

        return when (val response = request()) {
            is TraktTools4.TraktNonNullResponse.Success -> {
                val result = transform(response.data)

                if (result.notFound > 0) {
                    ServiceResult.failure(ErrorType.NOT_FOUND)
                } else {
                    ServiceResult.success(result)
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

    private fun TraktSyncResponse.notFoundCount(): Int {
        return if (TraktTools4.isNotFound(this)) 1 else 0
    }
}