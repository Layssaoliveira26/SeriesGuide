// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2016 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.util.tasks

import android.content.Context
import com.battlelancer.seriesguide.services.TrackingService
import com.battlelancer.seriesguide.services.models.ServiceResult
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
            return sendToService(createService()).toActionResult()
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
        service: TrackingService
    ): ServiceResult<Unit>

}
