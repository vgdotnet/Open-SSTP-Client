package kittoku.osc.ui

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow


// Emits once on collection and then on every change of a key accepted by the filter
internal fun SharedPreferences.changes(filter: (String) -> Boolean): Flow<Unit> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || filter(key)) trySend(Unit)
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(Unit)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
