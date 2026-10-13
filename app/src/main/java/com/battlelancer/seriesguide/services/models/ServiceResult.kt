// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.services.models

data class ServiceResult<T>(
    val data: T? = null,
    val success: Boolean = false,
    val error: ErrorType? = null
) {
    companion object {
        fun <T> success(data: T): ServiceResult<T> = ServiceResult(
            data = data,
            success = true,
            error = null
        )

        fun <T> successEmpty(): ServiceResult<T> = ServiceResult(
            data = null,
            success = true,
            error = null
        )

        fun <T> failure(error: ErrorType): ServiceResult<T> = ServiceResult(
            data = null,
            success = false,
            error = error
        )
    }
}

enum class ErrorType {
    NETWORK,
    UNAUTHORIZED,
    NOT_FOUND,
    RATE_LIMIT,
    ACCOUNT_LOCKED,
    NOT_VIP,
    OTHER
}
