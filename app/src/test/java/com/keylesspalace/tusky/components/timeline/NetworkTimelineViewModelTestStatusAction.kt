/*
 * Copyright 2023 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.timeline

import app.cash.turbine.test
import at.connyduck.calladapter.networkresult.NetworkResult
import com.google.common.truth.Truth.assertThat
import com.keylesspalace.tusky.FilterV1Test.Companion.mockStatus
import com.keylesspalace.tusky.components.timeline.viewmodel.StatusAction
import com.keylesspalace.tusky.components.timeline.viewmodel.StatusActionSuccess
import com.keylesspalace.tusky.components.timeline.viewmodel.UiError
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * Verify that [StatusAction] are handled correctly on receipt:
 *
 * - Is the correct [UiSuccess] or [UiError] value emitted?
 * - Is the correct [TimelineCases] function called, with the correct arguments?
 *   This is only tested in the success case; if it passed there it must also
 *   have passed in the error case.
 */
// TODO: With the exception of the types, this is identical to
// NotificationsViewModelTestStatusAction.
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTimelineViewModelTestStatusAction : NetworkTimelineViewModelTestBase() {
    private val status = mockStatus(pollOptions = listOf("Choice 1", "Choice 2", "Choice 3"))
    private val statusViewData = StatusViewData(
        status = status,
        isExpanded = true,
        isShowingContent = false,
        isCollapsed = false
    )

    /** Action to bookmark a status */
    private val bookmarkAction = StatusAction.Bookmark(true, statusViewData)

    /** Action to favourite a status */
    private val favouriteAction = StatusAction.Favourite(true, statusViewData)

    /** Action to reblog a status */
    private val reblogAction = StatusAction.Reblog(true, statusViewData)

    /** Action to vote in a poll */
    private val voteInPollAction = StatusAction.VoteInPoll(
        poll = status.poll!!,
        choices = listOf(1, 0, 0),
        statusViewData
    )

    /** Captors for status ID and state arguments */
    private val id = argumentCaptor<String>()
    private val state = argumentCaptor<Boolean>()

    @Test
    fun `bookmark succeeds && emits UiSuccess`() = runTest {
        // Given
        timelineCases.stub { onBlocking { bookmark(any(), any()) } doReturn NetworkResult.success(status) }

        viewModel.uiSuccess.test {
            // When
            viewModel.accept(bookmarkAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(StatusActionSuccess.Bookmark::class.java)
            assertThat((item as StatusActionSuccess).action).isEqualTo(bookmarkAction)
        }

        // Then
        verify(timelineCases).bookmark(id.capture(), state.capture())
        assertThat(id.firstValue).isEqualTo(statusViewData.status.id)
        assertThat(state.firstValue).isEqualTo(true)
    }

    @Test
    fun `bookmark fails && emits UiError`() = runTest {
        // Given
        timelineCases.stub { onBlocking { bookmark(any(), any()) } doThrow httpException }

        viewModel.uiError.test {
            // When
            viewModel.accept(bookmarkAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(UiError.Bookmark::class.java)
            assertThat(item.action).isEqualTo(bookmarkAction)
        }
    }

    @Test
    fun `favourite succeeds && emits UiSuccess`() = runTest {
        // Given
        timelineCases.stub {
            onBlocking { favourite(any(), any()) } doReturn NetworkResult.success(status)
        }

        viewModel.uiSuccess.test {
            // When
            viewModel.accept(favouriteAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(StatusActionSuccess.Favourite::class.java)
            assertThat((item as StatusActionSuccess).action).isEqualTo(favouriteAction)
        }

        // Then
        verify(timelineCases).favourite(id.capture(), state.capture())
        assertThat(id.firstValue).isEqualTo(statusViewData.status.id)
        assertThat(state.firstValue).isEqualTo(true)
    }

    @Test
    fun `favourite fails && emits UiError`() = runTest {
        // Given
        timelineCases.stub { onBlocking { favourite(any(), any()) } doThrow httpException }

        viewModel.uiError.test {
            // When
            viewModel.accept(favouriteAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(UiError.Favourite::class.java)
            assertThat(item.action).isEqualTo(favouriteAction)
        }
    }

    @Test
    fun `reblog succeeds && emits UiSuccess`() = runTest {
        // Given
        timelineCases.stub { onBlocking { reblog(any(), any()) } doReturn NetworkResult.success(status) }

        viewModel.uiSuccess.test {
            // When
            viewModel.accept(reblogAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(StatusActionSuccess.Reblog::class.java)
            assertThat((item as StatusActionSuccess).action).isEqualTo(reblogAction)
        }

        // Then
        verify(timelineCases).reblog(id.capture(), state.capture())
        assertThat(id.firstValue).isEqualTo(statusViewData.status.id)
        assertThat(state.firstValue).isEqualTo(true)
    }

    @Test
    fun `reblog fails && emits UiError`() = runTest {
        // Given
        timelineCases.stub { onBlocking { reblog(any(), any()) } doThrow httpException }

        viewModel.uiError.test {
            // When
            viewModel.accept(reblogAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(UiError.Reblog::class.java)
            assertThat(item.action).isEqualTo(reblogAction)
        }
    }

    @Test
    fun `voteinpoll succeeds && emits UiSuccess`() = runTest {
        // Given
        timelineCases.stub {
            onBlocking { voteInPoll(any(), any(), any()) } doReturn NetworkResult.success(status.poll!!)
        }

        viewModel.uiSuccess.test {
            // When
            viewModel.accept(voteInPollAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(StatusActionSuccess.VoteInPoll::class.java)
            assertThat((item as StatusActionSuccess).action).isEqualTo(voteInPollAction)
        }

        // Then
        val pollId = argumentCaptor<String>()
        val choices = argumentCaptor<List<Int>>()
        verify(timelineCases).voteInPoll(id.capture(), pollId.capture(), choices.capture())
        assertThat(id.firstValue).isEqualTo(statusViewData.status.id)
        assertThat(pollId.firstValue).isEqualTo(status.poll!!.id)
        assertThat(choices.firstValue).isEqualTo(voteInPollAction.choices)
    }

    @Test
    fun `voteinpoll fails && emits UiError`() = runTest {
        // Given
        timelineCases.stub { onBlocking { voteInPoll(any(), any(), any()) } doThrow httpException }

        viewModel.uiError.test {
            // When
            viewModel.accept(voteInPollAction)

            // Then
            val item = awaitItem()
            assertThat(item).isInstanceOf(UiError.VoteInPoll::class.java)
            assertThat(item.action).isEqualTo(voteInPollAction)
        }
    }
}
