package com.calltranscriber.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallListViewModel @Inject constructor(private val callRepository: CallRepository) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    val calls: StateFlow<List<CallEntity>> = _searchQuery.debounce(300).flatMapLatest { q -> if (q.isBlank()) callRepository.getAllCalls() else callRepository.searchCalls(q) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { viewModelScope.launch { try { callRepository.syncFromCloud() } catch (_: Exception) {} } }
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { callRepository.syncFromCloud() } catch (_: Exception) {}
            _isRefreshing.value = false
        }
    }
}
