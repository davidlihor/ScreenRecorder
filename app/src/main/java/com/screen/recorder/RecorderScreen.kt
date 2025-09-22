package com.screen.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RecorderContent(
    viewModel: RecorderViewModel,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val selected by viewModel.selectedAudioOption.collectAsState()
    val isTouchesEnabled by viewModel.isTouchesEnabled.collectAsState()

    val audioOptions = listOf(
        AudioMode.NONE to "None",
        AudioMode.MEDIA to "Media",
        AudioMode.MEDIA_MIC to "Media and Mic"
    )

    Column(
        modifier = Modifier.padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Start recording with Screen recorder?",
            style = TextStyle(
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Left,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )



        Text(
            text = "Screen recorder will have access to the info on your screen or played from your phone while recording. This can include passwords, payment details, pictures, messages, and more.",
            Modifier.padding(top = 10.dp, bottom = 5.dp),
            style = TextStyle(
                fontSize = 15.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Record sound",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Left,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )



        Spacer(modifier = Modifier.height(8.dp))

        audioOptions.forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateSelectedAudioOption(mode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    modifier = Modifier.offset(x = (-15).dp),
                    selected = (selected == mode),
                    onClick = { viewModel.updateSelectedAudioOption(mode) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.secondary
                    )
                )
                Spacer(modifier = Modifier.width(0.dp))
                Text(text = label, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.7.dp)
                .background(Color.LightGray)
        )


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (-15).dp)
                .padding(vertical = 10.dp, horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Show taps and touches",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Left,
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Switch(
                checked = isTouchesEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateIsTouchesEnabled(enabled)

                },
                modifier = Modifier.scale(0.85f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = Color.Gray,
                    uncheckedBorderColor = Color.Transparent,
                ),
                thumbContent = {
                    Box(
                        modifier = Modifier
                            .size(if (isTouchesEnabled) 20.dp else 24.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            )
        }


        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancelClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Cancel",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }


            Box(
                modifier = Modifier
                    .width(0.7.dp)
                    .height(20.dp)
                    .background(Color.LightGray)
            )

            TextButton(
                onClick = onStartClick,
                modifier = Modifier.weight(1.5f)
            ) {
                Text(
                    text = "Start recording",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}