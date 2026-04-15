package com.faray.leproducttest.ui.shared

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.faray.leproducttest.app.AppContainer
import com.faray.leproducttest.data.ProductionRepository
import kotlinx.coroutines.launch

class SharedSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.from(application.applicationContext)

    val uiState: LiveData<ShellUiState> = ProductionRepository.shellState

    init {
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
