// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright © 2026 Uwe Trottmann <uwe@uwetrottmann.com>

package com.battlelancer.seriesguide.services.adapter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.battlelancer.seriesguide.EmptyTestApplication
import com.battlelancer.seriesguide.services.TrackingService
import com.battlelancer.seriesguide.services.models.ErrorType
import com.battlelancer.seriesguide.services.models.ServiceResult
import com.google.common.truth.Truth.assertThat
import com.uwetrottmann.trakt5.enums.Rating
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyTestApplication::class)
class TraktServiceAdapterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun create_returnsTrackingServiceInstance() {
        val service: TrackingService = TraktServiceAdapter.create(context)
        assertThat(service).isNotNull()
        assertThat(service).isInstanceOf(TrackingService::class.java)
    }

    @Test
    fun unauthenticatedCalls_returnUnauthorizedError() = runTest {
        val service = TraktServiceAdapter.create(context)

        // Sem credenciais ativas, as operações devem falhar com UNAUTHORIZED de forma segura
        val addResult = service.addShowToWatchlist(100)
        assertThat(addResult).isInstanceOf(ServiceResult.Failure::class.java)
        assertThat((addResult as ServiceResult.Failure).errorType).isEqualTo(ErrorType.UNAUTHORIZED)

        val removeResult = service.removeShowFromWatchlist(100)
        assertThat(removeResult).isInstanceOf(ServiceResult.Failure::class.java)
        assertThat((removeResult as ServiceResult.Failure).errorType).isEqualTo(ErrorType.UNAUTHORIZED)

        val rateShowResult = service.rateShow(100, 8)
        assertThat(rateShowResult).isInstanceOf(ServiceResult.Failure::class.java)
        assertThat((rateShowResult as ServiceResult.Failure).errorType).isEqualTo(ErrorType.UNAUTHORIZED)

        val rateEpisodeResult = service.rateEpisode(100, 1, 1, 10)
        assertThat(rateEpisodeResult).isInstanceOf(ServiceResult.Failure::class.java)
        assertThat((rateEpisodeResult as ServiceResult.Failure).errorType).isEqualTo(ErrorType.UNAUTHORIZED)

        val rateMovieResult = service.rateMovie(200, 5)
        assertThat(rateMovieResult).isInstanceOf(ServiceResult.Failure::class.java)
        assertThat((rateMovieResult as ServiceResult.Failure).errorType).isEqualTo(ErrorType.UNAUTHORIZED)
    }

    @Test
    fun ratingConversion_mapsCorrectlyToTraktEnum() {
        assertThat(Rating.fromValue(1)).isEqualTo(Rating.WEAKSAUCE)
        assertThat(Rating.fromValue(5)).isEqualTo(Rating.MEH)
        assertThat(Rating.fromValue(8)).isEqualTo(Rating.GREAT)
        assertThat(Rating.fromValue(10)).isEqualTo(Rating.TOTALLYNINJA)
        assertThat(Rating.fromValue(null)).isNull()
        assertThat(Rating.fromValue(0)).isNull()
    }
}
