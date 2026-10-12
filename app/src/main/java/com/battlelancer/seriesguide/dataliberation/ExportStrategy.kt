// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 SeriesGuide Contributors

package com.battlelancer.seriesguide.dataliberation

import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.io.OutputStream

interface ExportStrategy {
    @Throws(IOException::class)
    suspend fun writeToStream(
        coroutineScope: CoroutineScope,
        out: OutputStream,
        isFullDump: Boolean,
        onProgressUpdate: suspend (total: Int, completed: Int) -> Unit
    )
}