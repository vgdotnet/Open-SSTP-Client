package kittoku.osc.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kittoku.osc.R


internal const val BLANK_ACTIVITY_TYPE_SAVE_CERT = 2

internal const val EXTRA_KEY_TYPE = "TYPE"
internal const val EXTRA_KEY_CERT = "CERT"
internal const val EXTRA_KEY_FILENAME = "FILENAME"

// Opened from SSLTerminal's "untrusted certificate" notification; name and extras are kept so the core stays untouched
class BlankActivity : ComponentActivity() {
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-x509-ca-cert")) { uri ->
        val cert = intent.getByteArrayExtra(EXTRA_KEY_CERT)

        if (uri != null && cert != null) {
            val saved = runCatching {
                contentResolver.openOutputStream(uri, "w")?.use { it.write(cert) } != null
            }.getOrDefault(false)

            Toast.makeText(this, if (saved) R.string.cert_saved else R.string.cert_save_failed, Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getIntExtra(EXTRA_KEY_TYPE, -1) != BLANK_ACTIVITY_TYPE_SAVE_CERT || intent.getByteArrayExtra(EXTRA_KEY_CERT) == null) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            saveLauncher.launch(intent.getStringExtra(EXTRA_KEY_FILENAME) ?: "server.crt")
        }
    }
}
