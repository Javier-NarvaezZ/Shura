package io.github.javiernarvaezz.shura.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.auth.AuthState
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.settings.resources.Res
import io.github.javiernarvaezz.shura.feature.settings.resources.account_section
import io.github.javiernarvaezz.shura.feature.settings.resources.autoplay
import io.github.javiernarvaezz.shura.feature.settings.resources.autoplay_hint
import io.github.javiernarvaezz.shura.feature.settings.resources.back
import io.github.javiernarvaezz.shura.feature.settings.resources.cancel
import io.github.javiernarvaezz.shura.feature.settings.resources.playback_section
import io.github.javiernarvaezz.shura.feature.settings.resources.session_expired
import io.github.javiernarvaezz.shura.feature.settings.resources.session_unreadable
import io.github.javiernarvaezz.shura.feature.settings.resources.settings_title
import io.github.javiernarvaezz.shura.feature.settings.resources.sign_in
import io.github.javiernarvaezz.shura.feature.settings.resources.sign_out
import io.github.javiernarvaezz.shura.feature.settings.resources.sign_out_body
import io.github.javiernarvaezz.shura.feature.settings.resources.sign_out_title
import io.github.javiernarvaezz.shura.feature.settings.resources.signed_in
import io.github.javiernarvaezz.shura.feature.settings.resources.signed_in_as
import io.github.javiernarvaezz.shura.feature.settings.resources.signed_out_hint
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * Settings: the YouTube Music account (where there is one; [account] is null on desktop for now) and playback.
 * [onSignIn] opens the platform's sign-in screen.
 */
@Composable
fun SettingsScreen(
    account: AccountModel?,
    autoplay: StateFlow<Boolean>?,
    onAutoplayChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
            ) { Icon(ShuraIcons.Back, stringResource(Res.string.back), tint = Shura.colors.text) }
        }
        Text(
            stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Shura.colors.text,
            modifier = Modifier.padding(horizontal = Shura.spacing.gutter),
        )
        if (account != null) AccountSection(account, onSignIn)
        if (autoplay != null) PlaybackSection(autoplay, onAutoplayChange)
    }
}

@Composable
private fun AccountSection(
    account: AccountModel,
    onSignIn: () -> Unit,
) {
    val state by account.state.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(Res.string.account_section)) {
        val message =
            when (val shown = state) {
                AuthState.SignedOut -> {
                    stringResource(Res.string.signed_out_hint)
                }

                is AuthState.SignedIn -> {
                    shown.accountName?.let { stringResource(Res.string.signed_in_as, it) }
                        ?: stringResource(Res.string.signed_in)
                }

                AuthState.Expired -> {
                    stringResource(Res.string.session_expired)
                }

                AuthState.NeedsSignIn -> {
                    stringResource(Res.string.session_unreadable)
                }
            }
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Shura.colors.textMuted)
        if (state is AuthState.SignedIn) {
            OutlinedButton(onClick = { confirming = true }) { Text(stringResource(Res.string.sign_out)) }
        } else {
            Button(
                onClick = onSignIn,
                colors = ButtonDefaults.buttonColors(Shura.colors.accent, Shura.colors.onAccent),
            ) { Text(stringResource(Res.string.sign_in)) }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.sign_out_title)) },
            text = { Text(stringResource(Res.string.sign_out_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    account.signOut()
                }) { Text(stringResource(Res.string.sign_out)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirming = false },
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PlaybackSection(
    autoplay: StateFlow<Boolean>,
    onChange: (Boolean) -> Unit,
) {
    val enabled by autoplay.collectAsStateWithLifecycle()
    Section(stringResource(Res.string.playback_section)) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.autoplay),
                    style = MaterialTheme.typography.titleSmall,
                    color = Shura.colors.text,
                )
                Text(
                    stringResource(Res.string.autoplay_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Shura.colors.textMuted,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Shura.spacing.gutter).padding(top = Shura.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.m),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Shura.colors.text)
        content()
    }
}
