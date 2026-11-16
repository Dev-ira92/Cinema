package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.cinema.data.SubtitleKeyStore
import com.cinema.ui.theme.CinemaBackground
import com.cinema.ui.theme.CinemaText
import com.cinema.ui.theme.CinemaMuted

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var apiKey by remember {
        mutableStateOf("")
    }

    var hasSavedKey by remember {
        mutableStateOf(SubtitleKeyStore.hasKey(context))
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        TextButton(onClick = onBack) {
            Text("Back")
        }

        Text(
            text = "Settings",
            color = CinemaText,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Subtitle provider",
            color = CinemaText,
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Enter your SubDL API key to search and download subtitles.",
            color = CinemaMuted,
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                message = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("SubDL API key")
            },
            placeholder = {
                Text("Enter API key")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Button(
            onClick = {
                val cleanKey = apiKey.trim()

                if (cleanKey.isBlank()) {
                    message = "Please enter your API key."
                } else {
                    try {
                        SubtitleKeyStore.save(
                            context,
                            cleanKey
                        )

                        apiKey = ""
                        hasSavedKey = true
                        message = "API key saved securely."
                    } catch (e: Exception) {
                        message = e.message
                            ?: "Unable to save API key."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save API key")
        }

        if (hasSavedKey) {

            Text(
                text = "A SubDL API key is saved on this device.",
                color = CinemaMuted
            )

            TextButton(
                onClick = {
                    SubtitleKeyStore.clear(context)
                    hasSavedKey = false
                    apiKey = ""
                    message = "Saved API key removed."
                }
            ) {
                Text("Remove saved API key")
            }
        } else {
            Text(
                text = "No API key saved.",
                color = CinemaMuted
            )
        }

        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
