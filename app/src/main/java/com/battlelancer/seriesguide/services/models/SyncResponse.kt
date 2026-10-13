// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.services.models

data class SyncResponse(
    val added: Int = 0,
    val deleted: Int = 0,
    val notFound: Int = 0
)