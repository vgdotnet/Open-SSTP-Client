package kittoku.osc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
