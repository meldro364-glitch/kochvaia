package com.kochvaia.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModePickerScreen(
    onPickParent: () -> Unit,
    onPickKid: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "⭐",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Kochvaia",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Stars for great days",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onPickParent,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Text("I'm a parent", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onPickKid,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Text("I'm a kid", style = MaterialTheme.typography.titleMedium)
        }
    }
}
