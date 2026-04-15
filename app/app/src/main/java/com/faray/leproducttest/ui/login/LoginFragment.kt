package com.faray.leproducttest.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.faray.leproducttest.R
import com.faray.leproducttest.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel by viewModels<AuthViewModel>()
    private var latestState = AuthUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLogin.setOnClickListener { submitLogin() }
        binding.inputUsername.doAfterTextChanged { updateLoginButton() }
        binding.inputPassword.doAfterTextChanged { updateLoginButton() }
        binding.inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && binding.buttonLogin.isEnabled) {
                submitLogin()
                true
            } else {
                false
            }
        }
        viewModel.uiState.observe(viewLifecycleOwner) {
            latestState = it
            render(it)
        }
        updateLoginButton()
    }

    private fun submitLogin() {
        viewModel.login(
            username = binding.inputUsername.text?.toString().orEmpty(),
            password = binding.inputPassword.text?.toString().orEmpty()
        )
    }

    private fun render(state: AuthUiState) {
        binding.progressLogin.isVisible = state.authenticating
        binding.textLoginError.isVisible = !state.errorMessage.isNullOrBlank()
        binding.textLoginError.text = state.errorMessage
        binding.inputUsername.isEnabled = !state.authenticating
        binding.inputPassword.isEnabled = !state.authenticating
        updateLoginButton()
    }

    private fun updateLoginButton() {
        val enabled = !latestState.authenticating &&
            !binding.inputUsername.text.isNullOrBlank() &&
            !binding.inputPassword.text.isNullOrBlank()
        binding.buttonLogin.isEnabled = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
