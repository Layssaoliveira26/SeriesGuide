// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2016 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.util.tasks

import android.content.Context
import com.battlelancer.seriesguide.services.adapter.TraktServiceAdapter
import com.battlelancer.seriesguide.services.models.ErrorType
import com.battlelancer.seriesguide.services.models.ServiceResult
import com.battlelancer.seriesguide.services.models.SyncResponse
import org.greenrobot.eventbus.EventBus

abstract class BaseShowActionTask(
    context: Context,
    protected val showTmdbId: Int
) : BaseActionTask(context) {

    class ShowChangedEvent

    override val isSendingToHexagon: Boolean
        get() = false

    override suspend fun doBackgroundAction(): Int {
        if (isSendingToTrakt) {
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

        return SUCCESS
    }

    override fun onPostExecute(result: Int) {
        super.onPostExecute(result)

        if (result == SUCCESS) {
            EventBus.getDefault().post(ShowChangedEvent())
        }
    }

    protected abstract suspend fun sendToService(
        service: TraktServiceAdapter
    ): ServiceResult<SyncResponse>

}
