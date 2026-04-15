package com.faray.leproducttest.ui.production

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.faray.leproducttest.data.ProductionRepository

class ProductionViewModel(application: Application) : AndroidViewModel(application) {
    val uiState: LiveData<ProductionUiState> = ProductionRepository.productionState

    fun startProduction() {
        ProductionRepository.startProduction(getApplication<Application>().applicationContext)
    }

    fun stopProduction() {
        ProductionRepository.stopProduction()
    }

    fun onBlePermissionDenied() {
        ProductionRepository.onPermissionDenied()
    }
}
