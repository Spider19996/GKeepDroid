package com.gkeepdroid

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Something made by others that GKeepDroid uses: who made it, under which license, and where it lives. */
private class Credit(val name: String, val author: String, val license: String, val url: String)

/**
 * Keep this list in step with what the app uses (build.gradle.kts, the Python requirements, files that were taken from
 * elsewhere): everyone whose work is used gets named here.
 */
private val credits = listOf(
    Credit("Jetpack Compose, Material 3, Glance, WorkManager, Activity, Core, Core SplashScreen, Security Crypto", "Google / Android Open Source Project", "Apache License 2.0", "https://developer.android.com/jetpack"),
    Credit("Material icons", "Google", "Apache License 2.0", "https://fonts.google.com/icons"),
    Credit("AndroidSVG", "Paul LeBeau", "Apache License 2.0", "https://bigbadaboom.github.io/androidsvg/"),
    Credit("Chaquopy (Python for Android)", "Chaquopy Ltd", "MIT License", "https://chaquo.com/chaquopy/"),
    Credit("gkeepapi (access to Google Keep)", "kiwiz", "MIT License", "https://github.com/kiwiz/gkeepapi"),
    Credit("gpsoauth (Google sign-in)", "Simon Weber", "MIT License", "https://github.com/simon-weber/gpsoauth"),
    Credit("requests", "Kenneth Reitz and contributors", "Apache License 2.0", "https://requests.readthedocs.io"),
    Credit("urllib3", "Andrey Petrov and contributors", "MIT License", "https://urllib3.readthedocs.io"),
    Credit("certifi", "Kenneth Reitz and contributors", "Mozilla Public License 2.0", "https://github.com/certifi/python-certifi"),
    Credit("idna", "Kim Davies and contributors", "BSD 3-Clause License", "https://github.com/kjd/idna"),
    Credit("charset-normalizer", "Ahmed R. TAHRI", "MIT License", "https://github.com/jawah/charset_normalizer"),
    Credit("PyCryptodome", "Helder Eijs and contributors", "BSD 2-Clause License", "https://www.pycryptodome.org"),
    Credit("Python", "Python Software Foundation", "PSF License", "https://www.python.org"),
    Credit("Google Keep (the notes, the background pictures, the formatting)", "Google LLC", "Keep is a trademark of Google LLC; its pictures stay Google's and are fetched from Google's servers, not part of this app", "https://keep.google.com"),
)

@Composable
internal fun ColumnScope.CreditsPage(s: SettingsState) {
    with(s) {
        Text(
            stringResource(R.string.credits_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.credits_not_affiliated),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            credits.forEachIndexed { index, credit ->
                MenuRow(
                    title = credit.name,
                    summary = credit.author + "\n" + credit.license,
                    chevron = R.drawable.ic_open_new,
                    shapes = ListItemDefaults.segmentedShapes(index, credits.size),
                ) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(credit.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        Text(
            stringResource(R.string.credits_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
