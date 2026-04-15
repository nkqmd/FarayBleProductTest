package com.faray.leproducttest.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.faray.leproducttest.R
import com.faray.leproducttest.databinding.FragmentConfigBinding
import com.faray.leproducttest.ui.shared.SharedSessionViewModel
import com.faray.leproducttest.ui.shared.ShellUiState

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel by viewModels<ConfigViewModel>()
    private val sharedSessionViewModel by activityViewModels<SharedSessionViewModel>()

    private var latestState = ConfigUiState()
    private var latestShell = ShellUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLoadBatch.setOnClickListener {
            viewModel.loadBatch(
                factoryId = binding.inputFactoryId.text?.toString().orEmpty(),
                batchId = binding.inputBatchId.text?.toString().orEmpty()
            )
        }
        binding.seekbarRssi.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                viewModel.updateRssi(progressToRssi(progress))
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.seekbarRssi.max = ConfigUiState.RSSI_RANGE_CEIL - ConfigUiState.RSSI_RANGE_FLOOR
        binding.seekbarRssi.progress = rssiToProgress(ConfigUiState.DEFAULT_RSSI)
        binding.inputFactoryId.doAfterTextChanged { updateButtonState() }
        binding.inputBatchId.doAfterTextChanged { updateButtonState() }

        viewModel.uiState.observe(viewLifecycleOwner) {
            latestState = it
            render()
        }
        sharedSessionViewModel.uiState.observe(viewLifecycleOwner) {
            latestShell = it
            render()
        }
    }

    private fun render() {
        updateIfChanged(binding.inputFactoryId, latestState.factoryId)
        updateIfChanged(binding.inputBatchId, latestState.batchId)

        binding.progressImport.isVisible = latestState.loading
        binding.progressImport.progress = latestState.importProgress
        binding.textSummaryStatus.text = latestState.summaryStatus
        binding.textMacStatus.text = latestState.macListStatus
        binding.textImportCount.text = labelValue(
            getString(R.string.label_import_count),
            latestState.importedCount.toString()
        )
        binding.textLastUpdated.text = labelValue(
            getString(R.string.label_last_updated),
            latestState.lastUpdatedAt ?: getString(R.string.placeholder_dash)
        )

        val batch = latestState.currentBatch
        binding.cardBatchSummary.isVisible = batch != null
        if (batch != null) {
            binding.textBatchIdValue.text = labelValue(getString(R.string.label_batch_id), batch.batchId)
            binding.textDeviceTypeValue.text = labelValue(getString(R.string.label_device_type), batch.deviceType)
            binding.textExpectedCountValue.text = labelValue(
                getString(R.string.label_expected_count),
                batch.expectedCount.toString()
            )
            binding.textFirmwareValue.text = labelValue(getString(R.string.label_firmware), batch.expectedFirmware)
            binding.textBlePrefixValue.text = labelValue(getString(R.string.label_ble_prefix), batch.bleNamePrefix)
            binding.textRssiRangeValue.text = formatRssiRange(batch.bleConfig.rssiMin)
            updateRssiSeekBarIfNeeded(batch.bleConfig.rssiMin)
        }

        binding.textBatchReady.isVisible = latestState.loading || latestState.canStartProduction
        binding.textBatchReady.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (latestState.loading) R.color.state_running_fg else R.color.state_success_fg
            )
        )
        binding.textBatchReady.text = if (latestState.loading) {
            "Loading batch data (${latestState.importProgress}%)"
        } else {
            "Batch is ready for production"
        }

        binding.textConfigError.isVisible = !latestState.errorMessage.isNullOrBlank()
        binding.textConfigError.text = latestState.errorMessage

        val editable = !latestState.loading && !latestShell.navigationLocked
        binding.inputFactoryId.isEnabled = editable
        binding.inputBatchId.isEnabled = editable
        binding.seekbarRssi.isEnabled = editable && batch != null
        updateButtonState()
    }

    private fun updateButtonState() {
        binding.buttonLoadBatch.isEnabled = !latestState.loading &&
            !latestShell.navigationLocked &&
            !binding.inputFactoryId.text.isNullOrBlank() &&
            !binding.inputBatchId.text.isNullOrBlank()
    }

    private fun updateIfChanged(editText: EditText, value: String) {
        if (value.isNotBlank() && editText.text?.toString() != value) {
            editText.setText(value)
            editText.setSelection(editText.text?.length ?: 0)
        }
    }

    private fun labelValue(label: String, value: String): String {
        return "$label: $value"
    }

    private fun formatRssiRange(rssi: Int): String {
        return "RSSI: ${formatRssiValue(0)} ~ ${formatRssiValue(rssi)}"
    }

    private fun formatRssiValue(value: Int): String {
        return getString(R.string.metric_rssi_format, value)
    }

    private fun updateRssiSeekBarIfNeeded(rssi: Int) {
        val expectedProgress = rssiToProgress(rssi)
        if (binding.seekbarRssi.progress != expectedProgress) {
            binding.seekbarRssi.progress = expectedProgress
        }
    }

    private fun rssiToProgress(rssi: Int): Int {
        return ConfigUiState.RSSI_RANGE_CEIL - ConfigUiState.normalizeRssi(rssi)
    }

    private fun progressToRssi(progress: Int): Int {
        return ConfigUiState.RSSI_RANGE_CEIL - progress
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
