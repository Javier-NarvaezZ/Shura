package io.github.javiernarvaezz.shura.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.ui.components.MediaRail
import io.github.javiernarvaezz.shura.core.ui.components.MediaTile
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.library.resources.Res
import io.github.javiernarvaezz.shura.feature.library.resources.account_loading
import io.github.javiernarvaezz.shura.feature.library.resources.account_offline
import io.github.javiernarvaezz.shura.feature.library.resources.account_section
import io.github.javiernarvaezz.shura.feature.library.resources.account_sign_in
import io.github.javiernarvaezz.shura.feature.library.resources.account_sign_in_needed
import io.github.javiernarvaezz.shura.feature.library.resources.albums
import io.github.javiernarvaezz.shura.feature.library.resources.artists
import io.github.javiernarvaezz.shura.feature.library.resources.device_section
import io.github.javiernarvaezz.shura.feature.library.resources.empty_library
import io.github.javiernarvaezz.shura.feature.library.resources.library_title
import io.github.javiernarvaezz.shura.feature.library.resources.liked_count
import io.github.javiernarvaezz.shura.feature.library.resources.liked_none
import io.github.javiernarvaezz.shura.feature.library.resources.liked_one
import io.github.javiernarvaezz.shura.feature.library.resources.liked_songs
import io.github.javiernarvaezz.shura.feature.library.resources.playlists
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource

/**
 * The Library tab: the liked songs, then saved playlists, albums and followed artists, newest first. Each
 * [scrollToTop] (its tab touched again) goes back to the top.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LibraryScreen(
    model: LibraryModel,
    links: LibraryLinks,
    modifier: Modifier = Modifier,
    account: AccountLibraryModel? = null,
    onSignIn: () -> Unit = {},
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(scrollToTop) { scrollToTop.collect { list.animateScrollToItem(0) } }
    val accountState = account?.state?.collectAsStateWithLifecycle()?.value ?: AccountLibraryState()
    LaunchedEffect(account) { account?.refresh() }
    PullToRefreshBox(
        isRefreshing = accountState.loading && accountState.snapshot != null,
        onRefresh = { account?.refresh(force = true) },
        modifier = modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(bottom = Shura.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Shura.spacing.xl),
        ) {
            item(key = "title") {
                Text(
                    stringResource(Res.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Shura.colors.text,
                    modifier = Modifier.padding(horizontal = Shura.spacing.gutter).padding(top = Shura.spacing.xl),
                )
            }
            if (state.isEmpty && !accountState.visible) {
                item(key = "empty") { Note(stringResource(Res.string.empty_library)) }
            }
            item(key = "liked") { LikedSongsCard(state.likedCount, links.onLikedSongs) }
            if (accountState.visible) accountSection(accountState, links, onSignIn)
            if (accountState.visible && !state.isEmpty) {
                item(key = "device") { SectionTitle(stringResource(Res.string.device_section)) }
            }
            rails(state.playlists, state.albums, state.artists, links, keyPrefix = "local")
        }
    }
}

/** The account's part of the tab: its state, then its playlists, albums and artists. */
private fun LazyListScope.accountSection(
    state: AccountLibraryState,
    links: LibraryLinks,
    onSignIn: () -> Unit,
) {
    item(key = "account") { SectionTitle(stringResource(Res.string.account_section)) }
    when {
        state.signInNeeded -> {
            item(key = "account-sign-in") {
                Column(Modifier.padding(horizontal = Shura.spacing.gutter)) {
                    Note(stringResource(Res.string.account_sign_in_needed), Modifier)
                    Button(
                        onClick = onSignIn,
                        colors = ButtonDefaults.buttonColors(Shura.colors.accent, Shura.colors.onAccent),
                        modifier = Modifier.padding(top = Shura.spacing.s),
                    ) { Text(stringResource(Res.string.account_sign_in)) }
                }
            }
        }

        state.snapshot == null && state.loading -> {
            item(key = "account-loading") { Note(stringResource(Res.string.account_loading)) }
        }

        state.offline -> {
            item(key = "account-offline") { Note(stringResource(Res.string.account_offline)) }
        }
    }
    state.snapshot?.let { rails(it.playlists, it.albums, it.artists, links, keyPrefix = "account") }
}

/** Playlists, albums and artists as rails, each opening its page; empty ones are left out. */
private fun LazyListScope.rails(
    playlists: List<PlaylistSummary>,
    albums: List<AlbumSummary>,
    artists: List<ArtistSummary>,
    links: LibraryLinks,
    keyPrefix: String,
) {
    if (playlists.isNotEmpty()) {
        item(key = "$keyPrefix-playlists") {
            MediaRail(stringResource(Res.string.playlists), playlists, key = { it.id }) { playlist ->
                MediaTile(playlist.title, playlist.author, playlist.thumbnailUrl, { links.onPlaylist(playlist.id) })
            }
        }
    }
    if (albums.isNotEmpty()) {
        item(key = "$keyPrefix-albums") {
            MediaRail(stringResource(Res.string.albums), albums, key = { it.id }) { album ->
                MediaTile(album.title, album.artistNames, album.thumbnailUrl, { links.onAlbum(album.id) })
            }
        }
    }
    if (artists.isNotEmpty()) {
        item(key = "$keyPrefix-artists") {
            MediaRail(stringResource(Res.string.artists), artists, key = { it.id }) { artist ->
                MediaTile(artist.name, null, artist.thumbnailUrl, { links.onArtist(artist.id) }, round = true)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Shura.colors.text,
        modifier = Modifier.padding(horizontal = Shura.spacing.gutter),
    )
}

@Composable
private fun Note(
    text: String,
    modifier: Modifier = Modifier.padding(horizontal = Shura.spacing.gutter),
) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Shura.colors.textMuted, modifier = modifier)
}

/** The liked songs as one card: a heart on the brand gradient, the name and how many there are. */
@Composable
private fun LikedSongsCard(
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Shura.spacing.gutter, vertical = Shura.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(CARD_ART)
                .clip(Shura.shapes.thumbnail)
                .background(Shura.colors.brandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ShuraIcons.FavoriteFilled, contentDescription = null, tint = Shura.colors.onAccent)
        }
        Column(Modifier.padding(start = Shura.spacing.l)) {
            Text(
                stringResource(Res.string.liked_songs),
                style = MaterialTheme.typography.titleMedium,
                color = Shura.colors.text,
            )
            Text(
                when (count) {
                    0 -> stringResource(Res.string.liked_none)
                    1 -> stringResource(Res.string.liked_one)
                    else -> stringResource(Res.string.liked_count, count)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Shura.colors.textMuted,
            )
        }
    }
}

private val CARD_ART = 64.dp
