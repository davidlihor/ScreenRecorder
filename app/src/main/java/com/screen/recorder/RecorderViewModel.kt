package com.screen.recorder

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecorderViewModel(application: Application) : AndroidViewModel(application) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _stopRecordingEvent = MutableStateFlow(false)
    val stopRecordingEvent: StateFlow<Boolean> = _stopRecordingEvent.asStateFlow()
    private val _isTouchesEnabled = MutableStateFlow(false)
    val isTouchesEnabled: StateFlow<Boolean> = _isTouchesEnabled.asStateFlow()

    private val _selectedAudioOption = MutableStateFlow(AudioMode.NONE)
    val selectedAudioOption: StateFlow<AudioMode> = _selectedAudioOption.asStateFlow()

    private val _requestAudioPermissionEvent = MutableStateFlow(false)
    val requestAudioPermissionEvent: StateFlow<Boolean> = _requestAudioPermissionEvent.asStateFlow()
    private val _requestNotificationPermissionEvent = MutableStateFlow(false)
    val requestNotificationPermissionEvent: StateFlow<Boolean> = _requestNotificationPermissionEvent.asStateFlow()

    private val _requestScreenCaptureEvent = MutableStateFlow(false)
    val requestScreenCaptureEvent: StateFlow<Boolean> = _requestScreenCaptureEvent.asStateFlow()

    fun updateSelectedAudioOption(option: AudioMode) {
        _selectedAudioOption.value = option
    }

    fun updateIsTouchesEnabled(state: Boolean) {
        _isTouchesEnabled.value = state
    }

    fun onStartRecording() {
        _requestNotificationPermissionEvent.value = true
    }

    fun onAudioPermissionGranted() {
        _requestScreenCaptureEvent.value = true
    }

    fun onNotificationPermissionGranted() {

        if (_selectedAudioOption.value == AudioMode.MEDIA_MIC) {
            _requestAudioPermissionEvent.value = true
        } else {
            _requestScreenCaptureEvent.value = true
        }
    }

    fun onRecordingStarted(resultCode: Int, data: Intent?) {
        _isRecording.value = true
    }

    fun onStopRecording() {
        _isRecording.value = false
        _stopRecordingEvent.value = true
    }

    fun consumeStopRecordingEvent() {
        _stopRecordingEvent.value = false
    }

    fun consumeRequestNotificationPermissionEvent() {
        _requestNotificationPermissionEvent.value = false
    }
    fun consumeRequestAudioPermissionEvent() {
        _requestAudioPermissionEvent.value = false
    }

    fun consumeRequestScreenCaptureEvent() {
        _requestScreenCaptureEvent.value = false
    }
}