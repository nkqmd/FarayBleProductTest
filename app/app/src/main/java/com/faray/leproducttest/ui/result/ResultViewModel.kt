package com.faray.leproducttest.ui.result

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faray.leproducttest.app.AppContainer
import com.faray.leproducttest.data.ProductionRepository
import kotlinx.coroutines.launch

class ResultViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.from(application.applicationContext)

    val uiState: LiveData<ResultUiState> = ProductionRepository.resultState

    init {
        restoreLatestResultState()
    }

    fun uploadResult() {
        ProductionRepository.uploadResult()
    }

    fun uploadBatchResult() {
        ProductionRepository.uploadBatchResult()
    }

    fun restoreLatestResultState() {
        ProductionRepository.bindContainer(container)
        viewModelScope.launch {
            val restored = container.restoreResultStateUseCase()
            ProductionRepository.adoptRestoredResultState(
                resultState = restored.resultState,
                shellState = restored.shellState
            )
        }
    }
}
