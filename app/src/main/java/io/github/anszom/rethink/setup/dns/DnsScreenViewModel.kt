package io.github.anszom.rethink.setup.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DnsScreenViewModel @Inject constructor(
    private val routeChecker: RouteChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow("")
    val uiState: StateFlow<String> = _uiState

    fun checkDns() {
        viewModelScope.launch {
            var resultString = ""
            _uiState.value = resultString
            routeChecker.checkDns().collect { result ->
                result.apply {
                    resultString += result
                }
                _uiState.value = resultString
            }
        }
    }


}