// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.services.models

sealed class ServiceResult<out T> {
    data class Success<out T>(val data: T) : ServiceResult<T>()
    data class Failure(val errorType: ErrorType) : ServiceResult<Nothing>()

    val success: Boolean get() = this is Success
    val error: ErrorType? get() = (this as? Failure)?.errorType

    companion object {
        fun <T> success(data: T): ServiceResult<T> = Success(data)
        fun failure(error: ErrorType): ServiceResult<Nothing> = Failure(error)
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