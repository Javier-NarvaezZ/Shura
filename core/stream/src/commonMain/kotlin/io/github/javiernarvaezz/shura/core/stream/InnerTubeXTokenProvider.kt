package io.github.javiernarvaezz.shura.core.stream

import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Adapts Shura's [PoTokenMinter] to InnerTubeX's `TokenProvider`. A failed mint returns no token, so InnerTubeX
 * falls back to clients that do not need one; the failure is recorded for the current resolution (R4).
 */
internal class InnerTubeXTokenProvider(
    private val minter: PoTokenMinter,
) : TokenProvider {
    override val capabilities =
        TokenProviderCapabilities(providers = setOf(PoTokenProviderKind.WEB_BOTGUARD), usesWebView = true)

    // Anonymous playback only: the cookie InnerTubeX passes is ignored.
    override suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String?,
    ): PoTokenResult? {
        val id = runCatching { VideoId(videoId) }.getOrNull() ?: return null
        return try {
            minter.mint(id, visitorData).let { PoTokenResult(it.player, it.streaming, it.visitorData) }
        } catch (e: PoTokenUnavailableException) {
            recordTokenFailure(e)
            null
        }
    }

    override suspend fun invalidateAttestation() = minter.invalidate()

    override suspend fun close() = minter.close()
}

/** Token failures seen during one resolution; scoped through the coroutine context, never shared globally. */
internal class TokenFailures : AbstractCoroutineContextElement(Key) {
    var last: PoTokenUnavailableException? = null

    companion object Key : CoroutineContext.Key<TokenFailures>
}

internal suspend fun recordTokenFailure(failure: PoTokenUnavailableException) {
    currentCoroutineContext()[TokenFailures]?.last = failure
}
