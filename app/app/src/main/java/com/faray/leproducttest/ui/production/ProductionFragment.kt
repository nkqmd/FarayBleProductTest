package com.faray.leproducttest.ui.production

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.faray.leproducttest.R
import com.faray.leproducttest.data.ProductionRepository
import com.faray.leproducttest.databinding.FragmentProductionBinding
import com.faray.leproducttest.model.SessionStatus
import com.faray.leproducttest.ui.shared.SharedSessionViewModel
import com.faray.leproducttest.ui.shared.ShellUiState
import java.util.Locale

class ProductionFragment : Fragment() {

    private var _binding: FragmentProductionBinding? = null
    private val binding get() = _binding!!
    private val viewModel by viewModels<ProductionViewModel>()
    private val sharedSessionViewModel by activityViewModels<SharedSessionViewModel>()
    private val adapter = DeviceAdapter()

    private var latestState = ProductionUiState()
    private var latestShell = ShellUiState()
    private var shouldNavigateToResultAfterStop = false

    private val blePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            val allGranted = ProductionRepository.requiredPermissions.all { permission ->
                grantResults[permission] == true || hasPermission(permission)
            }
            if (allGranted) {
                viewModel.startProduction()
            } else {
                viewModel.onBlePermissionDenied()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDevices.adapter = adapter

        binding.buttonStartProduction.setOnClickListener {
            shouldNavigateToResultAfterStop = false
            startProductionWithPermissions()
        }
        binding.buttonStopProduction.setOnClickListener {
            shouldNavigateToResultAfterStop = true
            viewModel.stopProduction()
        }

        viewModel.uiState.observe(viewLifecycleOwner) {
            val previousStatus = latestState.sessionStatus
            latestState = it
            render()
            navigateToResultIfNeeded(previousStatus = previousStatus, currentState = it)
        }
        sharedSessionViewModel.uiState.observe(viewLifecycleOwner) {
            latestShell = it
            render()
        }
    }

    private fun render() {
        val successRate = String.format(Locale.US, "%.2f%%", latestState.successRate)
        binding.textStatsTop.text =
            "${getString(R.string.label_total_count)}: ${latestState.totalCount}  |  ${getString(R.string.label_actual_count)}: ${latestState.actualCount}  |  ${getString(R.string.label_success_count)}: ${latestState.successCount}"
        binding.textStatsMiddle.text =
            "${getString(R.string.label_fail_count)}: ${latestState.failCount}  |  ${getString(R.string.label_invalid_count)}: ${latestState.invalidCount}  |  ${getString(R.string.label_success_rate)}: $successRate"

        adapter.submitList(latestState.devices)
        binding.textProductionEmpty.text = emptyMessage()
        binding.textProductionEmpty.visibility = if (latestState.devices.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerDevices.visibility = if (latestState.devices.isEmpty()) View.GONE else View.VISIBLE

        binding.buttonStartProduction.isEnabled =
            latestShell.canStartProduction &&
                !latestShell.navigationLocked &&
                latestState.sessionStatus != SessionStatus.RUNNING &&
                latestState.sessionStatus != SessionStatus.STOPPING
        binding.buttonStopProduction.isEnabled = latestState.sessionStatus == SessionStatus.RUNNING
    }

    private fun startProductionWithPermissions() {
        if (ProductionRepository.requiredPermissions.all(::hasPermission)) {
            viewModel.startProduction()
            return
        }
        blePermissionLauncher.launch(ProductionRepository.requiredPermissions)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun emptyMessage(): String {
        val errorMessage = latestState.errorMessage
        if (!errorMessage.isNullOrBlank()) {
            return errorMessage
        }
        if (!latestShell.canStartProduction) {
            return getString(R.string.empty_need_config)
        }
        if (latestState.sessionStatus == SessionStatus.RUNNING) {
            val prefix = latestState.filterPrefix
            return if (prefix.isNullOrBlank()) {
                "Scanning BLE devices..."
            } else {
                "Scanning BLE devices with prefix \"$prefix\"..."
            }
        }
        return getString(R.string.empty_no_devices)
    }

    private fun navigateToResultIfNeeded(
        previousStatus: SessionStatus?,
        currentState: ProductionUiState
    ) {
        if (!shouldNavigateToResultAfterStop) {
            return
        }
        if (currentState.sessionStatus != SessionStatus.STOPPED) {
            return
        }
        if (previousStatus != SessionStatus.RUNNING && previousStatus != SessionStatus.STOPPING) {
            return
        }

        shouldNavigateToResultAfterStop = false
        if (!hasTestedStatistics(currentState)) {
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.productionFragment) {
            navController.navigate(R.id.resultFragment)
        }
    }

    private fun hasTestedStatistics(state: ProductionUiState): Boolean {
        return state.actualCount > 0 || state.invalidCount > 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
