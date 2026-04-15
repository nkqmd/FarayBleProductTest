package com.faray.leproducttest.app

import android.content.Context
import com.faray.leproducttest.ble.parser.AdvertisementParser
import com.faray.leproducttest.ble.execution.NordicBleTestExecutor
import com.faray.leproducttest.ble.scan.BleScannerEngine
import com.faray.leproducttest.ble.scan.NordicBleScannerEngine
import com.faray.leproducttest.ble.scan.ScanScheduler
import com.faray.leproducttest.ble.scan.ScanSchedulerImpl
import com.faray.leproducttest.data.auth.AuthTokenStore
import com.faray.leproducttest.data.batch.BatchFileStore
import com.faray.leproducttest.data.local.AppDatabase
import com.faray.leproducttest.data.repository.AuthRepositoryImpl
import com.faray.leproducttest.data.repository.BatchRepositoryImpl
import com.faray.leproducttest.data.repository.LocalConfigRepositoryImpl
import com.faray.leproducttest.data.repository.ResultUploadRepositoryImpl
import com.faray.leproducttest.data.repository.SessionRepositoryImpl
import com.faray.leproducttest.data.repository.TestRecordRepositoryImpl
import com.faray.leproducttest.data.repository.WhitelistMatcherImpl
import com.faray.leproducttest.data.runtime.ProductionSessionCoordinatorImpl
import com.faray.leproducttest.data.runtime.ProductionStateMachineImpl
import com.faray.leproducttest.data.runtime.RuntimeDeviceStoreImpl
import com.faray.leproducttest.data.runtime.TestQueueDispatcherImpl
import com.faray.leproducttest.domain.service.ProductionSessionCoordinator
import com.faray.leproducttest.domain.service.ProductionStateMachine
import com.faray.leproducttest.domain.repository.AuthRepository
import com.faray.leproducttest.domain.repository.BatchRepository
import com.faray.leproducttest.domain.repository.LocalConfigRepository
import com.faray.leproducttest.domain.repository.ResultUploadRepository
import com.faray.leproducttest.domain.repository.SessionRepository
import com.faray.leproducttest.domain.repository.TestRecordRepository
import com.faray.leproducttest.domain.service.BleTestExecutor
import com.faray.leproducttest.domain.service.RuntimeDeviceStore
import com.faray.leproducttest.domain.service.TestQueueDispatcher
import com.faray.leproducttest.domain.service.WhitelistMatcher
import com.faray.leproducttest.domain.usecase.BuildSessionReportUseCase
import com.faray.leproducttest.domain.usecase.BuildBatchReportUseCase
import com.faray.leproducttest.domain.usecase.DownloadAndImportMacListUseCase
import com.faray.leproducttest.domain.usecase.LoadBatchSummaryUseCase
import com.faray.leproducttest.domain.usecase.LoginUseCase
import com.faray.leproducttest.domain.usecase.RestoreResultStateUseCase
import com.faray.leproducttest.domain.usecase.RestoreConfigStateUseCase
import com.faray.leproducttest.domain.usecase.UploadBatchReportUseCase
import com.faray.leproducttest.domain.usecase.UploadSessionReportUseCase
import com.faray.leproducttest.domain.usecase.ValidateProductionStartUseCase

class AppContainer private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val authTokenStore = AuthTokenStore(appContext)
    private val batchFileStore = BatchFileStore(appContext)

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(tokenStore = authTokenStore)
    }

    val localConfigRepository: LocalConfigRepository by lazy {
        LocalConfigRepositoryImpl(localConfigDao = database.localConfigDao())
    }

    val whitelistMatcher: WhitelistMatcher by lazy {
        WhitelistMatcherImpl(batchMacDao = database.batchMacDao())
    }

    val batchRepository: BatchRepository by lazy {
        BatchRepositoryImpl(
            database = database,
            batchProfileDao = database.batchProfileDao(),
            batchMacDao = database.batchMacDao(),
            batchFileStore = batchFileStore,
            whitelistMatcher = whitelistMatcher
        )
    }

    val runtimeDeviceStore: RuntimeDeviceStore by lazy {
        RuntimeDeviceStoreImpl()
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(productionSessionDao = database.productionSessionDao())
    }

    val testRecordRepository: TestRecordRepository by lazy {
        TestRecordRepositoryImpl(testRecordDao = database.testRecordDao())
    }

    val resultUploadRepository: ResultUploadRepository by lazy {
        ResultUploadRepositoryImpl(uploadRecordDao = database.uploadRecordDao())
    }

    val advertisementParser: AdvertisementParser by lazy {
        AdvertisementParser()
    }

    val bleTestExecutor: BleTestExecutor by lazy {
        NordicBleTestExecutor(appContext)
    }

    val bleScannerEngine: BleScannerEngine by lazy {
        NordicBleScannerEngine()
    }

    val scanScheduler: ScanScheduler by lazy {
        ScanSchedulerImpl(scannerEngine = bleScannerEngine)
    }

    val testQueueDispatcher: TestQueueDispatcher by lazy {
        TestQueueDispatcherImpl(
            bleTestExecutor = bleTestExecutor
        )
    }

    val loginUseCase: LoginUseCase by lazy {
        LoginUseCase(authRepository = authRepository)
    }

    val loadBatchSummaryUseCase: LoadBatchSummaryUseCase by lazy {
        LoadBatchSummaryUseCase(batchRepository = batchRepository)
    }

    val downloadAndImportMacListUseCase: DownloadAndImportMacListUseCase by lazy {
        DownloadAndImportMacListUseCase(batchRepository = batchRepository)
    }

    val validateProductionStartUseCase: ValidateProductionStartUseCase by lazy {
        ValidateProductionStartUseCase()
    }

    val restoreConfigStateUseCase: RestoreConfigStateUseCase by lazy {
        RestoreConfigStateUseCase(
            localConfigRepository = localConfigRepository,
            batchRepository = batchRepository
        )
    }

    val restoreResultStateUseCase: RestoreResultStateUseCase by lazy {
        RestoreResultStateUseCase(
            localConfigRepository = localConfigRepository,
            batchRepository = batchRepository,
            sessionRepository = sessionRepository,
            testRecordRepository = testRecordRepository,
            resultUploadRepository = resultUploadRepository
        )
    }

    val productionStateMachine: ProductionStateMachine by lazy {
        ProductionStateMachineImpl(
            runtimeDeviceStore = runtimeDeviceStore,
            batchRepository = batchRepository,
            advertisementParser = advertisementParser,
            testRecordRepository = testRecordRepository,
            testQueueDispatcher = testQueueDispatcher
        )
    }

    val productionSessionCoordinator: ProductionSessionCoordinator by lazy {
        ProductionSessionCoordinatorImpl(
            scanScheduler = scanScheduler,
            testQueueDispatcher = testQueueDispatcher,
            productionStateMachine = productionStateMachine,
            runtimeDeviceStore = runtimeDeviceStore,
            sessionRepository = sessionRepository
        )
    }

    val buildSessionReportUseCase: BuildSessionReportUseCase by lazy {
        BuildSessionReportUseCase(
            sessionRepository = sessionRepository,
            batchRepository = batchRepository,
            testRecordRepository = testRecordRepository
        )
    }

    val buildBatchReportUseCase: BuildBatchReportUseCase by lazy {
        BuildBatchReportUseCase(
            sessionRepository = sessionRepository,
            batchRepository = batchRepository,
            testRecordRepository = testRecordRepository
        )
    }

    val uploadSessionReportUseCase: UploadSessionReportUseCase by lazy {
        UploadSessionReportUseCase(
            authRepository = authRepository,
            resultUploadRepository = resultUploadRepository,
            sessionRepository = sessionRepository
        )
    }

    val uploadBatchReportUseCase: UploadBatchReportUseCase by lazy {
        UploadBatchReportUseCase(
            authRepository = authRepository,
            resultUploadRepository = resultUploadRepository
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun from(context: Context): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context).also { INSTANCE = it }
            }
        }
    }
}
