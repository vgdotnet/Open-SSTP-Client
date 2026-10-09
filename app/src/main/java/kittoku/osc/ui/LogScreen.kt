package kittoku.osc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kittoku.osc.R
import kittoku.osc.control.LogLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


internal fun formatLog(lines: List<LogLine>): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return lines.joinToString("\n") { "[${format.format(Date(it.timeMillis))}] ${it.text}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogScreen(lines: List<LogLine>, onBack: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { copyLog(context, lines) }, enabled = lines.isNotEmpty()) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.log_copy))
                    }
                    IconButton(onClick = { shareLog(context, lines) }, enabled = lines.isNotEmpty()) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.log_share))
                    }
                },
            )
        }
    ) { innerPadding ->
        if (lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = "${timeFormat.format(Date(line.timeMillis))}  ${line.text}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (line.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

private fun copyLog(context: Context, lines: List<LogLine>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Open SSTP Client log", formatLog(lines)))
    Toast.makeText(context, R.string.log_copied, Toast.LENGTH_SHORT).show()
}

private fun shareLog(context: Context, lines: List<LogLine>) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Open SSTP Client log")
        .putExtra(Intent.EXTRA_TEXT, formatLog(lines))
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.log_share)))
}
