package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_reconnect_action
import pm.bam.gamedeals.feature.account.generated.resources.account_reconnect_body
import pm.bam.gamedeals.feature.account.generated.resources.account_reconnect_title
import pm.bam.gamedeals.feature.account.generated.resources.account_section_collection
import pm.bam.gamedeals.feature.account.generated.resources.account_section_waitlist
import pm.bam.gamedeals.feature.account.generated.resources.account_sign_in
import pm.bam.gamedeals.feature.account.generated.resources.account_sign_out
import pm.bam.gamedeals.feature.account.generated.resources.account_signed_in_as
import pm.bam.gamedeals.feature.account.generated.resources.account_signed_out_body
import pm.bam.gamedeals.feature.account.generated.resources.account_signed_out_title
import pm.bam.gamedeals.feature.account.generated.resources.account_stat_collected
import pm.bam.gamedeals.feature.account.generated.resources.account_stat_waitlisted
import pm.bam.gamedeals.feature.account.ui.AccountViewModel.AccountScreenData
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun AccountScreen(
    onGameClick: (gameId: String) -> Unit = {},
    viewModel: AccountViewModel = koinViewModel(),
) {
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    AccountScreenContent(
        data = data,
        onLogin = viewModel::onLogin,
        onLogout = viewModel::onLogout,
        onGameClick = onGameClick,
    )
}

@Composable
private fun AccountScreenContent(
    data: AccountScreenData,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
) {
    if (!data.loggedIn) {
        LoggedOutContent(loggingIn = data.loggingIn, onLogin = onLogin)
    } else {
        // Reconnect re-runs the same OAuth flow as a fresh sign-in; on success the token is re-stamped
        // with the current scope version and the banner disappears (#273).
        LoggedInContent(data = data, onLogout = onLogout, onReconnect = onLogin, onGameClick = onGameClick)
    }
}

@Composable
private fun LoggedOutContent(loggingIn: Boolean, onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(GameDealsCustomTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.account_signed_out_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.account_signed_out_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = GameDealsCustomTheme.spacing.medium),
        )
        if (loggingIn) {
            CircularProgressIndicator()
        } else {
            Button(onClick = onLogin) {
                Text(stringResource(Res.string.account_sign_in))
            }
        }
    }
}

@Composable
private fun LoggedInContent(
    data: AccountScreenData,
    onLogout: () -> Unit,
    onReconnect: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.account_signed_in_as, data.username),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                OutlinedButton(onClick = onLogout) { Text(stringResource(Res.string.account_sign_out)) }
            }
        }

        if (data.needsReconnect) {
            item { ReconnectBanner(loggingIn = data.loggingIn, onReconnect = onReconnect) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)) {
                StatCard(stringResource(Res.string.account_stat_waitlisted), data.waitlist.size, Modifier.weight(1f))
                StatCard(stringResource(Res.string.account_stat_collected), data.collection.size, Modifier.weight(1f))
            }
        }

        if (data.waitlist.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.account_section_waitlist)) }
            items(data.waitlist.size, key = { "wl-${data.waitlist[it].gameId}" }) { i ->
                GameRow(data.waitlist[i].title, data.waitlist[i].boxart) { onGameClick(data.waitlist[i].gameId) }
            }
        }

        if (data.collection.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.account_section_collection)) }
            items(data.collection.size, key = { "co-${data.collection[it].gameId}" }) { i ->
                GameRow(data.collection[i].title, data.collection[i].boxart) { onGameClick(data.collection[i].gameId) }
            }
        }
    }
}

@Composable
private fun ReconnectBanner(loggingIn: Boolean, onReconnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(GameDealsCustomTheme.spacing.large)) {
            Text(
                text = stringResource(Res.string.account_reconnect_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.account_reconnect_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.extraSmall),
            )
            Button(
                onClick = onReconnect,
                enabled = !loggingIn,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = GameDealsCustomTheme.spacing.medium),
            ) {
                if (loggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.account_reconnect_action))
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GameDealsCustomTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = count.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = GameDealsCustomTheme.spacing.medium)
            .semantics { heading() },
    )
}

@Composable
private fun GameRow(title: String, boxart: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = boxart,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .height(60.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
        )
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = GameDealsCustomTheme.spacing.medium),
        )
    }
}
