package com.helucryptic.android.ui.call

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor() : ViewModel() {
    var muted          by mutableStateOf(false)
    var videoEnabled   by mutableStateOf(true)
    var speakerOn      by mutableStateOf(true)
    var elapsedSeconds by mutableIntStateOf(0)
    private var timerJob: Job? = null

    fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) { delay(1_000); elapsedSeconds++ }
        }
    }

    fun toggleMute()    { muted        = !muted }
    fun toggleVideo()   { videoEnabled = !videoEnabled }
    fun toggleSpeaker() { speakerOn    = !speakerOn }

    override fun onCleared() { timerJob?.cancel() }
}
