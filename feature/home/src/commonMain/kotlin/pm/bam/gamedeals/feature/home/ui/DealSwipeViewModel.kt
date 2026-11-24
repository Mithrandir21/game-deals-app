package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.dealswipe.DealSwipeRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error
import kotlin.coroutines.cancellation.CancellationException

/** Fewer cards than this left in the deck and the next page is fetched. */
internal const val SWIPE_LOW_WATER = 5

/** How many cards from the top get their description looked up ahead of time. */
internal const val SWIPE_DESCRIBE_AHEAD = 3

/**
 * Deal swipe (next-versions M8): a deck of today's hottest deals for signed-in users. A right swipe adds the game to
 * the ITAD waitlist; a left swipe skips it, which only hides it from the deck for a while, on this phone. The last
 * swipe can be undone. The deck refills itself a page at a time until ITAD's hottest list runs out.
 */
internal class DealSwipeViewModel(
    private val dealSwipeRepository: DealSwipeRepository,
    private val waitlistRepository: WaitlistRepository,
    storesRepository: StoresRepository,
    accountRepository: AccountRepository,
    private val logger: Logger,
) : ViewModel() {

    enum class Status { LOADING, READY, FINISHED, FAILED, SIGNED_OUT }

    @Immutable
    data class State(
        val status: Status = Status.LOADING,
        /** The deck, top card first. */
        val cards: ImmutableList<Deal> = persistentListOf(),
        val canUndo: Boolean = false,
        /** Games added to the waitlist from this deck since the screen opened. */
        val addedCount: Int = 0,
    )

    sealed interface Event {
        /** The waitlist add for [deal] failed; the screen offers a retry. */
        data class AddFailed(val deal: Deal) : Event

        /** Taking back a waitlist add failed, so the game is still on the waitlist. */
        data class UndoFailed(val deal: Deal) : Event
    }

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = mutableEvents.asSharedFlow()

    private val mutableDescriptions = MutableStateFlow<ImmutableMap<String, String>>(persistentMapOf())

    /** Game id → a short description, for the cards near the top that have one. */
    val descriptions: StateFlow<ImmutableMap<String, String>> = mutableDescriptions.asStateFlow()

    /** Games whose description was already asked for this visit. */
    private val described = mutableSetOf<String>()

    /** Stores keyed by id, for each card's store name and icon. */
    val stores: StateFlow<ImmutableMap<Int, Store>> = storesRepository.observeStores()
        .map { list -> list.associateBy { it.storeID }.toImmutableMap() }
        .onStart { emit(persistentMapOf()) }
        .catch { emit(persistentMapOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentMapOf())

    /** Where the next page starts; null once the deck has run out. */
    private var nextOffset: Int? = 0

    /** Games already dealt this visit, so a page that shifted while the user swiped can't deal one twice. */
    private val dealt = mutableSetOf<String>()

    private var loadJob: Job? = null

    /** The swipe that undo takes back. [write] is the waitlist add or skip; for an add, true when it added the game. */
    private class LastSwipe(val deal: Deal, val right: Boolean, val write: Deferred<Boolean>)

    private var lastSwipe: LastSwipe? = null

    init {
        viewModelScope.launch {
            accountRepository.observeAuthState()
                .map { it is AuthState.LoggedIn }
                .distinctUntilChanged()
                .collect { signedIn -> if (signedIn) restart() else signOut() }
        }
        viewModelScope.launch {
            mutableState.map { it.cards.take(SWIPE_DESCRIBE_AHEAD) }
                .distinctUntilChanged()
                .collect { top -> top.filter { described.add(it.gameID) }.forEach { describe(it) } }
        }
    }

    private fun describe(deal: Deal) {
        viewModelScope.launch {
            val text = dealSwipeRepository.description(deal) ?: return@launch
            mutableDescriptions.update { (it + (deal.gameID to text)).toImmutableMap() }
        }
    }

    fun onSwipeRight(deal: Deal) {
        if (!takeTop(deal)) return
        lastSwipe = LastSwipe(deal, right = true, write = viewModelScope.async { add(deal) })
        mutableState.update { it.copy(canUndo = true) }
    }

    fun onSwipeLeft(deal: Deal) {
        if (!takeTop(deal)) return
        val write = viewModelScope.async {
            runCatching { dealSwipeRepository.skip(deal.gameID) }
                .onFailure { if (it is CancellationException) throw it; error(logger, it) { "Couldn't save a swipe skip" } }
            true
        }
        lastSwipe = LastSwipe(deal, right = false, write = write)
        mutableState.update { it.copy(canUndo = true) }
    }

    /** Puts the last swiped card back on top and takes back its add or skip. */
    fun onUndo() {
        val swipe = lastSwipe ?: return
        lastSwipe = null
        mutableState.update {
            it.copy(cards = (listOf(swipe.deal) + it.cards).toImmutableList(), canUndo = false, status = Status.READY)
        }
        viewModelScope.launch {
            val wrote = swipe.write.await()
            if (!swipe.right) {
                runCatching { dealSwipeRepository.unskip(swipe.deal.gameID) }
                    .onFailure { if (it is CancellationException) throw it; error(logger, it) { "Couldn't undo a swipe skip" } }
            } else if (wrote) {
                // The waitlist write is a toggle, so only take back an add this deck made, and only once it landed.
                if (waitlistRepository.toggleWaitlist(swipe.deal.gameID) == RepoUpdateResult.UPDATED) {
                    mutableState.update { it.copy(addedCount = (it.addedCount - 1).coerceAtLeast(0)) }
                } else {
                    mutableEvents.emit(Event.UndoFailed(swipe.deal))
                }
            }
        }
    }

    /** Tries a failed add again (the snackbar's retry). */
    fun onRetryAdd(deal: Deal) {
        viewModelScope.launch { add(deal) }
    }

    /** Tries the first page again after it failed. */
    fun onRetry() {
        if (mutableState.value.status != Status.FAILED) return
        mutableState.update { it.copy(status = Status.LOADING) }
        refill()
    }

    /** Adds [deal]'s game to the waitlist; true only when this call added it. */
    private suspend fun add(deal: Deal): Boolean {
        if (deal.gameID in waitlistRepository.observeWaitlistIds().first()) return false
        return when (waitlistRepository.toggleWaitlist(deal.gameID)) {
            RepoUpdateResult.UPDATED -> {
                mutableState.update { it.copy(addedCount = it.addedCount + 1) }
                true
            }
            RepoUpdateResult.NOT_LOGGED_IN -> {
                signOut()
                false
            }
            RepoUpdateResult.FAILED -> {
                mutableEvents.emit(Event.AddFailed(deal))
                false
            }
        }
    }

    /** Takes [deal] off the top of the deck; false when it isn't the top card (a second callback for one swipe). */
    private fun takeTop(deal: Deal): Boolean {
        val cards = mutableState.value.cards
        if (cards.firstOrNull()?.dealID != deal.dealID) return false
        mutableState.update { it.copy(cards = cards.drop(1).toImmutableList()) }
        refill()
        return true
    }

    private fun restart() {
        loadJob?.cancel()
        nextOffset = 0
        dealt.clear()
        lastSwipe = null
        mutableState.value = State()
        refill()
    }

    private fun signOut() {
        loadJob?.cancel()
        lastSwipe = null
        mutableState.value = State(status = Status.SIGNED_OUT)
    }

    /** Fetches pages until the deck has more than [SWIPE_LOW_WATER] cards or ITAD's list runs out. */
    private fun refill() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            while (mutableState.value.cards.size <= SWIPE_LOW_WATER) {
                val offset = nextOffset ?: break
                val page = try {
                    dealSwipeRepository.nextPage(offset)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    error(logger, failure) { "Couldn't load swipe deals at $offset" }
                    // With cards left, keep swiping; the next swipe tries again.
                    if (mutableState.value.cards.isEmpty()) mutableState.update { it.copy(status = Status.FAILED) }
                    return@launch
                }
                nextOffset = page.nextOffset
                val fresh = page.deals.filter { dealt.add(it.gameID) }
                mutableState.update {
                    val cards = (it.cards + fresh).toImmutableList()
                    // Stay on the spinner while every game so far was one the user has, wants or skipped.
                    it.copy(cards = cards, status = if (cards.isEmpty()) it.status else Status.READY)
                }
            }
            if (mutableState.value.cards.isEmpty() && nextOffset == null) {
                mutableState.update { it.copy(status = Status.FINISHED) }
            }
        }
    }
}
