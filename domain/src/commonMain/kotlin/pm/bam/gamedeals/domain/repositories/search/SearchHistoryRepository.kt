package pm.bam.gamedeals.domain.repositories.search

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.domain.models.SavedSearch

/**
 * Local, client-side search history for the Deals tab (#6): a capped list of recently submitted queries
 * and a list of user-named [SavedSearch] presets (query + filter). Persisted via [Storage] under the
 * settings backend — no account/backend involved. Exposed reactively so the Deals UI updates as the user
 * searches and saves.
 */
interface SearchHistoryRepository {
    fun observeRecentSearches(): Flow<List<String>>
    fun observeSavedSearches(): Flow<List<SavedSearch>>

    /** Record a submitted query at the front (de-duped case-insensitively, capped). Blanks are ignored. */
    suspend fun recordSearch(query: String)
    suspend fun clearRecentSearches()

    /** Pin a query + filter under [name] (replacing any existing preset with the same name). */
    suspend fun saveSearch(name: String, query: String, filter: DealsFilter)
    suspend fun removeSavedSearch(name: String)
}

internal const val RECENT_SEARCHES_KEY = "recent_searches"
internal const val SAVED_SEARCHES_KEY = "saved_searches"
internal const val MAX_RECENT_SEARCHES = 10
internal const val MAX_SAVED_SEARCHES = 20

internal class SearchHistoryRepositoryImpl(
    private val storage: Storage,
    private val clock: Clock,
) : SearchHistoryRepository {

    private val recent = MutableStateFlow<List<String>?>(null)
    private val saved = MutableStateFlow<List<SavedSearch>?>(null)

    // Serializes each whole-list read-modify-write so concurrent updates can't drop entries (lost update).
    private val recentMutex = Mutex()
    private val savedMutex = Mutex()

    override fun observeRecentSearches(): Flow<List<String>> =
        recent.onStart { recentMutex.withLock { ensureRecentLoaded() } }.filterNotNull()

    override fun observeSavedSearches(): Flow<List<SavedSearch>> =
        saved.onStart { savedMutex.withLock { ensureSavedLoaded() } }.filterNotNull()

    override suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        recentMutex.withLock {
            val current = ensureRecentLoaded()
            val next = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            persistRecent(next)
        }
    }

    override suspend fun clearRecentSearches() = recentMutex.withLock { persistRecent(emptyList()) }

    override suspend fun saveSearch(name: String, query: String, filter: DealsFilter) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        savedMutex.withLock {
            val current = ensureSavedLoaded()
            val entry = SavedSearch(name = trimmedName, query = query.trim(), filter = filter, addedAtMs = clock.nowMillis())
            val next = (listOf(entry) + current.filterNot { it.name.equals(trimmedName, ignoreCase = true) })
                .take(MAX_SAVED_SEARCHES)
            persistSaved(next)
        }
    }

    override suspend fun removeSavedSearch(name: String) = savedMutex.withLock {
        persistSaved(ensureSavedLoaded().filterNot { it.name.equals(name, ignoreCase = true) })
    }

    // --- Storage plumbing (callers must hold the matching mutex; Mutex is not reentrant) ---

    private suspend fun ensureRecentLoaded(): List<String> {
        if (recent.value == null) recent.value = loadRecent()
        return recent.value.orEmpty()
    }

    private suspend fun ensureSavedLoaded(): List<SavedSearch> {
        if (saved.value == null) saved.value = loadSaved()
        return saved.value.orEmpty()
    }

    private suspend fun persistRecent(list: List<String>) {
        storage.save(RECENT_SEARCHES_KEY, list, ListSerializer(String.serializer()))
        recent.value = list
    }

    private suspend fun persistSaved(list: List<SavedSearch>) {
        storage.save(SAVED_SEARCHES_KEY, list, ListSerializer(SavedSearch.serializer()))
        saved.value = list
    }

    private suspend fun loadRecent(): List<String> =
        runCatching { storage.getNullable(RECENT_SEARCHES_KEY, ListSerializer(String.serializer())) }.getOrNull() ?: emptyList()

    private suspend fun loadSaved(): List<SavedSearch> =
        runCatching { storage.getNullable(SAVED_SEARCHES_KEY, ListSerializer(SavedSearch.serializer())) }.getOrNull() ?: emptyList()
}
