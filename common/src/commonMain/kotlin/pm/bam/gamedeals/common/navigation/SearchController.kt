package pm.bam.gamedeals.common.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level holder for the active title-search query (Search lives on the Deals tab — epic #291
 * follow-up). The app-shell toolbar's search field is the single search input: submitting a query sets
 * [activeQuery] (and the host navigates to the Deals tab), closing the field clears it. The Deals screen
 * observes [activeQuery] and runs the search; the Game Page's "no deals → search by title" deep-link also
 * routes through [search].
 *
 * A plain [StateFlow] (rather than a one-shot bus) so the toolbar field and the Deals results stay in
 * sync and survive navigation between tabs; it resets to no-search on process death.
 */
object SearchController {
    private val _activeQuery = MutableStateFlow<String?>(null)

    /** The active search query, or null when there is no active search (browse mode). */
    val activeQuery: StateFlow<String?> = _activeQuery.asStateFlow()

    /** Run a title search; a blank query is treated as [clear]. */
    fun search(query: String) {
        _activeQuery.value = query.trim().ifBlank { null }
    }

    /** Clear the active search (the toolbar field was closed). */
    fun clear() {
        _activeQuery.value = null
    }
}
