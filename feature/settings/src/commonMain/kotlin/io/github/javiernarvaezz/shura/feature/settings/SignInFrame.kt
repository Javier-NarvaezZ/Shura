package io.github.javiernarvaezz.shura.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.settings.resources.Res
import io.github.javiernarvaezz.shura.feature.settings.resources.back
import io.github.javiernarvaezz.shura.feature.settings.resources.camera_check
import io.github.javiernarvaezz.shura.feature.settings.resources.open_browser
import io.github.javiernarvaezz.shura.feature.settings.resources.sign_in_help
import io.github.javiernarvaezz.shura.feature.settings.resources.sign_in_help_body
import io.github.javiernarvaezz.shura.feature.settings.resources.sign_in_title
import org.jetbrains.compose.resources.stringResource

/**
 * The frame around the platform's sign-in page ([page], Google's own form): back, the title, help, and a notice
 * when Google asks for a camera check (a video selfie), which the app does not support: the user completes it in
 * the browser ([onOpenBrowser]) and signs in here again.
 */
@Composable
fun SignInFrame(
    cameraCheckAsked: Boolean,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    page: @Composable (Modifier) -> Unit,
) {
    var help by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Shura.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
            ) { Icon(ShuraIcons.Back, stringResource(Res.string.back), tint = Shura.colors.text) }
            Text(
                stringResource(Res.string.sign_in_title),
                style = MaterialTheme.typography.titleLarge,
                color = Shura.colors.text,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(Res.string.sign_in_help),
            style = MaterialTheme.typography.labelLarge,
            color = Shura.colors.accent,
            modifier =
                Modifier.clickable { help = !help }.padding(
                    horizontal = Shura.spacing.gutter,
                    vertical = Shura.spacing.s,
                ),
        )
        if (help) Notice(stringResource(Res.string.sign_in_help_body), onOpenBrowser)
        if (cameraCheckAsked) Notice(stringResource(Res.string.camera_check), onOpenBrowser)
        page(Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun Notice(
    text: String,
    onOpenBrowser: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Shura.colors.surface2)
            .padding(Shura.spacing.gutter),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Shura.colors.text)
        Button(
            onClick = onOpenBrowser,
            colors = ButtonDefaults.buttonColors(Shura.colors.accent, Shura.colors.onAccent),
            modifier = Modifier.padding(top = Shura.spacing.s),
        ) { Text(stringResource(Res.string.open_browser)) }
    }
}
