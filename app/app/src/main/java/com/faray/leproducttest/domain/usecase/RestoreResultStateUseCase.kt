package com.faray.leproducttest.domain.usecase

import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.repository.LocalConfigRepository
import com.faray.leproducttest.domain.repository.ResultUploadRepository
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.domain.repository.TestRecordRepository
import com.faray.leproducttest.model.SessionStatus
import com.faray.leproducttest.ui.result.ResultUiState
import com.faray.leproducttest.ui.shared.ShellUiState

class RestoreResultStateUseCase(
    private val localConfigRepository: LocalConfigRepository,
    private val batchRepository: BatchRepository,
    private val sessionRepository: SessionRepository,
    private val testRecordRepository: TestRecordRepository,
    private val resultUploadRepository: ResultUploadRepository
) {

    suspend operator fun invoke(): RestoredResultState {
        val interruptedSession = recoverInterruptedSessionIfNeeded()
        val batchId = interruptedSession?.batchId ?: localConfigRepository.getLastBatchId()?.trim().orEmpty()
        val factoryId = localConfigRepository.getFactoryId()?.trim().orEmpty()
        if (batchId.isBlank()) {
            return RestoredResultState(
                resultState = ResultUiState(
                    uploadStatus = "Pending upload",
                    uploadMessage = "Finish one production session before uploading the result"
                ),
                shellState = ShellUiState(
                    batchId = null,
                    factoryId = factoryId.ifBlank { null }
                )
            )
        }

        val batchProfile = batchRepository.getBatchProfile(batchId)
        val session = interruptedSession ?: sessionRepository.getLatestFinishedSession(batchId)
        val batchUploadRecord = resultUploadRepository.getLatestBatchUploadRecord(batchId)
        if (session == null) {
            return RestoredResultState(
                resultState = ResultUiState(
                    batchId = batchId,
                    expectedCount = batchProfile?.expectedCount ?: 0,
                    uploadStatus = "Pending upload",
                    uploadMessage = "Finish one production session before uploading the result",
                    batchUploadStatus = batchUploadRecord?.let {
                        if (it.duplicate) "Duplicate upload" else "Upload success"
                    } ?: "Pending upload",
                    batchUploadMessage = batchUploadRecord?.message
                        ?: "Finish one production session before uploading the batch result",
                    batchUploadId = batchUploadRecord?.uploadId,
                    batchUploadDuplicate = batchUploadRecord?.duplicate ?: false
                ),
                shellState = ShellUiState(
                    batchId = batchId,
                    factoryId = factoryId.ifBlank { null }
                )
            )
        }

        val statistics = testRecordRepository.getSessionStatistics(session.sessionId)
        val uploadRecord = resultUploadRepository.getLatestUploadRecord(session.sessionId)
        val uploadEnabled = session.status == SessionStatus.STOPPED || session.status == SessionStatus.UPLOADED
        val resultState = ResultUiState(
            sessionId = session.sessionId,
            batchId = session.batchId,
            expectedCount = batchProfile?.expectedCount ?: 0,
            actualCount = statistics.actualCount,
            successCount = statistics.successCount,
            failCount = statistics.failCount,
            invalidCount = statistics.invalidCount,
            successRate = statistics.successRate,
            uploadEnabled = uploadEnabled,
            uploading = false,
            uploadStatus = when {
                uploadRecord == null -> "Pending upload"
                uploadRecord.duplicate -> "Duplicate upload"
                else -> "Upload success"
            },
            uploadMessage = uploadRecord?.message ?: if (uploadEnabled) {
                if (interruptedSession?.sessionId == session.sessionId) {
                    "Recovered the interrupted session after app restart"
                } else {
                    "Upload is available for the latest stopped session"
                }
            } else {
                "Finish one production session before uploading the result"
            },
            uploadId = uploadRecord?.uploadId,
            duplicate = uploadRecord?.duplicate ?: false,
            batchUploadEnabled = uploadEnabled,
            batchUploading = false,
            batchUploadStatus = when {
                batchUploadRecord == null -> "Pending upload"
                batchUploadRecord.duplicate -> "Duplicate upload"
                else -> "Upload success"
            },
            batchUploadMessage = batchUploadRecord?.message ?: if (uploadEnabled) {
                "Upload is available for the current batch cumulative result"
            } else {
                "Finish one production session before uploading the batch result"
            },
            batchUploadId = batchUploadRecord?.uploadId,
            batchUploadDuplicate = batchUploadRecord?.duplicate ?: false
        )
        return RestoredResultState(
            resultState = resultState,
            shellState = ShellUiState(
                batchId = session.batchId,
                factoryId = session.factoryId.ifBlank { factoryId }.ifBlank { null },
                activeSessionId = session.sessionId
            )
        )
    }

    private suspend fun recoverInterruptedSessionIfNeeded() =
        sessionRepository.getRunningSession()?.let { runningSession ->
            val endedAt = System.currentTimeMillis()
            sessionRepository.markSessionStopped(
                sessionId = runningSession.sessionId,
                endedAt = endedAt
            )
            runningSession.copy(
                endedAt = endedAt,
                status = SessionStatus.STOPPED
            )
        }
}

data class RestoredResultState(
    val resultState: ResultUiState,
    val shellState: ShellUiState
)
