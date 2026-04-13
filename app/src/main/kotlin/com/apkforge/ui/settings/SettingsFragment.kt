package com.apkforge.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.apkforge.AppPreferences
import com.apkforge.databinding.FragmentSettingsBinding
import com.apkforge.viewmodel.BuildViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * APK Forge — Settings Fragment (Kotlin)
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BuildViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSavedValues()
        setupSaveButton()
        setupValidateButton()
    }

    private fun loadSavedValues() {
        lifecycleScope.launch {
            binding.etToken.setText(AppPreferences.githubToken.first())
            binding.etDefaultOwner.setText(AppPreferences.githubUser.first())
            binding.etDefaultRepo.setText(AppPreferences.defaultRepo.first())
            binding.etDefaultBranch.setText(AppPreferences.defaultBranch.first())
            binding.etWebhookUrl.setText(AppPreferences.webhookUrl.first())
            binding.switchAutoInstall.isChecked = AppPreferences.autoInstall.first()
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            lifecycleScope.launch {
                AppPreferences.setGithubToken(binding.etToken.text.toString().trim())
                AppPreferences.setDefaultRepo(binding.etDefaultRepo.text.toString().trim())
                AppPreferences.setDefaultBranch(binding.etDefaultBranch.text.toString().trim().ifBlank { "main" })
                AppPreferences.setWebhookUrl(binding.etWebhookUrl.text.toString().trim())
                AppPreferences.setAutoInstall(binding.switchAutoInstall.isChecked)
                AppPreferences.setSetupDone(true)
                Toast.makeText(context, "✅ Settings saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupValidateButton() {
        binding.btnValidateToken.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            viewModel.validateToken(token)
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is BuildViewModel.UiState.TokenValid ->
                    binding.tvTokenStatus.text = "✅ Valid — @${state.username}"
                is BuildViewModel.UiState.TokenInvalid ->
                    binding.tvTokenStatus.text = "❌ ${state.reason}"
                is BuildViewModel.UiState.TokenValidating ->
                    binding.tvTokenStatus.text = "🔄 Validating…"
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
