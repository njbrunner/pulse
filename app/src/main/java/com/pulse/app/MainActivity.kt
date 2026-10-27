package com.pulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.launch
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.health.permission.HealthConnectPermissionManager
import com.pulse.app.health.permission.HealthPermissionState
import com.pulse.app.health.permission.HealthPermissionViewModel
import com.pulse.app.health.permission.HealthPermissionViewModelFactory
import com.pulse.app.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    private val permissionManager by lazy {
        HealthConnectPermissionManager(applicationContext)
    }

    private val viewModel: HealthPermissionViewModel by viewModels {
        HealthPermissionViewModelFactory(permissionManager)
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<Unit>

    override fun onCreate(savedInstanceState: Bundle?) {
        permissionLauncher = registerForActivityResult(
            permissionManager.requestContract,
        ) { granted ->
            viewModel.onPermissionResult(granted)
        }

        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val state by viewModel.permissionState.collectAsStateWithLifecycle()

                    PermissionScreen(
                        state = state,
                        onRequestPermission = { permissionLauncher.launch() },
                        onRefresh = { viewModel.refreshPermissionState() },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PermissionScreen(
    state: HealthPermissionState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )

        val statusMessage = when (state) {
            is HealthPermissionState.Loading -> "Checking permissions..."
            is HealthPermissionState.Unavailable -> "Google Health Connect is not available on this device."
            is HealthPermissionState.Granted -> "All health permissions granted."
            is HealthPermissionState.Denied -> "Health permissions are required to use Pulse."
            is HealthPermissionState.PartiallyGranted -> {
                "Some permissions granted: ${state.granted.joinToString { it.name.lowercase() }}. " +
                    "Missing: ${state.denied.joinToString { it.name.lowercase() }}."
            }
        }

        Text(
            text = statusMessage,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (state !is HealthPermissionState.Loading && state !is HealthPermissionState.Granted) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text("Grant permissions")
            }
        }

        if (state !is HealthPermissionState.Loading) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Refresh")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionScreenPreview() {
    PulseTheme {
        PermissionScreen(
            state = HealthPermissionState.Denied,
            onRequestPermission = {},
            onRefresh = {},
        )
    }
}
