package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.ItadUser

/**
 * Runs the full ITAD OAuth login (epic #219, Phase 2.4) — the browser authorize round-trip, code
 * exchange, `/user/info` fetch, and token persistence — behind a domain seam so feature code depends
 * only on [pm.bam.gamedeals.domain.repositories.account.AccountRepository], never on the OAuth/browser
 * internals (which live in `:remote:itad`).
 */
interface ItadLoginSource {
    /** Performs the login; returns the signed-in [ItadUser], or null if the user cancelled the browser. */
    suspend fun login(): ItadUser?
}
