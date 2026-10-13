// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2013 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.sync

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import com.battlelancer.seriesguide.R
import com.battlelancer.seriesguide.SgApp
import com.battlelancer.seriesguide.settings.UpdateSettings
import com.battlelancer.seriesguide.shows.tools.ShowSync
import com.battlelancer.seriesguide.sync.SyncOptions.SyncType
import com.uwetrottmann.androidutils.AndroidUtils
import timber.log.Timber

/**
 * [AbstractThreadedSyncAdapter] que atua como ponto de entrada para o sistema operacional Android,
 * delegando a orquestração dos múltiplos subsistemas para a fachada [SyncCoordinator].
 */
class SgSyncAdapter(
    context: Context,
    private val syncCoordinator: SyncCoordinator = SyncCoordinatorImpl(context)
) : AbstractThreadedSyncAdapter(context, true, false) {

    enum class UpdateResult {
        SUCCESS, INCOMPLETE
    }

    init {
        Timber.d("Creating sync adapter")
    }

    /**
     * Definido por [onSyncCanceled], que é chamado a partir de outra thread.
     */
    @Volatile
    private var isCanceled = false

    override fun onSyncCanceled() {
        // Não chama super para evitar interrupção abrupta da thread;
        // o progresso é verificado entre etapas por [SyncProgress.throwIfCanceled].
        isCanceled = true
    }

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        // Redefine a flag de cancelamento com segurança pois apenas um sync roda por vez
        isCanceled = false

        // Determina o tipo de sincronização
        val options = SyncOptions(extras)
        Timber.i(
            "Syncing: %s%s",
            options.syncType,
            if (options.syncImmediately) "_IMMEDIATE" else "_REGULAR"
        )

        // JOBS: processamento de tarefas pendentes de rede
        if (options.syncType == SyncType.JOBS || options.syncType == SyncType.DELTA) {
            syncCoordinator.processJobs()
            if (options.syncType == SyncType.JOBS) {
                return  // nada mais a fazer para SyncType.JOBS
            }
        }

        // SYNC: sincronização de dados
        val showSync = ShowSync(options.syncType, options.singleShowId)
        val currentTime = System.currentTimeMillis()
        if (!options.syncImmediately && showSync.isSyncMultiple) {
            if (!isTimeForSync(context, currentTime)) {
                Timber.d("Syncing: ABORT_DID_JUST_SYNC")
                return
            }
        }

        val progress = SyncProgress { isCanceled }
        try {
            // Delega a orquestração completa dos subsistemas à Fachada
            syncCoordinator.performSync(showSync, currentTime, progress)
        } catch (e: SyncCanceledException) {
            Timber.d(e, "Sync canceled by system, trying again later.")
            progress.recordError()
            progress.setImportantMessageIfNone("Interrupted by system, trying again later.")
        }
        progress.publishFinished()
    }

    companion object {
        /** Usado ao registrar o SyncAdapter, ver [AccountUtils.createAccount]. */
        const val SYNC_INTERVAL_SECONDS = 24 * 60 * 60 // 1 dia (em segundos)

        /** Intervalo mínimo de sincronização em minutos. */
        private const val SYNC_INTERVAL_MINIMUM_MINUTES = 5

        /** Um dos [SyncType]. */
        const val EXTRA_SYNC_TYPE = "com.battlelancer.seriesguide.sync_type"

        /** Se [EXTRA_SYNC_TYPE] for [SyncType.SINGLE], o ID da série a ser sincronizada. */
        const val EXTRA_SYNC_SHOW_ID = "com.battlelancer.seriesguide.sync_show"

        /** Se a sincronização deve ocorrer imediatamente independentemente do limite de tempo. */
        const val EXTRA_SYNC_IMMEDIATE = "com.battlelancer.seriesguide.sync_immediate"

        private fun isTimeForSync(context: Context, currentTime: Long): Boolean {
            val previousUpdateTime = UpdateSettings.getLastAutoUpdateTime(context)
            return currentTime - previousUpdateTime >
                    SYNC_INTERVAL_MINIMUM_MINUTES * DateUtils.MINUTE_IN_MILLIS
        }

        /**
         * Dispara [requestSyncIfConnected] se não houver sincronização pendente recente.
         */
        @JvmStatic
        fun requestSyncIfTime(context: Context) {
            val account = AccountUtils.getAccount(context)
            if (account == null) {
                Timber.d("Requesting sync: no account, skip.")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ContentResolver.isSyncActive(account, SgApp.CONTENT_AUTHORITY)) {
                    Timber.d("Requesting sync: sync active, skip.")
                    return
                }
            } else {
                if (ContentResolver.isSyncPending(account, SgApp.CONTENT_AUTHORITY)) {
                    Timber.d("Requesting sync: sync pending, skip.")
                    return
                }
            }
            if (!isTimeForSync(context, System.currentTimeMillis())) {
                Timber.d("Requesting sync: did sync recently, skip.")
                return
            }
            requestSyncIfConnected(context, SyncType.DELTA, 0)
        }

        /**
         * Agenda sincronização para uma série específica.
         */
        fun requestSyncSingleIfConnected(context: Context, showId: Long) {
            requestSyncIfConnected(context, SyncType.SINGLE, showId)
        }

        private fun requestSyncIfConnected(context: Context, syncType: SyncType, showId: Long) {
            if (!AndroidUtils.isNetworkConnected(context) || !isSyncAutomatically(context)) {
                Timber.d("Requesting sync: offline or disabled, skip.")
                return
            }
            val args = Bundle()
            args.putInt(EXTRA_SYNC_TYPE, syncType.id)
            args.putLong(EXTRA_SYNC_SHOW_ID, showId)
            requestSync(context, args)
        }

        fun requestSyncJobsImmediate(context: Context) {
            val args = Bundle()
            args.putBoolean(EXTRA_SYNC_IMMEDIATE, true)
            args.putInt(EXTRA_SYNC_TYPE, SyncType.JOBS.id)
            args.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            args.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            requestSync(context, args)
        }

        fun requestSyncDeltaImmediate(context: Context, showStatusToast: Boolean) {
            requestSyncImmediate(context, SyncType.DELTA, 0, showStatusToast)
        }

        fun requestSyncSingleImmediate(context: Context, showStatusToast: Boolean, showId: Long) {
            requestSyncImmediate(context, SyncType.SINGLE, showId, showStatusToast)
        }

        fun requestSyncFullImmediate(context: Context, showStatusToast: Boolean) {
            requestSyncImmediate(context, SyncType.FULL, 0, showStatusToast)
        }

        private fun requestSyncImmediate(
            context: Context,
            syncType: SyncType,
            showId: Long,
            showStatusToast: Boolean
        ) {
            if (showStatusToast) {
                if (!AndroidUtils.isNetworkConnected(context)) {
                    Toast.makeText(context, R.string.update_no_connection, Toast.LENGTH_LONG).show()
                    return
                }
                Toast.makeText(context, R.string.update_scheduled, Toast.LENGTH_SHORT).show()
            }

            val args = Bundle()
            args.putBoolean(EXTRA_SYNC_IMMEDIATE, true)
            args.putInt(EXTRA_SYNC_TYPE, syncType.id)
            args.putLong(EXTRA_SYNC_SHOW_ID, showId)
            args.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            args.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)

            requestSync(context, args)
        }

        private fun requestSync(context: Context, args: Bundle) {
            val account = AccountUtils.getAccount(context) ?: return
            Timber.d("Requesting sync: queueing request!")
            ContentResolver.requestSync(account, SgApp.CONTENT_AUTHORITY, args)
        }

        fun cancelSync(context: Context) {
            val account = AccountUtils.getAccount(context) ?: return
            ContentResolver.cancelSync(account, SgApp.CONTENT_AUTHORITY)
        }

        fun isSyncActive(context: Context, isDisplayWarning: Boolean): Boolean {
            val account = AccountUtils.getAccount(context) ?: return false
            val isSyncActive = ContentResolver.isSyncActive(
                account,
                SgApp.CONTENT_AUTHORITY
            )
            if (isSyncActive && isDisplayWarning) {
                Toast.makeText(context, R.string.update_inprogress, Toast.LENGTH_LONG).show()
            }
            return isSyncActive
        }

        fun isSyncAutomatically(context: Context): Boolean {
            val account = AccountUtils.getAccount(context)
            return account != null && ContentResolver.getSyncAutomatically(
                account,
                SgApp.CONTENT_AUTHORITY
            )
        }

        fun setSyncAutomatically(context: Context, sync: Boolean) {
            val account = AccountUtils.getAccount(context) ?: return
            ContentResolver.setSyncAutomatically(account, SgApp.CONTENT_AUTHORITY, sync)
        }
    }
}