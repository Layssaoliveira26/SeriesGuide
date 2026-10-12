// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2013 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.dataliberation

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.annotation.VisibleForTesting
import com.battlelancer.seriesguide.R
import com.battlelancer.seriesguide.SgApp
import com.battlelancer.seriesguide.dataliberation.DataLiberationFragment.LiberationResultEvent
import com.battlelancer.seriesguide.lists.database.SgListHelper
import com.battlelancer.seriesguide.movies.database.MovieHelper
import com.battlelancer.seriesguide.provider.SgRoomDatabase
import com.battlelancer.seriesguide.shows.database.SgEpisode2Helper
import com.battlelancer.seriesguide.shows.database.SgSeason2Helper
import com.battlelancer.seriesguide.shows.database.SgShow2Helper
import com.battlelancer.seriesguide.util.Errors
import com.battlelancer.seriesguide.util.TextTools
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

@Suppress("BlockingMethodInNonBlockingContext")
open class JsonExportTask(
    context: Context,
    private val progressListener: OnTaskProgressListener?,
    private val isFullDump: Boolean,
    private val showsExportStrategy: ExportStrategy,
    private val listsExportStrategy: ExportStrategy,
    private val moviesExportStrategy: ExportStrategy
) {

    constructor(
        context: Context,
        progressListener: OnTaskProgressListener?,
        isFullDump: Boolean
    ) : this(
        context = context,
        progressListener = progressListener,
        isFullDump = isFullDump,
        showsExportStrategy = ShowsExportStrategy(
            SgRoomDatabase.getInstance(context).sgShow2Helper(),
            SgRoomDatabase.getInstance(context).sgSeason2Helper(),
            SgRoomDatabase.getInstance(context).sgEpisode2Helper()
        ),
        listsExportStrategy = ListsExportStrategy(
            SgRoomDatabase.getInstance(context).sgListHelper()
        ),
        moviesExportStrategy = MoviesExportStrategy(
            SgRoomDatabase.getInstance(context).movieHelper()
        )
    )

    private val context: Context = context.applicationContext

    @VisibleForTesting
    var errorCause: String? = null
        private set

    @VisibleForTesting
    var testExportFile: File? = null

    fun launch(export: Export): Job {
        return SgApp.coroutineScope.launch {
            run(export)
        }
    }

    suspend fun run(export: Export): Int {
        return withContext(Dispatchers.IO) {
            val result = exportData(this, export)
            onPostExecute(result)
            result
        }
    }

    internal suspend fun writeJsonStream(
        coroutineScope: CoroutineScope,
        out: OutputStream,
        export: Export
    ) {
        strategyFor(export).writeToStream(
            coroutineScope = coroutineScope,
            out = out,
            isFullDump = isFullDump
        ) { total, completed ->
            onProgressUpdate(total, completed)
        }
    }

    private fun strategyFor(export: Export): ExportStrategy {
        return when (export) {
            Export.Shows -> showsExportStrategy
            Export.Lists -> listsExportStrategy
            Export.Movies -> moviesExportStrategy
        }
    }

    private suspend fun onProgressUpdate(total: Int, completed: Int) {
        withContext(Dispatchers.Main) {
            progressListener?.onProgressUpdate(total, completed)
        }
    }

    private fun onPostExecute(result: Int) {
        val message: String
        val showIndefinite: Boolean
        when (result) {
            SUCCESS -> {
                message = context.getString(R.string.status_successful)
                showIndefinite = false
            }

            ERROR_FILE_ACCESS -> {
                message = TextTools.dotSeparate(
                    context,
                    R.string.status_failure,
                    R.string.status_failed_file_access
                )
                showIndefinite = true
            }

            else -> {
                message = context.getString(R.string.status_failure)
                showIndefinite = true
            }
        }
        EventBus.getDefault().post(
            LiberationResultEvent(
                TextTools.dotSeparate(context, message, errorCause),
                showIndefinite
            )
        )
    }

    private suspend fun exportData(coroutineScope: CoroutineScope, export: Export): Int {
        try {
            val testExportFile = testExportFile
            var pfd: ParcelFileDescriptor? = null
            val out = if (testExportFile == null) {
                val exportFileUri =
                    BackupSettings.getExportFileUri(context, export, isAutoBackup = false)
                        ?: return ERROR_FILE_ACCESS
                pfd = context.contentResolver.openFileDescriptor(exportFileUri, FILE_MODE)
                    ?: return ERROR_FILE_ACCESS
                FileOutputStream(pfd.fileDescriptor)
            } else {
                FileOutputStream(testExportFile)
            }

            try {
                writeJsonStream(coroutineScope, out, export)
            } finally {
                out.close()
                pfd?.close()
            }
        } catch (e: FileNotFoundException) {
            Timber.e(e, "File not found.")
            removeExportFileUri(export)
            errorCause = e.message
            return ERROR_FILE_ACCESS
        } catch (e: IOException) {
            Timber.e(e, "Failed to write to file.")
            removeExportFileUri(export)
            errorCause = e.message
            return ERROR_FILE_ACCESS
        } catch (e: SecurityException) {
            Timber.e(e, "No permission to access file.")
            removeExportFileUri(export)
            errorCause = e.message
            return ERROR_FILE_ACCESS
        } catch (e: JsonParseException) {
            Timber.e(e, "Failed to create JSON export.")
            errorCause = e.message
            return ERROR
        } catch (e: CancellationException) {
            Timber.e(e, "JSON export job cancelled.")
            errorCause = e.message
            return ERROR
        } catch (e: Exception) {
            Errors.logAndReport("Export failed unexpectedly.", e)
            errorCause = e.message
            return ERROR
        }

        return SUCCESS
    }

    private fun removeExportFileUri(export: Export) {
        BackupSettings.storeExportFileUri(context, export, null, isAutoBackup = false)
    }

    companion object {
        const val EXPORT_JSON_FILE_SHOWS = "seriesguide-shows-backup.json"
        const val EXPORT_JSON_FILE_LISTS = "seriesguide-lists-backup.json"
        const val EXPORT_JSON_FILE_MOVIES = "seriesguide-movies-backup.json"

        const val SUCCESS = 1
        private const val ERROR_FILE_ACCESS = 0
        private const val ERROR = -1
        const val FILE_MODE = "wt"
    }

    interface OnTaskProgressListener {
        fun onProgressUpdate(total: Int, completed: Int)
    }

    sealed class Export(val name: String) {
        object Shows : Export("seriesguide-shows")
        object Lists : Export("seriesguide-lists")
        object Movies : Export("seriesguide-movies")
    }

    object ShowStatusExport {
        const val IN_PRODUCTION = "in_production"
        const val PILOT = "pilot"
        const val CANCELED = "canceled"
        const val UPCOMING = "upcoming"
        const val CONTINUING = "continuing"
        const val ENDED = "ended"
        const val UNKNOWN = "unknown"
    }

    object ListItemTypesExport {
        const val MOVIE = "movie"
        const val SHOW = "show"
        const val TMDB_SHOW = "tmdb-show"
        const val SEASON = "season"
        const val EPISODE = "episode"
        const val IMDB_MOVIE = "imdb-movie"
    }
}
