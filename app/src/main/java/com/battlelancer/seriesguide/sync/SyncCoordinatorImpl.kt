// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.battlelancer.seriesguide.SgApp
import com.battlelancer.seriesguide.appwidget.ListWidgetProvider
import com.battlelancer.seriesguide.backend.HexagonTools
import com.battlelancer.seriesguide.backend.settings.HexagonSettings
import com.battlelancer.seriesguide.jobs.NetworkJobProcessor
import com.battlelancer.seriesguide.lists.ListsTools
import com.battlelancer.seriesguide.movies.tools.MovieTools
import com.battlelancer.seriesguide.notifications.NotificationService
import com.battlelancer.seriesguide.provider.SeriesGuideDatabase
import com.battlelancer.seriesguide.settings.UpdateSettings
import com.battlelancer.seriesguide.shows.tools.ShowSync
import com.battlelancer.seriesguide.shows.tools.ShowTools2
import com.battlelancer.seriesguide.traktapi.SgTrakt
import com.battlelancer.seriesguide.traktapi.TraktCredentials
import com.battlelancer.seriesguide.util.TaskManager
import com.uwetrottmann.tmdb2.Tmdb
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Implementação da Fachada (Facade) que orquestra os subsistemas de sincronização do SeriesGuide.
 *
 * Encapsula a complexidade e a coordenação procedural de múltiplos subsistemas remotos
 * (TMDb, Cloud/Hexagon, Trakt) e locais (Room, FTS, Notificações, Widgets e Tarefas),
 * oferecendo uma interface coesa para os adaptadores de sistema.
 */
class SyncCoordinatorImpl(
    private val context: Context,
    private val hexagonToolsProvider: () -> HexagonTools = {
        SgApp.getServicesComponent(context).hexagonTools()
    },
    private val tmdbProvider: () -> Tmdb = {
        SgApp.getServicesComponent(context).tmdb()
    },
    private val traktProvider: () -> SgTrakt = {
        SgApp.getServicesComponent(context).trakt()
    },
    private val showTools2Provider: () -> ShowTools2 = {
        SgApp.getServicesComponent(context).showTools()
    },
    private val movieToolsProvider: () -> MovieTools = {
        SgApp.getServicesComponent(context).movieTools()
    },
    private val networkJobProcessorProvider: () -> NetworkJobProcessor = {
        NetworkJobProcessor(context)
    }
) : SyncCoordinator {

    override fun processJobs() {
        networkJobProcessorProvider().process()
    }

    @Throws(SyncCanceledException::class)
    override fun performSync(
        showSync: ShowSync,
        currentTime: Long,
        progress: SyncProgress
    ): SgSyncAdapter.UpdateResult? {
        progress.publish(SyncProgress.Step.TMDB)
        progress.throwIfCanceled()

        // Obtém configuração mais recente do TMDb e provedores de streaming
        val tmdbSync = TmdbSync(context, tmdbProvider().configurationService(), movieToolsProvider())
        tmdbSync.updateConfigurationAndWatchProviders(progress)

        progress.throwIfCanceled()

        var hasAddedShows = false
        try {
            var resultCode = showSync.sync(context, currentTime, progress)
            Timber.d("Syncing: TMDB shows...DONE")
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (resultCode == null || resultCode == SgSyncAdapter.UpdateResult.INCOMPLETE) {
                progress.recordError()
                if (showSync.isSyncMultiple) {
                    updateTimeAndFailedCounter(prefs, resultCode)
                }
                return resultCode // Tentar novamente mais tarde
            }

            // Executa etapas complementares se for uma sincronização completa/múltipla
            if (showSync.isSyncMultiple) {
                progress.throwIfCanceled()

                // Sincronização com Cloud/Hexagon
                val isHexagonEnabled = HexagonSettings.isEnabled(context)
                if (isHexagonEnabled) {
                    val resultHexagonSync = HexagonSync(
                        context,
                        hexagonToolsProvider(),
                        movieToolsProvider(),
                        progress
                    ).sync()
                    hasAddedShows = resultHexagonSync.hasAddedShows
                    // Não sobrescreve falhas anteriores
                    if (resultCode == SgSyncAdapter.UpdateResult.SUCCESS) {
                        resultCode = if (resultHexagonSync.success) {
                            SgSyncAdapter.UpdateResult.SUCCESS
                        } else {
                            SgSyncAdapter.UpdateResult.INCOMPLETE
                        }
                    }
                    Timber.d("Syncing: Hexagon...DONE")
                } else {
                    Timber.d("Syncing: Hexagon...SKIP")
                }

                progress.throwIfCanceled()

                // Migra itens legados de listas
                ListsTools.migrateTvdbShowListItemsToTmdbIds(context)

                progress.throwIfCanceled()

                // Sincronização com Trakt (apenas avaliações se Hexagon estiver ativo)
                if (TraktCredentials.get(context).hasCredentials()) {
                    val resultTraktSync = TraktSync(
                        context,
                        showTools2Provider(),
                        movieToolsProvider(),
                        traktProvider(),
                        progress
                    ).sync(isHexagonEnabled)
                    // Não sobrescreve falhas anteriores
                    if (resultCode == SgSyncAdapter.UpdateResult.SUCCESS) {
                        resultCode = resultTraktSync
                    }
                    Timber.d("Syncing: trakt...DONE")
                } else {
                    Timber.d("Syncing: trakt...SKIP")
                }

                progress.throwIfCanceled()

                // Atualiza próximos episódios para todas as séries
                TaskManager.tryNextEpisodeUpdateTask(context)

                // Atualização do banco de filmes
                runBlocking {
                    movieToolsProvider().updateDatabaseAfterCustomListChange()
                }
                Timber.d("Syncing: updating movie database...DONE")

                progress.throwIfCanceled()

                // Atualiza dados de filmes a serem lançados
                if (!tmdbSync.updateMovies(progress)) {
                    progress.recordError()
                }
                Timber.d("Syncing: TMDB movies...DONE")

                updateTimeAndFailedCounter(prefs, resultCode)
            }

            Timber.i("Syncing: %s", resultCode.toString())
            return resultCode
        } finally {
            // Finaliza rotinas complementares mesmo se a sincronização falhar ou for cancelada
            if (showSync.isSyncMultiple && showSync.hasUpdatedShows() && !hasAddedShows) {
                SeriesGuideDatabase.rebuildFtsTable(context)
            }
            if (showSync.hasUpdatedShows()) {
                NotificationService.trigger(context)
            }
            ListWidgetProvider.notifyDataChanged(context)
        }
    }

    private fun updateTimeAndFailedCounter(
        prefs: SharedPreferences,
        resultCode: SgSyncAdapter.UpdateResult?
    ) {
        val currentTime = System.currentTimeMillis()
        if (resultCode == SgSyncAdapter.UpdateResult.SUCCESS) {
            prefs.edit()
                .putLong(UpdateSettings.KEY_LASTUPDATE, currentTime)
                .putInt(UpdateSettings.KEY_FAILED_COUNTER, 0)
                .apply()
        } else {
            val failedCount = prefs.getInt(UpdateSettings.KEY_FAILED_COUNTER, 0)
            prefs.edit()
                .putLong(UpdateSettings.KEY_LASTUPDATE, currentTime)
                .putInt(UpdateSettings.KEY_FAILED_COUNTER, failedCount + 1)
                .apply()
        }
    }
}
