// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2015 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.util.tasks

import android.content.Context
import com.battlelancer.seriesguide.R
import com.battlelancer.seriesguide.services.adapter.TraktServiceAdapter
import com.battlelancer.seriesguide.services.models.ErrorType
import com.battlelancer.seriesguide.services.models.ServiceResult
import com.battlelancer.seriesguide.services.models.SyncResponse
import com.uwetrottmann.trakt5.enums.Rating

/**
 * Stores the [rating] in the database and sends it to Trakt.
 *
 * If it is `null`, removes the rating instead.
 */
abstract class BaseRateItemTask(
    context: Context,
    protected val rating: Rating?
) : BaseActionTask(context) {

    override val isSendingToHexagon: Boolean
        get() = false // Hexagon does not support ratings.

    override suspend fun doBackgroundAction(): Int {
        if (isSendingToTrakt) {
            if (!loadTraktIds()) return ERROR_DATABASE

            val result = sendToService(TraktServiceAdapter.create(context))
            if (!result.success) {
                return when (result.error) {
                    ErrorType.UNAUTHORIZED -> ERROR_TRAKT_AUTH
                    ErrorType.NOT_FOUND -> ERROR_TRAKT_API_NOT_FOUND
                    ErrorType.ACCOUNT_LOCKED -> ERROR_TRAKT_ACCOUNT_LOCKED
                    ErrorType.RATE_LIMIT -> ERROR_TRAKT_ACCOUNT_LIMIT_EXCEEDED
                    else -> ERROR_TRAKT_API_CLIENT
                }
            }
        }

        if (!doDatabaseUpdate()) {
            return ERROR_DATABASE
        }

        return SUCCESS
    }

    override val successTextResId: Int
        get() = R.string.ack_rated

    /**
     * Loads the IDs required to identify the item on Trakt. Returns `false` on database error (if
     * required data is missing).
     */
    protected abstract fun loadTraktIds(): Boolean

    /**
     * Sends the [rating] to Trakt. Only called if [loadTraktIds] was successful.
     */
    protected abstract suspend fun sendToService(
        service: TraktServiceAdapter
    ): ServiceResult<SyncResponse>

    protected abstract fun doDatabaseUpdate(): Boolean
}
