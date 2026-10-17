package io.github.javiernarvaezz.shura.core.auth

/**
 * A signed-in YouTube Music session. Every value is a secret: [toString] never shows them, and none may be logged
 * or stored outside the encrypted session store.
 */
class Session(
    /** YouTube's cookies (for `music.youtube.com`), name to value, in the order Google set them. */
    val cookies: Map<String, String>,
    /** `ytcfg` `DATASYNC_ID`: "<channel page id>||<user id>"; the part before "||" goes in `onBehalfOfUser`. */
    val dataSyncId: String?,
    /** The account's display name, once known; shown in Settings. */
    val accountName: String?,
    val signedInAtMillis: Long,
    /** Google's own cookies (for `accounts.google.com`): only used to sign out at Google. */
    val googleCookies: Map<String, String> = emptyMap(),
) {
    /** The id to act on behalf of (a brand channel), or null for the account itself. */
    val onBehalfOfUser: String? get() = dataSyncId?.substringBefore("||")?.takeIf { it.isNotEmpty() }

    fun copy(
        cookies: Map<String, String> = this.cookies,
        accountName: String? = this.accountName,
    ) = Session(cookies, dataSyncId, accountName, signedInAtMillis, googleCookies)

    override fun toString(): String =
        "Session(cookies=${cookies.size} redacted, googleCookies=${googleCookies.size} redacted, " +
            "signedInAt=$signedInAtMillis)"

    override fun equals(other: Any?): Boolean =
        other is Session &&
            other.cookies == cookies &&
            other.dataSyncId == dataSyncId &&
            other.accountName == accountName &&
            other.signedInAtMillis == signedInAtMillis &&
            other.googleCookies == googleCookies

    override fun hashCode(): Int = cookies.hashCode() * HASH + signedInAtMillis.hashCode()

    private companion object {
        const val HASH = 31
    }
}

/** What Settings shows about the account. */
sealed interface AuthState {
    data object SignedOut : AuthState

    data class SignedIn(
        val accountName: String?,
    ) : AuthState

    /** Google no longer accepts the session; the user signs in again. */
    data object Expired : AuthState

    /** The stored session could not be read (the device key changed); the user signs in again. */
    data object NeedsSignIn : AuthState
}

/** What reading the stored session found. */
sealed interface StoredSession {
    data class Present(
        val session: Session,
    ) : StoredSession

    data object Absent : StoredSession

    /** It was there but could not be read (the device key changed, or the file is damaged); it has been deleted. */
    data object Unreadable : StoredSession
}

/** Where the session lives: encrypted with a device key on Android. Blocking: call it off the main thread. */
interface SessionStore {
    fun load(): StoredSession

    fun save(session: Session)

    /** Deletes the session and its key. */
    fun clear()
}
