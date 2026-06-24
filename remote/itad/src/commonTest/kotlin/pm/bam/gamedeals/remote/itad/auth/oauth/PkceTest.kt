package pm.bam.gamedeals.remote.itad.auth.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkceTest {

    /** RFC 7636 Appendix B test vector — validates the SHA-256 + base64url(no-pad) S256 derivation. */
    @Test
    fun codeChallenge_matches_rfc7636_appendixB_vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", codeChallenge(verifier))
    }

    /** Independent SHA-256 known-answer vector (FIPS 180-2 "abc"). */
    @Test
    fun sha256_matches_known_abc_vector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun generatePkce_challenge_is_consistent_and_url_safe() {
        val pkce = generatePkce()
        assertEquals(codeChallenge(pkce.codeVerifier), pkce.codeChallenge)
        // base64url, no padding/standard-alphabet chars
        assertTrue(pkce.codeChallenge.none { it == '=' || it == '+' || it == '/' })
        assertTrue(pkce.codeVerifier.length in 43..128)
    }

    @Test
    fun generatePkce_is_random_per_call() {
        assertTrue(generatePkce().codeVerifier != generatePkce().codeVerifier)
    }

    @Test
    fun randomState_is_url_safe_and_varies_per_call() {
        val state = randomState()
        assertTrue(state.isNotEmpty())
        assertTrue(state.none { it == '=' || it == '+' || it == '/' })
        assertTrue(randomState() != randomState())
    }

    @Test
    fun secureRandomBytes_returns_requested_size_and_differs_across_calls() {
        assertEquals(0, secureRandomBytes(0).size)
        assertEquals(64, secureRandomBytes(64).size)
        assertTrue(secureRandomBytes(32).toHex() != secureRandomBytes(32).toHex())
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
