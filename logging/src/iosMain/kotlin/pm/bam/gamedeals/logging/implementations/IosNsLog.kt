package pm.bam.gamedeals.logging.implementations

import platform.Foundation.NSLog

// Single-arg NSLog: the message is the format string. Pre-escape `%` so user content
// can't be reinterpreted as a format specifier (and because Kotlin/Native's varargs
// bridge to NSLog crashes on `%@` with a Kotlin String).
fun iosLog(line: String) {
    NSLog(line.replace("%", "%%"))
}
