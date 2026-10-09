package kittoku.osc.ui

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kittoku.osc.R
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onConnect: () -> String?,
    onDisconnect: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenLegacySettings: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onManageProfiles: () -> Unit,
    onCreateProfile: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onConnect()?.also { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }

    val connect: () -> Unit = {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            onConnect()?.also { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_legacy_settings)) },
                            onClick = {
                                menuExpanded = false
                                onOpenLegacySettings()
                            },
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileCard(
                activeName = uiState.profileName,
                names = uiState.profileNames,
                enabled = uiState.state == ConnectionState.DISCONNECTED || uiState.state == ConnectionState.ERROR,
                onSelect = onSelectProfile,
                onManage = onManageProfiles,
                onCreate = onCreateProfile,
            )

            Spacer(Modifier.height(32.dp))

            StatusCircle(uiState.state)

            Spacer(Modifier.height(16.dp))

            when (uiState.state) {
                ConnectionState.CONNECTED -> ConnectedCaption(uiState)
                ConnectionState.ERROR -> ErrorCard(uiState.errorCode, onOpenLog)
                else -> {}
            }

            if (uiState.state == ConnectionState.CONNECTED && uiState.status != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                DetailsTable(uiState.status)
            }

            Spacer(Modifier.weight(1f))

            if (uiState.profileNames.isEmpty()) {
                Button(onClick = onCreateProfile, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.profile_create))
                }
            } else {
                MainButton(uiState.state, connect, onDisconnect)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onOpenDetails, enabled = uiState.status != null) {
                    Text(stringResource(R.string.link_details))
                }
                TextButton(onClick = onOpenLog) {
                    Text(stringResource(R.string.link_log))
                }
                TextButton(onClick = onManageProfiles) {
                    Text(stringResource(R.string.link_profiles))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCard(
    activeName: String,
    names: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasProfiles = names.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open ->
            when {
                !enabled -> expanded = false
                hasProfiles -> expanded = open
                else -> onCreate()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasProfiles) activeName else stringResource(R.string.profile_create),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                when {
                    !enabled -> Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    hasProfiles -> ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    else -> Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            names.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = {
                        if (name == activeName) Icon(Icons.Outlined.Check, contentDescription = null)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        expanded = false
                        onSelect(name)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profiles_manage), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Spacer(Modifier.size(24.dp)) },
                onClick = {
                    expanded = false
                    onManage()
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
        }
    }
}

@Composable
private fun StatusCircle(state: ConnectionState) {
    val scheme = MaterialTheme.colorScheme
    val background: Color
    val foreground: Color
    val icon: ImageVector
    val label: Int

    when (state) {
        ConnectionState.DISCONNECTED -> {
            background = scheme.surfaceVariant
            foreground = scheme.onSurfaceVariant
            icon = Icons.Outlined.LockOpen
            label = R.string.state_disconnected
        }
        ConnectionState.CONNECTING -> {
            background = scheme.surfaceVariant
            foreground = scheme.primary
            icon = Icons.Outlined.LockOpen
            label = R.string.state_connecting
        }
        ConnectionState.CONNECTED -> {
            background = scheme.primaryContainer
            foreground = scheme.onPrimaryContainer
            icon = Icons.Outlined.Lock
            label = R.string.state_connected
        }
        ConnectionState.ERROR -> {
            background = scheme.errorContainer
            foreground = scheme.onErrorContainer
            icon = Icons.Outlined.ErrorOutline
            label = R.string.state_error
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(176.dp)) {
        if (state == ConnectionState.CONNECTING) {
            CircularProgressIndicator(modifier = Modifier.size(176.dp), strokeWidth = 4.dp)
        }
        Column(
            modifier = Modifier
                .size(160.dp)
                .background(background, CircleShape),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(label), color = foreground, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ConnectedCaption(uiState: HomeUiState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.connectedSince) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val elapsed = uiState.connectedSince?.let { formatElapsed(now - it) } ?: ""
    val address = uiState.status?.addresses?.firstOrNull()
    val caption = listOfNotNull(elapsed.ifEmpty { null }, address).joinToString(" · ")

    Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1_000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60)
}

@Composable
private fun ErrorCard(code: String, onOpenLog: () -> Unit) {
    val kind = classifyError(code)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(kind.titleRes()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(kind.hintRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                code,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onOpenLog, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.link_log))
            }
        }
    }
}

private fun ErrorKind.titleRes() = when (this) {
    ErrorKind.CREDENTIALS -> R.string.error_credentials_title
    ErrorKind.AUTH_PROTOCOL -> R.string.error_auth_protocol_title
    ErrorKind.CERTIFICATE -> R.string.error_certificate_title
    ErrorKind.HOST_UNREACHABLE -> R.string.error_unreachable_title
    ErrorKind.NOT_SSTP_SERVER -> R.string.error_not_sstp_title
    ErrorKind.PROXY -> R.string.error_proxy_title
    ErrorKind.SERVER_CLOSED -> R.string.error_server_closed_title
    ErrorKind.TIMEOUT -> R.string.error_timeout_title
    ErrorKind.UNKNOWN -> R.string.error_unknown_title
}

private fun ErrorKind.hintRes() = when (this) {
    ErrorKind.CREDENTIALS -> R.string.error_credentials_hint
    ErrorKind.AUTH_PROTOCOL -> R.string.error_auth_protocol_hint
    ErrorKind.CERTIFICATE -> R.string.error_certificate_hint
    ErrorKind.HOST_UNREACHABLE -> R.string.error_unreachable_hint
    ErrorKind.NOT_SSTP_SERVER -> R.string.error_not_sstp_hint
    ErrorKind.PROXY -> R.string.error_proxy_hint
    ErrorKind.SERVER_CLOSED -> R.string.error_server_closed_hint
    ErrorKind.TIMEOUT -> R.string.error_timeout_hint
    ErrorKind.UNKNOWN -> R.string.error_unknown_hint
}

@Composable
private fun DetailsTable(status: StatusSummary) {
    val tls = listOfNotNull(status.tlsProtocol, status.cipherSuite).joinToString(", ")
    val route = when {
        status.hasDefaultRoute -> stringResource(R.string.details_route_all)
        status.routes.isEmpty() -> stringResource(R.string.details_none)
        else -> stringResource(R.string.details_route_custom, status.routes.size)
    }
    val apps = status.appListType?.let { "$it: ${status.apps.size}" } ?: stringResource(R.string.details_apps_all)

    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailsRow(stringResource(R.string.details_tls), tls.ifEmpty { stringResource(R.string.details_none) })
        DetailsRow(stringResource(R.string.details_address), status.addresses.joinToString().ifEmpty { stringResource(R.string.details_none) })
        DetailsRow(stringResource(R.string.details_dns), status.dnsServers.joinToString().ifEmpty { stringResource(R.string.details_none) })
        DetailsRow(stringResource(R.string.details_route), route)
        DetailsRow(stringResource(R.string.details_apps), apps)
    }
}

@Composable
private fun DetailsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
    }
}

@Composable
private fun MainButton(state: ConnectionState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)

    when (state) {
        ConnectionState.DISCONNECTED -> Button(onClick = onConnect, modifier = modifier) {
            Text(stringResource(R.string.action_connect))
        }
        ConnectionState.ERROR -> Button(onClick = onConnect, modifier = modifier) {
            Text(stringResource(R.string.action_retry))
        }
        ConnectionState.CONNECTING -> OutlinedButton(onClick = onDisconnect, modifier = modifier) {
            Text(stringResource(R.string.action_cancel))
        }
        ConnectionState.CONNECTED -> OutlinedButton(onClick = onDisconnect, modifier = modifier) {
            Text(stringResource(R.string.action_disconnect))
        }
    }
}
