package kittoku.osc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kittoku.osc.R
import kittoku.osc.preference.AUTH_PROTOCOL_EAP_MSCHAPv2
import kittoku.osc.preference.AUTH_PROTOCOL_MSCHAPv2
import kittoku.osc.preference.AUTH_PROTOCOl_PAP
import kittoku.osc.preference.AUTH_TIMEOUT_RANGE
import kittoku.osc.preference.MRU_RANGE
import kittoku.osc.preference.MTU_RANGE
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.PORT_RANGE
import kittoku.osc.preference.Profile
import kittoku.osc.preference.RECONNECTION_COUNT_RANGE
import kittoku.osc.preference.RECONNECTION_INTERVAL_RANGE
import kittoku.osc.preference.SSL_VERSION_DEFAULT
import kittoku.osc.preference.SettingsError
import kittoku.osc.preference.getBoolean
import kittoku.osc.preference.getSet
import kittoku.osc.preference.getString
import kittoku.osc.preference.getUri
import kittoku.osc.preference.setSet
import kittoku.osc.preference.setString
import kittoku.osc.preference.setUri
import javax.net.ssl.SSLContext


private val AUTH_PROTOCOLS = listOf(
    AUTH_PROTOCOl_PAP to "PAP",
    AUTH_PROTOCOL_MSCHAPv2 to "MS-CHAPv2",
    AUTH_PROTOCOL_EAP_MSCHAPv2 to "EAP-MS-CHAPv2",
)

private fun takeFolderPermission(context: Context, uri: Uri, flags: Int) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    } catch (_: SecurityException) { }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AdvancedSettingsScreen(
    profile: Profile,
    errors: Set<SettingsError>,
    onEdit: ProfileEditor,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var suitesDialogOpen by remember { mutableStateOf(false) }
    val sslParameters = remember { SSLContext.getDefault().supportedSSLParameters }
    fun has(error: SettingsError) = error in errors

    val certDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        takeFolderPermission(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        onEdit { setUri(OscPrefKey.SSL_CERT_DIR, uri.toString()) }
    }
    val logDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        takeFolderPermission(context, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        onEdit { setUri(OscPrefKey.LOG_DIR, uri.toString()) }
    }

    Scaffold(topBar = { BackTopBar(R.string.section_advanced, onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader(stringResource(R.string.adv_tls))
            val defaultLabel = stringResource(R.string.adv_tls_default)
            ChoiceField(
                label = stringResource(R.string.adv_tls_version),
                options = listOf(SSL_VERSION_DEFAULT to defaultLabel) + sslParameters.protocols.map { it to it },
                selected = profile.getString(OscPrefKey.SSL_VERSION),
                onSelect = { version -> onEdit { setString(OscPrefKey.SSL_VERSION, version) } },
                error = if (has(SettingsError.TLS_VERSION_REQUIRED)) stringResource(R.string.error_tls_version_required) else null,
            )
            ProfileSwitch(profile, OscPrefKey.SSL_DO_VERIFY, onEdit, R.string.adv_verify, R.string.adv_verify_support)
            ProfileSwitch(profile, OscPrefKey.SSL_DO_USE_CUSTOM_SNI, onEdit, R.string.adv_sni)
            if (profile.getBoolean(OscPrefKey.SSL_DO_USE_CUSTOM_SNI)) {
                ProfileTextField(
                    profile, OscPrefKey.SSL_CUSTOM_SNI, onEdit,
                    label = R.string.adv_sni_field,
                    error = if (has(SettingsError.SNI_EMPTY)) R.string.error_required else null,
                    keyboardType = KeyboardType.Uri,
                )
            }
            ProfileSwitch(profile, OscPrefKey.SSL_DO_SELECT_SUITES, onEdit, R.string.adv_suites_only)
            if (profile.getBoolean(OscPrefKey.SSL_DO_SELECT_SUITES)) {
                NavRow(
                    title = stringResource(R.string.adv_suites),
                    summary = if (has(SettingsError.SUITES_EMPTY)) stringResource(R.string.error_suites_empty)
                    else stringResource(R.string.adv_suites_count, profile.getSet(OscPrefKey.SSL_SUITES).size),
                    onClick = { suitesDialogOpen = true },
                    isError = has(SettingsError.SUITES_EMPTY),
                )
            }
            ProfileSwitch(profile, OscPrefKey.SSL_DO_SPECIFY_CERT, onEdit, R.string.adv_certs, R.string.adv_certs_support)
            if (profile.getBoolean(OscPrefKey.SSL_DO_SPECIFY_CERT)) {
                FolderRow(
                    title = stringResource(R.string.adv_certs_dir),
                    uri = profile.getUri(OscPrefKey.SSL_CERT_DIR),
                    onPick = { certDirLauncher.launch(null) },
                    isError = has(SettingsError.CERT_DIR_MISSING),
                )
            }

            SectionHeader(stringResource(R.string.adv_ppp))
            Text(
                text = stringResource(R.string.adv_auth),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            val authProtocols = profile.getSet(OscPrefKey.PPP_AUTH_PROTOCOLS)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                AUTH_PROTOCOLS.forEach { (value, title) ->
                    val isSelected = value in authProtocols
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onEdit {
                                val current = getSet(OscPrefKey.PPP_AUTH_PROTOCOLS)
                                setSet(OscPrefKey.PPP_AUTH_PROTOCOLS, if (isSelected) current - value else current + value)
                            }
                        },
                        label = { Text(title) },
                    )
                }
            }
            if (has(SettingsError.AUTH_EMPTY)) ErrorText(stringResource(R.string.error_auth_empty))
            ProfileIntField(profile, OscPrefKey.PPP_AUTH_TIMEOUT, onEdit, R.string.adv_auth_timeout, AUTH_TIMEOUT_RANGE)
            ProfileIntField(profile, OscPrefKey.PPP_MRU, onEdit, R.string.adv_mru, MRU_RANGE)
            ProfileIntField(profile, OscPrefKey.PPP_MTU, onEdit, R.string.adv_mtu, MTU_RANGE)
            ProfileSwitch(profile, OscPrefKey.PPP_IPv4_ENABLED, onEdit, R.string.adv_ipv4)
            if (profile.getBoolean(OscPrefKey.PPP_IPv4_ENABLED)) {
                ProfileSwitch(profile, OscPrefKey.PPP_DO_REQUEST_STATIC_IPv4_ADDRESS, onEdit, R.string.adv_static_ipv4)
                if (profile.getBoolean(OscPrefKey.PPP_DO_REQUEST_STATIC_IPv4_ADDRESS)) {
                    ProfileTextField(
                        profile, OscPrefKey.PPP_STATIC_IPv4_ADDRESS, onEdit,
                        label = R.string.adv_static_ipv4_field,
                        placeholder = "192.168.2.10",
                        error = if (has(SettingsError.STATIC_IPV4_INVALID)) R.string.error_ipv4_invalid else null,
                        keyboardType = KeyboardType.Decimal,
                    )
                }
            }
            ProfileSwitch(profile, OscPrefKey.PPP_IPv6_ENABLED, onEdit, R.string.adv_ipv6, R.string.adv_ipv6_support)
            if (has(SettingsError.NO_NETWORK_PROTOCOL)) ErrorText(stringResource(R.string.error_no_protocol))

            SectionHeader(stringResource(R.string.adv_proxy))
            ProfileSwitch(profile, OscPrefKey.PROXY_DO_USE_PROXY, onEdit, R.string.adv_proxy_use)
            if (profile.getBoolean(OscPrefKey.PROXY_DO_USE_PROXY)) {
                ProfileTextField(
                    profile, OscPrefKey.PROXY_HOSTNAME, onEdit,
                    label = R.string.adv_proxy_host,
                    error = if (has(SettingsError.PROXY_HOST_EMPTY)) R.string.error_required else null,
                    keyboardType = KeyboardType.Uri,
                )
                ProfileIntField(profile, OscPrefKey.PROXY_PORT, onEdit, R.string.adv_proxy_port, PORT_RANGE)
                ProfileTextField(profile, OscPrefKey.PROXY_USERNAME, onEdit, label = R.string.adv_proxy_user, supporting = R.string.field_optional)
                ProfileTextField(profile, OscPrefKey.PROXY_PASSWORD, onEdit, label = R.string.adv_proxy_password, supporting = R.string.field_optional, password = true)
            }

            SectionHeader(stringResource(R.string.adv_reconnect))
            ProfileSwitch(profile, OscPrefKey.RECONNECTION_ENABLED, onEdit, R.string.adv_reconnect_use)
            if (profile.getBoolean(OscPrefKey.RECONNECTION_ENABLED)) {
                ProfileIntField(profile, OscPrefKey.RECONNECTION_COUNT, onEdit, R.string.adv_reconnect_count, RECONNECTION_COUNT_RANGE)
                ProfileIntField(profile, OscPrefKey.RECONNECTION_INTERVAL, onEdit, R.string.adv_reconnect_interval, RECONNECTION_INTERVAL_RANGE)
            }

            SectionHeader(stringResource(R.string.adv_log))
            ProfileSwitch(profile, OscPrefKey.LOG_DO_SAVE_LOG, onEdit, R.string.adv_log_save)
            if (profile.getBoolean(OscPrefKey.LOG_DO_SAVE_LOG)) {
                FolderRow(
                    title = stringResource(R.string.adv_log_dir),
                    uri = profile.getUri(OscPrefKey.LOG_DIR),
                    onPick = { logDirLauncher.launch(null) },
                    isError = has(SettingsError.LOG_DIR_MISSING),
                )
            }
        }
    }

    if (suitesDialogOpen) {
        SuitesDialog(
            all = remember { sslParameters.cipherSuites.toList() },
            initial = profile.getSet(OscPrefKey.SSL_SUITES),
            onDismiss = { suitesDialogOpen = false },
            onConfirm = { suites -> onEdit { setSet(OscPrefKey.SSL_SUITES, suites) } },
        )
    }
}

@Composable
private fun SuitesDialog(all: List<String>, initial: Set<String>, onDismiss: () -> Unit, onConfirm: (Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adv_suites)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(all) { suite ->
                    val checked = suite in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, role = Role.Checkbox) { selected = if (it) selected + suite else selected - suite }
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(suite, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selected)
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
