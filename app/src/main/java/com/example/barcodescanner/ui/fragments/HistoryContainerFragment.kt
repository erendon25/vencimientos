package com.example.barcodescanner.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.barcodescanner.databinding.FragmentHistoryContainerBinding

class HistoryContainerFragment : Fragment() {
    private var _binding: FragmentHistoryContainerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.adapter = HistoryPagerAdapter(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class HistoryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HistoryFragment()
                1 -> WithdrawalScheduleFragment()
                else -> throw IllegalStateException("Invalid position $position")
            }
        }
    }
}