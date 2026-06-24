package pm.bam.gamedeals.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormattingTest {

    @Test
    fun `prefix-style currencies render the symbol first`() {
        assertEquals("$9.99", formatMoney(9.99, "USD"))
        assertEquals("€9.99", formatMoney(9.99, "EUR"))
        assertEquals("£9.99", formatMoney(9.99, "GBP"))
        assertEquals("CA$9.99", formatMoney(9.99, "CAD"))
        assertEquals("MX$9.99", formatMoney(9.99, "MXN"))
    }

    @Test
    fun `unknown currency falls back to a trailing code`() {
        // PLN's symbol conventionally trails, so it's deliberately absent from the prefix map.
        assertEquals("9.99 PLN", formatMoney(9.99, "PLN"))
        assertEquals("9.99 ZZZ", formatMoney(9.99, "ZZZ"))
    }

    @Test
    fun `currency code is matched case-insensitively`() {
        assertEquals("$9.99", formatMoney(9.99, "usd"))
        assertEquals("€9.99", formatMoney(9.99, "eur"))
    }

    @Test
    fun `cents are always padded to two digits`() {
        assertEquals("$19.50", formatMoney(19.5, "USD"))
        assertEquals("$19.05", formatMoney(19.05, "USD"))
        assertEquals("$0.00", formatMoney(0.0, "USD"))
    }

    @Test
    fun `amount is rounded to the nearest cent`() {
        assertEquals("$10.00", formatMoney(9.999, "USD"))
        assertEquals("$9.99", formatMoney(9.994, "USD"))
    }

    @Test
    fun `large amounts are rendered without grouping separators`() {
        assertEquals("$1234.00", formatMoney(1234.0, "USD"))
    }

    @Test
    fun `zero-decimal currencies render without decimals`() {
        // JPY has no minor unit, so no trailing ".00".
        assertEquals("¥900", formatMoney(900.0, "JPY"))
        // Still rounds to the nearest whole unit.
        assertEquals("¥901", formatMoney(900.6, "JPY"))
        // A zero-decimal currency without a prefix symbol drops decimals on the trailing-code path too.
        assertEquals("9000 KRW", formatMoney(9000.0, "KRW"))
    }
}
