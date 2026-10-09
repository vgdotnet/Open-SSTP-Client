package kittoku.osc.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kittoku.osc.BuildConfig
import kittoku.osc.R


private const val FORK_URL = "https://github.com/vgdotnet/Open-SSTP-Client"
private const val UPSTREAM_URL = "https://github.com/kittoku/Open-SSTP-Client"
private const val AUTHOR_URL = "https://github.com/kittoku"
private const val UPSTREAM_CONTRIBUTORS = "Nicki de Wet, nynauy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val open = { url: String ->
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    }

    Scaffold(topBar = { BackTopBar(R.string.about_title, onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            SectionHeader(stringResource(R.string.about_thanks))
            Text(
                stringResource(R.string.about_thanks_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LinkRow("KOBAYASHI Ittoku", AUTHOR_URL) { open(AUTHOR_URL) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_contributors)) },
                supportingContent = { Text(UPSTREAM_CONTRIBUTORS) },
            )

            SectionHeader(stringResource(R.string.about_links))
            LinkRow(stringResource(R.string.about_fork), FORK_URL) { open(FORK_URL) }
            LinkRow(stringResource(R.string.about_upstream), UPSTREAM_URL) { open(UPSTREAM_URL) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_license)) },
                supportingContent = { Text(stringResource(R.string.about_license_text)) },
            )
        }
    }
}

@Composable
private fun LinkRow(title: String, url: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(url.removePrefix("https://")) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
