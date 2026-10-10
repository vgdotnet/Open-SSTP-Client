package kittoku.osc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kittoku.osc.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.Profile
import kittoku.osc.preference.SettingsError
import kittoku.osc.preference.getBoolean


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutesSettingsScreen(
    profile: Profile,
    errors: Set<SettingsError>,
    onEdit: ProfileEditor,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { BackTopBar(R.string.section_routes, onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader(stringResource(R.string.routes_header))
            ProfileSwitch(profile, OscPrefKey.ROUTE_DO_ADD_DEFAULT_ROUTE, onEdit, R.string.routes_default, R.string.routes_default_support)
            ProfileSwitch(profile, OscPrefKey.ROUTE_DO_ROUTE_PRIVATE_ADDRESSES, onEdit, R.string.routes_private, R.string.routes_private_support)
            ProfileSwitch(profile, OscPrefKey.ROUTE_DO_ADD_CUSTOM_ROUTES, onEdit, R.string.routes_custom)
            if (profile.getBoolean(OscPrefKey.ROUTE_DO_ADD_CUSTOM_ROUTES)) {
                ProfileTextField(
                    profile, OscPrefKey.ROUTE_CUSTOM_ROUTES, onEdit,
                    label = R.string.routes_custom_field,
                    placeholder = "192.168.1.0/24\n10.8.1.0/24",
                    supporting = R.string.routes_custom_support,
                    error = if (SettingsError.CUSTOM_ROUTES_INVALID in errors) R.string.error_routes_invalid else null,
                    singleLine = false,
                )
            }

            SectionHeader(stringResource(R.string.bypass_header))
            ProfileSwitch(profile, OscPrefKey.ROUTE_DO_BYPASS_DOMAINS, onEdit, R.string.bypass_enable, R.string.bypass_enable_support)
            if (profile.getBoolean(OscPrefKey.ROUTE_DO_BYPASS_DOMAINS)) {
                ProfileTextField(
                    profile, OscPrefKey.ROUTE_BYPASS_DOMAINS, onEdit,
                    label = R.string.bypass_field,
                    placeholder = "ozon.ru\nsberbank.ru",
                    supporting = R.string.bypass_field_support,
                    error = if (SettingsError.BYPASS_DOMAINS_INVALID in errors) R.string.error_domains_invalid else null,
                    keyboardType = KeyboardType.Uri,
                    singleLine = false,
                )
                Text(
                    text = stringResource(R.string.bypass_dns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SectionHeader(stringResource(R.string.dns_header))
            ProfileSwitch(profile, OscPrefKey.DNS_DO_REQUEST_ADDRESS, onEdit, R.string.dns_request)
            ProfileSwitch(profile, OscPrefKey.DNS_DO_USE_CUSTOM_SERVER, onEdit, R.string.dns_custom, R.string.dns_custom_support)
            if (profile.getBoolean(OscPrefKey.DNS_DO_USE_CUSTOM_SERVER)) {
                ProfileTextField(
                    profile, OscPrefKey.DNS_CUSTOM_ADDRESS, onEdit,
                    label = R.string.dns_custom_field,
                    placeholder = "1.1.1.1",
                    error = if (SettingsError.CUSTOM_DNS_INVALID in errors) R.string.error_dns_invalid else null,
                )
            }
        }
    }
}
