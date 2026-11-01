package com.meshlink.app.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.EmergencyGuide
import com.meshlink.app.domain.repository.EmergencyGuideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class EmergencyGuideViewModel @Inject constructor(
    private val repository: EmergencyGuideRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val guides: StateFlow<List<EmergencyGuide>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query -> repository.searchGuides(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
