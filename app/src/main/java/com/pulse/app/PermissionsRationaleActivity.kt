package com.pulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pulse.app.ui.theme.PulseTheme

/**
 * Activity shown by the system / Health Connect when the user asks to see why Pulse needs
 * health permissions. It explains which data is read and how Pulse uses it.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PermissionsRationaleScreen(
                        onDone = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PermissionsRationaleScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "Why Pulse needs Health Connect access",
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            text = "Pulse reads the following health data from Google Health Connect " +
                "to show you a simple, at-a-glance summary on your home-screen widget:",
            style = MaterialTheme.typography.bodyLarge,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RationaleBullet("Sleep duration and sleep sessions")
            RationaleBullet("Steps")
            RationaleBullet("Resting heart rate")
            RationaleBullet("Heart rate variability (HRV)")
            RationaleBullet("Hydration")
        }

        Text(
            text = "Pulse only reads this data. It does not write health data, " +
                "and it does not send your health information to any server or third party. " +
                "All data stays on your device and is used only to render the widget.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Text(
            text = "You can revoke access at any time in Health Connect settings.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = onDone,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun RationaleBullet(text: String) {
    Text(
        text = "\u2022 $text",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Preview(showBackground = true)
@Composable
fun PermissionsRationaleScreenPreview() {
    PulseTheme {
        PermissionsRationaleScreen(
            onDone = {},
        )
    }
}
