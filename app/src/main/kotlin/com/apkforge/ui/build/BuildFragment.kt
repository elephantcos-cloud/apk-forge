package com.apkforge.ui.build

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.apkforge.databinding.FragmentBuildBinding
import com.apkforge.model.BuildJob
import com.apkforge.viewmodel.BuildViewModel

/**
 * APK Forge — Build Fragment (Kotlin)
 * The main screen for triggering and monitoring builds
 */
class BuildFragment : Fragment() {

    private var _binding: FragmentBuildBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BuildViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentBuildBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        observeState()
        observeBranches()
    }

    private fun setupButtons() {
        // Load repo info when user finishes typing repo name
        binding.etRepo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) tryLoadRepo()
        }

        // Trigger build
        binding.btnBuild.setOnClickListener {
            val owner = binding.etOwner.text.toString().trim()
            val repo  = binding.etRepo.text.toString().trim()
            val branch = binding.spinnerBranch.selectedItem?.toString() ?: "main"
            val workflow = binding.etWorkflow.text.toString()
                .ifBlank { "build.yml" }

            if (owner.isEmpty() || repo.isEmpty()) {
                Toast.makeText(context, "Please enter owner and repo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val job = BuildJob(owner, repo, branch, workflow)
            viewModel.startBuild(job)
        }

        // Cancel build
        binding.btnCancel.setOnClickListener {
            val owner = binding.etOwner.text.toString().trim()
            val repo  = binding.etRepo.text.toString().trim()
            viewModel.cancelBuild(owner, repo)
        }
    }

    private fun tryLoadRepo() {
        val owner = binding.etOwner.text.toString().trim()
        val repo  = binding.etRepo.text.toString().trim()
        if (owner.isNotBlank() && repo.isNotBlank()) {
            viewModel.loadRepo(owner, repo)
        }
    }

    private fun observeBranches() {
        viewModel.branches.observe(viewLifecycleOwner) { branches ->
            if (branches.isNotEmpty()) {
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    branches
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                binding.spinnerBranch.adapter = adapter
            }
        }
    }

    private fun observeState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is BuildViewModel.UiState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnBuild.isEnabled = true
                    binding.btnCancel.visibility = View.GONE
                    binding.tvStatus.text = "Ready to build"
                }
                is BuildViewModel.UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnBuild.isEnabled = false
                    binding.tvStatus.text = "Loading…"
                }
                is BuildViewModel.UiState.BuildQueued -> {
                    binding.tvStatus.text = "⏳ Build queued (Run #${state.runId})"
                    binding.btnCancel.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.VISIBLE
                }
                is BuildViewModel.UiState.BuildRunning -> {
                    binding.tvStatus.text = state.statusText
                    binding.progressBar.progress = state.progress
                    binding.progressBar.visibility = View.VISIBLE
                }
                is BuildViewModel.UiState.BuildSuccess -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnBuild.isEnabled = true
                    binding.btnCancel.visibility = View.GONE
                    binding.tvStatus.text = "✅ Build complete! ${state.artifacts.size} artifact(s)"
                    showArtifacts(state.artifacts)
                }
                is BuildViewModel.UiState.BuildFailed -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnBuild.isEnabled = true
                    binding.btnCancel.visibility = View.GONE
                    binding.tvStatus.text = "❌ ${state.reason}"
                }
                is BuildViewModel.UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnBuild.isEnabled = true
                    binding.tvStatus.text = "⚠️ ${state.message}"
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        viewModel.repoInfo.observe(viewLifecycleOwner) { info ->
            info?.let {
                binding.tvRepoDesc.text = "${it.fullName} • ⭐${it.stars} • ${it.language ?: "Unknown"}"
                binding.tvRepoDesc.visibility = View.VISIBLE
            }
        }
    }

    private fun showArtifacts(artifacts: List<com.apkforge.core.BuildManager.ArtifactInfo>) {
        val text = artifacts.joinToString("\n") { "📦 ${it.name} (${it.getFormattedSize()})" }
        binding.tvArtifacts.text = text
        binding.tvArtifacts.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
