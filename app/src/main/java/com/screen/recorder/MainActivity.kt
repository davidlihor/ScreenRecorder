package com.screen.recorder

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screen.recorder.ui.theme.ScreenRecorderTheme

class MainActivity : ComponentActivity() {
    private lateinit var mpm: MediaProjectionManager
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            startForegroundService(serviceIntent)
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
        } else {
            Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            ScreenRecorderTheme {
                RecorderScreen(
                    onStartClick = {
                        requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    onStopClick = {
                        stopScreenRecording()
                    }
                )
            }
        }
    }

    private fun stopScreenRecording() {
        stopService(Intent(this, ScreenRecordService::class.java))
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RecorderScreen(onStartClick: () -> Unit, onStopClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onStartClick) {
            Text("Start Recording")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStopClick) {
            Text("Stop Recording")
        }
    }
}