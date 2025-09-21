package com.screen.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp




@Composable
fun RecorderContent(
    selectedAudioOption: String,
    onAudioOptionSelected: (String) -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val audioOptions = listOf("None", "Media", "Media + Mic")

    Column(
        modifier = Modifier.padding(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Screen recorder will have access to the info on your screen or played from your phone while recording. This can include passwords, payment details, pictures, messages, and more.")

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Record sound", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Left)


        Spacer(modifier = Modifier.height(8.dp))

        audioOptions.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAudioOptionSelected(option) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selectedAudioOption == option),
                    onClick = { onAudioOptionSelected(option) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = option)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancelClick) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onStartClick) {
                Text("Start recording")
            }
        }
    }
}