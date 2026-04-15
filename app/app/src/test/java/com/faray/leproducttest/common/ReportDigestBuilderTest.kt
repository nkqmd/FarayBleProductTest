package com.faray.leproducttest.common

import com.faray.leproducttest.model.UploadFailRecord
import com.faray.leproducttest.model.UploadInvalidRecord
import com.faray.leproducttest.model.UploadProductionResultDigestSource
import com.faray.leproducttest.model.UploadStatistics
import com.faray.leproducttest.model.UploadSuccessRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDigestBuilderTest {

    @Test
    fun buildReportDigest_isStableAndBuildsBatchPrefixedReportId() {
        val source = UploadProductionResultDigestSource(
            batchId = "BATCH20260413001",
            factoryId = "FACTORY001",
            appVersion = "1.0.0",
            testStartTime = "2026-04-13T10:00:00+08:00",
            testEndTime = "2026-04-13T10:05:00+08:00",
            statistics = UploadStatistics(
                expectedCount = 10,
                actualCount = 2,
                successCount = 1,
                failCount = 1,
                successRate = 50.0
            ),
            successRecords = listOf(UploadSuccessRecord(mac = "01020304050A", time = "2026-04-13T10:01:00+08:00")),
            failRecords = listOf(
                UploadFailRecord(
                    mac = "01020304050B",
                    result = "FAIL",
                    reason = "Connect Timeout",
                    time = "2026-04-13T10:02:00+08:00"
                )
            ),
            invalid = listOf(UploadInvalidRecord(mac = "FFFFFFFFFFFF", time = "2026-04-13T10:03:00+08:00"))
        )

        val digest1 = ReportDigestBuilder.buildReportDigest(source)
        val digest2 = ReportDigestBuilder.buildReportDigest(source)
        val reportId = ReportDigestBuilder.buildReportId(source.batchId, digest1)

        assertEquals(digest1, digest2)
        assertEquals(64, digest1.length)
        assertTrue(digest1.all { it.isDigit() || it in 'a'..'f' })
        assertEquals("BATCH20260413001_${digest1.take(16)}", reportId)
    }
}
