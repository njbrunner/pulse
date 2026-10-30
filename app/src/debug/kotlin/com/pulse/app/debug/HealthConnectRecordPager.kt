package com.pulse.app.debug

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass

/**
 * Result of a complete paginated read through Health Connect.
 *
 * @param records all records collected across every page
 * @param pagesRetrieved number of Health Connect pages read (always at least one)
 */
internal data class PagedReadResult<T>(
    val records: List<T>,
    val pagesRetrieved: Int,
)

/**
 * A single page of records returned by a paginated reader.
 *
 * @param records records on this page
 * @param nextPageToken token for the next page, or `null` when this is the final page
 */
internal data class Page<T>(
    val records: List<T>,
    val nextPageToken: String?,
)

/**
 * Follows Health Connect page tokens until no next page remains.
 *
 * This is the testable core of the pagination logic. It knows nothing about
 * Health Connect types; callers provide a [readPage] function that performs
 * one page fetch and returns the records plus the next token.
 */
internal suspend fun <T> fetchAllPages(
    readPage: suspend (pageToken: String?) -> Page<T>,
): PagedReadResult<T> {
    val allRecords = mutableListOf<T>()
    var pagesRetrieved = 0
    var pageToken: String? = null
    do {
        val page = readPage(pageToken)
        allRecords.addAll(page.records)
        pageToken = page.nextPageToken
        pagesRetrieved++
    } while (pageToken != null)

    return PagedReadResult(records = allRecords, pagesRetrieved = pagesRetrieved)
}

/**
 * Reads every [Record] of type [recordType] that falls within [timeRangeFilter]
 * by following Health Connect's page-token chain.
 *
 * Health Connect caps each [HealthConnectClient.readRecords] response at
 * [pageSize] record objects (default 1,000). A single call therefore cannot be
 * assumed to contain the full requested range.
 */
internal suspend fun <T : Record> HealthConnectClient.readAllRecords(
    recordType: KClass<T>,
    timeRangeFilter: TimeRangeFilter,
    pageSize: Int = 1000,
): PagedReadResult<T> = fetchAllPages { token ->
    val request = ReadRecordsRequest(
        recordType = recordType,
        timeRangeFilter = timeRangeFilter,
        pageSize = pageSize,
        pageToken = token,
    )
    val response = readRecords(request)
    Page(records = response.records, nextPageToken = response.pageToken)
}
