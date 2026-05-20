package com.petmanager.presentation.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.petmanager.databinding.ChatFragBinding
import com.petmanager.domain.model.ChatInfo
import com.petmanager.presentation.adapter.ChatAdapter
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.presentation.viewmodel.ChatListViewModel
import com.petmanager.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: ChatFragBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatListViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = arrayListOf<ChatInfo>()

    companion object {
        fun newInstance(): ChatFragment = ChatFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = ChatFragBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatAdapter = ChatAdapter(chatList)
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChat.setHasFixedSize(true)
        binding.rvChat.adapter = chatAdapter

        binding.emptyCtaBtn.setOnClickListener {
            (activity as? MainActivity)?.let { main ->
                main.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavi)
                    ?.selectedItemId = R.id.bottom_nav_home
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ChatListViewModel.ChatListUiState.Loading -> {
                            // 목록 로딩 중 — 기존 empty 유지
                        }
                        is ChatListViewModel.ChatListUiState.Success -> {
                            chatList.clear()
                            chatList.addAll(state.rooms)
                            chatAdapter.notifyDataSetChanged()
                            updateEmptyState(chatList)
                        }
                        is ChatListViewModel.ChatListUiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            updateEmptyState(chatList)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRooms()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateEmptyState(list: List<ChatInfo>) {
        if (list.isEmpty()) {
            binding.rvChat.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.rvChat.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }
}
