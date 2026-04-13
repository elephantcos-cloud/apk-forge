package com.apkforge.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.apkforge.R
import com.apkforge.databinding.FragmentDashboardBinding
import com.apkforge.viewmodel.BuildViewModel

/**
 * APK Forge — Dashboard Fragment (Kotlin)
 * Shows quick stats, recent builds, and shortcut actions
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BuildViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Quick build button
        binding.btnQuickBuild.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_build)
        }

        // View history button
        binding.btnViewHistory.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_history)
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }

        // Observe state for last build status card
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is BuildViewModel.UiState.BuildSuccess ->
                    binding.tvLastBuildStatus.text = "✅ Last build succeeded"
                is BuildViewModel.UiState.BuildFailed ->
                    binding.tvLastBuildStatus.text = "❌ Last build failed: ${state.reason}"
                is BuildViewModel.UiState.BuildRunning ->
                    binding.tvLastBuildStatus.text = "🔨 Build in progress… ${state.progress}%"
                else -> binding.tvLastBuildStatus.text = "No recent builds"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
