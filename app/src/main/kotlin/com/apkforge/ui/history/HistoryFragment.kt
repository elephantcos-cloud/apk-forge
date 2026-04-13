package com.apkforge.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.apkforge.databinding.FragmentHistoryBinding

/**
 * APK Forge — History Fragment (Kotlin)
 * Shows list of past build records from Room database
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvEmpty.text = "No builds yet.\nGo to Build tab to start your first build!"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
