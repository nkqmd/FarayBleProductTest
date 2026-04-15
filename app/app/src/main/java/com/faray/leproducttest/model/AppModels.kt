package com.faray.leproducttest.model

data class BatchProfile(
    val batchId: String,
    val expectedCount: Int,
    val expireTime: String,
    val expectedFirmware: String,
    val deviceType: String,
    val bleNamePrefix: String,
    val bleNameRule: String? = null,
    val bleConfig: BleTestPlan,
    val rawBleConfigJson: String,
    val macListCount: Int,
    val macListHash: String,
    val macListVersion: String,
    val macListUrl: String
)

data class BleTestPlan(
    val rssiMin: Int,
    val rssiMax: Int?,
    val scanIdleMs: Long,
    val scanActiveMs: Long,
    val maxConcurrent: Int,
    val connectTimeoutMs: Long,
    val notifyTimeoutMs: Long,
    val overallTimeoutMs: Long,
    val serviceUuid: String,
    val notifyCharacteristicUuid: String,
    val writeCharacteristicUuid: String,
    val writePayloadHex: String,
    val expectedNotifyValueHex: String?,
    val retryMax: Int = 0,
    val retryIntervalMs: Long = 0L
)

data class RuntimeDeviceItem(
    val parsedMac: Long,
    val deviceAddress: String,
    val advName: String,
    val rssi: Int,
    val lastSeenAt: Long,
    val sequenceNo: Long,
    val uiStatus: DeviceUiStatus,
    val warning: DeviceUiWarning? = null,
    val retainUntilAt: Long? = null,
    val passAt: Long? = null
)

data class ProductionSession(
    val sessionId: String,
    val batchId: String,
    val factoryId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus
)

data class ImportStats(
    val batchId: String,
    val importedCount: Int,
    val skippedCount: Int,
    val startedAt: Long,
    val endedAt: Long
)

data class ScanPayload(
    val deviceAddress: String,
    val advName: String,
    val rssi: Int,
    val seenAt: Long
)

data class TestTask(
    val sessionId: String,
    val batchId: String,
    val parsedMac: Long,
    val deviceAddress: String,
    val advName: String,
    val rssi: Int,
    val createdAt: Long
)

data class TestExecutionResult(
    val sessionId: String,
    val batchId: String,
    val parsedMac: Long,
    val deviceAddress: String,
    val advName: String,
    val rssi: Int,
    val finalStatus: DeviceUiStatus,
    val success: Boolean,
    val reason: String?,
    val failureReason: BleFailureReason? = null,
    val startedAt: Long,
    val endedAt: Long
)

data class QueueSnapshot(
    val queuedCount: Int,
    val runningCount: Int,
    val maxConcurrent: Int
)

data class SessionStatistics(
    val actualCount: Int,
    val successCount: Int,
    val failCount: Int,
    val invalidCount: Int,
    val successRate: Double
)

data class UploadStatistics(
    val expectedCount: Int,
    val actualCount: Int,
    val successCount: Int,
    val failCount: Int,
    val successRate: Double
)

data class BatchUploadStatistics(
    val expectedCount: Int,
    val actualCount: Int,
    val successCount: Int,
    val failCount: Int,
    val invalidCount: Int,
    val successRate: Double
)

data class UploadSuccessRecord(
    val sessionId: String? = null,
    val mac: String,
    val time: String
)

data class UploadFailRecord(
    val sessionId: String? = null,
    val mac: String,
    val result: String,
    val reason: String,
    val time: String
)

data class UploadInvalidRecord(
    val sessionId: String? = null,
    val mac: String,
    val time: String
)

data class UploadRecord(
    val sessionId: String,
    val batchId: String,
    val reportId: String,
    val reportDigest: String,
    val uploadId: String?,
    val duplicate: Boolean,
    val uploadedAt: Long,
    val message: String?
)

data class UploadProductionResultDigestSource(
    val batchId: String,
    val factoryId: String,
    val appVersion: String,
    val testStartTime: String,
    val testEndTime: String,
    val statistics: UploadStatistics,
    val successRecords: List<UploadSuccessRecord>,
    val failRecords: List<UploadFailRecord>,
    val invalid: List<UploadInvalidRecord>
)

data class UploadProductionResultRequest(
    val reportId: String,
    val reportDigest: String,
    val batchId: String,
    val factoryId: String,
    val appVersion: String,
    val testStartTime: String,
    val testEndTime: String,
    val statistics: UploadStatistics,
    val successRecords: List<UploadSuccessRecord>,
    val failRecords: List<UploadFailRecord>,
    val invalid: List<UploadInvalidRecord>
)

data class UploadProductionResultResponse(
    val uploadId: String?,
    val uploadTime: String?,
    val reportId: String,
    val reportDigest: String,
    val duplicate: Boolean,
    val message: String?
)

data class UploadIncludedSession(
    val sessionId: String,
    val testStartTime: String,
    val testEndTime: String
)

data class UploadBatchResultFilePayload(
    val batchId: String,
    val factoryId: String,
    val appVersion: String,
    val aggregateStartTime: String,
    val aggregateEndTime: String,
    val statistics: BatchUploadStatistics,
    val includedSessions: List<UploadIncludedSession>,
    val successRecords: List<UploadSuccessRecord>,
    val failRecords: List<UploadFailRecord>,
    val invalid: List<UploadInvalidRecord>
)

data class UploadBatchResultRequest(
    val batchReportId: String,
    val batchReportDigest: String,
    val batchId: String,
    val factoryId: String,
    val appVersion: String,
    val aggregateStartTime: String,
    val aggregateEndTime: String,
    val fileName: String,
    val fileJson: String,
    val fileBytes: ByteArray
)

data class UploadBatchResultResponse(
    val uploadId: String?,
    val uploadedAt: String?,
    val batchReportId: String,
    val batchReportDigest: String,
    val duplicate: Boolean,
    val message: String?
)

enum class SessionStatus {
    READY,
    RUNNING,
    STOPPING,
    STOPPED,
    UPLOADED
}

enum class DeviceUiStatus {
    DISCOVERED,
    INVALID_DEVICE,
    ALREADY_TESTED,
    QUEUED,
    CONNECTING,
    SUBSCRIBING,
    SENDING,
    WAITING_NOTIFY,
    DISCONNECTING,
    PASS,
    FAIL
}

enum class DeviceUiWarning {
    DUPLICATE_MAC_IN_PROGRESS
}

enum class BleFailureReason {
    CONNECT_FAILED,
    SUBSCRIBE_FAILED,
    WRITE_FAILED,
    SERVICE_NOT_FOUND,
    CHARACTERISTIC_NOT_FOUND,
    NOTIFY_TIMEOUT,
    NOTIFY_MISMATCH,
    STOPPED_BY_SESSION,
    UNKNOWN
}
