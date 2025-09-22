package com.screen.recorder

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.screen.recorder.ui.theme.ScreenRecorderTheme
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val viewModel: RecorderViewModel by viewModels()
    private lateinit var mpm: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {

            val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
                putExtra(ScreenRecordService.EXTRA_AUDIO_MODE, viewModel.selectedAudioOption.value.modeValue)
            }
            startForegroundService(serviceIntent)
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
        viewModel.onRecordingStarted(result.resultCode, result.data)
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.onAudioPermissionGranted()
        } else {
            Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.onNotificationPermissionGranted()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        super.onCreate(savedInstanceState)
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (ScreenRecordService.isRecording.value) {
            stopService(Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            })
            finish()
            return
        }

        setContent {
            ScreenRecorderTheme {
                val isRecording by viewModel.isRecording.collectAsState()
                var showRecordingDialog by remember { mutableStateOf(true) }

                LaunchedEffect(isRecording) {
                    if (isRecording) {
                        finish()
                    }
                }
                if (showRecordingDialog) {
                    Dialog(
                        onDismissRequest = {
                            showRecordingDialog = false
                            finish()
                       },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(bottom = 50.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(0.93f)
                                    .widthIn(max = 400.dp)
                                    .wrapContentHeight(),
                                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 20.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            ) {
                                RecorderContent(
                                    viewModel = viewModel,
                                    onStartClick = {
                                        viewModel.onStartRecording()
                                   },
                                    onCancelClick = {
                                        finish()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.stopRecordingEvent.collect { shouldStop ->
                if (shouldStop) {
                    stopScreenRecording()
                    viewModel.consumeStopRecordingEvent()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.requestAudioPermissionEvent.collect { shouldRequest ->
                if (shouldRequest) {
                    requestAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    viewModel.consumeRequestAudioPermissionEvent()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.requestNotificationPermissionEvent.collect { shouldRequest ->
                if (shouldRequest) {
                    requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    viewModel.consumeRequestNotificationPermissionEvent()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.requestScreenCaptureEvent.collect { shouldRequest ->
                if (shouldRequest) {
                    screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                    viewModel.consumeRequestScreenCaptureEvent()
                }
            }
        }
    }

    fun requestWriteSettingsPermissionIfNeeded() {
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = "package:$packageName".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun stopScreenRecording() {
        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        stopService(serviceIntent)
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
    }
}