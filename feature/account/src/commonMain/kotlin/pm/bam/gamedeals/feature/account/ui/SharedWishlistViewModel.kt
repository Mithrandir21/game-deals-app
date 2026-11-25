package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.links.ShareLinks
import pm.bam.gamedeals.common.ui.share.DealShareBuilder
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.sharedwishlist.SharedWishlist
import pm.bam.gamedeals.domain.repositories.sharedwishlist.SharedWishlistGame
import pm.bam.gamedeals.domain.repositories.sharedwishlist.SharedWishlistRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.runCatchingLogged
import pm.bam.gamedeals.common.exceptions.isRateLimited

@Immutable
internal data class SharedWishlistRowUi(
    val gameId: String,
    val title: String,
    val imageUrl: String?,
    val salePrice: String?,
    val regularPrice: String?,
    val discountPercent: Int,
    val storeName: String?,
    val storeIconUrl: String?,
    val hasVoucher: Boolean,
    val isNewHistoricalLow: Boolean,
    val isStoreLow: Boolean,
    val isLowestEver: Boolean,
    val isWaitlisted: Boolean,
    /** The best price as a number, for cheapest-first; null when the game isn't on sale anywhere. */
    val bestPriceValue: Double?,
)

@Immutable
internal data class SharedWishlistUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** The load failed because IsThereAnyDeal is rate-limiting the app, not because the phone is offline. */
    val itadBusy: Boolean = false,
    val signedIn: Boolean = false,
    /** Signed in: the friend's games that are also on the user's waitlist, cheapest first. */
    val bothWant: ImmutableList<SharedWishlistRowUi> = persistentListOf(),
    /** The rest of the friend's list (all of it when signed out), cheapest first. */
    val others: ImmutableList<SharedWishlistRowUi> = persistentListOf(),
    /** Games in the link that couldn't be loaded right now. */
    val unavailableCount: Int = 0,
) {
    val isEmpty: Boolean get() = !loading && !failed && bothWant.isEmpty() && others.isEmpty()
}

/**
 * Backs a friend's shared wishlist (`gamedeals.bam.pm/w/…`, see [ShareLinks]). The link's games are loaded once
 * ([SharedWishlistRepository]); signed in, the ones also on the user's own waitlist come first under "You both want".
 * Both groups are cheapest first, games with no current deal last. Signing in from this screen re-checks the
 * waitlist, so the overlap appears without reopening the link. Rows open the shared peek sheet.
 */
internal class SharedWishlistViewModel(
    savedStateHandle: SavedStateHandle,
    private val sharedWishlistRepository: SharedWishlistRepository,
    accountRepository: AccountRepository,
    private val waitlistRepository: WaitlistRepository,
    storesRepository: StoresRepository,
    gamesRepository: GamesRepository,
    collectionRepository: CollectionRepository,
    ignoredRepository: IgnoredRepository,
    dealShareBuilder: DealShareBuilder,
    private val logger: Logger,
) : ViewModel() {

    val peek = GamePeekDelegate(
        viewModelScope, gamesRepository, storesRepository, waitlistRepository,
        collectionRepository, ignoredRepository, dealShareBuilder, logger,
    )

    // The route carries the ids comma-joined; the link was already checked by ShareLinks, but cap it again here.
    private val gameIds: List<String> = savedStateHandle.get<String>("gameIds").orEmpty()
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(ShareLinks.MAX_WISHLIST_GAMES)

    private sealed interface Load {
        data object Loading : Load
        data class Failed(val busy: Boolean) : Load
        data class Done(val list: SharedWishlist) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)

    val uiState: StateFlow<SharedWishlistUiState>
        field = MutableStateFlow(SharedWishlistUiState())

    init {
        fetch()
        val signedIn = accountRepository.observeAuthState().map { it is AuthState.LoggedIn }.distinctUntilChanged()
        // Signing in here (from the card) or arriving signed in: make sure the waitlist id set is current, so the
        // overlap is right. The repository reuses a fetch made moments ago.
        viewModelScope.launch {
            signedIn.collect { if (it) runCatchingLogged(logger) { waitlistRepository.getWaitlist() } }
        }
        viewModelScope.launch {
            val storeIcons = storesRepository.observeStores().map { stores -> stores.associate { it.storeName to it.iconUrl } }
            combine(load, signedIn, waitlistRepository.observeWaitlistIds(), storeIcons) { load, isSignedIn, waitlistIds, icons ->
                load.toUiState(isSignedIn, waitlistIds, icons)
            }.collect { uiState.value = it }
        }
    }

    fun retry() {
        if (load.value is Load.Failed) fetch()
    }

    private fun fetch() {
        load.value = Load.Loading
        viewModelScope.launch {
            load.value = runCatchingLogged(logger) { sharedWishlistRepository.load(gameIds) }
                .fold(onSuccess = { Load.Done(it) }, onFailure = { Load.Failed(busy = it.isRateLimited()) })
        }
    }

    private fun Load.toUiState(signedIn: Boolean, waitlistIds: Set<String>, icons: Map<String, String>): SharedWishlistUiState =
        when (this) {
            Load.Loading -> SharedWishlistUiState(loading = true, signedIn = signedIn)
            is Load.Failed -> SharedWishlistUiState(loading = false, failed = true, itadBusy = busy, signedIn = signedIn)
            is Load.Done -> {
                // The waitlist id set is empty while signed out, so nothing counts as shared then.
                val rows = list.games.map { it.toRowUi(isWaitlisted = signedIn && it.gameId in waitlistIds, icons) }
                    .sortedWith(CHEAPEST_FIRST)
                SharedWishlistUiState(
                    loading = false,
                    signedIn = signedIn,
                    bothWant = rows.filter { it.isWaitlisted }.toImmutableList(),
                    others = rows.filterNot { it.isWaitlisted }.toImmutableList(),
                    unavailableCount = list.unavailableCount,
                )
            }
        }

    private companion object {
        val CHEAPEST_FIRST: Comparator<SharedWishlistRowUi> =
            compareBy<SharedWishlistRowUi> { it.bestPriceValue ?: Double.MAX_VALUE }.thenBy { it.title.lowercase() }
    }
}

private fun SharedWishlistGame.toRowUi(isWaitlisted: Boolean, iconsByStore: Map<String, String>): SharedWishlistRowUi {
    val deal = price?.takeIf { it.bestPriceDenominated != null && it.bestPriceValue != null }
    return SharedWishlistRowUi(
        gameId = gameId,
        title = title,
        imageUrl = artwork.thumbnail,
        salePrice = deal?.bestPriceDenominated,
        regularPrice = deal?.bestRegularDenominated,
        discountPercent = deal?.bestCutPercent ?: 0,
        storeName = deal?.bestShopName,
        storeIconUrl = deal?.bestShopName?.let { iconsByStore[it]?.takeIf(String::isNotBlank) },
        hasVoucher = deal?.bestHasVoucher == true,
        isNewHistoricalLow = deal?.bestIsNewHistoricalLow == true,
        isStoreLow = deal?.bestIsStoreLow == true,
        isLowestEver = deal?.bestIsLowestEver == true,
        isWaitlisted = isWaitlisted,
        bestPriceValue = deal?.bestPriceValue,
    )
}
