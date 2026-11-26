package pm.bam.gamedeals.common.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Process-level, one-shot signal that a logged-out user attempted a sign-in-gated action (waitlist,
 * collection, ignore, note). The app shell hosts a single sign-in bottom sheet that collects [requests]
 * and offers a one-tap ITAD sign-in, so every gated surface routes here instead of showing its own
 * passive snackbar.
 *
 * Mirrors [SearchController]'s process-level singleton-holder pattern; resets on process death. The buffer
 * (capacity 1, drop-oldest) lets [request] be a non-suspending fire-and-forget from any caller.
 */
object SignInPromptController {
    private val _requests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emitted each time a gated action is attempted while logged out; collected once by the shell host. */
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    /** Request the sign-in prompt (called by a screen when a gated action returns `NOT_LOGGED_IN`). */
    fun request() {
        _requests.tryEmit(Unit)
    }

    /**
     * Like [request], but first waits for the shell host to start collecting. [request] is dropped while nothing is
     * collecting (the buffer only holds values for existing subscribers), which is the case for a caller that runs
     * before the shell has composed — a launcher shortcut or Siri intent on a cold start.
     */
    suspend fun requestWhenReady() {
        _requests.subscriptionCount.first { it > 0 }
        _requests.emit(Unit)
    }
}
