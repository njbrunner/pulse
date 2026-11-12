package com.pulse.app.health.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass

/**
 * Test double for [HealthConnectClient] that supports the aggregate APIs used
 * by Pulse Steps tests.
 *
 * All other methods throw [NotImplementedError].
 */
class FakeHealthConnectClient : HealthConnectClient {

    var aggregateResponse: AggregationResult = AggregationResult(
        longValues = emptyMap(),
        doubleValues = emptyMap(),
        dataOrigins = emptySet(),
    )
    var aggregateGroupByPeriodResponse: List<AggregationResultGroupedByPeriod> = emptyList()
    var throwSecurityException: Boolean = false
    var throwException: Exception? = null

    override val permissionController: PermissionController
        get() = throw NotImplementedError()

    override suspend fun aggregate(request: AggregateRequest): AggregationResult {
        if (throwSecurityException) throw SecurityException()
        throwException?.let { throw it }
        return aggregateResponse
    }

    override suspend fun aggregateGroupByPeriod(
        request: AggregateGroupByPeriodRequest,
    ): List<AggregationResultGroupedByPeriod> {
        if (throwSecurityException) throw SecurityException()
        throwException?.let { throw it }
        return aggregateGroupByPeriodResponse
    }

    override suspend fun insertRecords(records: List<Record>) =
        throw NotImplementedError()

    override suspend fun updateRecords(records: List<Record>) =
        throw NotImplementedError()

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        recordIdsList: List<String>,
        clientRecordIdsList: List<String>,
    ) = Unit

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        timeRangeFilter: TimeRangeFilter,
    ) = Unit

    override suspend fun <T : Record> readRecord(
        recordType: KClass<T>,
        recordId: String,
    ): ReadRecordResponse<T> = throw NotImplementedError()

    override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> =
        throw NotImplementedError()

    override suspend fun aggregateGroupByDuration(
        request: AggregateGroupByDurationRequest,
    ): List<androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration> =
        throw NotImplementedError()

    override suspend fun getChangesToken(request: ChangesTokenRequest): String =
        throw NotImplementedError()

    override suspend fun getChanges(token: String): ChangesResponse =
        throw NotImplementedError()
}
