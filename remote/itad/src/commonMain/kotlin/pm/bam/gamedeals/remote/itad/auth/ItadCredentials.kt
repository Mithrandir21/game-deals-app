package pm.bam.gamedeals.remote.itad.auth

/**
 * IsThereAnyDeal API credential. Provided from `BuildConfig.ITAD_API_KEY` in the app DI graph
 * (mirroring the IGDB credential wiring), and sent as the `ITAD-API-Key` header on every request
 * by [pm.bam.gamedeals.remote.itad.logic.itadHttpClient].
 */
data class ItadCredentials(val apiKey: String)
