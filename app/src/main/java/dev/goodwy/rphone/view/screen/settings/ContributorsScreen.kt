package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.GITHUB_DEV
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.Title
import androidx.annotation.StringRes

data class Contributor(
    val name: String,
    val role: String,
    val githubUrl: String? = null,
    @field:StringRes val roleResource: Int? = null,
)

val appContributors = listOf(
    Contributor("Alexandru Butica", "", GITHUB_DEV, R.string.contributor_developer),
)

val otherContributors = listOf(
    Contributor("Goodwy — Rill Phone", "", "https://github.com/Goodwy/RPhone", R.string.contributor_upstream),
    Contributor("RivoPhoneApp", "user-grinch", "https://github.com/user-grinch/RivoPhoneApp"),
    Contributor("Ever-Dialer", "hari161008", "https://github.com/hari161008/Ever-Dialer"),
    Contributor( "Phone by Google","Google LLC", "https://play.google.com/store/apps/details?id=com.google.android.dialer"),
    Contributor("ExDialer", "", roleResource = R.string.contributor_inspiration),
    Contributor("Samsung Phone", "", "https://www.samsung.com/", R.string.contributor_inspiration),
    Contributor("Thunderbird", "", "https://www.thunderbird.net/", R.string.contributor_inspiration),
    Contributor("Jira", "Atlassian", "https://www.atlassian.com/software/jira"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContributorsScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val rotation = context.currentDisplayRotation()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.contributors)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
//                .padding(padding)
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    SettingsSectionLabel(stringResource(R.string.development_team))
                    RillExpressiveCard {
                        appContributors.forEachIndexed { index, contributor ->
                            RillListItem(
                                headline = contributor.name,
                                expandText = true,
                                supporting = contributor.roleResource?.let { stringResource(it) } ?: contributor.role,
                                trailingIcon = if (contributor.githubUrl != null) Icons.AutoMirrored.Outlined.Launch else null,
                                modifierTrailingIcon = Modifier.padding(end = 8.dp).size(20.dp),
                                onClick = {
                                    contributor.githubUrl?.let { openLink(context, it) }
                                }
                            )
                        }
                    }
                }
            }
            item {
                Column {
                    SettingsSectionLabel(stringResource(R.string.apps_that_inspired_us))
                    RillExpressiveCard {
                        otherContributors.forEachIndexed { index, contributor ->
                            RillListItem(
                                headline = contributor.name,
                                expandText = true,
                                supporting = contributor.roleResource?.let { stringResource(it) } ?: contributor.role,
                                trailingIcon = if (contributor.githubUrl != null) Icons.AutoMirrored.Outlined.Launch else null,
                                modifierTrailingIcon = Modifier.padding(end = 8.dp).size(20.dp),
                                onClick = {
                                    contributor.githubUrl?.let { openLink(context, it) }
                                }
                            )
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }
}
