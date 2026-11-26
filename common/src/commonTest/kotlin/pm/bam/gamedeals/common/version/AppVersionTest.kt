package pm.bam.gamedeals.common.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    private fun assertOrdered(lower: String, higher: String) {
        val forward = compareVersions(lower, higher)
        val backward = compareVersions(higher, lower)
        assertTrue(forward != null && forward < 0, "expected $lower < $higher, got $forward")
        assertTrue(backward != null && backward > 0, "expected $higher > $lower, got $backward")
    }

    private fun assertEqualPrecedence(a: String, b: String) {
        assertEquals(0, compareVersions(a, b), "expected $a == $b")
        assertEquals(0, compareVersions(b, a), "expected $b == $a")
    }

    @Test
    fun `identical versions have equal precedence`() = assertEqualPrecedence("1.2.3", "1.2.3")

    @Test
    fun `orders by patch then minor then major`() {
        assertOrdered("1.2.3", "1.2.4")
        assertOrdered("1.2.9", "1.3.0")
        assertOrdered("1.99.99", "2.0.0")
    }

    @Test
    fun `components compare numerically not lexically`() {
        // The bug this guards: "1.10.0" sorts before "1.9.0" under string comparison.
        assertOrdered("1.9.0", "1.10.0")
        assertOrdered("1.2.9", "1.2.10")
    }

    @Test
    fun `missing components are zero padded`() {
        assertEqualPrecedence("1.2", "1.2.0")
        assertEqualPrecedence("1", "1.0.0")
        assertOrdered("1.2", "1.2.1")
    }

    @Test
    fun `extra components are honoured`() = assertOrdered("1.2.3", "1.2.3.4")

    @Test
    fun `leading v is stripped`() {
        assertEqualPrecedence("v1.2.3", "1.2.3")
        assertEqualPrecedence("V1.2.3", "1.2.3")
    }

    @Test
    fun `build metadata is ignored`() {
        assertEqualPrecedence("1.2.3+456", "1.2.3")
        assertEqualPrecedence("1.2.3+456", "1.2.3+999")
    }

    @Test
    fun `surrounding whitespace is tolerated`() = assertEqualPrecedence("  1.2.3  ", "1.2.3")

    @Test
    fun `a pre-release precedes its release`() {
        assertOrdered("1.2.0-beta", "1.2.0")
        assertOrdered("1.2.0-rc.1", "1.2.0")
    }

    @Test
    fun `pre-release identifiers order per semver`() {
        assertOrdered("1.0.0-alpha", "1.0.0-beta")
        assertOrdered("1.0.0-alpha.1", "1.0.0-alpha.2")
        assertOrdered("1.0.0-alpha.9", "1.0.0-alpha.10")   // numeric, not lexical
        assertOrdered("1.0.0-1", "1.0.0-alpha")            // numeric sorts below alphanumeric
        assertOrdered("1.0.0-alpha", "1.0.0-alpha.1")      // prefix loses to the longer list
    }

    @Test
    fun `malformed versions return null so callers can fail open`() {
        listOf(
            "",
            "   ",
            "abc",
            "1.a.3",
            "1..3",
            "1.2.3-",       // trailing hyphen, empty pre-release identifier
            "1.2.3-a..b",   // empty identifier inside the pre-release
            "-1.2.3",       // no numeric core at all
            "1.-2.3",
        ).forEach { assertNull(compareVersions(it, "1.0.0"), "expected null for '$it'") }
    }

    @Test
    fun `null propagates from either side`() {
        assertNull(compareVersions("1.0.0", "nonsense"))
        assertNull(compareVersions("nonsense", "1.0.0"))
    }

    @Test
    fun `isBelowMinimum is true only when strictly older`() {
        assertTrue(isBelowMinimum(current = "1.1.3", minimum = "1.2.0"))
        assertFalse(isBelowMinimum(current = "1.2.0", minimum = "1.2.0"))
        assertFalse(isBelowMinimum(current = "1.3.0", minimum = "1.2.0"))
    }

    @Test
    fun `isBelowMinimum fails open on unparseable input`() {
        // A remote-config typo must never gate anyone.
        assertFalse(isBelowMinimum(current = "1.1.3", minimum = "one point two"))
        assertFalse(isBelowMinimum(current = "garbage", minimum = "1.2.0"))
        assertFalse(isBelowMinimum(current = "1.1.3", minimum = ""))
    }

    @Test
    fun `a pre-release build is below the release it precedes`() {
        // A tester on 1.2.0-rc.1 should still be told to move to the 1.2.0 floor.
        assertTrue(isBelowMinimum(current = "1.2.0-rc.1", minimum = "1.2.0"))
    }
}
