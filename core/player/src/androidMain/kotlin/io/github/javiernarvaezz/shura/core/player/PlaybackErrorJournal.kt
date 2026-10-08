package io.github.javiernarvaezz.shura.core.player

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Keeps [PlaybackErrorLog] in a private file that is never backed up, so the last failures can be read with
 * `dumpsys` after logcat is gone, on release builds too. All file access runs on one background thread.
 */
internal class PlaybackErrorJournal(
    file: File,
) {
    private val file = AtomicFile(file)
    private val executor =
        Executors.newSingleThreadExecutor { Thread(it, "ShuraErrorJournal").apply { isDaemon = true } }

    // Touched only on the executor thread.
    private var records: List<PlaybackErrorRecord>? = null

    fun record(record: PlaybackErrorRecord) =
        executor.execute {
            val updated = PlaybackErrorLog.append(loaded(), record)
            records = updated
            save(updated)
        }

    /** The stored records, newest first; null if they could not be read within [timeoutMs]. */
    fun snapshot(timeoutMs: Long): List<PlaybackErrorRecord>? =
        try {
            executor.submit<List<PlaybackErrorRecord>> { loaded() }.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (
            @Suppress("SwallowedException") e: TimeoutException,
        ) {
            null
        } catch (
            @Suppress("SwallowedException") e: ExecutionException,
        ) {
            null
        }

    /** Pending writes still complete. */
    fun close() = executor.shutdown()

    private fun loaded(): List<PlaybackErrorRecord> = records ?: read().also { records = it }

    private fun read(): List<PlaybackErrorRecord> =
        try {
            PlaybackErrorLog.decode(file.readFully().decodeToString())
        } catch (
            // No file yet, or unreadable: start empty.
            @Suppress("SwallowedException") e: IOException,
        ) {
            emptyList()
        }

    private fun save(records: List<PlaybackErrorRecord>) {
        val stream =
            try {
                file.startWrite()
            } catch (e: IOException) {
                Log.w(TAG, "Could not save the playback error log", e)
                return
            }
        try {
            stream.write(PlaybackErrorLog.encode(records).encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            Log.w(TAG, "Could not save the playback error log", e)
        }
    }

    private companion object {
        const val TAG = "ShuraPlayer"
    }
}

/** The active network as the system sees it; null if it cannot be read. */
internal fun Context.networkSnapshot(): NetworkSnapshot? =
    getSystemService(ConnectivityManager::class.java)?.let { connectivity ->
        connectivity.activeNetwork
            ?.let(connectivity::getNetworkCapabilities)
            ?.let(::networkSnapshotOf)
            ?: NetworkSnapshot(NetworkTransport.None, validated = false)
    }

private fun networkSnapshotOf(capabilities: NetworkCapabilities): NetworkSnapshot {
    val transport =
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.Cellular
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.Ethernet
            else -> NetworkTransport.Other
        }
    return NetworkSnapshot(transport, capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
}

/** The app's version name, or "unknown". */
internal fun Context.appVersionName(): String {
    val info =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (
            @Suppress("SwallowedException") e: PackageManager.NameNotFoundException,
        ) {
            null
        }
    return info?.versionName ?: "unknown"
}
