package com.pulse.app.debug

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectRecordPagerTest {

    @Test
    fun emptyResult_returnsEmptyRecordsAndOnePage() = runTest {
        val result = fetchAllPages<String> { Page(records = emptyList(), nextPageToken = null) }

        assertTrue(result.records.isEmpty())
        assertEquals(1, result.pagesRetrieved)
    }

    @Test
    fun singlePage_terminatesWhenNextTokenIsNull() = runTest {
        val result = fetchAllPages { Page(records = listOf(1, 2, 3), nextPageToken = null) }

        assertEquals(listOf(1, 2, 3), result.records)
        assertEquals(1, result.pagesRetrieved)
    }

    @Test
    fun multiplePages_accumulatesAllRecordsAndCountsPages() = runTest {
        val result = fetchAllPages { token ->
            when (token) {
                null -> Page(records = listOf("a", "b"), nextPageToken = "token1")
                "token1" -> Page(records = listOf("c"), nextPageToken = "token2")
                "token2" -> Page(records = listOf("d", "e"), nextPageToken = null)
                else -> error("Unexpected page token: $token")
            }
        }

        assertEquals(listOf("a", "b", "c", "d", "e"), result.records)
        assertEquals(3, result.pagesRetrieved)
    }

    @Test
    fun pagesWithEmptyRecords_stillFollowTokens() = runTest {
        var calls = 0
        val result = fetchAllPages { token ->
            calls++
            when (token) {
                null -> Page(records = emptyList(), nextPageToken = "next")
                "next" -> Page(records = listOf(42), nextPageToken = null)
                else -> error("Unexpected page token: $token")
            }
        }

        assertEquals(listOf(42), result.records)
        assertEquals(2, result.pagesRetrieved)
        assertEquals(2, calls)
    }
}
