package pm.bam.gamedeals.remote.igdb.auth

/** Test double for [IgdbTokenStore] — records writes so a cold start can be replayed against them. */
internal class InMemoryIgdbTokenStore(
    initial: StoredIgdbToken? = null,
) : IgdbTokenStore {

    var stored: StoredIgdbToken? = initial
        private set

    var saveCount: Int = 0
        private set

    var loadCount: Int = 0
        private set

    override suspend fun load(): StoredIgdbToken? {
        loadCount++
        return stored
    }

    override suspend fun save(token: StoredIgdbToken) {
        saveCount++
        stored = token
    }
}
