// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.sync

import com.battlelancer.seriesguide.shows.tools.ShowSync

/**
 * Interface da Fachada (Facade) para o subsistema de sincronização do SeriesGuide.
 *
 * Unifica e simplifica os múltiplos fluxos de sincronização da aplicação (TMDb,
 * Séries, Cloud/Hexagon, Trakt, Filmes, Pesquisa FTS, Notificações e Widgets),
 * isolando a lógica de negócio do componente legado [SgSyncAdapter] do Android.
 */
interface SyncCoordinator {

    /**
     * Executa os jobs pendentes de rede através do processador de tarefas.
     */
    fun processJobs()

    /**
     * Orquestra a execução completa da sincronização de dados remotos e locais.
     *
     * @param showSync Configuração e escopo da sincronização de séries.
     * @param currentTime Timestamp atual da sincronização.
     * @param progress Rastreador de progresso e cancelamento.
     * @return O resultado consolidado da sincronização ([SgSyncAdapter.UpdateResult]).
     * @throws SyncCanceledException Se a sincronização for cancelada durante o processo.
     */
    @Throws(SyncCanceledException::class)
    fun performSync(
        showSync: ShowSync,
        currentTime: Long,
        progress: SyncProgress
    ): SgSyncAdapter.UpdateResult?

}
