package io.github.javiernarvaezz.shura

import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Emits each time the system has a new default network, for covers that ran out of retries (`LocalNetworkRegained`).
 * Also emits once when registered if a network is up, which only costs a waiting cover one extra attempt.
 */
internal fun networkRegained(connectivity: ConnectivityManager?): Flow<Unit> {
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    connectivity?.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                events.tryEmit(Unit)
            }
        },
    )
    return events.asSharedFlow()
}
