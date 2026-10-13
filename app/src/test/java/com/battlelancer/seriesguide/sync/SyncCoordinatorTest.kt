// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.sync

import android.content.Context
import com.battlelancer.seriesguide.jobs.NetworkJobProcessor
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * Testes unitários para validar a Fachada [SyncCoordinator] e sua implementação [SyncCoordinatorImpl].
 */
class SyncCoordinatorTest {

    @Test
    fun processJobs_delegaParaNetworkJobProcessor() {
        val mockContext = mock(Context::class.java)
        val mockJobProcessor = mock(NetworkJobProcessor::class.java)

        val coordinator = SyncCoordinatorImpl(
            context = mockContext,
            networkJobProcessorProvider = { mockJobProcessor }
        )

        coordinator.processJobs()

        // Verifica se a fachada delegou a execução dos jobs para o processador de tarefas
        verify(mockJobProcessor).process()
    }
}
