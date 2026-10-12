// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 SeriesGuide Contributors

package com.battlelancer.seriesguide.dataliberation

import com.battlelancer.seriesguide.dataliberation.JsonExportTask.ListItemTypesExport
import com.battlelancer.seriesguide.dataliberation.model.ListItem
import com.battlelancer.seriesguide.lists.database.SgList
import com.battlelancer.seriesguide.lists.database.SgListHelper
import com.battlelancer.seriesguide.provider.SeriesGuideContract.ListItemTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.OutputStream
import com.battlelancer.seriesguide.dataliberation.model.List as ExportList

// Responsável especificamente pela exportação das Listas personalizadas do usuário

class ListsExportStrategy(
    private val sgListHelper: SgListHelper
) : ExportStrategy {
    @Throws(IOException::class)
    override suspend fun writeToStream(
        coroutineScope: CoroutineScope,
        out: OutputStream,
        isFullDump: Boolean,
        onProgressUpdate: suspend (total: Int, completed: Int) -> Unit
    ) {
        val transform: (SgList) -> ExportList = { sgList ->
            val list = ExportList()
            list.list_id = sgList.listId
            list.name = sgList.name
            list.order = sgList.orderOrDefault
            addListItems(list)
            list        
    }
        val lists = sgListHelper.getListsForExport()

        coroutineScope.ensureActive()
        
        SgJsonWriter(
            itemsToTransform = lists,
            outputClass = ExportList::class.java,
            outputStream = out,
            transform = transform
        ).write { total, done ->
            onProgressUpdate(total, done)
        }
    }

    private fun addListItems(list: ExportList) {
        val listItems = sgListHelper.getListItemsForExport(list.list_id)
        
        list.items = ArrayList()
        for (listItem in listItems) {
            val item = ListItem()
            item.list_item_id = listItem.listItemId
            item.externalId = listItem.itemRefId
            when (listItem.type) {
                ListItemTypes.TMDB_MOVIE -> item.type = ListItemTypesExport.MOVIE
                ListItemTypes.TVDB_SHOW -> item.type = ListItemTypesExport.SHOW
                ListItemTypes.TMDB_SHOW -> item.type = ListItemTypesExport.TMDB_SHOW
                ListItemTypes.SEASON -> item.type = ListItemTypesExport.SEASON
                ListItemTypes.EPISODE -> item.type = ListItemTypesExport.EPISODE
            }
            list.items.add(item)
        }
    }
}
    
