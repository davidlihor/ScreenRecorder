package com.screen.recorder


import android.R
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Surface
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode.Companion.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.screen.recorder.ui.theme.ScreenRecorderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: RecorderViewModel by viewModels()
    private lateinit var mpm: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val audioModeValue = when (viewModel.selectedAudioOption.value) {
                "None" -> AudioMode.NONE
                "Media" -> AudioMode.MEDIA
                "Media + Mic" -> AudioMode.MEDIA_MIC
                else -> AudioMode.NONE
            }

            val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
                putExtra(ScreenRecordService.EXTRA_AUDIO_MODE, audioModeValue.modeValue)
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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawableResource(R.color.transparent)
        window.setDimAmount(0f)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        super.onCreate(savedInstanceState)
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            ScreenRecorderTheme {
                val isRecording by viewModel.isRecording.collectAsState()
                val selectedAudioOption by viewModel.selectedAudioOption.collectAsState()


                var showRecordingDialog by remember { mutableStateOf(true) }

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)


                    Dialog(
                        onDismissRequest = { showRecordingDialog = false }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),

                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                color = androidx.compose.ui.graphics.Color.Red,
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            ) {
                                RecorderContent(
                                    selectedAudioOption = viewModel.selectedAudioOption.collectAsState().value,
                                    onAudioOptionSelected = { viewModel.updateSelectedAudioOption(it) },
                                    onStartClick = {
                                        viewModel.onStartRecording()
                                        showRecordingDialog = false
                                    },
                                    onCancelClick = {
                                        showRecordingDialog = false
                                    }
                                )
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
            viewModel.requestScreenCaptureEvent.collect { shouldRequest ->
                if (shouldRequest) {
                    screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                    viewModel.consumeRequestScreenCaptureEvent()
                }
            }
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