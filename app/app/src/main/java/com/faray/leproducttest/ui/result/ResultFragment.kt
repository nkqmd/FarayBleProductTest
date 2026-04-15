package com.faray.leproducttest.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.faray.leproducttest.R
import com.faray.leproducttest.databinding.FragmentResultBinding
import com.faray.leproducttest.ui.formatRate
import com.faray.leproducttest.ui.shared.SharedSessionViewModel
import com.faray.leproducttest.ui.shared.ShellUiState

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!
    private val viewModel by viewModels<ResultViewModel>()
    private val sharedSessionViewModel by activityViewModels<SharedSessionViewModel>()

    private var latestState = ResultUiState()
    private var latestShell = ShellUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonUploadResult.setOnClickListener { viewModel.uploadResult() }
        binding.buttonUploadBatchResult.setOnClickListener { viewModel.uploadBatchResult() }
        viewModel.restoreLatestResultState()
        viewModel.uiState.observe(viewLifecycleOwner) {
            latestState = it
            render()
        }
        sharedSessionViewModel.uiState.observe(viewLifecycleOwner) {
            latestShell = it
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreLatestResultState()
    }

    private fun render() {
        val hasSession = !latestState.sessionId.isNullOrBlank()
        val hasBatch = !latestState.batchId.isNullOrBlank() || !latestShell.batchId.isNullOrBlank()

        binding.textResultEmpty.isVisible = !hasSession
        binding.cardSessionSummary.isVisible = hasSession
        binding.cardResultStats.isVisible = hasSession
        binding.cardUploadFeedback.isVisible = hasSession
        binding.buttonUploadResult.isVisible = hasSession
        binding.cardBatchUploadFeedback.isVisible = hasBatch
        binding.buttonUploadBatchResult.isVisible = hasBatch

        if (!hasSession) {
            binding.textResultEmpty.text = getString(R.string.empty_no_result)
        }

        binding.textResultSessionId.text =
            "会话：${latestState.sessionId ?: getString(R.string.placeholder_dash)}"
        binding.textResultBatchId.text =
            "批次：${latestState.batchId ?: latestShell.batchId ?: getString(R.string.placeholder_dash)}"
        binding.textResultStatsTop.text =
            "${getString(R.string.label_expected_count)}：${latestState.expectedCount}  |  " +
                "${getString(R.string.label_actual_count)}：${latestState.actualCount}  |  " +
                "${getString(R.string.label_success_count)}：${latestState.successCount}"
        binding.textResultStatsBottom.text =
            "${getString(R.string.label_fail_count)}：${latestState.failCount}  |  " +
                "${getString(R.string.label_invalid_count)}：${latestState.invalidCount}  |  " +
                "${getString(R.string.label_success_rate)}：${formatRate(latestState.successRate)}"

        binding.buttonUploadResult.isEnabled = latestState.uploadEnabled && !latestState.uploading
        binding.buttonUploadResult.text =
            if (latestState.uploading) "上报中..." else getString(R.string.action_upload_result)

        binding.buttonUploadBatchResult.isEnabled =
            latestState.batchUploadEnabled && !latestState.batchUploading
        binding.buttonUploadBatchResult.text =
            if (latestState.batchUploading) "批次上报中..." else "批次累计结果上报"

        binding.textUploadStatus.text =
            "${getString(R.string.label_upload_status)}：${latestState.uploadStatus ?: getString(R.string.state_idle)}"
        binding.textUploadMessage.text =
            latestState.uploadMessage ?: getString(R.string.message_upload_ready)
        binding.textUploadId.text =
            "${getString(R.string.label_upload_id)}：${latestState.uploadId ?: getString(R.string.placeholder_dash)}"
        binding.textUploadDuplicate.text =
            "${getString(R.string.label_duplicate)}：" +
                if (latestState.duplicate) getString(R.string.value_boolean_true)
                else getString(R.string.value_boolean_false)
        binding.textUploadStatus.setTextColor(
            resolveUploadColor(
                uploading = latestState.uploading,
                duplicate = latestState.duplicate,
                uploadId = latestState.uploadId
            )
        )

        binding.textBatchUploadStatus.text =
            "${getString(R.string.label_upload_status)}：${latestState.batchUploadStatus ?: getString(R.string.state_idle)}"
        binding.textBatchUploadMessage.text =
            latestState.batchUploadMessage ?: "停止产测后可在此页上传当前批次累计结果"
        binding.textBatchUploadId.text =
            "${getString(R.string.label_upload_id)}：${latestState.batchUploadId ?: getString(R.string.placeholder_dash)}"
        binding.textBatchUploadDuplicate.text =
            "${getString(R.string.label_duplicate)}：" +
                if (latestState.batchUploadDuplicate) getString(R.string.value_boolean_true)
                else getString(R.string.value_boolean_false)
        binding.textBatchUploadStatus.setTextColor(
            resolveUploadColor(
                uploading = latestState.batchUploading,
                duplicate = latestState.batchUploadDuplicate,
                uploadId = latestState.batchUploadId
            )
        )
    }

    private fun resolveUploadColor(uploading: Boolean, duplicate: Boolean, uploadId: String?): Int {
        return ContextCompat.getColor(
            requireContext(),
            when {
                uploading -> R.color.state_running_fg
                duplicate -> R.color.state_warning_fg
                !uploadId.isNullOrBlank() -> R.color.state_success_fg
                else -> R.color.state_default_fg
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
